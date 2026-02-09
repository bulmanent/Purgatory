package com.purgatory.tasks

enum class TaskStatus(val sheetValue: String) {
    UNASSIGNED("unassigned"),
    DUE("due"),
    CRUCIAL("crucial"),
    COMPLETE("complete");

    companion object {
        fun fromSheet(value: String?): TaskStatus {
            val cleaned = value?.trim().orEmpty()
            if (cleaned.isBlank()) return UNASSIGNED
            return values().firstOrNull { it.sheetValue.equals(cleaned, ignoreCase = true) }
                ?: DUE
        }
    }
}
