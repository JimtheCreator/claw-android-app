package market.symbol.ui.analysis

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.claw.ai.R
import com.claw.ai.databinding.ActivitySymbolMarketDataBinding
import market.symbol.adapters.TimeframeDropdownAdapter
import market.symbol.ui.dialogs.CustomTimeframeDialog

class AnalysisPanelManager(
    private val binding: ActivitySymbolMarketDataBinding,
    private val onTimeframeSelectedForAnalysis: (String) -> Unit
) {
    private val context = binding.root.context
    private val analysisLayout = binding.analysispagelayout
    private val bottomSection = binding.bottomSection
    private val header = binding.marketChartLayout.header
    private val lottieAnimationView = binding.marketChartLayout.lottieAnimationView
    private val headerBorder = binding.marketChartLayout.parentBorder
    private var swipeToAnalyze = binding.analysispagelayout.swipeToAnalyze
    private val timeframeAdapter: TimeframeDropdownAdapter
    private var currentInterval: String = "1m"

    // NEW: Add references to the main panel elements for collapsing
    private val main = binding.main
    private val dragHandle = binding.dragHandle
    private val topSection = binding.topSection
    private val marketChartLayout = binding.marketChartLayout

    init {
        timeframeAdapter = TimeframeDropdownAdapter(emptyList()) { selectedTimeframe ->
            handleTimeframeSelection(selectedTimeframe)
        }
        analysisLayout.timeframeRecyclerView.adapter = timeframeAdapter
        analysisLayout.spinnerContainer.setOnClickListener {
            val isDropdownVisible = analysisLayout.timeframeRecyclerView.visibility == View.VISIBLE
            // This will call the new toggle function with animation by default
            toggleDropdown(show = !isDropdownVisible)
        }

        swipeToAnalyze.setOnClickListener {
            // NEW: Better architecture - collapse panel first, then show animation
            collapsePanel {
                showLoadingAnimation()
            }
        }
    }

    private fun handleTimeframeSelection(timeframe: String) {
        if (timeframe == TimeframeDropdownAdapter.CUSTOM_ITEM) {
            showAddCustomTimeframeDialog()
        } else {
            analysisLayout.selectedTimeframeText.text = timeframe
            // This will also call the new function with animation by default
            toggleDropdown(show = false)
            onTimeframeSelectedForAnalysis(timeframe)
        }
    }

    fun updateTimeframesForInterval(interval: String) {
        this.currentInterval = interval

        var timeframes = when (interval) {
            "1m" -> listOf("5m", "15m", "30m", "1h", "4h")
            "5m" -> listOf("15m", "30m", "1h", "4h", "1d")
            "15m" -> listOf("30m", "1h", "4h", "1d", "3d")
            "30m" -> listOf("1h", "4h", "1d", "3d", "1w")
            "1h", "2h" -> listOf("4h", "1d", "3d", "1w", "1M")
            "1d" -> listOf("3d", "1w", "1M", "3M", "6M")
            "1w" -> listOf("1M", "3M", "6M", "1Y")
            "1M" -> listOf("3M", "6M", "1Y", "All Time")
            else -> listOf()
        }.toMutableList()

        if (interval == "1m") {
            timeframes =
                timeframes.filter { !it.contains("M") && !it.contains("Y") }.toMutableList()
        }

        timeframeAdapter.updateData(timeframes)
        analysisLayout.selectedTimeframeText.text = "Select Timeframe"
    }

    private fun showAddCustomTimeframeDialog() {
        CustomTimeframeDialog.show(
            context = context,
            currentInterval = currentInterval, // Pass the current interval for validation
            onAdd = { customText ->
                // MODIFIED: Directly handle the new custom timeframe.
                // This will set it as the selected text and close the dropdown.
                handleTimeframeSelection(customText)
            },
            onCancel = {
                // If the user cancels, we still want the dropdown to be visible
                toggleDropdown(show = true)
            }
        )
    }

    fun showLoadingAnimation() {
        val animationView = lottieAnimationView
        animationView.playAnimation()
    }

    // MODIFIED: This function now controls the animation behavior.
    fun toggleDropdown(show: Boolean) {
        val recyclerView = analysisLayout.timeframeRecyclerView

        TransitionManager.beginDelayedTransition(bottomSection)

        recyclerView.visibility = if (show) View.VISIBLE else View.GONE
        header.visibility = if (show) View.GONE else View.VISIBLE
        headerBorder.visibility = if (show) View.GONE else View.VISIBLE
    }

    // MODIFIED: A convenient public wrapper that passes the animation flag.
    fun closeDropdown() {
        if (analysisLayout.timeframeRecyclerView.visibility == View.VISIBLE) {
            toggleDropdown(show = false)
        }
    }

    // NEW: Dedicated collapse function for better architecture
    fun collapsePanel(onComplete: (() -> Unit)? = null) {
        val dropdownAnimationDuration = 300L
        val panelAnimationDuration = 300L

        // Step 1: Close dropdown first if it's open
        closeDropdown()

        // Step 2: Post a delayed action to start the panel collapse animation
        // right after the dropdown animation is expected to finish.
        main.postDelayed({
            // A new transition for the panel collapse
            val transition = AutoTransition().apply {
                duration = panelAnimationDuration
                interpolator = FastOutSlowInInterpolator()
            }
            TransitionManager.beginDelayedTransition(main, transition)

            // Animate the layout changes for the top and bottom sections
            topSection.layoutParams = (topSection.layoutParams as LinearLayout.LayoutParams).apply {
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                weight = 0f
            }
            bottomSection.layoutParams =
                (bottomSection.layoutParams as LinearLayout.LayoutParams).apply {
                    height = 0
                    weight = 0f
                }

            // After the panel collapse animation, hide the views completely and execute callback
            main.postDelayed({
                bottomSection.visibility = View.GONE
                dragHandle.visibility = View.GONE

                // Update UI elements
                marketChartLayout.aiButton.setBackgroundResource(R.drawable.ai_circle)
                marketChartLayout.closePanel.setImageResource(R.drawable.ai_stars)

                // Execute completion callback
                onComplete?.invoke()
            }, panelAnimationDuration)

        }, dropdownAnimationDuration)
    }
}