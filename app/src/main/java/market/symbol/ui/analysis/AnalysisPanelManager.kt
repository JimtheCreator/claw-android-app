package market.symbol.ui.analysis

import android.animation.ObjectAnimator
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.LifecycleOwner
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.Target
import com.claw.ai.R
import com.claw.ai.databinding.ActivitySymbolMarketDataBinding
import market.symbol.ui.market_chart.ChartManager
import market.symbol.viewmodel.AnalysisMode
import market.symbol.viewmodel.SymbolMarketDataViewModel
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.math.ceil

class AnalysisPanelManager(
    private val binding: ActivitySymbolMarketDataBinding,
    private val onTimeframeSelectedForAnalysis: (String) -> Unit,
    private val viewModel: SymbolMarketDataViewModel,
    private val chartManager: ChartManager,
    private val onBackPressedDispatcher: OnBackPressedDispatcher,
    private val lifecycleOwner: LifecycleOwner,
    private val onPermissionNeeded: (callback: () -> Unit) -> Unit
) {
    private val context = binding.root.context
    private val analysisLayout = binding.analysispagelayout
    private val bottomSection = binding.bottomSection
    private val rotate_to_fullscreen = binding.marketChartLayout.rotateToFullscreen
    private val chartFrame = binding.marketChartLayout.frame
    private var currentInterval: String = "1m"

    private val numberPicker = binding.analysispagelayout.numberPicker
    private var timeframeOptions: Array<String> = emptyArray()

    private val swipeContainer =
        binding.analysispagelayout.swipeToAnalyzeActionLayout.swipeToAnalyzeContainer
    private val swipeThumb = binding.analysispagelayout.swipeToAnalyzeActionLayout.swipeThumb
    private val swipeText = binding.analysispagelayout.swipeToAnalyzeActionLayout.swipeToAnalyzeText
    private val shimmerContainer =
        binding.analysispagelayout.swipeToAnalyzeActionLayout.shimmerViewContainer

    private val main = binding.main
    private val dragHandle = binding.dragHandle
    private val topSection = binding.topSection
    private val marketChartLayout = binding.marketChartLayout

    private var selectedTimeframe: String? = null

    private var currentMode: AnalysisMode = AnalysisMode.SUPPORT_RESISTANCE

    private var isChartInFullscreen = false

    private var highResImageUri: Uri? = null
    private var compositeImageUri: Uri? = null
    private var rawChartUri: Uri? = null

    private val backPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (isChartInFullscreen) {
                exitFullscreen()
            }
        }
    }

    init {
        setupNumberPicker()
        styleNumberPicker(numberPicker, ContextCompat.getColor(context, R.color.off_white))
        setupSwipeToAnalyzeListener()
        onClickHandler()
        onBackPressedDispatcher.addCallback(lifecycleOwner, backPressedCallback)
    }

    fun setAnalysisImageUris(compositeUri: Uri, rawChartUri: Uri) {
        this.compositeImageUri = compositeUri
        this.rawChartUri = rawChartUri
    }

    private fun onClickHandler() {
        analysisLayout.rotateToLandscape.setOnClickListener {
            enterFullscreen()
        }

        analysisLayout.clickToDownload.setOnClickListener {
            compositeImageUri?.let { uri ->
                val saveAction = { saveImageUriToGallery(context, uri) }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    onPermissionNeeded(saveAction)
                } else {
                    saveAction()
                }
            } ?: Toast.makeText(context, "Image not yet available to save.", Toast.LENGTH_SHORT)
                .show()
        }

        analysisLayout.shareToSocials.setOnClickListener {
            compositeImageUri?.let { uri ->
                shareImageUri(context, uri)
            } ?: Toast.makeText(context, "Image not yet available to share.", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun enterFullscreen() {
        rawChartUri?.let { uri ->
            isChartInFullscreen = true
            backPressedCallback.isEnabled = true
            binding.fullscreenContainer.visibility = View.VISIBLE
            Glide.with(context)
                .load(uri)
                .override(Target.SIZE_ORIGINAL)
                .fitCenter()
                .into(binding.fullscreenImage)
            binding.main.visibility = View.GONE
            (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } ?: Toast.makeText(context, "Chart not available for fullscreen.", Toast.LENGTH_SHORT).show()
    }

    private fun exitFullscreen() {
        isChartInFullscreen = false
        backPressedCallback.isEnabled = false
        binding.fullscreenContainer.visibility = View.GONE
        binding.main.visibility = View.VISIBLE
        (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    private fun saveImageUriToGallery(context: Context, imageUri: Uri) {
        val resolver = context.contentResolver
        val displayName = "Watchers_Trendline_Analysis_${System.currentTimeMillis()}.png"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Watchers Trendline Analysis")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                val picturesDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val albumDir = File(picturesDir, "Watchers Trendline Analysis")
                if (!albumDir.exists()) albumDir.mkdirs()
                val imageFile = File(albumDir, displayName)
                put(MediaStore.Images.Media.DATA, imageFile.absolutePath)
            }
        }
        val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        var newImageUri: Uri? = null
        try {
            newImageUri = resolver.insert(imageCollection, contentValues)
            newImageUri?.let { destUri ->
                resolver.openOutputStream(destUri)?.use { outputStream ->
                    resolver.openInputStream(imageUri)?.use { inputStream ->
                        inputStream.copyTo(outputStream)
                    } ?: throw IOException("Failed to open input stream from cached URI.")
                } ?: throw IOException("Failed to get output stream for gallery.")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(destUri, contentValues, null, null)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaScannerConnection.scanFile(context, arrayOf(destUri.toString()), arrayOf("image/png"), null)
                } else {
                    val projection = arrayOf(MediaStore.Images.Media.DATA)
                    resolver.query(destUri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                            val filePath = cursor.getString(columnIndex)
                            MediaScannerConnection.scanFile(context, arrayOf(filePath), arrayOf("image/png")) { path, uri ->
                                Log.d("GallerySave", "Scan completed for: $path")
                            }
                        }
                    }
                }
                Toast.makeText(context, "Saved to Gallery in 'Watchers Trendline Analysis' album.", Toast.LENGTH_LONG).show()
                Log.d("GallerySave", "Image saved successfully to: $destUri")
            } ?: throw IOException("Failed to create MediaStore record.")
        } catch (e: Exception) {
            newImageUri?.let { resolver.delete(it, null, null) }
            Toast.makeText(context, "Failed to save image: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("GallerySave", "Error saving image URI", e)
        }
    }

    private fun shareImageUri(context: Context, imageUri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Chart"))
    }

    fun setMode(mode: AnalysisMode) {
        this.currentMode = mode
        val title = when (mode) {
            AnalysisMode.SUPPORT_RESISTANCE -> "Support & Resistance"
            AnalysisMode.TRENDLINES -> "Trendline Analysis"
        }
        analysisLayout.analysisType.text = title
        analysisLayout.trendlineAnalysisLayout.visibility = View.GONE
        analysisLayout.numberPicker.visibility = View.VISIBLE
        analysisLayout.swipeToAnalyzeActionLayout.root.visibility = View.VISIBLE
        resetSwipeState()
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
                        if (currentMode == AnalysisMode.TRENDLINES) {
                            viewModel.startAnalysis(timeframe)
                        } else {
                            collapsePanel { viewModel.startAnalysis(timeframe) }
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
            "1m", "5m", "15m" -> "1h"
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

    private fun setupNumberPicker() {
        numberPicker.apply {
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
            wrapSelectorWheel = false
            setOnValueChangedListener { _, _, newVal ->
                if (timeframeOptions.isNotEmpty() && newVal < timeframeOptions.size) {
                    val selected = timeframeOptions[newVal]
                    if (selected == "Create Custom") {
                        showCustomTimeframeDialog()
                    } else {
                        selectedTimeframe = selected
                        onTimeframeSelectedForAnalysis(selectedTimeframe!!)
                    }
                }
            }
        }
    }

    private fun showCustomTimeframeDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.custom_timeframe_dialog, null)
        val numberInputLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.custom_timeframe_number_layout)
        val numberEditText = dialogView.findViewById<EditText>(R.id.custom_timeframe_number)
        val unitSpinner = dialogView.findViewById<Spinner>(R.id.custom_timeframe_unit)
        val okButton = dialogView.findViewById<Button>(R.id.custom_timeframe_ok)
        val allowedRangeText = dialogView.findViewById<android.widget.TextView>(R.id.allowed_range_text)

        val units = arrayOf("minutes", "hours", "days", "weeks", "months", "years")
        val unitAdapter = ArrayAdapter(context, R.layout.spinner_item, units).apply {
            setDropDownViewResource(R.layout.spinner_item)
        }
        unitSpinner.adapter = unitAdapter

        val (minWindow, maxWindow) = intervalLimits[currentInterval] ?: Pair(0, 0)
        val intervalMinutes = parseTimeframeToMinutes(currentInterval) ?: 1L
        val minTotalMinutes = minWindow * intervalMinutes
        val maxTotalMinutes = maxWindow * intervalMinutes

        unitSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedUnit = units[position]
                val conversionFactor = when (selectedUnit) {
                    "minutes" -> 1.0
                    "hours" -> 60.0
                    "days" -> 1440.0
                    "weeks" -> 10080.0
                    "months" -> 43800.0
                    "years" -> 525600.0
                    else -> 1.0
                }

                val minValue = minTotalMinutes / conversionFactor
                val maxValue = maxTotalMinutes / conversionFactor

                val minStr = if (minValue == minValue.toLong().toDouble()) minValue.toLong().toString() else String.format(Locale.US, "%.2f", minValue)
                val maxStr = if (maxValue == maxValue.toLong().toDouble()) maxValue.toLong().toString() else String.format(Locale.US, "%.2f", maxValue)

                allowedRangeText.text = "Allowed range: $minStr to $maxStr $selectedUnit"
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }

        val dialog = AlertDialog.Builder(context, R.style.ModernDialogTheme)
            .setView(dialogView)
            .setTitle("Custom Timeframe")
            .setNegativeButton("Cancel", null)
            .create()

        okButton.setOnClickListener {
            val numberStr = numberEditText.text.toString()
            val unit = units[unitSpinner.selectedItemPosition]

            if (numberStr.isEmpty()) {
                numberInputLayout.error = "Please enter a number"
                return@setOnClickListener
            }

            val number = numberStr.toLongOrNull()
            if (number == null || number <= 0) {
                numberInputLayout.error = "Invalid number"
                return@setOnClickListener
            }

            val unitMultiplier = when (unit) {
                "minutes" -> 1.0
                "hours" -> 60.0
                "days" -> 1440.0
                "weeks" -> 10080.0
                "months" -> 43800.0
                "years" -> 525600.0
                else -> 1.0
            }

            val customMinutes = number * unitMultiplier
            if (customMinutes < minTotalMinutes || customMinutes > maxTotalMinutes) {
                val minValue = minTotalMinutes / unitMultiplier
                val maxValue = maxTotalMinutes / unitMultiplier
                val minStr = if (minValue == minValue.toLong().toDouble()) minValue.toLong().toString() else String.format(Locale.US, "%.2f", minValue)
                val maxStr = if (maxValue == maxValue.toLong().toDouble()) maxValue.toLong().toString() else String.format(Locale.US, "%.2f", maxValue)
                numberInputLayout.error = "Enter a number between $minStr and $maxStr"
                return@setOnClickListener
            }

            numberInputLayout.error = null
            val unitCode = when (unit) {
                "minutes" -> "m"
                "hours" -> "h"
                "days" -> "d"
                "weeks" -> "w"
                "months" -> "M"
                "years" -> "y"
                else -> ""
            }

            val customTimeframe = "$number$unitCode"
            selectedTimeframe = customTimeframe
            onTimeframeSelectedForAnalysis(selectedTimeframe!!)

            val newList = timeframeOptions.toMutableList()
            newList.remove("Create Custom")
            if (customTimeframe !in newList) {
                newList.add(customTimeframe)
            }
            newList.sortWith(compareBy { parseTimeframeToMinutes(it) ?: Long.MAX_VALUE })
            newList.add("Create Custom")

            timeframeOptions = newList.toTypedArray()

            numberPicker.displayedValues = null
            numberPicker.maxValue = timeframeOptions.size - 1
            numberPicker.displayedValues = timeframeOptions.map { if (it == "Create Custom") it else "Last $it" }.toTypedArray()

            val newIndex = timeframeOptions.indexOf(customTimeframe)
            if (newIndex != -1) {
                numberPicker.value = newIndex
            }

            dialog.dismiss()
        }

        unitSpinner.setSelection(0, false)
        unitSpinner.onItemSelectedListener
            ?.onItemSelected(null, null, 0, 0L)

        dialog.show()
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation

        val layoutParams = dialog.window?.attributes
        layoutParams?.width = (context.resources.displayMetrics.widthPixels * 0.9).toInt()
        dialog.window?.attributes = layoutParams
    }

    private val intervalLimits = mapOf(
        "1m" to Pair(120, 300),   // 2 to 5 hours
        "5m" to Pair(72, 300),    // 6 to 25 hours
        "15m" to Pair(96, 400),   // 24 to 100 hours ≈ 1 to 4 days
        "30m" to Pair(96, 400),   // 48 to 200 hours ≈ 2 to 8 days
        "1h" to Pair(120, 600),   // 5 to 25 days
        "2h" to Pair(180, 800),   // 15 to 66 days ≈ 2 weeks to 2 months
        "4h" to Pair(200, 900),   // 33 to 150 days ≈ 1 to 5 months
        "6h" to Pair(250, 1000),  // 62 to 250 days ≈ 2 to 8 months
        "1d" to Pair(180, 720),   // 6 months to 2 years
        "3d" to Pair(120, 480),   // 1 to 4 years
        "1w" to Pair(52, 260),    // 1 to 5 years
        "1M" to Pair(12, 60)      // 1 to 5 years
    )

    private fun parseTimeframeToMinutes(timeframe: String): Long? {
        val regex = Regex("^(\\d+)([mhdwMYy])$")
        val matchResult = regex.find(timeframe.lowercase()) ?: return null
        val (numberStr, unit) = matchResult.destructured
        val number = numberStr.toLongOrNull() ?: return null
        return when (unit) {
            "m" -> number
            "h" -> number * 60
            "d" -> number * 1440 // 60 * 24
            "w" -> number * 10080 // 60 * 24 * 7
            "M" -> number * 43800 // Approx. 60 * 24 * 365 / 12
            "y" -> number * 525600 // 60 * 24 * 365
            else -> null
        }
    }

    private fun formatMinutesToTimeframe(minutes: Long): String {
        return when {
            minutes >= 525600 -> "${(minutes + 262800) / 525600}y" // Round to nearest year
            minutes >= 43800 -> "${(minutes + 21900) / 43800}M"   // Round to nearest month
            minutes >= 10080 -> "${(minutes + 5040) / 10080}w"  // Round to nearest week
            minutes >= 1440 -> "${(minutes + 720) / 1440}d"   // Round to nearest day
            minutes >= 60 -> "${(minutes + 30) / 60}h"     // Round to nearest hour
            else -> "${minutes}m"
        }
    }

    /**
     * FIX: New helper function to format minutes by rounding UP to the nearest unit.
     * This ensures the generated timeframe is always greater than or equal to the minimum required minutes.
     */
    private fun formatMinutesToTimeframeCeil(minutes: Long): String {
        val minutesAsDouble = minutes.toDouble()
        return when {
            minutes >= 525600 -> "${ceil(minutesAsDouble / 525600.0).toLong()}y"
            minutes >= 43800 -> "${ceil(minutesAsDouble / 43800.0).toLong()}M"
            minutes >= 10080 -> "${ceil(minutesAsDouble / 10080.0).toLong()}w"
            minutes >= 1440 -> "${ceil(minutesAsDouble / 1440.0).toLong()}d"
            minutes >= 60 -> "${ceil(minutesAsDouble / 60.0).toLong()}h"
            else -> "${minutes}m"
        }
    }

    /**
     * FIX: This function has been updated to prevent generating timeframes below the minimum requirement.
     */
    private fun generateSensibleTimeframes(interval: String): List<String> {
        val (minWindow, maxWindow) = intervalLimits[interval] ?: return listOf("1h", "4h", "1d", "1w", "1M")
        val intervalMinutes = parseTimeframeToMinutes(interval) ?: 1L
        if (intervalMinutes <= 0) return emptyList()

        val minTotalMinutes = minWindow * intervalMinutes
        val maxTotalMinutes = maxWindow * intervalMinutes
        val generatedTimeframes = mutableSetOf<String>()

        // Generate 5 evenly spaced, human-readable timeframes
        for (i in 0..4) {
            val ratio = i / 4.0
            val targetMinutes = minTotalMinutes + (ratio * (maxTotalMinutes - minTotalMinutes)).toLong()
            generatedTimeframes.add(formatMinutesToTimeframe(targetMinutes))
        }

        val sortedTimeframes = generatedTimeframes.toMutableList()
            .sortedWith(compareBy { parseTimeframeToMinutes(it) ?: 0L })
            .toMutableList()

        // --- FIX STARTS HERE ---
        // Verify that the first, shortest timeframe is not below the minimum required minutes.
        // This can happen due to the rounding logic in `formatMinutesToTimeframe`.
        if (sortedTimeframes.isNotEmpty()) {
            val firstOption = sortedTimeframes.first()
            val firstOptionMinutes = parseTimeframeToMinutes(firstOption) ?: 0L

            if (firstOptionMinutes < minTotalMinutes) {
                // If the first option is invalid, replace it with a valid one by rounding up.
                sortedTimeframes[0] = formatMinutesToTimeframeCeil(minTotalMinutes)
            }
        }
        // --- FIX ENDS HERE ---

        return sortedTimeframes.distinct() // Use distinct to remove duplicates that might arise from the fix
    }

    fun updateTimeframesForInterval(interval: String) {
        this.currentInterval = interval

        val validTimeframes = generateSensibleTimeframes(interval).toMutableList()

        validTimeframes.add("Create Custom")
        timeframeOptions = validTimeframes.toTypedArray()

        numberPicker.apply {
            displayedValues = null
            minValue = 0
            maxValue = timeframeOptions.size - 1
            value = 0

            displayedValues = timeframeOptions.map { if (it == "Create Custom") it else "Last $it" }.toTypedArray()
            invalidate()
        }

        selectedTimeframe = timeframeOptions.firstOrNull() ?: getDefaultTimeframeForInterval()
        onTimeframeSelectedForAnalysis(selectedTimeframe!!)
    }

    private fun styleNumberPicker(picker: NumberPicker, color: Int) {
        for (i in 0 until picker.childCount) {
            val child = picker.getChildAt(i)
            if (child is EditText) {
                try {
                    child.setTextColor(color)
                    child.setHintTextColor(color)
                    child.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    child.isCursorVisible = false
                    val typeface = ResourcesCompat.getFont(picker.context, R.font.sf_pro_text_medium)
                    if (typeface != null) child.typeface = typeface
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
        bottomSection.layoutParams =
            (bottomSection.layoutParams as LinearLayout.LayoutParams).apply {
                height = 0
                weight = 0f
            }
        main.postDelayed({
            bottomSection.visibility = View.GONE
            dragHandle.visibility = View.GONE
            if (currentMode == AnalysisMode.TRENDLINES) {
                marketChartLayout.trendlineButton.setBackgroundResource(R.drawable.white_circle)
                marketChartLayout.trendlineImg.setImageResource(R.drawable.trendlines_ic)
            } else {
                marketChartLayout.supportResistanceButton.setBackgroundResource(R.drawable.white_circle)
                marketChartLayout.supportResistanceImg.setImageResource(R.drawable.sr_ic)
            }
//            rotate_to_fullscreen.visibility = View.VISIBLE
            onComplete?.invoke()
        }, panelAnimationDuration)
    }
}