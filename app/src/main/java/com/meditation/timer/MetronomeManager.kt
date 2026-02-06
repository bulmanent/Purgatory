package com.meditation.timer

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlin.math.max

class MetronomeManager(
    private val audioManager: AudioManager,
    private val onStopped: (() -> Unit)? = null
) {
    private val handler = Handler(Looper.getMainLooper())
    private var toneGenerator: ToneGenerator? = null
    private var bpm = 90
    private var beatsPerBar = 4
    private var beatIndex = 0
    private var running = false
    private var volume = 1.0f
    private var audioFocusRequest: AudioFocusRequest? = null
    private var focusChangeListener: AudioManager.OnAudioFocusChangeListener? = null

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            playTick()
            val intervalMs = (60_000.0 / max(1, bpm)).toLong()
            handler.postDelayed(this, intervalMs)
        }
    }

    fun configure(bpm: Int, beatsPerBar: Int) {
        this.bpm = bpm
        this.beatsPerBar = beatsPerBar
    }

    fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        recreateToneGeneratorIfNeeded()
    }

    fun start() {
        if (running) return
        if (!requestFocus()) return
        recreateToneGeneratorIfNeeded()
        running = true
        beatIndex = 0
        handler.removeCallbacks(tickRunnable)
        handler.post(tickRunnable)
    }

    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacks(tickRunnable)
        abandonFocus()
        onStopped?.invoke()
    }

    fun isRunning(): Boolean = running

    fun release() {
        stop()
        toneGenerator?.release()
        toneGenerator = null
    }

    fun onAudioFocusLost() {
        stop()
    }

    private fun ensureToneGenerator() {
        if (toneGenerator != null) return
        val scaledVolume = (volume * 100).toInt().coerceIn(0, 100)
        toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, scaledVolume)
    }

    private fun recreateToneGeneratorIfNeeded() {
        toneGenerator?.release()
        toneGenerator = null
        if (running) {
            ensureToneGenerator()
        }
    }

    private fun playTick() {
        ensureToneGenerator()
        val isAccent = beatIndex % max(1, beatsPerBar) == 0
        val tone = if (isAccent) {
            ToneGenerator.TONE_PROP_BEEP2
        } else {
            ToneGenerator.TONE_PROP_BEEP
        }
        toneGenerator?.startTone(tone, 80)
        beatIndex = (beatIndex + 1) % max(1, beatsPerBar)
    }

    private fun requestFocus(): Boolean {
        val listener = AudioManager.OnAudioFocusChangeListener { change ->
            if (change <= AudioManager.AUDIOFOCUS_LOSS) {
                onAudioFocusLost()
            }
        }
        focusChangeListener = listener
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setOnAudioFocusChangeListener(listener)
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                listener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonFocus() {
        val request = audioFocusRequest
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (request != null) {
                audioManager.abandonAudioFocusRequest(request)
            }
        } else {
            @Suppress("DEPRECATION")
            focusChangeListener?.let { audioManager.abandonAudioFocus(it) }
        }
        audioFocusRequest = null
        focusChangeListener = null
    }
}
