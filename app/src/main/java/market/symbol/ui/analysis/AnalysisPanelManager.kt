package market.symbol.ui.analysis

import android.animation.ObjectAnimator
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.claw.ai.R
import com.claw.ai.databinding.ActivitySymbolMarketDataBinding
import com.facebook.shimmer.ShimmerFrameLayout
import com.tradingview.lightweightcharts.api.series.models.Time
import com.tradingview.lightweightcharts.api.series.models.TimeRange
import market.symbol.adapters.TimeframeDropdownAdapter
import market.symbol.ui.dialogs.CustomTimeframeDialog
import market.symbol.ui.market_chart.ChartManager
import market.symbol.viewmodel.SymbolMarketDataViewModel

class AnalysisPanelManager(
    private val binding: ActivitySymbolMarketDataBinding,
    private val onTimeframeSelectedForAnalysis: (String) -> Unit,
    private val viewModel: SymbolMarketDataViewModel,
    private val chartManager: ChartManager
) {
    private val context = binding.root.context
    private val analysisLayout = binding.analysispagelayout
    private val bottomSection = binding.bottomSection
    private val chartFrame = binding.marketChartLayout.frame
    private val lottieAnimationView = binding.marketChartLayout.lottieAnimationView
    private val timeframeAdapter: TimeframeDropdownAdapter
    private var currentInterval: String = "1m"

    // NEW: References for the iOS-style swipe control
    private val swipeContainer = binding.analysispagelayout.swipeToAnalyzeContainer
    private val swipeThumb = binding.analysispagelayout.swipeThumb
    private val swipeText = binding.analysispagelayout.swipeToAnalyzeText
    private val shimmerContainer = binding.analysispagelayout.shimmerViewContainer

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
            toggleDropdown(show = !isDropdownVisible)
        }

        // NEW: Setup the touch listener for the new swipe control
        setupSwipeToAnalyzeListener()
    }

    // NEW: Encapsulated touch handling logic for the swipe control
    private fun setupSwipeToAnalyzeListener() {
        var initialTouchX = 0f
        var initialThumbX = 0f

        swipeThumb.setOnTouchListener { view, event ->
            val maxTranslationX = (swipeContainer.width - swipeThumb.width).toFloat()
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialThumbX = swipeThumb.translationX
                    shimmerContainer.stopShimmer() // Stop shimmer on touch
                    true // Consume the event
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    var newTranslationX = initialThumbX + dx

                    // Clamp the translation within bounds
                    newTranslationX = newTranslationX.coerceIn(0f, maxTranslationX)
                    swipeThumb.translationX = newTranslationX

                    // Fade out the text as the user swipes
                    val swipeProgress = newTranslationX / maxTranslationX
                    swipeText.alpha = 1.0f - swipeProgress * 1.5f // Fade out faster

                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val threshold = maxTranslationX * 0.75 // 75% swipe to trigger

                    if (swipeThumb.translationX >= threshold) {
                        // Action triggered
                        collapsePanel {
                            showLoadingAnimation()
                        }
                    } else {
                        // Animate back to start
                        resetSwipeState(animated = true)
                    }
                    true
                }
                else -> false
            }
        }
    }

    // NEW: Public method to reset the swipe control's state
    fun resetSwipeState(animated: Boolean = false) {
        if (animated) {
            val animator = ObjectAnimator.ofFloat(swipeThumb, "translationX", 0f)
            animator.duration = 200
            animator.interpolator = FastOutSlowInInterpolator()
            animator.start()
        } else {
            swipeThumb.translationX = 0f
        }
        swipeText.alpha = 1.0f
        shimmerContainer.startShimmer()
    }

    private fun handleTimeframeSelection(timeframe: String) {
        if (timeframe == TimeframeDropdownAdapter.CUSTOM_ITEM) {
            showAddCustomTimeframeDialog()
        } else {
            val formattedTime = "Last $timeframe"
            analysisLayout.selectedTimeframeText.text = formattedTime
            toggleDropdown(show = false)
            scrollChartToTimeframe(timeframe)
            onTimeframeSelectedForAnalysis(timeframe)
        }
    }

    private fun scrollChartToTimeframe(timeframe: String) {
        val timeframeMinutes = parseTimeframeToMinutes(timeframe) ?: return
        val currentTimeMillis = System.currentTimeMillis()
        val targetTimeMillis = currentTimeMillis - (timeframeMinutes * 60 * 1000L)

        val candles = viewModel.candles.value
        if (candles.isNotEmpty()) {
            val earliestCandleTime = candles.first().time * 1000
            if (targetTimeMillis < earliestCandleTime) {
                viewModel.loadHistoricalDataUntil(targetTimeMillis / 1000) { success ->
                    if (success) {
                        performChartScroll(targetTimeMillis)
                    } else {
                        chartManager.scrollToPosition(0f, true) // Fallback to earliest data
                    }
                }
            } else {
                performChartScroll(targetTimeMillis)
            }
        }
    }

    private fun performChartScroll(targetTimeMillis: Long) {
        val fromTime = Time.Utc(targetTimeMillis / 1000)
        val toTime = Time.Utc(System.currentTimeMillis() / 1000)
        val timeRange = TimeRange(fromTime, toTime)
        Log.d("ChartDebug", "Setting visible range from $fromTime to $toTime")
        binding.marketChartLayout.candlesStickChart.api.timeScale.setVisibleRange(timeRange)
    }

    private fun parseTimeframeToMinutes(timeframe: String): Long? {
        if (timeframe.isEmpty()) return null
        when (timeframe.lowercase()) {
            "all time" -> return 365L * 24 * 60 * 10 // 10 years in minutes
            else -> {}
        }
        val regex = Regex("^(\\d+)([mhdwMY])$")
        val matchResult = regex.find(timeframe.lowercase()) ?: return null
        val (numberStr, unit) = matchResult.destructured
        val number = numberStr.toLongOrNull() ?: return null
        return when (unit) {
            "m" -> number
            "h" -> number * 60
            "d" -> number * 60 * 24
            "w" -> number * 60 * 24 * 7
            "M" -> number * 60 * 24 * 30 // Month (approximate)
            "y" -> number * 60 * 24 * 365 // Year (approximate)
            else -> null
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
            currentInterval = currentInterval,
            onAdd = { customText -> handleTimeframeSelection(customText) },
            onCancel = { toggleDropdown(show = true) }
        )
    }

    fun showLoadingAnimation() {
        val animationView = lottieAnimationView
        animationView.playAnimation()
    }

    fun toggleDropdown(show: Boolean) {
        val recyclerView = analysisLayout.timeframeRecyclerView
        TransitionManager.beginDelayedTransition(binding.root as ViewGroup)
        recyclerView.visibility = if (show) View.VISIBLE else View.GONE
        chartFrame.visibility = if (show) View.GONE else View.VISIBLE
    }

    fun closeDropdown() {
        if (analysisLayout.timeframeRecyclerView.visibility == View.VISIBLE) {
            toggleDropdown(show = false)
        }
    }

    fun collapsePanel(onComplete: (() -> Unit)? = null) {
        val dropdownAnimationDuration = 300L
        val panelAnimationDuration = 300L
        closeDropdown()
        main.postDelayed({
            val transition = AutoTransition().apply {
                duration = panelAnimationDuration
                interpolator = FastOutSlowInInterpolator()
            }
            TransitionManager.beginDelayedTransition(main, transition)
            topSection.layoutParams = (topSection.layoutParams as LinearLayout.LayoutParams).apply {
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                weight = 0f
            }
            bottomSection.layoutParams =
                (bottomSection.layoutParams as LinearLayout.LayoutParams).apply {
                    height = 0
                    weight = 0f
                }
            main.postDelayed({
                bottomSection.visibility = View.GONE
                dragHandle.visibility = View.GONE
                marketChartLayout.aiButton.setBackgroundResource(R.drawable.ai_circle)
                marketChartLayout.closePanel.setImageResource(R.drawable.ai_stars)
                onComplete?.invoke()
            }, panelAnimationDuration)
        }, dropdownAnimationDuration)
    }
}