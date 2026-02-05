package com.meditation.timer

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.meditation.timer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var presetManager: PresetManager
    private var currentEntrainmentUri: Uri? = null
    private var currentMusicUri: Uri? = null
    private var currentStartChimeUri: Uri? = null
    private var currentIntervalChimeUri: Uri? = null
    private var currentEndChimeUri: Uri? = null
    private var isEntrainmentMuted = false
    private var isMusicMuted = false
    private var service: MeditationTimerService? = null
    private var isBound = false
    private var pendingAudioPickerTarget: AudioPickerTarget? = null

    private val entrainmentPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        handlePickedUri(uri, "Unable to persist entrainment permission.") { pickedUri ->
            currentEntrainmentUri = pickedUri
            binding.entrainmentFilename.text = pickedUri.lastPathSegment ?: pickedUri.toString()
        }
    }

    private val musicPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        handlePickedUri(uri, "Unable to persist music permission.") { pickedUri ->
            currentMusicUri = pickedUri
            binding.musicFilename.text = pickedUri.lastPathSegment ?: pickedUri.toString()
        }
    }

    private val startChimePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        handlePickedUri(uri, "Unable to persist start chime permission.") { pickedUri ->
            currentStartChimeUri = pickedUri
            binding.startChimeFilename.text = pickedUri.lastPathSegment ?: pickedUri.toString()
        }
    }

    private val intervalChimePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        handlePickedUri(uri, "Unable to persist interval chime permission.") { pickedUri ->
            currentIntervalChimeUri = pickedUri
            binding.intervalChimeFilename.text = pickedUri.lastPathSegment ?: pickedUri.toString()
        }
    }

    private val endChimePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        handlePickedUri(uri, "Unable to persist end chime permission.") { pickedUri ->
            currentEndChimeUri = pickedUri
            binding.endChimeFilename.text = pickedUri.lastPathSegment ?: pickedUri.toString()
        }
    }

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "Notifications are needed for timer status.", Toast.LENGTH_LONG).show()
        }
    }

    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchPendingPicker()
        } else {
            Toast.makeText(this, "Media access is required to pick audio files.", Toast.LENGTH_LONG).show()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val localBinder = binder as MeditationTimerService.LocalBinder
            service = localBinder.getService()
            isBound = true
            runOnUiThread {
                if (isEntrainmentMuted) {
                    service?.setEntrainmentVolume(0f)
                } else {
                    service?.setEntrainmentVolume(binding.entrainmentVolumeSlider.value)
                }
                if (isMusicMuted) {
                    service?.setMusicVolume(0f)
                } else {
                    service?.setMusicVolume(binding.musicVolumeSlider.value)
                }
                updateButtons()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            isBound = false
            runOnUiThread {
                updateButtons()
            }
        }
    }

    private val timerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val remaining = intent.getLongExtra(MeditationTimerService.EXTRA_REMAINING_SECONDS, 0L)
            val total = intent.getLongExtra(MeditationTimerService.EXTRA_TOTAL_SECONDS, 0L)
            updateTimerDisplay(remaining, total)
            updateButtons()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        presetManager = PresetManager(this)
        binding.durationInput.setText("20")
        binding.intervalInput.setText("0")
        binding.entrainmentFilename.text = getString(R.string.entrainment_none)
        binding.startChimeFilename.text = getString(R.string.chime_none)
        binding.intervalChimeFilename.text = getString(R.string.chime_none)
        binding.endChimeFilename.text = getString(R.string.chime_none)
        binding.entrainmentVolumeSlider.value = 1.0f
        binding.musicVolumeSlider.value = 1.0f
        binding.entrainmentVolumeValue.text = formatVolumePercent(1.0f, false)
        binding.musicVolumeValue.text = formatVolumePercent(1.0f, false)
        binding.entrainmentMuteCheckbox.isChecked = false
        binding.musicMuteCheckbox.isChecked = false
        binding.entrainmentVolumeSlider.alpha = 1.0f
        binding.musicVolumeSlider.alpha = 1.0f
        updateButtons()

        binding.selectEntrainmentButton.setOnClickListener {
            pendingAudioPickerTarget = AudioPickerTarget.ENTRAINMENT
            ensureMediaPermissionAndPick()
        }
        binding.clearEntrainmentButton.setOnClickListener {
            clearEntrainmentSelection()
        }
        binding.selectMusicButton.setOnClickListener {
            pendingAudioPickerTarget = AudioPickerTarget.MUSIC
            ensureMediaPermissionAndPick()
        }
        binding.clearMusicButton.setOnClickListener {
            clearMusicSelection()
        }
        binding.selectStartChimeButton.setOnClickListener {
            pendingAudioPickerTarget = AudioPickerTarget.START_CHIME
            ensureMediaPermissionAndPick()
        }
        binding.selectIntervalChimeButton.setOnClickListener {
            pendingAudioPickerTarget = AudioPickerTarget.INTERVAL_CHIME
            ensureMediaPermissionAndPick()
        }
        binding.selectEndChimeButton.setOnClickListener {
            pendingAudioPickerTarget = AudioPickerTarget.END_CHIME
            ensureMediaPermissionAndPick()
        }
        binding.startButton.setOnClickListener {
            startTimer()
        }
        binding.pauseButton.setOnClickListener {
            pauseOrResumeTimer()
        }
        binding.stopButton.setOnClickListener {
            stopTimer()
        }
        binding.savePresetButton.setOnClickListener {
            savePreset()
        }
        binding.loadPresetButton.setOnClickListener {
            loadPreset()
        }
        binding.entrainmentVolumeSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser && isEntrainmentMuted) {
                binding.entrainmentMuteCheckbox.isChecked = false
            }
            binding.entrainmentVolumeValue.text = formatVolumePercent(value, isEntrainmentMuted)
            if (!isEntrainmentMuted) {
                service?.setEntrainmentVolume(value)
            }
        }
        binding.musicVolumeSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser && isMusicMuted) {
                binding.musicMuteCheckbox.isChecked = false
            }
            binding.musicVolumeValue.text = formatVolumePercent(value, isMusicMuted)
            if (!isMusicMuted) {
                service?.setMusicVolume(value)
            }
        }
        binding.entrainmentMuteCheckbox.setOnCheckedChangeListener { _, isChecked ->
            isEntrainmentMuted = isChecked
            binding.entrainmentVolumeSlider.alpha = if (isChecked) 0.5f else 1.0f
            if (isChecked) {
                service?.setEntrainmentVolume(0f)
            } else {
                service?.setEntrainmentVolume(binding.entrainmentVolumeSlider.value)
            }
            binding.entrainmentVolumeValue.text =
                formatVolumePercent(binding.entrainmentVolumeSlider.value, isEntrainmentMuted)
        }
        binding.musicMuteCheckbox.setOnCheckedChangeListener { _, isChecked ->
            isMusicMuted = isChecked
            binding.musicVolumeSlider.alpha = if (isChecked) 0.5f else 1.0f
            if (isChecked) {
                service?.setMusicVolume(0f)
            } else {
                service?.setMusicVolume(binding.musicVolumeSlider.value)
            }
            binding.musicVolumeValue.text =
                formatVolumePercent(binding.musicVolumeSlider.value, isMusicMuted)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, MeditationTimerService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        val filter = IntentFilter(MeditationTimerService.ACTION_TICK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(timerReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(timerReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        unregisterReceiver(timerReceiver)
    }

    private fun ensureMediaPermissionAndPick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
                launchPendingPicker()
            } else {
                requestAudioPermission.launch(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            launchPendingPicker()
        }
    }

    private fun launchPendingPicker() {
        when (pendingAudioPickerTarget) {
            AudioPickerTarget.ENTRAINMENT -> entrainmentPicker.launch(arrayOf("audio/*"))
            AudioPickerTarget.MUSIC -> musicPicker.launch(arrayOf("audio/*"))
            AudioPickerTarget.START_CHIME -> startChimePicker.launch(arrayOf("audio/*"))
            AudioPickerTarget.INTERVAL_CHIME -> intervalChimePicker.launch(arrayOf("audio/*"))
            AudioPickerTarget.END_CHIME -> endChimePicker.launch(arrayOf("audio/*"))
            null -> Unit
        }
        pendingAudioPickerTarget = null
    }

    private fun handlePickedUri(uri: Uri?, errorMessage: String, onSuccess: (Uri) -> Unit) {
        if (uri == null) {
            return
        }
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (exception: SecurityException) {
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        }
        onSuccess(uri)
    }

    private fun startTimer() {
        val duration = binding.durationInput.text?.toString()?.toIntOrNull() ?: 0
        val interval = binding.intervalInput.text?.toString()?.toIntOrNull() ?: 0
        if (duration < 1) {
            Toast.makeText(this, "Duration must be at least 1 minute.", Toast.LENGTH_LONG).show()
            return
        }
        if (interval < 0) {
            Toast.makeText(this, "Interval cannot be negative.", Toast.LENGTH_LONG).show()
            return
        }

        val config = TimerConfig(
            durationMinutes = duration,
            intervalMinutes = interval,
            entrainmentUri = currentEntrainmentUri?.toString(),
            entrainmentVolume = if (isEntrainmentMuted) 0f else binding.entrainmentVolumeSlider.value,
            musicUri = currentMusicUri?.toString(),
            musicVolume = if (isMusicMuted) 0f else binding.musicVolumeSlider.value,
            startChimeUri = currentStartChimeUri?.toString(),
            intervalChimeUri = currentIntervalChimeUri?.toString(),
            endChimeUri = currentEndChimeUri?.toString()
        )

        val intent = Intent(this, MeditationTimerService::class.java).apply {
            action = MeditationTimerService.ACTION_START
            putExtra(MeditationTimerService.EXTRA_DURATION_MINUTES, config.durationMinutes)
            putExtra(MeditationTimerService.EXTRA_INTERVAL_MINUTES, config.intervalMinutes)
            putExtra(MeditationTimerService.EXTRA_ENTRAINMENT_URI, config.entrainmentUri)
            putExtra(MeditationTimerService.EXTRA_ENTRAINMENT_VOLUME, config.entrainmentVolume)
            putExtra(MeditationTimerService.EXTRA_MUSIC_URI, config.musicUri)
            putExtra(MeditationTimerService.EXTRA_MUSIC_VOLUME, config.musicVolume)
            putExtra(MeditationTimerService.EXTRA_START_CHIME, config.startChimeUri)
            putExtra(MeditationTimerService.EXTRA_INTERVAL_CHIME, config.intervalChimeUri)
            putExtra(MeditationTimerService.EXTRA_END_CHIME, config.endChimeUri)
        }
        ContextCompat.startForegroundService(this, intent)
        binding.root.postDelayed({ updateButtons() }, 150)
    }

    private fun pauseOrResumeTimer() {
        val currentState = service?.currentState ?: MeditationTimerService.TimerState.IDLE
        val action = if (currentState == MeditationTimerService.TimerState.RUNNING) {
            MeditationTimerService.ACTION_PAUSE
        } else {
            MeditationTimerService.ACTION_RESUME
        }
        val intent = Intent(this, MeditationTimerService::class.java).apply {
            this.action = action
        }
        startService(intent)
        binding.root.postDelayed({ updateButtons() }, 150)
    }

    private fun stopTimer() {
        val intent = Intent(this, MeditationTimerService::class.java).apply {
            action = MeditationTimerService.ACTION_STOP
        }
        startService(intent)
        binding.root.postDelayed({ updateButtons() }, 150)
    }

    private fun savePreset() {
        val duration = binding.durationInput.text?.toString()?.toIntOrNull() ?: 0
        val interval = binding.intervalInput.text?.toString()?.toIntOrNull() ?: 0
        if (duration < 1) {
            Toast.makeText(this, "Duration must be at least 1 minute.", Toast.LENGTH_LONG).show()
            return
        }
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Preset name")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().ifBlank { "Unnamed preset" }
                val preset = Preset(
                    name = name,
                    durationMinutes = duration,
                    intervalMinutes = interval,
                    entrainmentUri = currentEntrainmentUri?.toString(),
                    entrainmentVolume = binding.entrainmentVolumeSlider.value,
                    entrainmentMuted = isEntrainmentMuted,
                    musicUri = currentMusicUri?.toString(),
                    musicVolume = binding.musicVolumeSlider.value,
                    musicMuted = isMusicMuted,
                    startChimeUri = currentStartChimeUri?.toString(),
                    intervalChimeUri = currentIntervalChimeUri?.toString(),
                    endChimeUri = currentEndChimeUri?.toString()
                )
                presetManager.savePreset(preset)
                Toast.makeText(this, "Preset saved.", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadPreset() {
        val presets = presetManager.loadPresets()
        if (presets.isEmpty()) {
            Toast.makeText(this, "No presets saved yet.", Toast.LENGTH_LONG).show()
            return
        }
        val names = presets.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Load preset")
            .setItems(names) { _, which ->
                val preset = presets[which]
                binding.durationInput.setText(preset.durationMinutes.toString())
                binding.intervalInput.setText(preset.intervalMinutes.toString())
                currentEntrainmentUri = preset.entrainmentUri?.let { Uri.parse(it) }
                binding.entrainmentFilename.text =
                    currentEntrainmentUri?.lastPathSegment ?: getString(R.string.entrainment_none)
                binding.entrainmentVolumeSlider.value = preset.entrainmentVolume ?: 1.0f
                binding.entrainmentVolumeValue.text =
                    formatVolumePercent(binding.entrainmentVolumeSlider.value, isEntrainmentMuted)
                isEntrainmentMuted = preset.entrainmentMuted ?: false
                binding.entrainmentMuteCheckbox.isChecked = isEntrainmentMuted
                binding.entrainmentVolumeSlider.alpha = if (isEntrainmentMuted) 0.5f else 1.0f
                currentMusicUri = preset.musicUri?.let { Uri.parse(it) }
                binding.musicFilename.text = currentMusicUri?.lastPathSegment ?: getString(R.string.music_none)
                binding.musicVolumeSlider.value = preset.musicVolume ?: 1.0f
                binding.musicVolumeValue.text =
                    formatVolumePercent(binding.musicVolumeSlider.value, isMusicMuted)
                isMusicMuted = preset.musicMuted ?: false
                binding.musicMuteCheckbox.isChecked = isMusicMuted
                binding.musicVolumeSlider.alpha = if (isMusicMuted) 0.5f else 1.0f
                currentStartChimeUri = preset.startChimeUri?.let { Uri.parse(it) }
                currentIntervalChimeUri = preset.intervalChimeUri?.let { Uri.parse(it) }
                currentEndChimeUri = preset.endChimeUri?.let { Uri.parse(it) }
                binding.startChimeFilename.text =
                    currentStartChimeUri?.lastPathSegment ?: getString(R.string.chime_none)
                binding.intervalChimeFilename.text =
                    currentIntervalChimeUri?.lastPathSegment ?: getString(R.string.chime_none)
                binding.endChimeFilename.text =
                    currentEndChimeUri?.lastPathSegment ?: getString(R.string.chime_none)
            }
            .show()
    }

    private fun updateTimerDisplay(remainingSeconds: Long, totalSeconds: Long) {
        val minutes = remainingSeconds / 60
        val seconds = remainingSeconds % 60
        binding.timerDisplay.text = String.format("%02d:%02d", minutes, seconds)
        val progress = if (totalSeconds > 0) {
            ((totalSeconds - remainingSeconds) * 100 / totalSeconds).toInt()
        } else {
            0
        }
        binding.progressBar.progress = progress
    }

    private fun updateButtons() {
        val state = service?.currentState ?: MeditationTimerService.TimerState.IDLE
        val isRunning = state == MeditationTimerService.TimerState.RUNNING
        val isPaused = state == MeditationTimerService.TimerState.PAUSED
        binding.startButton.isEnabled = state == MeditationTimerService.TimerState.IDLE
        binding.pauseButton.isEnabled = isRunning || isPaused
        binding.pauseButton.text = if (isPaused) getString(R.string.resume) else getString(R.string.pause)
        binding.stopButton.isEnabled = isRunning || isPaused
    }

    private fun clearEntrainmentSelection() {
        currentEntrainmentUri = null
        binding.entrainmentFilename.text = getString(R.string.entrainment_none)
        service?.stopEntrainmentPlayback()
    }

    private fun clearMusicSelection() {
        currentMusicUri = null
        binding.musicFilename.text = getString(R.string.music_none)
        service?.stopMusicPlayback()
    }

    private fun formatVolumePercent(value: Float, isMuted: Boolean): String {
        val percent = (value * 100).toInt()
        return if (isMuted) "$percent% (Muted)" else "$percent%"
    }

    enum class AudioPickerTarget {
        ENTRAINMENT,
        MUSIC,
        START_CHIME,
        INTERVAL_CHIME,
        END_CHIME
    }
}
