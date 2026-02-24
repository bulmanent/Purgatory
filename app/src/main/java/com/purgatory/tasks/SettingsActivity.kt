package com.purgatory.tasks

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.purgatory.tasks.databinding.ActivitySettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var defaultUserOptions: List<String>
    private val repository = SheetsRepository()
    private val diagnosticDateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.settingsToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val users = AppUsers.all.map { it.displayName }
        defaultUserOptions = listOf(getString(R.string.filter_all)) + users
        binding.defaultUserInput.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, defaultUserOptions)
        )

        val defaultUser = AppSettings.getDefaultUser(this)
        binding.defaultUserInput.setText(defaultUser ?: getString(R.string.filter_all), false)
        binding.defaultUserInput.setOnItemClickListener { _, _, position, _ ->
            persistDefaultUser(defaultUserOptions[position])
            refreshNotificationDiagnostics()
        }

        binding.spreadsheetIdInput.setText(AppSettings.getSpreadsheetId(this).orEmpty())

        binding.dailyReminderSwitch.isChecked = AppSettings.isDailyReminderEnabled(this)
        binding.dailyReminderSwitch.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setDailyReminderEnabled(this, isChecked)
            if (isChecked) {
                ReminderScheduler.schedule(this)
            } else {
                ReminderScheduler.cancel(this)
            }
        }

        binding.signOutButton.isEnabled = false

        binding.refreshTasksButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_FORCE_REFRESH, true)
            }
            startActivity(intent)
        }

        refreshNotificationDiagnostics()
    }

    override fun onResume() {
        super.onResume()
        refreshNotificationDiagnostics()
    }

    override fun onPause() {
        super.onPause()
        val id = binding.spreadsheetIdInput.text?.toString()?.trim().orEmpty()
        AppSettings.setSpreadsheetId(this, id)
        persistDefaultUser(binding.defaultUserInput.text?.toString())
        BackgroundTaskSyncScheduler.schedule(this)
    }

    private fun persistDefaultUser(rawValue: String?) {
        val selected = rawValue?.trim().orEmpty()
        val allLabel = getString(R.string.filter_all)
        if (selected.isBlank() || selected.equals(allLabel, ignoreCase = true)) {
            AppSettings.clearDefaultUser(this)
            binding.defaultUserInput.setText(allLabel, false)
            return
        }

        val user = AppUsers.byDisplayName(selected)
        if (user != null) {
            AppSettings.setDefaultUser(this, user.displayName)
            binding.defaultUserInput.setText(user.displayName, false)
        } else {
            AppSettings.clearDefaultUser(this)
            binding.defaultUserInput.setText(allLabel, false)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun refreshNotificationDiagnostics() {
        updateDiagnosticsUi(
            defaultUserLabel = AppSettings.getDefaultUser(this) ?: getString(R.string.filter_all),
            eligibleCountLabel = getString(R.string.diagnostic_loading),
            nextReminderLabel = getString(R.string.diagnostic_loading)
        )

        lifecycleScope.launch {
            val spreadsheetId = AppSettings.getSpreadsheetId(this@SettingsActivity)
            if (spreadsheetId.isNullOrBlank()) {
                updateDiagnosticsUi(
                    defaultUserLabel = AppSettings.getDefaultUser(this@SettingsActivity) ?: getString(R.string.filter_all),
                    eligibleCountLabel = getString(R.string.diagnostic_none),
                    nextReminderLabel = getString(R.string.diagnostic_none)
                )
                return@launch
            }

            try {
                val result = withContext(Dispatchers.IO) {
                    val token = ServiceAccountAuth.getAccessToken(this@SettingsActivity)
                    val tasks = repository.loadTasks(token, spreadsheetId)
                    val eligible = TaskReminderScheduler.eligibleTasks(this@SettingsActivity, tasks)
                    val next = TaskReminderScheduler.nextReminderTime(eligible, LocalDateTime.now())
                    Triple(
                        AppSettings.getDefaultUser(this@SettingsActivity) ?: getString(R.string.filter_all),
                        eligible.size,
                        next
                    )
                }
                val nextLabel = result.third?.format(diagnosticDateTimeFormatter)
                    ?: getString(R.string.diagnostic_none)
                updateDiagnosticsUi(
                    defaultUserLabel = result.first,
                    eligibleCountLabel = result.second.toString(),
                    nextReminderLabel = nextLabel
                )
            } catch (_: Exception) {
                updateDiagnosticsUi(
                    defaultUserLabel = AppSettings.getDefaultUser(this@SettingsActivity) ?: getString(R.string.filter_all),
                    eligibleCountLabel = getString(R.string.diagnostic_error),
                    nextReminderLabel = getString(R.string.diagnostic_error)
                )
            }
        }
    }

    private fun updateDiagnosticsUi(
        defaultUserLabel: String,
        eligibleCountLabel: String,
        nextReminderLabel: String
    ) {
        binding.diagnosticDefaultUserText.text =
            getString(R.string.diagnostic_default_user, defaultUserLabel)
        binding.diagnosticEligibleCountText.text =
            getString(R.string.diagnostic_eligible_count, eligibleCountLabel)
        binding.diagnosticNextReminderText.text =
            getString(R.string.diagnostic_next_reminder, nextReminderLabel)
    }

}
