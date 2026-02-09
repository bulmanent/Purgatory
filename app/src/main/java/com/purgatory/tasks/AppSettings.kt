package com.purgatory.tasks

import android.content.Context

object AppSettings {
    private const val PREFS_NAME = "purgatory_settings"
    const val KEY_DEFAULT_USER = "default_user"
    const val KEY_SPREADSHEET_ID = "spreadsheet_id"
    const val KEY_DAILY_REMINDER = "daily_reminder"
    private const val DEFAULT_SPREADSHEET_ID = "1MOrfuWJKrc9QSgryFWTegCz587xXF6JGzGUCqWMeQEk"

    fun getDefaultUser(context: Context): String? =
        prefs(context).getString(KEY_DEFAULT_USER, AppUsers.all.firstOrNull()?.displayName)

    fun setDefaultUser(context: Context, user: String) {
        prefs(context).edit().putString(KEY_DEFAULT_USER, user).apply()
    }

    fun getSpreadsheetId(context: Context): String? =
        prefs(context).getString(KEY_SPREADSHEET_ID, DEFAULT_SPREADSHEET_ID)
            ?.trim()
            ?.ifBlank { DEFAULT_SPREADSHEET_ID }

    fun setSpreadsheetId(context: Context, id: String) {
        prefs(context).edit().putString(KEY_SPREADSHEET_ID, normalizeSpreadsheetId(id)).apply()
    }

    fun isDailyReminderEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DAILY_REMINDER, false)

    fun setDailyReminderEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DAILY_REMINDER, enabled).apply()
    }

    fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun normalizeSpreadsheetId(input: String): String {
        val trimmed = input.trim()
        if (trimmed.contains("/d/")) {
            val start = trimmed.indexOf("/d/") + 3
            val end = trimmed.indexOf("/edit", start).let { if (it == -1) trimmed.length else it }
            return trimmed.substring(start, end)
        }
        return trimmed
    }
}
