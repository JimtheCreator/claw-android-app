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
                onAdd(customText)
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
}