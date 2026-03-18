package com.purgatory.tasks.eventtracker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.purgatory.tasks.R
import com.purgatory.tasks.databinding.ActivityEventTrackerBinding
import com.purgatory.tasks.databinding.DialogAddEventTypeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EventTrackerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEventTrackerBinding
    private val repository by lazy { EventTrackerRepository(this) }
    private val eventTypeAdapter = EventTypeAdapter(
        onLogClicked = { item ->
            EventLogDialogHelper.show(this, repository, item.name) {
                loadEventTypes()
            }
        },
        onDetailsClicked = { item ->
            val intent = Intent(this, EventDetailActivity::class.java)
            intent.putExtra(EventDetailActivity.EXTRA_EVENT_NAME, item.name)
            startActivity(intent)
        }
    )

    private var reportInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventTrackerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.eventTrackerToolbar)

        binding.eventTypeList.layoutManager = LinearLayoutManager(this)
        binding.eventTypeList.adapter = eventTypeAdapter

        binding.newEventTypeButton.setOnClickListener {
            showAddEventTypeDialog()
        }
        binding.eventTrackerRefresh.setOnRefreshListener {
            loadEventTypes()
        }
        binding.eventRetryButton.setOnClickListener {
            loadEventTypes()
        }

        loadEventTypes()
    }

    override fun onResume() {
        super.onResume()
        loadEventTypes()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.event_tracker_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_report)?.isEnabled = !reportInProgress
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_report -> {
                exportReport()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadEventTypes() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val eventTypes = withContext(Dispatchers.IO) {
                    repository.getEventTypes()
                }
                eventTypeAdapter.submitList(eventTypes)
                showEmptyState(eventTypes.isEmpty())
            } catch (ex: Exception) {
                showEmptyState(true)
                showSnackbar(ex.message ?: getString(R.string.event_error_generic), Snackbar.LENGTH_LONG)
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.eventTrackerRefresh.isRefreshing = isLoading
    }

    private fun showEmptyState(isEmpty: Boolean) {
        binding.eventEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.eventTrackerRefresh.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun showAddEventTypeDialog() {
        val dialogBinding = DialogAddEventTypeBinding.inflate(layoutInflater)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.event_add_type_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.task_save, null)
            .setNegativeButton(R.string.task_cancel, null)
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val cancelButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            saveButton.setOnClickListener {
                val name = dialogBinding.eventTypeNameInput.text?.toString().orEmpty().trim()
                dialogBinding.eventTypeNameLayout.error = null
                if (name.isBlank()) {
                    dialogBinding.eventTypeNameLayout.error = getString(R.string.event_type_required)
                    return@setOnClickListener
                }

                setAddTypeSavingState(dialogBinding, saveButton, cancelButton, true)
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        repository.addEventType(name)
                    }

                    if (result.isSuccess) {
                        dialog.dismiss()
                        loadEventTypes()
                        showSnackbar(getString(R.string.event_type_saved), Snackbar.LENGTH_SHORT)
                    } else {
                        setAddTypeSavingState(dialogBinding, saveButton, cancelButton, false)
                        dialogBinding.eventTypeNameLayout.error =
                            result.exceptionOrNull()?.message ?: getString(R.string.event_error_generic)
                    }
                }
            }
        }

        dialog.show()
    }

    private fun setAddTypeSavingState(
        binding: DialogAddEventTypeBinding,
        saveButton: Button,
        cancelButton: Button,
        isSaving: Boolean
    ) {
        binding.addTypeProgress.visibility = if (isSaving) View.VISIBLE else View.GONE
        binding.eventTypeNameInput.isEnabled = !isSaving
        saveButton.isEnabled = !isSaving
        cancelButton.isEnabled = !isSaving
    }

    private fun exportReport() {
        reportInProgress = true
        invalidateOptionsMenu()

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.exportToReport()
            }
            reportInProgress = false
            invalidateOptionsMenu()

            if (result.isSuccess) {
                val rowCount = result.getOrNull() ?: 0
                showReportSuccessDialog(rowCount)
            } else {
                showSnackbar(
                    result.exceptionOrNull()?.message ?: getString(R.string.event_error_generic),
                    Snackbar.LENGTH_LONG
                )
            }
        }
    }

    private fun showReportSuccessDialog(rowCount: Int) {
        val dialog = MaterialAlertDialogBuilder(this)
            .setMessage(getString(R.string.event_report_success, rowCount))
            .setPositiveButton(R.string.event_report_close, null)
            .setNegativeButton(R.string.event_report_copy_csv, null)
            .create()

        dialog.setOnShowListener {
            val copyButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            copyButton.setOnClickListener {
                lifecycleScope.launch {
                    try {
                        val entries = withContext(Dispatchers.IO) {
                            repository.getEventLog(null)
                        }
                        val csv = buildCsv(entries)
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("event_report", csv))
                        showSnackbar(getString(R.string.event_report_copied), Snackbar.LENGTH_SHORT)
                    } catch (ex: Exception) {
                        showSnackbar(ex.message ?: getString(R.string.event_error_generic), Snackbar.LENGTH_LONG)
                    }
                }
            }
        }

        dialog.show()
    }

    private fun buildCsv(entries: List<EventEntry>): String {
        val lines = mutableListOf("Event,Date,Time,Details,Severity,Action")
        entries.forEach { entry ->
            lines.add(
                listOf(
                    entry.event,
                    entry.date,
                    entry.time,
                    entry.details,
                    entry.severity,
                    entry.action
                ).joinToString(",") { escapeCsv(it) }
            )
        }
        return lines.joinToString("\n")
    }

    private fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun showSnackbar(message: String, length: Int) {
        Snackbar.make(binding.root, message, length).show()
    }
}
