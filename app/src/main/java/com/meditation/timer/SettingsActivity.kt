package com.meditation.timer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.meditation.timer.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            binding.dailyReminderSwitch.isChecked = false
            AppSettings.setDailyReminderEnabled(this, false)
            ReminderScheduler.cancel(this)
        }
    }

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
                ensureNotificationPermission()
                ReminderScheduler.schedule(this)
            } else {
                ReminderScheduler.cancel(this)
            }
        }

        binding.signOutButton.setOnClickListener {
            AuthManager.getSignInClient(this).signOut().addOnCompleteListener {
                Toast.makeText(this, "Signed out.", Toast.LENGTH_SHORT).show()
            }
        }
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

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
