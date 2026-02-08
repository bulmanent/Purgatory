package com.meditation.timer

enum class TaskStatus(val sheetValue: String) {
    UNASSIGNED("unassigned"),
    DUE("due"),
    CRUCIAL("crucial"),
    COMPLETE("complete");

    companion object {
        fun fromSheet(value: String?): TaskStatus {
            return values().firstOrNull { it.sheetValue.equals(value?.trim(), ignoreCase = true) }
                ?: DUE
        }
    }
}
