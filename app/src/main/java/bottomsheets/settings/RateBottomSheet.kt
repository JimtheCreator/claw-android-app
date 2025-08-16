package bottomsheets.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.claw.ai.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class RateBottomSheet : BottomSheetDialogFragment() {

    private lateinit var starsContainer: LinearLayout
    private lateinit var titleText: TextView
    private lateinit var descriptionText: TextView
    private val stars = mutableListOf<ImageView>()
    private var selectedRating = 0

    companion object {
        fun newInstance(): RateBottomSheet {
            return RateBottomSheet()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_rate, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        starsContainer = view.findViewById(R.id.starsContainer)
        titleText = view.findViewById(R.id.titleText)
        descriptionText = view.findViewById(R.id.descriptionText)

        setupStars()
        setupInitialState()
    }

    private fun setupStars() {
        for (i in 1..5) {
            val star = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.star_size),
                    resources.getDimensionPixelSize(R.dimen.star_size)
                ).apply {
                    if (i < 5) marginEnd = resources.getDimensionPixelSize(R.dimen.star_margin)
                }
                setImageResource(R.drawable.ic_star_empty)
                setColorFilter(ContextCompat.getColor(requireContext(), R.color.off_white))
                isClickable = true
                isFocusable = true

                setOnClickListener {
                    onStarClicked(i)
                }
            }
            stars.add(star)
            starsContainer.addView(star)
        }
    }

    private fun setupInitialState() {
        titleText.text = "Rate Watchers"
        descriptionText.text = "How would you rate your experience with Watchers?"
    }

    private fun onStarClicked(rating: Int) {
        selectedRating = rating
        updateStarVisuals(rating)

        // Quick animation/confirmation
        titleText.text = "Thanks for rating!"
        descriptionText.text = "Rating recorded. Share more thoughts with our community!"

        // Disable further star clicks
        stars.forEach { it.isClickable = false }

        // Redirect to community after a short delay
        view?.postDelayed({
            openCommunityFeedback()
            dismiss()
        }, 1500)
    }

    private fun updateStarVisuals(rating: Int) {
        stars.forEachIndexed { index, star ->
            if (index < rating) {
                star.setImageResource(R.drawable.ic_star_filled)
                star.setColorFilter(ContextCompat.getColor(requireContext(), R.color.accent_color))
            } else {
                star.setImageResource(R.drawable.ic_star_empty)
                star.setColorFilter(ContextCompat.getColor(requireContext(), R.color.off_white))
            }
        }
    }

    private fun openCommunityFeedback() {
        try {
            val group = getString(R.string.watchers_telegram_group)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(group))
            startActivity(intent)
        } catch (e: Exception) {
            val group = getString(R.string.watchers_telegram_group)
            // Fallback if Telegram is not installed
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(group))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }
}