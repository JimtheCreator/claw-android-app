package market.symbol.ui.analysis

import android.animation.ObjectAnimator
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.NumberPicker
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.claw.ai.R
import com.claw.ai.databinding.ActivitySymbolMarketDataBinding
import com.facebook.shimmer.ShimmerFrameLayout
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
    private val rotate_to_fullscreen = binding.marketChartLayout.rotateToFullscreen
    private val chartFrame = binding.marketChartLayout.frame
    private var currentInterval: String = "1m"

    // NumberPicker reference
    private val numberPicker = binding.analysispagelayout.numberPicker
    private var timeframeOptions: Array<String> = emptyArray()

    // References for the iOS-style swipe control
    private val swipeContainer = binding.analysispagelayout.swipeToAnalyzeActionLayout.swipeToAnalyzeContainer
    private val swipeThumb = binding.analysispagelayout.swipeToAnalyzeActionLayout.swipeThumb
    private val swipeText = binding.analysispagelayout.swipeToAnalyzeActionLayout.swipeToAnalyzeText
    private val shimmerContainer = binding.analysispagelayout.swipeToAnalyzeActionLayout.shimmerViewContainer

    private val main = binding.main
    private val dragHandle = binding.dragHandle
    private val topSection = binding.topSection
    private val marketChartLayout = binding.marketChartLayout

    private var selectedTimeframe: String? = null

    init {
        setupNumberPicker()
        setupSwipeToAnalyzeListener()
    }

    private fun setupNumberPicker() {
        numberPicker.apply {
            // Disable keyboard input and focus
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS

            // Disable infinite scroll (wrapping)
            wrapSelectorWheel = false

            // Set value change listener
            setOnValueChangedListener { _, _, newVal ->
                if (timeframeOptions.isNotEmpty() && newVal < timeframeOptions.size) {
                    selectedTimeframe = timeframeOptions[newVal]
                    onTimeframeSelectedForAnalysis(selectedTimeframe!!)
                }
            }
        }
    }

    private fun setupSwipeToAnalyzeListener() {
        var initialTouchX = 0f
        var initialThumbX = 0f

        swipeThumb.setOnTouchListener { view, event ->
            val maxTranslationX = (swipeContainer.width - swipeThumb.width).toFloat()
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialThumbX = swipeThumb.translationX
                    shimmerContainer.stopShimmer()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    var newTranslationX = initialThumbX + dx
                    newTranslationX = newTranslationX.coerceIn(0f, maxTranslationX)
                    swipeThumb.translationX = newTranslationX
                    val swipeProgress = newTranslationX / maxTranslationX
                    swipeText.alpha = 1.0f - swipeProgress * 1.5f
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val threshold = maxTranslationX * 0.75
                    if (swipeThumb.translationX >= threshold) {
                        val timeframe = selectedTimeframe ?: getDefaultTimeframeForInterval()
                        collapsePanel {
                            viewModel.startAnalysis(timeframe)
                        }
                    } else {
                        resetSwipeState(animated = true)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun getDefaultTimeframeForInterval(): String {
        return when (currentInterval) {
            "1m" -> "1h"
            "5m" -> "1h"
            "15m" -> "1h"
            "30m" -> "4h"
            "1h", "2h" -> "1d"
            "1d" -> "1w"
            "1w" -> "1M"
            "1M" -> "3M"
            else -> "1h"
        }
    }

    fun showLoadingAnimation() {
        binding.overlayContainer.visibility = View.VISIBLE
        binding.fullScreenLoader.visibility = View.VISIBLE
        binding.fullScreenLoader.playAnimation()
        binding.blurView.visibility = View.VISIBLE
        binding.fullScreenLoader.bringToFront()
        binding.loadingStatusText.bringToFront()
    }

    fun hideLoadingAnimation() {
        binding.overlayContainer.visibility = View.GONE
        binding.fullScreenLoader.visibility = View.GONE
        binding.fullScreenLoader.cancelAnimation()
        binding.blurView.visibility = View.GONE
    }

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

    fun updateTimeframesForInterval(interval: String) {
        this.currentInterval = interval

        val timeframes = when (interval) {
            "1m" -> listOf("5m", "15m", "30m", "1h", "4h")
            "5m" -> listOf("15m", "30m", "1h", "4h", "1d")
            "15m" -> listOf("30m", "1h", "4h", "1d", "3d")
            "30m" -> listOf("1h", "4h", "1d", "3d", "1w")
            "1h", "2h" -> listOf("4h", "1d", "3d", "1w", "1M")
            "1d" -> listOf("3d", "1w", "1M", "3M", "6M")
            "1w" -> listOf("1M", "3M", "6M", "1Y")
            "1M" -> listOf("3M", "6M", "1Y", "All Time")
            else -> listOf("1h")
        }.let { list ->
            // Filter out monthly/yearly options for 1m interval
            if (interval == "1m") {
                list.filter { !it.contains("M") && !it.contains("Y") }
            } else {
                list
            }
        }

        // Update NumberPicker with new options
        timeframeOptions = timeframes.toTypedArray()

        numberPicker.apply {
            // Force the picker to discard its old recycled views
            displayedValues = null

            // Set the new range and values
            minValue = 0
            maxValue = timeframeOptions.size - 1
            value = 0 // Reset to first option before setting displayed values
            displayedValues = timeframeOptions.map { "Last $it" }.toTypedArray()

            // --- The Fix ---
            // 1. Programmatically apply the style to its children
            styleNumberPicker(this, ContextCompat.getColor(context, R.color.off_white))
            // 2. Force the view to redraw itself now
            invalidate()
        }


        // Set default selected timeframe
        selectedTimeframe = if (timeframeOptions.isNotEmpty()) timeframeOptions[0] else getDefaultTimeframeForInterval()
        onTimeframeSelectedForAnalysis(selectedTimeframe!!)
    }

    private fun styleNumberPicker(picker: NumberPicker, color: Int) {
        // This function correctly finds the EditText child to style it.
        for (i in 0 until picker.childCount) {
            val child = picker.getChildAt(i)
            if (child is EditText) {
                try {
                    child.setTextColor(color)
                    child.setHintTextColor(color)
                    child.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    child.isCursorVisible = false

                    val typeface = ResourcesCompat.getFont(picker.context, R.font.sf_pro_text_medium)
                    if (typeface != null) {
                        child.typeface = typeface
                    }
                } catch (e: Exception) {
                    Log.w("AnalysisPanelManager", "Failed to style NumberPicker EditText.", e)
                }
            }
        }
    }

    fun collapsePanel(onComplete: (() -> Unit)? = null) {
        val panelAnimationDuration = 300L

        val transition = AutoTransition().apply {
            duration = panelAnimationDuration
            interpolator = FastOutSlowInInterpolator()
        }

        TransitionManager.beginDelayedTransition(main, transition)

        topSection.layoutParams = (topSection.layoutParams as LinearLayout.LayoutParams).apply {
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            weight = 0f
        }

        bottomSection.layoutParams = (bottomSection.layoutParams as LinearLayout.LayoutParams).apply {
            height = 0
            weight = 0f
        }

        main.postDelayed({
            bottomSection.visibility = View.GONE
            dragHandle.visibility = View.GONE
            marketChartLayout.supportResistanceButton.setBackgroundResource(R.drawable.white_circle)
            marketChartLayout.supportResistanceImg.setImageResource(R.drawable.sr_ic)
            rotate_to_fullscreen.visibility = View.VISIBLE
            onComplete?.invoke()
        }, panelAnimationDuration)
    }
}