package market.symbol.ui.analysis

import android.view.View
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
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
    private val header_border = binding.marketChartLayout.parentBorder

    private val timeframeAdapter: TimeframeDropdownAdapter
    // REMOVED: No longer need to store custom timeframes.
    // private val customTimeframesByInterval = mutableMapOf<String, MutableSet<String>>()
    private var currentInterval: String = "1m"

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
            timeframes = timeframes.filter { !it.contains("M") && !it.contains("Y") }.toMutableList()
        }

        // REMOVED: The logic for adding saved custom timeframes is no longer needed.
        // customTimeframesByInterval[interval]?.let { custom ->
        //     timeframes.addAll(custom.sorted())
        // }

        timeframeAdapter.updateData(timeframes)
        analysisLayout.selectedTimeframeText.text = "Select Timeframe"
    }

    private fun showAddCustomTimeframeDialog() {
        CustomTimeframeDialog.show(
            context = context,
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

    // MODIFIED: This function now controls the animation behavior.
    fun toggleDropdown(show: Boolean, animated: Boolean = true) {
        val recyclerView = analysisLayout.timeframeRecyclerView

        // Only begin a transition if animation is requested
        if (animated) {
            val transition = AutoTransition().apply {
                duration = 300
                interpolator = FastOutSlowInInterpolator()
            }
            TransitionManager.beginDelayedTransition(bottomSection, transition)
        }

        header.visibility = if (show) View.GONE else View.VISIBLE
        header_border.visibility = if (show) View.GONE else View.VISIBLE
        recyclerView.visibility = if (show) View.VISIBLE else View.GONE
    }

    // MODIFIED: A convenient public wrapper that passes the animation flag.
    fun closeDropdown(animated: Boolean = true) {
        if (analysisLayout.timeframeRecyclerView.visibility == View.VISIBLE) {
            toggleDropdown(show = false, animated = animated)
        }
    }
}