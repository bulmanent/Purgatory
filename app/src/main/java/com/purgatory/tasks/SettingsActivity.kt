package com.purgatory.tasks

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.purgatory.tasks.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var defaultUserOptions: List<String>

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
    }

    override fun onPause() {
        super.onPause()
        val id = binding.spreadsheetIdInput.text?.toString()?.trim().orEmpty()
        AppSettings.setSpreadsheetId(this, id)
        persistDefaultUser(binding.defaultUserInput.text?.toString())
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

}
