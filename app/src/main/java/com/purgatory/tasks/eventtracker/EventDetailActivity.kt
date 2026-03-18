package com.purgatory.tasks.eventtracker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.purgatory.tasks.R
import com.purgatory.tasks.databinding.ActivityEventDetailBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EventDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEventDetailBinding
    private val repository by lazy { EventTrackerRepository(this) }
    private val logAdapter = EventLogAdapter()
    private var reportInProgress = false

    private val eventName: String by lazy {
        intent.getStringExtra(EXTRA_EVENT_NAME).orEmpty()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.eventDetailToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = eventName

        binding.eventLogList.layoutManager = LinearLayoutManager(this)
        binding.eventLogList.adapter = logAdapter

        binding.detailLogButton.setOnClickListener {
            EventLogDialogHelper.show(this, repository, eventName) {
                loadEventLogs()
            }
        }
        binding.detailReportButton.setOnClickListener {
            exportReport()
        }

        binding.eventDetailRefresh.setOnRefreshListener {
            loadEventLogs()
        }

        loadEventLogs()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadEventLogs() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val entries = withContext(Dispatchers.IO) {
                    repository.getEventLog(eventName)
                }
                logAdapter.submitList(entries)
                showEmptyState(entries.isEmpty())
            } catch (ex: Exception) {
                showEmptyState(true)
                Snackbar.make(
                    binding.root,
                    ex.message ?: getString(R.string.event_error_generic),
                    Snackbar.LENGTH_LONG
                ).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.eventDetailRefresh.isRefreshing = isLoading
    }

    private fun showEmptyState(isEmpty: Boolean) {
        binding.detailEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.eventDetailRefresh.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun exportReport() {
        if (reportInProgress) return

        reportInProgress = true
        binding.detailReportButton.isEnabled = false

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.exportToReport(listOf(eventName))
            }
            reportInProgress = false
            binding.detailReportButton.isEnabled = true

            if (result.isSuccess) {
                showReportSuccessDialog(result.getOrNull() ?: 0)
            } else {
                Snackbar.make(
                    binding.root,
                    result.exceptionOrNull()?.message ?: getString(R.string.event_error_generic),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showReportSuccessDialog(rowCount: Int) {
        val dialog = MaterialAlertDialogBuilder(this)
            .setMessage(getString(R.string.event_report_success_single, eventName, rowCount))
            .setPositiveButton(R.string.event_report_close, null)
            .setNegativeButton(R.string.event_report_copy_csv, null)
            .create()

        dialog.setOnShowListener {
            val copyButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            copyButton.setOnClickListener {
                lifecycleScope.launch {
                    try {
                        val entries = withContext(Dispatchers.IO) {
                            repository.getEventLog(eventName)
                        }
                        val csv = buildCsv(entries)
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("event_report", csv))
                        Snackbar.make(
                            binding.root,
                            getString(R.string.event_report_copied),
                            Snackbar.LENGTH_SHORT
                        ).show()
                    } catch (ex: Exception) {
                        Snackbar.make(
                            binding.root,
                            ex.message ?: getString(R.string.event_error_generic),
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun buildCsv(entries: List<EventEntry>): String {
        val lines = mutableListOf("Event,Date,Time,Details,Severity,Action")
        entries.forEach { entry ->
            lines += listOf(
                entry.event,
                entry.date,
                entry.time,
                entry.details,
                entry.severity,
                entry.action
            ).joinToString(",") { value ->
                "\"${value.replace("\"", "\"\"")}\""
            }
        }
        return lines.joinToString("\n")
    }

    companion object {
        const val EXTRA_EVENT_NAME = "extra_event_name"
    }
}
