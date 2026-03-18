package com.purgatory.tasks.eventtracker

import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.purgatory.tasks.R
import com.purgatory.tasks.databinding.DialogLogEventBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import android.view.WindowManager

object EventLogDialogHelper {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun show(
        activity: AppCompatActivity,
        repository: EventTrackerRepository,
        eventName: String,
        onSaved: () -> Unit
    ) {
        val dialogBinding = DialogLogEventBinding.inflate(activity.layoutInflater)
        val dateText = LocalDate.now().toString()
        val timeText = LocalTime.now().format(timeFormatter)

        dialogBinding.eventNameValueText.text = eventName
        dialogBinding.eventDateValueText.text = dateText
        dialogBinding.eventTimeValueText.text = timeText

        val severityOptions = listOf("", "Low", "Medium", "High")
        dialogBinding.logSeverityInput.setAdapter(
            ArrayAdapter(activity, android.R.layout.simple_list_item_1, severityOptions)
        )
        dialogBinding.logSeverityInput.setText("", false)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.event_log_title)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        dialog.setOnShowListener {
            val saveButton = dialogBinding.logSaveButton
            val cancelButton = dialogBinding.logCancelButton
            cancelButton.setOnClickListener { dialog.dismiss() }
            saveButton.setOnClickListener {
                setSavingState(dialogBinding, saveButton, cancelButton, true)
                activity.lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        repository.logEvent(
                            eventName = eventName,
                            details = dialogBinding.logDetailsInput.text?.toString().orEmpty(),
                            severity = dialogBinding.logSeverityInput.text?.toString().orEmpty(),
                            action = dialogBinding.logActionInput.text?.toString().orEmpty()
                        )
                    }

                    if (result.isSuccess) {
                        dialog.dismiss()
                        onSaved()
                        Snackbar.make(
                            activity.findViewById(android.R.id.content),
                            activity.getString(R.string.event_log_saved),
                            Snackbar.LENGTH_SHORT
                        ).show()
                    } else {
                        setSavingState(dialogBinding, saveButton, cancelButton, false)
                        Snackbar.make(
                            activity.findViewById(android.R.id.content),
                            result.exceptionOrNull()?.message
                                ?: activity.getString(R.string.event_error_generic),
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun setSavingState(
        binding: DialogLogEventBinding,
        saveButton: com.google.android.material.button.MaterialButton,
        cancelButton: com.google.android.material.button.MaterialButton,
        isSaving: Boolean
    ) {
        binding.logEventProgress.visibility = if (isSaving) android.view.View.VISIBLE else android.view.View.GONE
        binding.logDetailsInput.isEnabled = !isSaving
        binding.logSeverityInput.isEnabled = !isSaving
        binding.logActionInput.isEnabled = !isSaving
        saveButton.isEnabled = !isSaving
        cancelButton.isEnabled = !isSaving
    }
}
