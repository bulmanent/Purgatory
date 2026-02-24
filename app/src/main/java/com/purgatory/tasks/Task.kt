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

fun Task.isOverdue(today: LocalDate = LocalDate.now()): Boolean {
    if (status == TaskStatus.COMPLETE || status == TaskStatus.UNASSIGNED || status == TaskStatus.ANYTIME) {
        return false
    }
    return dueDate?.isBefore(today) == true
}

fun Task.displayStatus(today: LocalDate = LocalDate.now()): TaskStatus {
    return if (status == TaskStatus.OVERDUE || isOverdue(today)) {
        TaskStatus.OVERDUE
    } else {
        status
    }
}
