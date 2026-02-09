package com.purgatory.tasks

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.purgatory.tasks.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.settingsToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val users = AppUsers.all.map { it.displayName }
        binding.defaultUserInput.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, users)
        )

        val defaultUser = AppSettings.getDefaultUser(this)
        binding.defaultUserInput.setText(defaultUser ?: users.firstOrNull(), false)
        binding.defaultUserInput.setOnItemClickListener { _, _, position, _ ->
            AppSettings.setDefaultUser(this, users[position])
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
    }

    override fun onPause() {
        super.onPause()
        val id = binding.spreadsheetIdInput.text?.toString()?.trim().orEmpty()
        AppSettings.setSpreadsheetId(this, id)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

}
