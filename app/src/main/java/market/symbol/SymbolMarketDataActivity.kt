package market.symbol

import accounts.SignUpBottomSheet
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import bottomsheets.PriceAlertsBottomSheetFragment
import com.claw.ai.R
import com.claw.ai.databinding.ActivitySymbolMarketDataBinding
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.tradingview.lightweightcharts.api.chart.models.color.IntColor
import com.tradingview.lightweightcharts.api.chart.models.color.surface.SolidColor
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi
import com.tradingview.lightweightcharts.api.options.models.CandlestickSeriesOptions
import com.tradingview.lightweightcharts.api.options.models.GridLineOptions
import com.tradingview.lightweightcharts.api.options.models.GridOptions
import com.tradingview.lightweightcharts.api.options.models.HistogramSeriesOptions
import com.tradingview.lightweightcharts.api.options.models.LayoutOptions
import com.tradingview.lightweightcharts.api.options.models.LocalizationOptions
import com.tradingview.lightweightcharts.api.options.models.PriceScaleMargins
import com.tradingview.lightweightcharts.api.options.models.PriceScaleOptions
import com.tradingview.lightweightcharts.api.options.models.TimeScaleOptions
import com.tradingview.lightweightcharts.api.series.enums.LineWidth
import com.tradingview.lightweightcharts.api.series.models.CandlestickData
import com.tradingview.lightweightcharts.api.series.models.HistogramData
import com.tradingview.lightweightcharts.api.series.models.PriceScaleId
import com.tradingview.lightweightcharts.api.series.models.Time
import factory.HomeViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import market.symbol.repo.Candle
import market.symbol.repo.MarketDataRepository
import market.symbol.viewmodel.SymbolMarketDataViewModel
import model_interfaces.OnWatchlistActionListener
import models.Symbol
import viewmodels.HomeViewModel
import java.util.Locale

class SymbolMarketDataActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySymbolMarketDataBinding
    private lateinit var viewModel: SymbolMarketDataViewModel
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var watchListListener: OnWatchlistActionListener
    private var candleSeries: SeriesApi? = null
    private var volumeSeries: SeriesApi? = null

    // Store initial data from intent
    private var initialPrice: Double? = null
    private var initialChange: Double? = null

    // Define colors for candles and volume bars
    private val upColor = IntColor(Color.parseColor("#26a69a"))
    private val downColor = IntColor(Color.parseColor("#ef5350"))

    // NEW: Add cooldown properties
    private var chartInitializedTime = 0L
    private val LOAD_MORE_COOLDOWN_MS = 3000L // 3 second cooldown
    private var lastLoadMoreTime = 0L
    private var symbol: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySymbolMarketDataBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeViewModel()
        // Extract and display initial data immediately
        extractAndDisplayInitialData()

        initializeChart()
        initializeTimeIntervalTabs()
        setupObservers()

        // Initialize with symbol and interval after UI is set up
        initializeWithSymbol()

        // NEW: Initialize the onClicksAndDrags function
        initializeClickHandlers()
    }

    // Add this new method to your class
    private fun initializeClickHandlers() {
        // You'll need to create instances of HomeViewModel and get FirebaseUser
        // Assuming you have these available or can create them:

        val firebaseUser = FirebaseAuth.getInstance().currentUser

        // Call the extension function on your binding
        binding.onClicksAndDrags(
            firebaseUser = firebaseUser,
            getSupportFragmentManager = { supportFragmentManager },
            finishActivity = { finish() }
        )
    }

    private fun ActivitySymbolMarketDataBinding.onClicksAndDrags(
        firebaseUser: FirebaseUser?, // Pass your FirebaseUser here
        getSupportFragmentManager: () -> androidx.fragment.app.FragmentManager, // Pass a lambda to get FragmentManager
        finishActivity: () -> Unit // Pass a lambda to finish the activity
    ) {
        // Close Page OnClickListener
        closePage.setOnClickListener {
            finishActivity() // Call the lambda to finish the activity
        }

        // Add to Watchlist OnClickListener
        marketChartLayout.addToWatchlist.setOnClickListener {
            val userId = firebaseUser?.uid
            if (userId == null) {
                openSignUpBottomSheet(getSupportFragmentManager())
                return@setOnClickListener // Use return@setOnClickListener to return from the lambda
            }

            // Retrieve necessary data from intent extras
            // Assuming 'intent' is accessible from the current context (e.g., an Activity)
            // You might need to pass the intent or access it via `this.intent` if this code is in an Activity
            val intent = (root.context as? androidx.activity.ComponentActivity)?.intent
            val symbolStr =
                intent?.getStringExtra("SYMBOL") ?: "" // Renamed to avoid conflict with class name
            val asset = intent?.getStringExtra("ASSET") ?: ""
            val baseCurrency = intent?.getStringExtra("BASE_CURRENCY") ?: ""
            val currentPrice = intent?.getDoubleExtra("CURRENT_PRICE", 0.0) ?: 0.0
            val change24h = intent?.getDoubleExtra("CHANGE_24H", 0.0) ?: 0.0
            val sparklineArray = intent?.getDoubleArrayExtra("SPARKLINE")

            // Convert sparkline array to List<Double> using ArrayList for Java compatibility
            val sparkline: List<Double> = sparklineArray?.let {
                ArrayList(it.toList()) // Convert array to list and then to ArrayList
            } ?: ArrayList() // Provide an empty ArrayList if null

            // Create Symbol object with available data (default values for missing fields)
            // IMPORTANT: Use positional arguments as Symbol is a Java class.
            // The constructor signature from your Symbol.java:
            // public Symbol(String symbol, String asset, String pair, String baseCurrency, double currentPrice, double _24hChange, double price, double change, double _24hVolume, List<Double> sparkline, boolean isInWatchlist) {
            val symbolObj = Symbol(
                symbolStr,           // 1. String symbol
                asset,               // 2. String asset
                "",             // 3. String pair (not available, set empty)
                baseCurrency,        // 4. String baseCurrency
                currentPrice,        // 5. double currentPrice
                change24h,           // 6. double _24hChange
                currentPrice,        // 7. double price (same as currentPrice)
                change24h,           // 8. double change (same as 24h change)
                0.0,      // 9. double _24hVolume (default 0)
                sparkline,           // 10. List<Double> sparkline
                false     // 11. boolean isInWatchlist (default false)
            )

            // Add to watchlist
            watchListListener.onAddToWatchlist(userId, symbolObj, "Binance")
        }

        // Create Price Alert OnClickListener
        marketChartLayout.createPriceAlert.setOnClickListener {
            val symbol =
                (root.context as? androidx.activity.ComponentActivity)?.intent?.getStringExtra("SYMBOL")
                    ?: ""
            val stringCurrentPrice = marketChartLayout.currentPrice.text.toString()
            // Remove all non-numeric characters except dot and minus
            val numeric = stringCurrentPrice.replace("[^\\d.-]".toRegex(), "")
            val price =
                numeric.toDoubleOrNull() ?: 0.0 // Safely parse to Double, default to 0.0 if invalid

            firebaseUser?.let { user ->
                val priceAlertFragment =
                    PriceAlertsBottomSheetFragment.newInstance(user.uid, symbol, price)
                priceAlertFragment.show(getSupportFragmentManager(), priceAlertFragment.tag)
            } ?: run {
                openSignUpBottomSheet(getSupportFragmentManager())
            }
        }
    }

    private fun openSignUpBottomSheet(supportFragmentManager: FragmentManager) {
        val signUpBottomSheet = SignUpBottomSheet.newInstance()
        signUpBottomSheet.show(supportFragmentManager, signUpBottomSheet.tag)
    }

    private fun initViews() {
        // Load watchlist if user is logged in
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        val userId = firebaseUser?.uid

        watchListListener = object : OnWatchlistActionListener {
            override fun onAddToWatchlist(
                ignoredUserId: String,
                symbol: Symbol,
                source: String
            ) { // userId from adapter is ignored
                if (userId != null && symbol != null) {
                    homeViewModel.addToWatchlist(userId, symbol, source)
                } else {
                    Toast.makeText(
                        applicationContext,
                        "Please sign in to modify watchlist.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onRemoveFromWatchlist(
                ignoredUserId: String,
                symbolTicker: String
            ) { // userId from adapter is ignored
                if (userId != null && symbolTicker != null) {
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

        val sparklineArray = intent.getDoubleArrayExtra("SPARKLINE")

        // Convert sparkline array to List<Double>
        val sparkline: MutableList<Double> = java.util.ArrayList()
        if (sparklineArray != null) {
            for (value in sparklineArray) {
                sparkline.add(value)
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

        // Display initial data immediately
        binding.marketChartLayout.symbol.text = symbol
        binding.marketChartLayout.asset.text = asset

        // Show initial price if available
        initialPrice?.let { price ->
            binding.marketChartLayout.currentPrice.text = String.format(Locale.US, "US$%.2f", price)
        }

        // Show initial change if available
        initialChange?.let { change ->
            binding.marketChartLayout.percentagePriceChange.text =
                String.format(Locale.US, "%.2f%%", change)
            val colorRes = if (change >= 0) R.color.green_chart_color else R.color.crimson_red
            binding.marketChartLayout.percentagePriceChange.setTextColor(
                ContextCompat.getColor(this, colorRes)
            )
        }

        // Update sparkline
        updateSparkline(sparklineArray)
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
        Log.d("SymbolMarketDataActivity", "Initializing with symbol: $symbol")
        viewModel.setSymbol(symbol)
        viewModel.setInterval("1m") // Default interval
    }

    private fun setupObservers() {
        // Observe watchlist to update button visibility - FIXED: Remove lifecycleScope for LiveData
        homeViewModel.watchlist.observe(this) { symbols: List<Symbol>? ->
            if (symbols != null) {
                val isInWatchlist = symbols.any { it.symbol == symbol }
                binding.marketChartLayout.addToWatchlist.visibility =
                    if (isInWatchlist) View.GONE else View.VISIBLE
            }
        }

        // Observe the unified candle list from the ViewModel (StateFlow/Flow)
        lifecycleScope.launch {
            viewModel.candles.collectLatest { candles ->
                Log.d("SymbolMarketDataActivity", "Updating chart with ${candles.size} candles.")
                if (candles.isNotEmpty()) {
                    // Set the data for the series
                    candleSeries?.setData(candles.map { it.toCandlestickData() })
                    volumeSeries?.setData(candles.map { it.toVolumeData() })
                } else {
                    // Clear the chart if there's no data
                    candleSeries?.setData(emptyList())
                    volumeSeries?.setData(emptyList())
                }
            }
        }

        // Observe hasInitialDataLoaded to scroll the chart (StateFlow/Flow)
        lifecycleScope.launch {
            viewModel.hasInitialDataLoaded.collect { hasLoaded ->
                if (hasLoaded) {
                    // Scroll to the most recent data point once loaded
                    binding.marketChartLayout.candlesStickChart.api.timeScale.scrollToRealTime()
                    Log.d(
                        "SymbolMarketDataActivity",
                        "Initial data loaded. Scrolling to real-time."
                    )
                }
            }
        }

        // Observe loading state (StateFlow/Flow)
        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.marketChartLayout.progressBar.visibility =
                    if (isLoading) View.VISIBLE else View.GONE
            }
        }

        // Observe real-time price and change updates (StateFlow/Flow)
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

        // Observe errors (StateFlow/Flow)
        lifecycleScope.launch {
            viewModel.error.collect { error ->
                error?.let {
                    Toast.makeText(this@SymbolMarketDataActivity, it, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun initializeTimeIntervalTabs() {
        val intervals = listOf(
            "1m", "5m", "15m", "30m", "1h", "2h", "1d", "1w", "1M"
        )

        // Clear any existing tabs
        binding.marketChartLayout.timeIntervalTabLayout.removeAllTabs()

        // Set tab mode before adding tabs
        binding.marketChartLayout.timeIntervalTabLayout.tabMode = TabLayout.MODE_SCROLLABLE
        binding.marketChartLayout.timeIntervalTabLayout.tabGravity = TabLayout.GRAVITY_START

        // Configure scroll view
        configureTabScrollView()

        // Add tabs
        for (interval in intervals) {
            val tab = binding.marketChartLayout.timeIntervalTabLayout.newTab()
            val customTab = LayoutInflater.from(this)
                .inflate(R.layout.tab_item, binding.marketChartLayout.timeIntervalTabLayout, false)

            val text = customTab.findViewById<TextView>(R.id.tabTitle)
            val tabHolder = customTab.findViewById<LinearLayout>(R.id.tab_holder)

            tabHolder.background =
                ContextCompat.getDrawable(applicationContext, R.drawable.bg_unselected)
            text.text = interval
            text.setTextColor(ContextCompat.getColor(applicationContext, R.color.gray_inactive))

            tab.customView = customTab
            binding.marketChartLayout.timeIntervalTabLayout.addTab(tab)
        }

        // Adjust spacing after tabs are added
        adjustTabSpacing()

        // Set up tab selection listener
        binding.marketChartLayout.timeIntervalTabLayout.addOnTabSelectedListener(object :
            TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let {
                    // Update UI for selected tab
                    updateTabAppearance(tab, true)

                    // Get the selected interval
                    val text = tab.customView?.findViewById<TextView>(R.id.tabTitle)
                    val interval = text?.text?.toString() ?: "1m"

                    Log.d("SymbolMarketDataActivity", "Tab selected: $interval")

                    // Show loading immediately for smooth transition
                    binding.marketChartLayout.progressBar.visibility = View.VISIBLE

                    // Clear chart data for immediate visual feedback
                    candleSeries?.setData(emptyList())

                    // Update interval in ViewModel
                    viewModel.setInterval(interval)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                tab?.let { updateTabAppearance(it, false) }
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                // Handle reselection if needed
            }
        })

        // Select first tab by default
        val firstTab = binding.marketChartLayout.timeIntervalTabLayout.getTabAt(0)
        firstTab?.let { tab ->
            binding.marketChartLayout.timeIntervalTabLayout.selectTab(tab, false)
            updateTabAppearance(tab, true)
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

    private fun initializeChart() {
        val chartsView = binding.marketChartLayout.candlesStickChart

        chartsView.api.applyOptions {
            // Layout configuration
            layout = LayoutOptions().apply {
                background =
                    SolidColor(ContextCompat.getColor(applicationContext, R.color.darkTheme))
                textColor = IntColor(Color.WHITE)
            }

            // Grid configuration
            grid = GridOptions().apply {
                vertLines = GridLineOptions().apply {
                    color = IntColor(0xFF1c1c1c.toInt())
                }
                horzLines = GridLineOptions().apply {
                    color = IntColor(0xFF1c1c1c.toInt())
                }
            }

            // Time scale configuration
            timeScale = TimeScaleOptions().apply {
                timeVisible = true           // Show time on the scale
                borderVisible = false        // Hide the border
                fixLeftEdge = false // Allow scrolling past the left edge
                rightBarStaysOnScroll = true // Keep the right bar visible during scrolling
                localization = LocalizationOptions().apply {
                    locale = Locale.getDefault().toLanguageTag() // Use device locale
                }
            }

            // Price scale configuration
            rightPriceScale = PriceScaleOptions().apply {
                borderVisible = false
            }
            leftPriceScale = PriceScaleOptions().apply {
                scaleMargins = PriceScaleMargins(0.85f, 0.02f)
                visible = false
            }
        }

        // Add candlestick series (example)
        val candleOptions = CandlestickSeriesOptions().apply {
            upColor = this@SymbolMarketDataActivity.upColor
            downColor = this@SymbolMarketDataActivity.downColor
            borderUpColor = this@SymbolMarketDataActivity.upColor
            borderDownColor = this@SymbolMarketDataActivity.downColor
            wickUpColor = this@SymbolMarketDataActivity.upColor
            wickDownColor = this@SymbolMarketDataActivity.downColor
            borderVisible = true
            wickVisible = true
        }
        chartsView.api.addCandlestickSeries(candleOptions) { series ->
            candleSeries = series
            Log.d("SymbolMarketDataActivity", "Candlestick series initialized")
        }

        // Add histogram series for volume (example)
        val volumeOptions = HistogramSeriesOptions().apply {
            base = 0.0f
            baseLineWidth = LineWidth.TWO
            priceScaleId = PriceScaleId.LEFT
        }

        chartsView.api.addHistogramSeries(volumeOptions) { series ->
            volumeSeries = series
        }

        chartsView.api.timeScale.subscribeVisibleTimeRangeChange { timeRange ->
            if (timeRange == null) return@subscribeVisibleTimeRangeChange

            if (!viewModel.hasInitialDataLoaded.value) {
                return@subscribeVisibleTimeRangeChange
            }

            val currentTime = System.currentTimeMillis()
            val timeSinceLastLoad = currentTime - lastLoadMoreTime

            if (timeSinceLastLoad < LOAD_MORE_COOLDOWN_MS || viewModel.isLoading.value) {
                return@subscribeVisibleTimeRangeChange
            }

            val candles = viewModel.candles.value
            if (candles.isEmpty()) return@subscribeVisibleTimeRangeChange

            val earliestTime = candles.first().time
            val fromTime = (timeRange.from as Time.Utc).timestamp
            val toTime = (timeRange.to as Time.Utc).timestamp
            val latestTime = candles.last().time

            Log.d("ChartScroll", "Visible: $fromTime to $toTime, Data: $earliestTime to $latestTime")

            // Calculate the visible duration
            val visibleDuration = toTime - fromTime

            // Set a threshold, e.g., 10% of the visible duration
            val threshold = (visibleDuration * 0.1).toLong()

            // Trigger load more when fromTime is within the threshold of earliestTime
            if (fromTime < earliestTime + threshold) {
                Log.d("ChartScroll", "Approaching earliest data, loading more. fromTime: $fromTime, earliestTime: $earliestTime, threshold: $threshold")
                lastLoadMoreTime = currentTime
                viewModel.loadMoreHistoricalData()
            }
        }
    }

    // Data conversion
    private fun Candle.toCandlestickData(): CandlestickData {
        return CandlestickData(
            time = Time.Utc(this.time),
            open = this.open.toFloat(),
            high = this.high.toFloat(),
            low = this.low.toFloat(),
            close = this.close.toFloat()
        )
    }

    private fun Candle.toVolumeData(): HistogramData {
        val color = if (close >= open) upColor else downColor

        return HistogramData(
            time = Time.Utc(this.time),
            value = this.volume.toFloat(),
            color = color
        )
    }

    private fun updateSparkline(sparklineArray: DoubleArray?) {
        if (sparklineArray == null || sparklineArray.isEmpty()) return

        val chart = binding.marketChartLayout.sparklineChart
        val entries = mutableListOf<Entry>()

        sparklineArray.forEachIndexed { index, value ->
            entries.add(Entry(index.toFloat(), value.toFloat()))
        }

        setupSparklineChart(chart, entries)
    }

    private fun setupSparklineChart(chart: LineChart, entries: List<Entry>) {
        if (entries.isEmpty()) return

        val isDowntrend = entries.last().y < entries.first().y
        val lineColor = if (isDowntrend) Color.RED else Color.GREEN
        val shadeColor = if (isDowntrend) R.drawable.chart_fill_red else R.drawable.chart_fill_green

        val dataSet = LineDataSet(entries, "Price")
        dataSet.color = lineColor
        dataSet.setDrawCircles(false)
        dataSet.lineWidth = 1.5f
        dataSet.setDrawValues(false)
        dataSet.setDrawFilled(true)
        dataSet.fillDrawable = ContextCompat.getDrawable(chart.context, shadeColor)

        val lineData = LineData(dataSet)
        chart.data = lineData

        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawLabels(false)
        xAxis.setDrawGridLines(false)

        chart.axisLeft.setDrawLabels(false)
        chart.axisLeft.isEnabled = false
        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = false
        chart.description.isEnabled = false
        chart.setTouchEnabled(false)

        chart.invalidate()
    }

    override fun onDestroy() {
        Log.d("SymbolMarketDataActivity", "Activity destroyed, cleaning up")
        super.onDestroy()
    }
}