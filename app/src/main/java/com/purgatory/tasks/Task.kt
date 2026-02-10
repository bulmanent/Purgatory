package com.purgatory.tasks

import java.time.LocalDate
import java.time.LocalTime

data class Task(
    val rowIndex: Int,
    val details: String,
    val importance: Int,
    val owner: AppUser?,
    val status: TaskStatus,
    val dueDate: LocalDate?,
    val notifyEnabled: Boolean,
    val notifyTime: LocalTime?
)
