package com.meditation.timer

data class TimerConfig(
    val durationMinutes: Int,
    val intervalMinutes: Int,
    val entrainmentUri: String?,
    val entrainmentVolume: Float,
    val musicUri: String?,
    val musicVolume: Float,
    val startChimeUri: String?,
    val intervalChimeUri: String?,
    val endChimeUri: String?
)

data class Preset(
    val name: String,
    val durationMinutes: Int,
    val intervalMinutes: Int,
    val entrainmentUri: String?,
    val entrainmentVolume: Float?,
    val entrainmentMuted: Boolean?,
    val musicUri: String?,
    val musicVolume: Float?,
    val musicMuted: Boolean?,
    val startChimeUri: String?,
    val intervalChimeUri: String?,
    val endChimeUri: String?
)
