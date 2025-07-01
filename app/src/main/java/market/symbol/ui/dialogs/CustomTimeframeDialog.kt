package market.symbol.ui.dialogs

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

object CustomTimeframeDialog {

    fun show(
        context: Context,
        currentInterval: String,
        onAdd: (String) -> Unit,
        onCancel: () -> Unit
    ) {
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle("Add Custom Timeframe")

        // Use TextInputLayout for a modern, floating label text field
        val textInputLayout = TextInputLayout(context).apply {
            hint = "e.g., 6h, 2d, 2w"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        }
        val input = TextInputEditText(context)
        textInputLayout.addView(input)

        // Add padding to the dialog content
        val container = FrameLayout(context)
        val density = context.resources.displayMetrics.density
        val margin = (20 * density).toInt()
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = margin
            rightMargin = margin
        }
        container.addView(textInputLayout, params)
        builder.setView(container)

        builder.setPositiveButton("Add") { _, _ ->
            val customText = input.text.toString().trim()
            if (customText.isNotEmpty()) {
                val validationResult = validateTimeframe(customText, currentInterval)
                if (validationResult.isValid) {
                    onAdd(customText)
                } else {
                    // Show warning dialog
                    showValidationWarning(context, validationResult.errorMessage, customText) { shouldProceed ->
                        if (shouldProceed) {
                            onAdd(customText)
                        } else {
                            // Re-show the custom timeframe dialog
                            show(context, currentInterval, onAdd, onCancel)
                        }
                    }
                }
            }
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }

        // This ensures the onCancel lambda is called when dismissing via back press or tapping outside
        builder.setOnCancelListener {
            onCancel()
        }

        builder.show()
    }

    private data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String = ""
    )

    private fun validateTimeframe(timeframe: String, currentInterval: String): ValidationResult {
        val timeframeMinutes = parseTimeframeToMinutes(timeframe)
        val intervalMinutes = parseTimeframeToMinutes(currentInterval)

        if (timeframeMinutes == null) {
            return ValidationResult(false, "Invalid timeframe format. Use formats like: 5m, 2h, 1d, 1w")
        }

        if (intervalMinutes == null) {
            return ValidationResult(true) // Can't validate against unknown interval
        }

        // FIXED: Now we warn about requesting TOO MUCH historical data for small intervals
        val maximumRecommendedTimeframe = when {
            // For 1m interval, recommend max 4h analysis to avoid excessive data
            intervalMinutes == 1 -> 240 // 4 hours
            // For 5m interval, recommend max 12h analysis
            intervalMinutes == 5 -> 720 // 12 hours
            // For 15m interval, recommend max 1d analysis
            intervalMinutes == 15 -> 1440 // 1 day
            // For 30m interval, recommend max 3d analysis
            intervalMinutes == 30 -> 4320 // 3 days
            // For 1h interval, recommend max 1w analysis
            intervalMinutes == 60 -> 10080 // 1 week
            // For 2h interval, recommend max 2w analysis
            intervalMinutes == 120 -> 20160 // 2 weeks
            // For daily and above, allow larger timeframes
            else -> Int.MAX_VALUE
        }

        if (maximumRecommendedTimeframe < Int.MAX_VALUE && timeframeMinutes > maximumRecommendedTimeframe) {
            val maximumFormatted = formatMinutesToTimeframe(maximumRecommendedTimeframe)
            return ValidationResult(
                false,
                "For $currentInterval charts, analysis timeframes longer than $maximumFormatted may require excessive historical data and cause performance issues. This could lead to slow loading times and high data usage."
            )
        }

        return ValidationResult(true)
    }

    private fun parseTimeframeToMinutes(timeframe: String): Int? {
        if (timeframe.isEmpty()) return null

        val regex = Regex("^(\\d+)([mhdwMY])$")
        val matchResult = regex.find(timeframe.lowercase()) ?: return null

        val (numberStr, unit) = matchResult.destructured
        val number = numberStr.toIntOrNull() ?: return null

        return when (unit) {
            "m" -> number
            "h" -> number * 60
            "d" -> number * 60 * 24
            "w" -> number * 60 * 24 * 7
            "M" -> number * 60 * 24 * 30 // Month
            "y" -> number * 60 * 24 * 365 // Year
            else -> null
        }
    }

    private fun formatMinutesToTimeframe(minutes: Int): String {
        return when {
            minutes < 60 -> "${minutes}m"
            minutes < 1440 -> "${minutes / 60}h"
            minutes < 10080 -> "${minutes / 1440}d"
            else -> "${minutes / 10080}w"
        }
    }

    private fun showValidationWarning(
        context: Context,
        message: String,
        timeframe: String,
        onResult: (Boolean) -> Unit
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle("⚠️ High Data Usage Warning")
            .setMessage("$message\n\nDo you still want to proceed with '$timeframe'?")
            .setPositiveButton("Proceed Anyway") { _, _ ->
                onResult(true)
            }
            .setNegativeButton("Choose Smaller Timeframe") { _, _ ->
                onResult(false)
            }
            .setCancelable(false)
            .show()
    }
}