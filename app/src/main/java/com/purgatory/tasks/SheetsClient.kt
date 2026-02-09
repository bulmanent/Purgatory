package com.purgatory.tasks

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class SheetsClient(
    private val gson: Gson = Gson(),
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    data class ValuesResponse(
        val range: String?,
        val majorDimension: String?,
        val values: List<List<String>>?
    )

    suspend fun fetchTasks(accessToken: String, spreadsheetId: String): List<List<String>> {
        val url =
            "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/Purgatory!A2:E?majorDimension=ROWS"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val details = response.body?.string().orEmpty()
                throw IllegalStateException("Sheets fetch failed: ${response.code} $details")
            }
            val body = response.body?.string().orEmpty()
            val parsed = gson.fromJson(body, ValuesResponse::class.java)
            return parsed.values ?: emptyList()
        }
    }

    suspend fun appendTask(
        accessToken: String,
        spreadsheetId: String,
        values: List<String>
    ) {
        val url =
            "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/Purgatory!A:E:append?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS"
        val payload = gson.toJson(mapOf("values" to listOf(values)))
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .post(payload.toRequestBody(jsonMediaType))
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val details = response.body?.string().orEmpty()
                throw IllegalStateException("Sheets append failed: ${response.code} $details")
            }
        }
    }

    suspend fun updateTask(
        accessToken: String,
        spreadsheetId: String,
        rowIndex: Int,
        values: List<String>
    ) {
        val url =
            "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/Purgatory!A$rowIndex:E$rowIndex?valueInputOption=USER_ENTERED"
        val payload = gson.toJson(mapOf("values" to listOf(values)))
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .put(payload.toRequestBody(jsonMediaType))
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val details = response.body?.string().orEmpty()
                throw IllegalStateException("Sheets update failed: ${response.code} $details")
            }
        }
    }
}
