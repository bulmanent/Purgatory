package com.purgatory.tasks.eventtracker

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
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

    companion object {
        const val EXTRA_EVENT_NAME = "extra_event_name"
    }
}
