package bottomsheets.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import com.claw.ai.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class SupportBottomSheet : BottomSheetDialogFragment() {

    private lateinit var telegramOption: LinearLayout
    private lateinit var emailOption: LinearLayout

    companion object {
        fun newInstance(): SupportBottomSheet {
            return SupportBottomSheet()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_support, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        telegramOption = view.findViewById(R.id.telegramOption)
        emailOption = view.findViewById(R.id.emailOption)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        telegramOption.setOnClickListener {
            openTelegramSupport()
            dismiss()
        }

        emailOption.setOnClickListener {
            openEmailSupport()
            dismiss()
        }
    }

    private fun openTelegramSupport() {
        try {
            val assistant = getString(R.string.watchers_telegram_assistant)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(assistant))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Please install Telegram or try the email option",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun openEmailSupport() {
        try {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                val email = getString(R.string.watchers_email)
                data = Uri.parse(email)
                putExtra(Intent.EXTRA_SUBJECT, "Watchers App Support")
                putExtra(Intent.EXTRA_TEXT, "Hi Watchers team,\n\nI need help with:\n\n[Please describe your issue here]\n\nThanks!")
            }
            startActivity(Intent.createChooser(emailIntent, "Send email via..."))
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "No email app found. Please contact us at contactwatchers@gmail.com",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}