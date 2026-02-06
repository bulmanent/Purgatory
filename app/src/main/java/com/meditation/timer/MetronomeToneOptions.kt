package com.meditation.timer

import android.media.ToneGenerator

object MetronomeToneOptions {
    data class ToneOption(
        val label: String,
        val normalTone: Int,
        val accentTone: Int
    )

    val options = listOf(
        ToneOption("Click", ToneGenerator.TONE_PROP_BEEP, ToneGenerator.TONE_PROP_BEEP2),
        ToneOption("Beep", ToneGenerator.TONE_PROP_BEEP, ToneGenerator.TONE_PROP_BEEP),
        ToneOption("Soft", ToneGenerator.TONE_PROP_ACK, ToneGenerator.TONE_PROP_BEEP),
        ToneOption("Alert", ToneGenerator.TONE_PROP_BEEP2, ToneGenerator.TONE_PROP_BEEP2),
        ToneOption("Chirp", ToneGenerator.TONE_SUP_RINGTONE, ToneGenerator.TONE_SUP_RINGTONE)
    )
}
