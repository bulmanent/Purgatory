package com.meditation.timer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.meditation.timer.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.settingsToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.keepScreenAwakeSwitch.isChecked = AppSettings.isKeepScreenAwakeEnabled(this)
        binding.keepScreenAwakeSwitch.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setKeepScreenAwakeEnabled(this, isChecked)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
