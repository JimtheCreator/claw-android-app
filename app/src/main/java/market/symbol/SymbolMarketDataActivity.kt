package market.symbol

import accounts.SignUpBottomSheet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentManager
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import bottomsheets.PriceAlertsBottomSheetFragment
import com.bumptech.glide.Glide
import com.bumptech.glide.manager.TargetTracker
import com.claw.ai.R
import com.claw.ai.databinding.ActivitySymbolMarketDataBinding
import com.claw.ai.databinding.MarketChartBinding
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.tradingview.lightweightcharts.api.series.models.Time
import com.tradingview.lightweightcharts.api.series.models.TimeRange
import factory.HomeViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import market.symbol.repo.MarketDataRepository
import market.symbol.ui.analysis.AnalysisPanelManager
import market.symbol.ui.market_chart.ChartManager
import market.symbol.viewmodel.AnalysisMode
import market.symbol.viewmodel.SymbolMarketDataViewModel
import model_interfaces.OnWatchlistActionListener
import models.Symbol
import viewmodels.HomeViewModel
import java.io.File
import java.util.Locale

class SymbolMarketDataActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySymbolMarketDataBinding
    private lateinit var viewModel: SymbolMarketDataViewModel
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var watchListListener: OnWatchlistActionListener
    private lateinit var chartManager: ChartManager
    private var isExpanded = false
    private var currentAnimator: ValueAnimator? = null
    private var symbol: String? = null
    private var initialPrice: Double? = null
    private var initialChange: Double? = null
    private val LOAD_MORE_COOLDOWN_MS = 3000L
    private var lastLoadMoreTime = 0L
    private lateinit var analysisPanelManager: AnalysisPanelManager
    private var onPermissionGrantedCallback: (() -> Unit)? = null

    // 1. Add the permission launcher as a property of your Activity
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Permission was granted, run the action that was waiting for it
                onPermissionGrantedCallback?.invoke()
                onPermissionGrantedCallback = null // Clear the callback
            } else {
                // Permission was denied. Inform the user.
                Toast.makeText(this, "Storage permission is required to save the image.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySymbolMarketDataBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeViewModel()
        initializeChartManager()
        initializeAnalysisPanelManager()
        extractAndDisplayInitialData()
        initializeTimeIntervalTabs()
        setupObservers()
        initializeWithSymbol()
        initializeAnalysisPanel()
        initializeClickHandlers()
    }

    private fun initializeChartManager() {
        chartManager = ChartManager(
            this,
            binding.marketChartLayout
        )
        { from, to ->
            handleVisibleTimeRangeChange(from, to)
        }
    }

    private fun initializeTimeIntervalTabs() {
        val intervals = listOf("1m", "5m", "15m", "30m", "1h", "2h", "1d", "1w", "1M")
        val tabLayout = binding.marketChartLayout.timeIntervalTabLayout
        tabLayout.removeAllTabs()
        tabLayout.tabMode = TabLayout.MODE_SCROLLABLE
        tabLayout.tabGravity = TabLayout.GRAVITY_START
        configureTabScrollView()

        for (interval in intervals) {
            val tab = tabLayout.newTab()
            val customTab = LayoutInflater.from(this).inflate(R.layout.tab_item, tabLayout, false)
            val text = customTab.findViewById<TextView>(R.id.tabTitle)
            val tabHolder = customTab.findViewById<LinearLayout>(R.id.tab_holder)

            tabHolder.background =
                ContextCompat.getDrawable(applicationContext, R.drawable.bg_unselected)
            text.text = interval
            text.setTextColor(ContextCompat.getColor(applicationContext, R.color.gray_inactive))

            tab.customView = customTab
            tabLayout.addTab(tab)
        }

        adjustTabSpacing()

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let {
                    updateTabAppearance(tab, true)
                    val textView = tab.customView?.findViewById<TextView>(R.id.tabTitle)
                    val interval = textView?.text?.toString() ?: "1m"

                    Log.d("SymbolMarketDataActivity", "Tab selected: $interval")
                    chartManager.clearAnalysis()
                    Log.d("SymbolMarketDataActivity", "Cleared analysis")
                    viewModel.cancelStream()
                    Log.d("SymbolMarketDataActivity", "Previous Stream cancelled")

                    binding.marketChartLayout.progressBar.visibility = View.VISIBLE
                    chartManager.setCandleData(emptyList())
                    chartManager.setVolumeData(emptyList())
                    viewModel.setInterval(interval)

                    analysisPanelManager.collapsePanel()
                    isExpanded = false
                    analysisPanelManager.updateTimeframesForInterval(interval)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                tab?.let { updateTabAppearance(it, false) }
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        tabLayout.getTabAt(0)?.let { firstTab ->
            firstTab.select()
            updateTabAppearance(firstTab, true)
            analysisPanelManager.updateTimeframesForInterval("1m")
        }
    }

    private fun expandPanel(
        isFromTrendline: Boolean,
        dragHandle: RelativeLayout,
        bottomSection: LinearLayout,
        main: LinearLayout,
        topSection: LinearLayout,
        marketChartLayout: MarketChartBinding
    ) {
        // NEW: Reset the swipe control when the panel expands
        analysisPanelManager.resetSwipeState()

        dragHandle.visibility = View.VISIBLE
        bottomSection.visibility = View.VISIBLE
        main.post {
            // ... rest of the expandPanel function is unchanged
            val transition = AutoTransition().apply {
                duration = 300
                interpolator = FastOutSlowInInterpolator()
            }
            TransitionManager.beginDelayedTransition(main, transition)

            bottomSection.layoutParams =
                (bottomSection.layoutParams as LinearLayout.LayoutParams).apply {
                    height = LinearLayout.LayoutParams.WRAP_CONTENT
                    weight = 0f
                }
            topSection.layoutParams = (topSection.layoutParams as LinearLayout.LayoutParams).apply {
                height = 0
                weight = 1f
            }

            if (isFromTrendline) {
                marketChartLayout.trendlineButton.setBackgroundResource(R.drawable.cool_black_circle)
                marketChartLayout.trendlineImg.setImageResource(R.drawable.close_ic_grey)
            } else {
                marketChartLayout.supportResistanceButton.setBackgroundResource(R.drawable.cool_black_circle)
                marketChartLayout.supportResistanceImg.setImageResource(R.drawable.close_ic_grey)
            }

            marketChartLayout.rotateToFullscreen.visibility = View.GONE
            isExpanded = true
        }
    }

    private fun initializeAnalysisPanel() {
        binding.bottomSection.visibility = View.GONE
        binding.dragHandle.visibility = View.GONE
        binding.bottomSection.layoutParams =
            (binding.bottomSection.layoutParams as LinearLayout.LayoutParams).apply { height = 0 }
        isExpanded = false
    }

    private fun initializeClickHandlers() {
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        binding.onClicksAndDrags(
            firebaseUser = firebaseUser,
            getSupportFragmentManager = { supportFragmentManager },
            finishActivity = { finish() })
    }

    private fun ActivitySymbolMarketDataBinding.onClicksAndDrags(
        firebaseUser: FirebaseUser?,
        getSupportFragmentManager: () -> FragmentManager,
        finishActivity: () -> Unit
    ) {
        closePage.setOnClickListener { finishActivity() }
        marketChartLayout.addToWatchlist.setOnClickListener {
            val userId = firebaseUser?.uid
            if (userId == null) {
                openSignUpBottomSheet(getSupportFragmentManager())
                return@setOnClickListener
            }
            val intent = (root.context as? ComponentActivity)?.intent
            val symbolStr = intent?.getStringExtra("SYMBOL") ?: ""
            val asset = intent?.getStringExtra("ASSET") ?: ""
            val baseCurrency = intent?.getStringExtra("BASE_CURRENCY") ?: ""
            val currentPrice = intent?.getDoubleExtra("CURRENT_PRICE", 0.0) ?: 0.0
            val change24h = intent?.getDoubleExtra("CHANGE_24H", 0.0) ?: 0.0
            val sparklineArray = intent?.getDoubleArrayExtra("SPARKLINE")
            val sparkline: List<Double> =
                sparklineArray?.let { ArrayList(it.toList()) } ?: ArrayList()
            val symbolObj = Symbol(
                symbolStr,
                asset,
                "",
                baseCurrency,
                currentPrice,
                change24h,
                currentPrice,
                change24h,
                0.0,
                sparkline,
                false
            )
            watchListListener.onAddToWatchlist(userId, symbolObj, "Binance")
        }
        marketChartLayout.createPriceAlert.setOnClickListener {
            val symbol =
                (root.context as? ComponentActivity)?.intent?.getStringExtra("SYMBOL") ?: ""
            val stringCurrentPrice = marketChartLayout.currentPrice.text.toString()
            val numeric = stringCurrentPrice.replace("[^\\d.-]".toRegex(), "")
            val price = numeric.toDoubleOrNull() ?: 0.0
            firebaseUser?.let { user ->
                val priceAlertFragment =
                    PriceAlertsBottomSheetFragment.newInstance(user.uid, symbol, price)
                priceAlertFragment.show(getSupportFragmentManager(), priceAlertFragment.tag)
            } ?: run {
                openSignUpBottomSheet(getSupportFragmentManager())
            }
        }
        // MODIFIED S/R Button Listener
        marketChartLayout.supportResistanceButton.setOnClickListener {
            currentAnimator?.cancel()
            // Set the mode in the ViewModel before opening the panel
            viewModel.setAnalysisMode(AnalysisMode.SUPPORT_RESISTANCE)
            analysisPanelManager.setMode(AnalysisMode.SUPPORT_RESISTANCE)

            if (!isExpanded) {
                expandPanel(false, dragHandle, bottomSection, main, topSection, marketChartLayout)
            } else {
                analysisPanelManager.collapsePanel()
                isExpanded = false
            }
        }
        // ADDED Trendline Button Listener
        marketChartLayout.trendlineButton.setOnClickListener {
            currentAnimator?.cancel()
            // Set the mode for trendlines
            viewModel.setAnalysisMode(AnalysisMode.TRENDLINES)
            analysisPanelManager.setMode(AnalysisMode.TRENDLINES)

            if (!isExpanded) {
                expandPanel(true, dragHandle, bottomSection, main, topSection, marketChartLayout)
            } else {
                analysisPanelManager.collapsePanel()
                isExpanded = false
            }
        }
    }

    private fun openSignUpBottomSheet(supportFragmentManager: FragmentManager) {
        val signUpBottomSheet = SignUpBottomSheet.newInstance()
        signUpBottomSheet.show(supportFragmentManager, signUpBottomSheet.tag)
    }

    private fun initViews() {
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        val userId = firebaseUser?.uid
        watchListListener = object : OnWatchlistActionListener {
            override fun onAddToWatchlist(ignoredUserId: String, symbol: Symbol, source: String) {
                if (userId != null) {
                    homeViewModel.addToWatchlist(userId, symbol, source)
                } else {
                    Toast.makeText(
                        applicationContext,
                        "Please sign in to modify watchlist.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onRemoveFromWatchlist(ignoredUserId: String, symbolTicker: String) {
                if (userId != null) {
                    homeViewModel.removeFromWatchlist(userId, symbolTicker)
                } else {
                    Toast.makeText(
                        applicationContext,
                        "Please sign in to modify watchlist.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        if (userId != null) {
            homeViewModel.loadWatchlist(userId)
        } else {
            binding.marketChartLayout.addToWatchlist.visibility = View.GONE
        }
    }

    private fun extractAndDisplayInitialData() {
        initViews()
        symbol = intent.getStringExtra("SYMBOL") ?: "BTCUSDT"
        val asset = intent.getStringExtra("ASSET") ?: ""
        initialPrice = intent.getDoubleExtra("CURRENT_PRICE", 0.0).takeIf { it != 0.0 }
        initialChange = intent.getDoubleExtra("CHANGE_24H", 0.0)
        val sparklineArray = intent.getDoubleArrayExtra("SPARKLINE")
        binding.marketChartLayout.symbol.text = symbol
        binding.marketChartLayout.asset.text = asset
        initialPrice?.let { price ->
            binding.marketChartLayout.currentPrice.text = String.format(Locale.US, "US$%.2f", price)
        }
        initialChange?.let { change ->
            binding.marketChartLayout.percentagePriceChange.text =
                String.format(Locale.US, "%.2f%%", change)
            val colorRes = if (change >= 0) R.color.green_chart_color else R.color.crimson_red
            binding.marketChartLayout.percentagePriceChange.setTextColor(
                ContextCompat.getColor(
                    this,
                    colorRes
                )
            )
        }
        chartManager.updateSparkline(sparklineArray)
    }

    private fun initializeViewModel() {
        val repository = MarketDataRepository()
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(SymbolMarketDataViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST") return SymbolMarketDataViewModel(repository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
        viewModel = ViewModelProvider(this, factory)[SymbolMarketDataViewModel::class.java]
        val homeViewFactory = HomeViewModelFactory(application)
        homeViewModel = ViewModelProvider(this, homeViewFactory)[HomeViewModel::class.java]
    }

    private fun initializeWithSymbol() {
        val symbol = intent.getStringExtra("SYMBOL") ?: "BTCUSDT"
        viewModel.setSymbol(symbol)
        viewModel.setInterval("1m")
    }

    private fun setupObservers() {
        homeViewModel.watchlist.observe(this) { symbols: List<Symbol>? ->
            if (symbols != null) {
                val isInWatchlist = symbols.any { it.symbol == symbol }
                binding.marketChartLayout.addToWatchlist.visibility =
                    if (isInWatchlist) View.GONE else View.VISIBLE
            }
        }

        lifecycleScope.launch {
            viewModel.hasInitialDataLoaded.collect { hasLoaded ->
                if (hasLoaded) {
                    chartManager.scrollToRealTime()
                }
            }
        }
        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.marketChartLayout.progressBar.visibility =
                    if (isLoading) View.VISIBLE else View.GONE
            }
        }
        lifecycleScope.launch {
            viewModel.price.collect { price ->
                price?.let {
                    binding.marketChartLayout.currentPrice.text =
                        String.format(Locale.US, "US$%.2f", it)
                }
            }
        }
        lifecycleScope.launch {
            viewModel.change.collect { change ->
                change?.let {
                    binding.marketChartLayout.percentagePriceChange.text =
                        String.format(Locale.US, "%.2f%%", it)
                    val colorRes = if (it >= 0) R.color.green_chart_color else R.color.crimson_red
                    binding.marketChartLayout.percentagePriceChange.setTextColor(
                        ContextCompat.getColor(
                            this@SymbolMarketDataActivity,
                            colorRes
                        )
                    )
                }
            }
        }
        lifecycleScope.launch {
            viewModel.error.collect { error ->
                error?.let {
                    Toast.makeText(this@SymbolMarketDataActivity, it, Toast.LENGTH_SHORT).show()
                }
            }
        }

        // New observers for analysis status
        lifecycleScope.launch {
            viewModel.isAnalyzing.collect { isAnalyzing ->
                if (isAnalyzing) {
                    analysisPanelManager.showLoadingAnimation()
                } else {
                    analysisPanelManager.hideLoadingAnimation()
                }
            }
        }

        // This lifecycleScope.launch block provides the required coroutine context.
        lifecycleScope.launch {
            viewModel.analysisResult.collect { result ->
                if (result != null) {
                    // This is the call site. It is now correctly calling a suspend function
                    // from within a coroutine.
                    chartManager.renderAnalysisData(result, viewModel.candles.value)
                }
            }
        }

        // Observer for S/R analysis (if you want a specific loader for it)
        lifecycleScope.launch {
            viewModel.isSRAnalysisInProgress.collect { isInProgress ->
                // Handle S/R loading state if needed
            }
        }

        lifecycleScope.launch {
            viewModel.analysisStatus.collect { status ->
                binding.loadingStatusText.text = status
            }
        }

        // This observer controls the visibility of the in-panel loading/result states
        lifecycleScope.launch {
            viewModel.isTrendlineAnalysisInProgress.collect { isInProgress ->
                val analysisPage = binding.analysispagelayout
                if (isInProgress) {
                    // Analysis has started, show the in-panel loading state
                    analysisPage.numberPicker.visibility = View.GONE
                    analysisPage.swipeToAnalyzeActionLayout.root.visibility = View.GONE

                    analysisPage.trendlineAnalysisLayout.visibility = View.VISIBLE
                    analysisPage.trendlineAnalysisLoadingState.visibility = View.VISIBLE
                    analysisPage.trendlineAnalysisResults.visibility = View.GONE
                }
            }
        }

        // This observer updates the text of the in-panel loading state
        lifecycleScope.launch {
            viewModel.trendlineAnalysisStatus.collect { status ->
                if (status.isNotEmpty()) {
                    binding.analysispagelayout.loadingState.text = status
                }
            }
        }

        lifecycleScope.launch {
            viewModel.candles.collect { candles ->
                if (candles.isEmpty()) {
                    chartManager.clearAnalysis()
                }
            }
        }

        lifecycleScope.launch {
            viewModel.candlestickData.collectLatest { data ->
                chartManager.setCandleData(data)
            }
        }

        lifecycleScope.launch {
            viewModel.volumeData.collectLatest { data ->
                chartManager.setVolumeData(data)
            }
        }

        lifecycleScope.launch {
            viewModel.trendlineChartUrl.collect { url ->
                if (url == null) return@collect

                val analysisPage = binding.analysispagelayout

                // 1. Show a loading state while we generate the composite image.
                analysisPage.trendlineAnalysisLoadingState.visibility = View.VISIBLE
                analysisPage.trendlineAnalysisResults.visibility = View.GONE
                analysisPage.loadingState.text = "Generating your chart..."

                // 2. Launch a background coroutine for image processing.
                lifecycleScope.launch {
                    // 2a. Download the raw chart image from the backend.
                    val chartImageUri = downloadAndCacheImageFromUrl(this@SymbolMarketDataActivity, url)

                    if (chartImageUri != null) {
                        // 2b. Get data needed for the composite image header.
                        val symbolText = symbol ?: "N/A"
                        val intervalText = viewModel.interval.value
                        // ✅ FIX: Get the actual selected timeframe from the ViewModel
                        val timeframeText = viewModel.selectedAnalysisTimeframe.value

                        // 2c. Create the final composite image.
                        val compositeImageUri = createCompositeAnalysisImage(
                            this@SymbolMarketDataActivity,
                            chartImageUri,
                            symbolText,
                            intervalText,
                            timeframeText
                        )

                        // 3. Switch back to the main thread to update the UI.
                        withContext(Dispatchers.Main) {
                            if (compositeImageUri != null) {
                                // 4. Give the final URI to the panel manager for sharing/saving.
                                analysisPanelManager.setAnalysisImageUris(
                                    compositeUri = compositeImageUri,
                                    rawChartUri = chartImageUri
                                )

                                // 5. Hide the loader and show the final result.
                                analysisPage.trendlineAnalysisLoadingState.visibility = View.GONE
                                analysisPage.trendlineAnalysisResults.visibility = View.VISIBLE

                                // 6. Load the final composite image into the ImageView.
                                Glide.with(this@SymbolMarketDataActivity)
                                    .load(compositeImageUri)
                                    .into(analysisPage.trendlineChart)

                                // 7. HIDE the on-screen header and footer to prevent duplication.
                                analysisPage.analysisHeader.visibility = View.GONE
                                analysisPage.analysisFooter.visibility = View.GONE
                            } else {
                                // Handle failure in creating the composite image.
                                analysisPage.trendlineAnalysisLoadingState.visibility = View.GONE
                                Toast.makeText(this@SymbolMarketDataActivity, "Failed to create analysis chart.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        // Handle failure in downloading the initial chart.
                        withContext(Dispatchers.Main) {
                            analysisPage.trendlineAnalysisLoadingState.visibility = View.GONE
                            Toast.makeText(this@SymbolMarketDataActivity, "Failed to load analysis chart.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    /**
     * Downloads an image from a URL using Glide and saves it to the app's private cache.
     * @param context The application context.
     * @param url The URL of the image to download.
     * @return A content URI for the cached file, or null on failure.
     */
    private suspend fun downloadAndCacheImageFromUrl(context: Context, url: String): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                val file = Glide.with(context).asFile().load(url).submit().get()
                val cachePath = File(context.cacheDir, "raw_charts")
                cachePath.mkdirs()
                val destFile = File(cachePath, "raw_chart_${System.currentTimeMillis()}.png")
                file.copyTo(destFile, overwrite = true)
                FileProvider.getUriForFile(context, "${context.packageName}.provider", destFile)
            } catch (e: Exception) {
                Log.e("ImageCache", "Failed to cache image from URL", e)
                null
            }
        }
    }

    // --- COMPLETELY REWRITTEN FUNCTION ---
    private suspend fun createCompositeAnalysisImage(
        context: Context,
        chartImageUri: Uri,
        symbol: String,
        interval: String,
        timeframe: String
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            // 1. Inflate the layout that contains the header, image, and footer
            val viewToCapture = withContext(Dispatchers.Main) {
                LayoutInflater.from(context).inflate(R.layout.analysispage, null)
                    .findViewById<LinearLayout>(R.id.trendline_analysis_image_frame)
            }
            val header = viewToCapture.findViewById<LinearLayout>(R.id.analysis_header)
            val footer = viewToCapture.findViewById<LinearLayout>(R.id.analysis_footer)
            val chartImageView = viewToCapture.findViewById<android.widget.ImageView>(R.id.trendline_chart)

            // 2. Load the downloaded chart and scale it
            val originalChartBitmap = Glide.with(context).asBitmap().load(chartImageUri).submit().get()
            val targetWidth = 1080 // Standard width for a high-quality image
            val scale = targetWidth.toFloat() / originalChartBitmap.width
            val targetChartHeight = (originalChartBitmap.height * scale).toInt()
            val scaledChartBitmap = Bitmap.createScaledBitmap(originalChartBitmap, targetWidth, targetChartHeight, true)

            var finalHeight = 0

            // 3. Populate, measure, and layout the views on the Main thread
            withContext(Dispatchers.Main) {
                // Populate header text
                header.findViewById<TextView>(R.id.symbol).text = symbol.uppercase()
                header.findViewById<TextView>(R.id.interval_analyzed).text = "$interval Chart"
                header.findViewById<TextView>(R.id.timeframe_of_the_analysis).text = "Last $timeframe Trendline-Analysis"

                // **FIX STARTS HERE**

                // A. Apply the scaled bitmap to the ImageView
                chartImageView.setImageBitmap(scaledChartBitmap)

                // B. Explicitly set the ImageView's layout params to match the bitmap dimensions
                val imageLayoutParams = LinearLayout.LayoutParams(targetWidth, targetChartHeight)
                chartImageView.layoutParams = imageLayoutParams

                // C. Measure the entire container view to ensure all children get their correct size
                val widthSpec = View.MeasureSpec.makeMeasureSpec(targetWidth, View.MeasureSpec.EXACTLY)
                val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                viewToCapture.measure(widthSpec, heightSpec)

                // D. Get the final measured height of the container
                finalHeight = viewToCapture.measuredHeight

                // E. Layout the view to its final measured size
                viewToCapture.layout(0, 0, viewToCapture.measuredWidth, finalHeight)

                // **FIX ENDS HERE**
            }

            // 4. Create the final bitmap and canvas
            val finalBitmap = Bitmap.createBitmap(targetWidth, finalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(finalBitmap)

            // Draw a dark background that matches the UI theme
            canvas.drawColor(ContextCompat.getColor(context, R.color.darkTheme))

            // 5. Draw the now-correctly-laid-out view onto our canvas
            withContext(Dispatchers.Main) {
                viewToCapture.draw(canvas)
            }

            // 6. Save the final bitmap to cache and return its URI
            val cachePath = File(context.cacheDir, "analysis_exports")
            cachePath.mkdirs()
            val destFile = File(cachePath, "Watchers_Analysis_${System.currentTimeMillis()}.png")
            destFile.outputStream().use {
                finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            FileProvider.getUriForFile(context, "${context.packageName}.provider", destFile)
        } catch (e: Exception) {
            Log.e("CompositeImage", "Failed to create composite image", e)
            null
        }
    }

    private fun configureTabScrollView() {
        val tabLayoutGroup: ViewGroup = binding.marketChartLayout.timeIntervalTabLayout
        for (i in 0 until tabLayoutGroup.childCount) {
            val child = tabLayoutGroup.getChildAt(i)
            if (child.javaClass.simpleName.contains("ScrollView")) {
                val scrollView = child as ViewGroup
                scrollView.clipToPadding = false
                scrollView.setPadding(0, 0, 0, 0)
                val params = scrollView.layoutParams as MarginLayoutParams
                params.marginStart = -15
                params.marginEnd = -15
                scrollView.layoutParams = params
                break
            }
        }
    }

    private fun adjustTabSpacing() {
        for (i in 0 until binding.marketChartLayout.timeIntervalTabLayout.tabCount) {
            val tabView =
                (binding.marketChartLayout.timeIntervalTabLayout.getChildAt(0) as ViewGroup).getChildAt(
                    i
                )
            val params = getMarginLayoutParams(tabView, i)
            tabView.layoutParams = params
        }
    }

    private fun updateTabAppearance(tab: TabLayout.Tab, isSelected: Boolean) {
        val text = tab.customView?.findViewById<TextView>(R.id.tabTitle)
        val tabHolder = tab.customView?.findViewById<LinearLayout>(R.id.tab_holder)
        if (text != null && tabHolder != null) {
            if (isSelected) {
                tabHolder.setBackgroundResource(R.drawable.bg_selected)
                text.setTextColor(ContextCompat.getColor(applicationContext, R.color.white))
            } else {
                tabHolder.setBackgroundResource(R.drawable.bg_unselected)
                text.setTextColor(ContextCompat.getColor(applicationContext, R.color.gray_inactive))
            }
        }
    }

    private fun getMarginLayoutParams(tabView: View, i: Int): MarginLayoutParams {
        val params = tabView.layoutParams as MarginLayoutParams
        when (i) {
            0 -> {
                params.marginStart = 0
                params.marginEnd = 15
            }

            binding.marketChartLayout.timeIntervalTabLayout.tabCount - 1 -> {
                params.marginStart = 15
                params.marginEnd = 0
            }

            else -> {
                params.marginStart = 15
                params.marginEnd = 15
            }
        }
        return params
    }

    private fun handleVisibleTimeRangeChange(from: Time.Utc, to: Time.Utc) {
        if (!viewModel.hasInitialDataLoaded.value) return
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastLoadMoreTime < LOAD_MORE_COOLDOWN_MS || viewModel.isLoading.value) return
        val candles = viewModel.candles.value
        if (candles.isEmpty()) return
        val earliestTime = candles.first().time
        val fromTime = from.timestamp
        val visibleDuration = to.timestamp - fromTime
        val threshold = (visibleDuration * 0.1).toLong()
        if (fromTime < earliestTime + threshold) {
            lastLoadMoreTime = currentTime
            viewModel.loadMoreHistoricalData()
        }
    }

    private fun scrollChartToTimeframe(timeframe: String) {
        // FIX: Add a guard clause to prevent scrolling while the chart is loading data.
        if (viewModel.isLoading.value) {
            Log.d("ChartDebug", "Skipping scroll: Chart is loading new data.")
            return
        }

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

        // FIX: Wrap the call in a try-catch block to prevent the crash during re-initialization.
        try {
            binding.marketChartLayout.candlesStickChart.api.timeScale.setVisibleRange(timeRange)
        } catch (e: IllegalStateException) {
            Log.e("ChartDebug", "Failed to set visible range. Chart might be re-initializing.", e)
            // This error can be ignored. The chart is not ready for a scroll operation,
            // likely due to an orientation change or rapid tab switching. The app will not crash,
            // and the chart will render correctly once its data is loaded.
        }
    }

    private fun parseTimeframeToMinutes(timeframe: String): Long? {
        if (timeframe.isEmpty()) return null
        val regex = Regex("^(\\d+)([mhdwMY])$")
        val matchResult = regex.find(timeframe.lowercase()) ?: return null
        val (numberStr, unit) = matchResult.destructured
        val number = numberStr.toLongOrNull() ?: return null

        return when (unit) {
            "m" -> number
            "h" -> number * 60
            "d" -> number * 60 * 24
            "w" -> number * 60 * 24 * 7
            "M" -> number * 60 * 24 * 30
            "y" -> number * 60 * 24 * 365
            else -> null
        }
    }

    private fun initializeAnalysisPanelManager() {
        analysisPanelManager = AnalysisPanelManager(
            binding,
            { selectedTimeframe ->
                scrollChartToTimeframe(selectedTimeframe)
            },
            viewModel,
            chartManager,
            this.onBackPressedDispatcher, // Pass the activity's dispatcher
            this, // The activity itself is a LifecycleOwner
            onPermissionNeeded = { actionToRunAfterPermission ->
                this.onPermissionGrantedCallback = actionToRunAfterPermission
                requestPermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        currentAnimator?.cancel()
    }
}