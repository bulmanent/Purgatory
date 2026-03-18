package com.purgatory.tasks.eventtracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.purgatory.tasks.MainActivity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventTrackerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.eventTrackerToolbar)
        supportActionBar?.title = null
        binding.topTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (tab.position == 0) {
                    val intent = Intent(this@EventTrackerActivity, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent)
                    overridePendingTransition(0, 0)
                    finish()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

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
        binding.topTabs.getTabAt(1)?.select()
        loadEventTypes()
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

    private fun showSnackbar(message: String, length: Int) {
        Snackbar.make(binding.root, message, length).show()
    }
}
