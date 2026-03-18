package com.purgatory.tasks.eventtracker

data class EventType(val name: String)

data class EventEntry(
    val event: String,
    val date: String,
    val time: String,
    val details: String,
    val severity: String,
    val action: String
)
