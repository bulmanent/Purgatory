package com.meditation.timer

import android.content.Context

object AppSettings {
    private const val PREFS_NAME = "purgatory_settings"
    const val KEY_DEFAULT_USER = "default_user"
    const val KEY_SPREADSHEET_ID = "spreadsheet_id"
    const val KEY_DAILY_REMINDER = "daily_reminder"

    fun getDefaultUser(context: Context): String? =
        prefs(context).getString(KEY_DEFAULT_USER, AppUsers.all.firstOrNull()?.displayName)

    fun setDefaultUser(context: Context, user: String) {
        prefs(context).edit().putString(KEY_DEFAULT_USER, user).apply()
    }

    fun getSpreadsheetId(context: Context): String? =
        prefs(context).getString(KEY_SPREADSHEET_ID, null)

    fun setSpreadsheetId(context: Context, id: String) {
        prefs(context).edit().putString(KEY_SPREADSHEET_ID, id).apply()
    }

    fun isDailyReminderEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DAILY_REMINDER, false)

    fun setDailyReminderEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DAILY_REMINDER, enabled).apply()
    }

    fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
