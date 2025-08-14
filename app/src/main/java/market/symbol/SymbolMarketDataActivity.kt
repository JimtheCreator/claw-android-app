package market.symbol

import accounts.SignUpBottomSheet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
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
import com.claw.ai.R
import com.claw.ai.databinding.ActivitySymbolMarketDataBinding
import com.claw.ai.databinding.MarketChartBinding
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.tradingview.lightweightcharts.api.series.models.CandlestickData
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
import java.text.SimpleDateFormat
import java.util.Date
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
    // REMOVED: These are no longer needed as they come from the cached object
    // private var initialPrice: Double? = null
    // private var initialChange: Double? = null
    private val LOAD_MORE_COOLDOWN_MS = 3000L
    private var lastLoadMoreTime = 0L
    private lateinit var analysisPanelManager: AnalysisPanelManager
    private var onPermissionGrantedCallback: (() -> Unit)? = null
    private var currentlyDisplayedCandles = mutableListOf<CandlestickData>()

    private var isAddingToWatchlist = false
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                onPermissionGrantedCallback?.invoke()
                onPermissionGrantedCallback = null
            } else {
                Toast.makeText(
                    this,
                    "Storage permission is required to save the image.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySymbolMarketDataBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentlyDisplayedCandles.clear()
        symbol = intent.getStringExtra("SYMBOL") ?: "BTCUSDT"

        // Set up the basic UI first
        initializeViewModel()
        initViews()
        viewModel.loadInitialSymbolData(symbol!!)

        // Defer heavy stuff until the UI is shown
        binding.root.post {
            initializeChartManager()
            initializeAnalysisPanelManager()
            initializeTimeIntervalTabs()
            setupObservers()
            initializeWithSymbol() // This will now fetch detailed data in the background
            initializeAnalysisPanel()
            initializeClickHandlers()
        }
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

    // NEW: A dedicated function to display the initial data from the cache.
    private fun displayInitialData(cachedSymbol: Symbol) {
        binding.marketChartLayout.symbol.text = cachedSymbol.symbol
        binding.marketChartLayout.asset.text = cachedSymbol.asset

        // Use the consistent price/change fields from your cached Symbol object
        val price = cachedSymbol.currentPrice
        val change = cachedSymbol.get_24hChange()

        binding.marketChartLayout.currentPrice.text = String.format(Locale.US, "US$%.2f", price)
        binding.marketChartLayout.percentagePriceChange.text = String.format(Locale.US, "%.2f%%", change)
        val colorRes = if (change >= 0) R.color.green_chart_color else R.color.crimson_red
        binding.marketChartLayout.percentagePriceChange.setTextColor(ContextCompat.getColor(this, colorRes))

        // Update sparkline from cached data
        val sparkline = cachedSymbol.sparkline?.toDoubleArray()
        chartManager.updateSparkline(sparkline)
    }

    private fun initializeTimeIntervalTabs() {
        val intervals = listOf("1m", "5m", "15m", "30m", "1h", "2h", "1d")
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
                    if (viewModel.interval.value == interval) {
                        Log.d(
                            "SymbolMarketDataActivity",
                            "Tab for the current interval ($interval) was re-selected. Ignoring."
                        )
                        return
                    }
                    Log.d("SymbolMarketDataActivity", "Tab selected: $interval")
                    chartManager.clearAnalysis()
                    Log.d("SymbolMarketDataActivity", "Cleared analysis")
                    viewModel.cancelStream()
                    Log.d("SymbolMarketDataActivity", "Previous Stream cancelled")
                    currentlyDisplayedCandles.clear()
                    chartManager.setCandleData(emptyList())
                    chartManager.setVolumeData(emptyList())
                    viewModel.setInterval(interval)
                    if (interval == "1d" || interval == "1w" || interval == "1M") {
                        binding.marketChartLayout.trendlineButton.visibility = View.GONE
                    }
                    analysisPanelManager.collapsePanel()
                    isExpanded = false
                    analysisPanelManager.updateTimeframesForInterval(interval)
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) { tab?.let { updateTabAppearance(it, false) } }
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
        analysisPanelManager.resetSwipeState()
        dragHandle.visibility = View.VISIBLE
        bottomSection.visibility = View.VISIBLE
        main.post {
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
//            marketChartLayout.rotateToFullscreen.visibility = View.GONE
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
            val currentPrice = viewModel.cachedSymbol.value?.currentPrice ?: 0.0
            val change24h = viewModel.cachedSymbol.value?.get_24hChange() ?: 0.0
            val sparkline: List<Double> = viewModel.cachedSymbol.value?.sparkline ?: emptyList()

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
            val userId = firebaseUser?.uid
            if (userId == null) {
                openSignUpBottomSheet(getSupportFragmentManager())
                return@setOnClickListener
            }
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
        marketChartLayout.supportResistanceButton.setOnClickListener {
            val userId = firebaseUser?.uid
            if (userId == null) {
                openSignUpBottomSheet(getSupportFragmentManager())
                return@setOnClickListener
            }
            currentAnimator?.cancel()
            viewModel.setAnalysisMode(AnalysisMode.SUPPORT_RESISTANCE)
            analysisPanelManager.setMode(AnalysisMode.SUPPORT_RESISTANCE)

            if (!isExpanded) {
                expandPanel(false, dragHandle, bottomSection, main, topSection, marketChartLayout)
            } else {
                analysisPanelManager.collapsePanel()
                isExpanded = false
            }
        }
        marketChartLayout.trendlineButton.setOnClickListener {
            val userId = firebaseUser?.uid
            if (userId == null) {
                openSignUpBottomSheet(getSupportFragmentManager())
                return@setOnClickListener
            }
            currentAnimator?.cancel()
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

    private fun initializeViewModel() {
        val repository = MarketDataRepository(application)
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(SymbolMarketDataViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    // Pass the application context to the ViewModel if it needs it for the repo
                    return SymbolMarketDataViewModel(application, repository) as T
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
        // NEW: Observe the cached symbol data for the initial display
        lifecycleScope.launch {
            viewModel.cachedSymbol.collect { cachedSymbol ->
                cachedSymbol?.let {
                    displayInitialData(it)
                }
            }
        }
        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.marketChartLayout.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }
        lifecycleScope.launch {
            viewModel.chunkedCandlestickData.collect { chunk ->
                if (chunk.isEmpty() && currentlyDisplayedCandles.isNotEmpty()) {
                    currentlyDisplayedCandles.clear()
                } else {
                    currentlyDisplayedCandles.addAll(chunk)
                }
                chartManager.setCandleData(currentlyDisplayedCandles.toList())
                Log.d("ChartRender", "Rendered chunk. Total candles: ${currentlyDisplayedCandles.size}")
            }
        }
        lifecycleScope.launch {
            viewModel.volumeData.collectLatest { data ->
                chartManager.setVolumeData(data)
            }
        }
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
        lifecycleScope.launch {
            viewModel.isAnalyzing.collect { isAnalyzing ->
                if (isAnalyzing) {
                    analysisPanelManager.showLoadingAnimation()
                } else {
                    analysisPanelManager.hideLoadingAnimation()
                }
            }
        }
        lifecycleScope.launch {
            viewModel.analysisResult.collect { result ->
                if (result != null) {
                    chartManager.renderAnalysisData(result, viewModel.candles.value)
                }
            }
        }
        lifecycleScope.launch {
            viewModel.isSRAnalysisInProgress.collect { isInProgress ->
            }
        }
        lifecycleScope.launch {
            viewModel.analysisStatus.collect { status ->
                binding.loadingStatusText.text = status
            }
        }
        lifecycleScope.launch {
            viewModel.isTrendlineAnalysisInProgress.collect { isInProgress ->
                val analysisPage = binding.analysispagelayout
                if (isInProgress) {
                    analysisPage.numberPicker.visibility = View.GONE
                    analysisPage.swipeToAnalyzeActionLayout.root.visibility = View.GONE
                    analysisPage.trendlineAnalysisLayout.visibility = View.VISIBLE
                    analysisPage.trendlineAnalysisLoadingState.visibility = View.VISIBLE
                    analysisPage.trendlineAnalysisResults.visibility = View.GONE
                }
            }
        }
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
            viewModel.interval.collect { interval ->
                if (interval == "1d" || interval == "1w" || interval == "1M") {
                    binding.marketChartLayout.trendlineButton.visibility = View.GONE
                } else {
                    binding.marketChartLayout.trendlineButton.visibility = View.VISIBLE
                }
            }
        }
        lifecycleScope.launch {
            viewModel.trendlineChartUrl.collect { url ->
                if (url == null) return@collect
                val analysisPage = binding.analysispagelayout
                analysisPage.trendlineAnalysisLoadingState.visibility = View.VISIBLE
                analysisPage.trendlineAnalysisResults.visibility = View.GONE
                analysisPage.loadingState.text = "Generating your chart..."
                lifecycleScope.launch {
                    try {
                        val chartImageUri =
                            downloadAndCacheImageFromUrl(this@SymbolMarketDataActivity, url)
                        if (chartImageUri != null) {
                            val symbolText = symbol ?: "N/A"
                            val intervalText = viewModel.interval.value
                            val timeframeText = viewModel.selectedAnalysisTimeframe.value
                            val compositeImageUri = createCompositeAnalysisImage(
                                this@SymbolMarketDataActivity,
                                chartImageUri,
                                symbolText,
                                intervalText,
                                timeframeText
                            )
                            withContext(Dispatchers.Main) {
                                if (compositeImageUri != null) {
                                    analysisPanelManager.setAnalysisImageUris(
                                        compositeUri = compositeImageUri,
                                        rawChartUri = chartImageUri
                                    )
                                    analysisPage.trendlineAnalysisLoadingState.visibility =
                                        View.GONE
                                    analysisPage.footer.visibility = View.GONE
                                    analysisPage.watermark.visibility = View.GONE
                                    val liveCardView = analysisPage.cardview
                                    liveCardView.cardElevation = 0f
                                    liveCardView.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    (liveCardView.parent.parent as? View)?.background = null
                                    analysisPage.trendlineAnalysisResults.visibility = View.VISIBLE
                                    val finalImageView = analysisPage.trendlineChart
                                    Glide.with(this@SymbolMarketDataActivity)
                                        .load(compositeImageUri)
                                        .into(finalImageView)
                                } else {
                                    handleAnalysisFailure(
                                        "Failed to create analysis chart.",
                                        analysisPage
                                    )
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                handleAnalysisFailure(
                                    "Failed to download chart image. Please check your internet connection.",
                                    analysisPage
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("SymbolMarketDataActivity", "Error processing analysis chart", e)
                        withContext(Dispatchers.Main) {
                            handleAnalysisFailure(
                                "An error occurred while processing the chart.",
                                analysisPage
                            )
                        }
                    }
                }
            }
        }
    }

    private fun handleAnalysisFailure(
        message: String,
        analysisPage: com.claw.ai.databinding.AnalysispageBinding
    ) {
        analysisPage.trendlineAnalysisLoadingState.visibility = View.GONE
        analysisPage.trendlineAnalysisResults.visibility = View.GONE

        // Show error message to user
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

        // Optionally, collapse the panel or show retry option
        analysisPanelManager.collapsePanel()
        isExpanded = false
    }

    private suspend fun createCompositeAnalysisImage(
        context: Context,
        chartImageUri: Uri,
        symbol: String,
        interval: String,
        timeframe: String
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val margin = 30

            // 1. Inflate the layout and find views
            val viewToCapture = withContext(Dispatchers.Main) {
                LayoutInflater.from(context).inflate(R.layout.analysispage, null)
                    .findViewById<LinearLayout>(R.id.trendline_analysis_image_frame)
            }
            val templateCardView =
                viewToCapture.findViewById<CardView>(R.id.cardview)
            val chartImageView =
                viewToCapture.findViewById<android.widget.ImageView>(R.id.trendline_chart)
            val footerFrame = viewToCapture.findViewById<LinearLayout>(R.id.footer)

            // 2. Load and scale the chart image
            val originalChartBitmap =
                Glide.with(context).asBitmap().load(chartImageUri).submit().get()
            val targetWidth = 1080
            val scale = targetWidth.toFloat() / originalChartBitmap.width
            val targetChartHeight = (originalChartBitmap.height * scale).toInt()
            val scaledChartBitmap =
                Bitmap.createScaledBitmap(originalChartBitmap, targetWidth, targetChartHeight, true)

            var finalHeight = 0
            var finalWidth = 0
            var cornerRadius = 0f // Variable to hold the corner radius

            // 3. Layout Pass: Populate views, measure them, and get the corner radius
            withContext(Dispatchers.Main) {
                templateCardView.cardElevation = 0f // Add this line
                templateCardView.setCardBackgroundColor(android.graphics.Color.parseColor("#E0D9C8"))

                // Populate text
                footerFrame.findViewById<TextView>(R.id.symbol).text = symbol.uppercase()
                footerFrame.findViewById<TextView>(R.id.interval_analyzed).text = "$interval Chart"
                // NEW CODE:
                val currentTime = System.currentTimeMillis()
                val dateFormat = SimpleDateFormat("MMM d, yyyy @ h:mma", Locale.US)
                val formattedTimestamp = dateFormat.format(Date(currentTime))

                footerFrame.findViewById<TextView>(R.id.timeframe_of_the_analysis).text =
                    "$timeframe Trendline Analysis • $formattedTimestamp"


                chartImageView.setImageBitmap(scaledChartBitmap)
                chartImageView.layoutParams =
                    FrameLayout.LayoutParams(targetWidth, targetChartHeight)

                // Measure and layout the view
                val widthSpec =
                    View.MeasureSpec.makeMeasureSpec(targetWidth, View.MeasureSpec.EXACTLY)
                val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                viewToCapture.measure(widthSpec, heightSpec)
                finalHeight = viewToCapture.measuredHeight
                finalWidth = viewToCapture.measuredWidth
                viewToCapture.layout(0, 0, finalWidth, finalHeight)

                // Get the corner radius from the card AFTER it has been laid out
                cornerRadius = templateCardView.radius
            }


            // 4. Drawing Pass: Create bitmap and draw with consistent corner radius
            val finalBitmap = Bitmap.createBitmap(
                finalWidth + margin * 2,
                finalHeight + margin * 2,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(finalBitmap)
            canvas.drawColor(ContextCompat.getColor(context, android.R.color.transparent))
            canvas.translate(margin.toFloat(), margin.toFloat())

            val cardViewTop = templateCardView.top.toFloat()
            val cardViewLeft = templateCardView.left.toFloat()
            val cardViewRight = (templateCardView.left + templateCardView.width).toFloat()
            val cardViewBottom = (templateCardView.top + templateCardView.height).toFloat()

            val clipPath = android.graphics.Path()
            val clipRect = android.graphics.RectF(cardViewLeft, cardViewTop, cardViewRight, cardViewBottom)
            val fixedCornerRadius = 20f
            clipPath.addRoundRect(clipRect, fixedCornerRadius, fixedCornerRadius, android.graphics.Path.Direction.CW)

            canvas.save()
            canvas.clipPath(clipPath)

            withContext(Dispatchers.Main) {
                viewToCapture.draw(canvas)
                canvas.restore()
            }

            // --- THE DEFINITIVE FIX ---
            // Now, draw the entire view hierarchy onto the pre-clipped canvas.
            withContext(Dispatchers.Main) {
                viewToCapture.draw(canvas)
            }

            // 5. Save and return the final image URI
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

    /**
     * Downloads an image from a URL using Glide and saves it to the app's private cache.
     * @param context The application context.
     * @param url The URL of the image to download.
     * @return A content URI for the cached file, or null on failure.
     */
    private suspend fun downloadAndCacheImageFromUrl(context: Context, url: String): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("ImageCache", "Attempting to download image from URL: $url")

                // Validate URL
                if (url.isBlank()) {
                    Log.e("ImageCache", "URL is empty or blank")
                    return@withContext null
                }

                // Check network connectivity
                if (!isNetworkAvailable(context)) {
                    Log.e("ImageCache", "No network connectivity available")
                    return@withContext null
                }

                // Add timeout and retry logic
                val file = Glide.with(context)
                    .asFile()
                    .load(url)
                    .timeout(30000) // 30 second timeout
                    .submit()
                    .get()

                Log.d("ImageCache", "Successfully downloaded file: ${file.absolutePath}")

                val cachePath = File(context.cacheDir, "raw_charts")
                if (!cachePath.exists()) {
                    val created = cachePath.mkdirs()
                    Log.d("ImageCache", "Cache directory created: $created")
                }

                val destFile = File(cachePath, "raw_chart_${System.currentTimeMillis()}.png")
                file.copyTo(destFile, overwrite = true)

                Log.d("ImageCache", "File cached successfully: ${destFile.absolutePath}")

                val uri =
                    FileProvider.getUriForFile(context, "${context.packageName}.provider", destFile)
                Log.d("ImageCache", "Generated URI: $uri")

                return@withContext uri

            } catch (e: java.util.concurrent.ExecutionException) {
                Log.e("ImageCache", "ExecutionException while downloading image", e)

                // Log the root cause
                val cause = e.cause
                when (cause) {
                    is com.bumptech.glide.load.HttpException -> {
                        Log.e("ImageCache", "HTTP Error - Status Code: ${cause.statusCode}")
                        Log.e("ImageCache", "HTTP Error - Message: ${cause.message}")
                    }

                    is java.net.UnknownHostException -> {
                        Log.e("ImageCache", "Unknown host - check internet connection and URL")
                    }

                    is java.net.SocketTimeoutException -> {
                        Log.e("ImageCache", "Request timed out - server may be slow or unreachable")
                    }

                    is javax.net.ssl.SSLException -> {
                        Log.e(
                            "ImageCache",
                            "SSL/TLS error - certificate issues or insecure connection"
                        )
                    }

                    else -> {
                        Log.e(
                            "ImageCache",
                            "Other network error: ${cause?.javaClass?.simpleName} - ${cause?.message}"
                        )
                    }
                }
                null
            } catch (e: Exception) {
                Log.e("ImageCache", "Unexpected error while caching image", e)
                null
            }
        }
    }

    /**
     * Check if network is available
     */
    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo?.isConnected == true
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