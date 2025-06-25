package market.symbol

import accounts.SignUpBottomSheet
import market.symbol.adapters.TimeframeDropdownAdapter
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import bottomsheets.PriceAlertsBottomSheetFragment
import com.claw.ai.R
import com.claw.ai.databinding.ActivitySymbolMarketDataBinding
import com.claw.ai.databinding.MarketChartBinding
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
import com.tradingview.lightweightcharts.api.options.models.*
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
import market.symbol.ui.analysis.AnalysisPanelManager
import market.symbol.viewmodel.SymbolMarketDataViewModel
import model_interfaces.OnWatchlistActionListener
import models.Symbol
import viewmodels.HomeViewModel
import java.util.*

class SymbolMarketDataActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySymbolMarketDataBinding
    private lateinit var viewModel: SymbolMarketDataViewModel
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var watchListListener: OnWatchlistActionListener
    private var candleSeries: SeriesApi? = null
    private var volumeSeries: SeriesApi? = null
    private var isExpanded = false
    private var currentAnimator: ValueAnimator? = null
    private var symbol: String? = null
    private var initialPrice: Double? = null
    private var initialChange: Double? = null
    private val upColor = IntColor(Color.parseColor("#26a69a"))
    private val downColor = IntColor(Color.parseColor("#ef5350"))
    private val LOAD_MORE_COOLDOWN_MS = 3000L
    private var lastLoadMoreTime = 0L

    // The new manager for the analysis panel UI
    private lateinit var analysisPanelManager: AnalysisPanelManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySymbolMarketDataBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize the new manager to handle all analysis panel logic
        analysisPanelManager = AnalysisPanelManager(binding) { selectedTimeframe ->
            // This callback is triggered when a user selects a timeframe for analysis
            // You can trigger your analysis logic here
            Toast.makeText(this, "Analysis for $selectedTimeframe requested", Toast.LENGTH_SHORT).show()
        }

        initializeViewModel()
        extractAndDisplayInitialData()
        initializeChart()
        initializeTimeIntervalTabs()
        setupObservers()
        initializeWithSymbol()
        initializeAnalysisPanel()
        initializeClickHandlers()
    }


    private fun initializeTimeIntervalTabs() {
        val intervals = listOf("1m", "5m", "15m", "30m", "1h", "2h", "1d", "1w", "1M")
        val tabLayout = binding.marketChartLayout.timeIntervalTabLayout
        tabLayout.removeAllTabs()
        tabLayout.tabMode = TabLayout.MODE_SCROLLABLE
        tabLayout.tabGravity = TabLayout.GRAVITY_START
        configureTabScrollView()

        // Add tabs
        for (interval in intervals) {
            val tab = tabLayout.newTab()
            val customTab = LayoutInflater.from(this).inflate(R.layout.tab_item, tabLayout, false)
            val text = customTab.findViewById<TextView>(R.id.tabTitle)
            val tabHolder = customTab.findViewById<LinearLayout>(R.id.tab_holder)

            tabHolder.background = ContextCompat.getDrawable(applicationContext, R.drawable.bg_unselected)
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

                    // **FIX: Explicitly cancel previous stream before starting a new one**
                    // You will need to implement `cancelDataStream()` in your ViewModel.
                    // See the implementation guide at the end of the response.
                    viewModel.cancelStream()

                    Log.d("SymbolMarketDataActivity", "Previous Stream cancelled")

                    binding.marketChartLayout.progressBar.visibility = View.VISIBLE
                    candleSeries?.setData(emptyList())
                    volumeSeries?.setData(emptyList())
                    viewModel.setInterval(interval)

                    // Delegate timeframe list updates to the manager
                    collapsePanel(binding.main, binding.bottomSection, binding.dragHandle, binding.topSection, binding.marketChartLayout)
                    analysisPanelManager.updateTimeframesForInterval(interval)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                tab?.let { updateTabAppearance(it, false) }
            }
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Set initial selection
        tabLayout.getTabAt(0)?.let { firstTab ->
            firstTab.select()
            updateTabAppearance(firstTab, true)
            analysisPanelManager.updateTimeframesForInterval("1m")
        }
    }

    private fun collapsePanel(
        main: LinearLayout,
        bottomSection: LinearLayout,
        dragHandle: RelativeLayout,
        topSection: LinearLayout,
        marketChartLayout: MarketChartBinding)
    {
        // THE FIX: Close the dropdown but pass 'false' to disable its personal animation.
        analysisPanelManager.closeDropdown(animated = false)

        // This single, parent transition will now smoothly animate BOTH the panel collapsing
        // AND the dropdown disappearing, as it's a child view whose visibility has changed.
        val transition = AutoTransition().apply {
            duration = 300
            interpolator = FastOutSlowInInterpolator()
        }
        TransitionManager.beginDelayedTransition(main, transition)

        // These layout changes will be animated as before
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
        }, 300)
        marketChartLayout.aiButton.setBackgroundResource(R.drawable.ai_circle)
        marketChartLayout.closePanel.setImageResource(R.drawable.ai_stars)
        isExpanded = false
    }

    private fun expandPanel(
        dragHandle: RelativeLayout,
        bottomSection: LinearLayout,
        main: LinearLayout,
        topSection: LinearLayout,
        marketChartLayout: MarketChartBinding)
    {
        dragHandle.visibility = View.VISIBLE
        bottomSection.visibility = View.VISIBLE
        main.post {
            val transition = AutoTransition().apply {
                duration = 300 // MODIFIED: Reduced duration slightly for a quicker response
                // MODIFIED: Switched to a smoother, more standard interpolator.
                interpolator = FastOutSlowInInterpolator()
            }
            TransitionManager.beginDelayedTransition(main, transition)

            // The rest of the function remains the same...
            bottomSection.layoutParams = (bottomSection.layoutParams as LinearLayout.LayoutParams).apply {
                height = LinearLayout.LayoutParams.WRAP_CONTENT
                weight = 0f
            }
            topSection.layoutParams = (topSection.layoutParams as LinearLayout.LayoutParams).apply {
                height = 0
                weight = 1f
            }
            marketChartLayout.aiButton.setBackgroundResource(R.drawable.cool_black_circle)
            marketChartLayout.closePanel.setImageResource(R.drawable.white_close_ic)
            isExpanded = true
        }
    }



    private fun initializeAnalysisPanel() {
        binding.bottomSection.visibility = View.GONE
        binding.dragHandle.visibility = View.GONE
        binding.bottomSection.layoutParams = (binding.bottomSection.layoutParams as LinearLayout.LayoutParams).apply { height = 0 }
        isExpanded = false
    }

    private fun initializeClickHandlers() {
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        binding.onClicksAndDrags(firebaseUser = firebaseUser, getSupportFragmentManager = { supportFragmentManager }, finishActivity = { finish() })
    }

    private fun ActivitySymbolMarketDataBinding.onClicksAndDrags(
        firebaseUser: FirebaseUser?,
        getSupportFragmentManager: () -> FragmentManager,
        finishActivity: () -> Unit)
    {
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
            val sparkline: List<Double> = sparklineArray?.let { ArrayList(it.toList()) } ?: ArrayList()
            val symbolObj = Symbol(symbolStr, asset, "", baseCurrency, currentPrice, change24h, currentPrice, change24h, 0.0, sparkline, false)
            watchListListener.onAddToWatchlist(userId, symbolObj, "Binance")
        }
        marketChartLayout.createPriceAlert.setOnClickListener {
            val symbol = (root.context as? ComponentActivity)?.intent?.getStringExtra("SYMBOL") ?: ""
            val stringCurrentPrice = marketChartLayout.currentPrice.text.toString()
            val numeric = stringCurrentPrice.replace("[^\\d.-]".toRegex(), "")
            val price = numeric.toDoubleOrNull() ?: 0.0
            firebaseUser?.let { user ->
                val priceAlertFragment = PriceAlertsBottomSheetFragment.newInstance(user.uid, symbol, price)
                priceAlertFragment.show(getSupportFragmentManager(), priceAlertFragment.tag)
            } ?: run {
                openSignUpBottomSheet(getSupportFragmentManager())
            }
        }
        marketChartLayout.aiButton.setOnClickListener {
            currentAnimator?.cancel()
            if (!isExpanded) {
                expandPanel(dragHandle, bottomSection, main, topSection, marketChartLayout)
            } else {
                collapsePanel(main, bottomSection, dragHandle, topSection, marketChartLayout)
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
                    Toast.makeText(applicationContext, "Please sign in to modify watchlist.", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onRemoveFromWatchlist(ignoredUserId: String, symbolTicker: String) {
                if (userId != null) {
                    homeViewModel.removeFromWatchlist(userId, symbolTicker)
                } else {
                    Toast.makeText(applicationContext, "Please sign in to modify watchlist.", Toast.LENGTH_SHORT).show()
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
        initialPrice?.let { price -> binding.marketChartLayout.currentPrice.text = String.format(Locale.US, "US$%.2f", price) }
        initialChange?.let { change ->
            binding.marketChartLayout.percentagePriceChange.text = String.format(Locale.US, "%.2f%%", change)
            val colorRes = if (change >= 0) R.color.green_chart_color else R.color.crimson_red
            binding.marketChartLayout.percentagePriceChange.setTextColor(ContextCompat.getColor(this, colorRes))
        }
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
        viewModel.setSymbol(symbol)
        viewModel.setInterval("1m")
    }

    private fun setupObservers() {
        homeViewModel.watchlist.observe(this) { symbols: List<Symbol>? ->
            if (symbols != null) {
                val isInWatchlist = symbols.any { it.symbol == symbol }
                binding.marketChartLayout.addToWatchlist.visibility = if (isInWatchlist) View.GONE else View.VISIBLE
            }
        }
        lifecycleScope.launch {
            viewModel.candles.collectLatest { candles ->
                if (candles.isNotEmpty()) {
                    candleSeries?.setData(candles.map { it.toCandlestickData() })
                    volumeSeries?.setData(candles.map { it.toVolumeData() })
                } else {
                    candleSeries?.setData(emptyList())
                    volumeSeries?.setData(emptyList())
                }
            }
        }
        lifecycleScope.launch {
            viewModel.hasInitialDataLoaded.collect { hasLoaded ->
                if (hasLoaded) {
                    binding.marketChartLayout.candlesStickChart.api.timeScale.scrollToRealTime()
                }
            }
        }
        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.marketChartLayout.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }
        lifecycleScope.launch {
            viewModel.price.collect { price ->
                price?.let { binding.marketChartLayout.currentPrice.text = String.format(Locale.US, "US$%.2f", it) }
            }
        }
        lifecycleScope.launch {
            viewModel.change.collect { change ->
                change?.let {
                    binding.marketChartLayout.percentagePriceChange.text = String.format(Locale.US, "%.2f%%", it)
                    val colorRes = if (it >= 0) R.color.green_chart_color else R.color.crimson_red
                    binding.marketChartLayout.percentagePriceChange.setTextColor(ContextCompat.getColor(this@SymbolMarketDataActivity, colorRes))
                }
            }
        }
        lifecycleScope.launch {
            viewModel.error.collect { error ->
                error?.let { Toast.makeText(this@SymbolMarketDataActivity, it, Toast.LENGTH_SHORT).show() }
            }
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
            val tabView = (binding.marketChartLayout.timeIntervalTabLayout.getChildAt(0) as ViewGroup).getChildAt(i)
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
            layout = LayoutOptions().apply {
                background = SolidColor(ContextCompat.getColor(applicationContext, R.color.darkTheme))
                textColor = IntColor(Color.WHITE)
            }
            grid = GridOptions().apply {
                vertLines = GridLineOptions().apply { color = IntColor(0xFF1c1c1c.toInt()) }
                horzLines = GridLineOptions().apply { color = IntColor(0xFF1c1c1c.toInt()) }
            }
            timeScale = TimeScaleOptions().apply {
                timeVisible = true
                borderVisible = false
                fixLeftEdge = false
                rightBarStaysOnScroll = true
                localization = LocalizationOptions().apply { locale = Locale.getDefault().toLanguageTag() }
            }
            rightPriceScale = PriceScaleOptions().apply { borderVisible = false }
            leftPriceScale = PriceScaleOptions().apply {
                scaleMargins = PriceScaleMargins(0.85f, 0.02f)
                visible = false
            }
        }
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
        chartsView.api.addCandlestickSeries(candleOptions) { series -> candleSeries = series }
        val volumeOptions = HistogramSeriesOptions().apply {
            base = 0.0f
            baseLineWidth = LineWidth.TWO
            priceScaleId = PriceScaleId.LEFT
        }
        chartsView.api.addHistogramSeries(volumeOptions) { series -> volumeSeries = series }
        chartsView.api.timeScale.subscribeVisibleTimeRangeChange { timeRange ->
            if (timeRange == null || !viewModel.hasInitialDataLoaded.value) return@subscribeVisibleTimeRangeChange
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastLoadMoreTime < LOAD_MORE_COOLDOWN_MS || viewModel.isLoading.value) return@subscribeVisibleTimeRangeChange
            val candles = viewModel.candles.value
            if (candles.isEmpty()) return@subscribeVisibleTimeRangeChange
            val earliestTime = candles.first().time
            val fromTime = (timeRange.from as Time.Utc).timestamp
            val visibleDuration = (timeRange.to as Time.Utc).timestamp - fromTime
            val threshold = (visibleDuration * 0.1).toLong()
            if (fromTime < earliestTime + threshold) {
                lastLoadMoreTime = currentTime
                viewModel.loadMoreHistoricalData()
            }
        }
    }

    private fun Candle.toCandlestickData(): CandlestickData = CandlestickData(Time.Utc(this.time), this.open.toFloat(), this.high.toFloat(), this.low.toFloat(), this.close.toFloat())
    private fun Candle.toVolumeData(): HistogramData = HistogramData(Time.Utc(this.time), this.volume.toFloat(), if (close >= open) upColor else downColor)

    private fun updateSparkline(sparklineArray: DoubleArray?) {
        if (sparklineArray == null || sparklineArray.isEmpty()) return
        val chart = binding.marketChartLayout.sparklineChart
        val entries = mutableListOf<Entry>()
        sparklineArray.forEachIndexed { index, value -> entries.add(Entry(index.toFloat(), value.toFloat())) }
        setupSparklineChart(chart, entries)
    }

    private fun setupSparklineChart(chart: LineChart, entries: List<Entry>) {
        if (entries.isEmpty()) return
        val isDowntrend = entries.last().y < entries.first().y
        val lineColor = if (isDowntrend) Color.RED else Color.GREEN
        val shadeColor = if (isDowntrend) R.drawable.chart_fill_red else R.drawable.chart_fill_green
        val dataSet = LineDataSet(entries, "Price").apply {
            this.color = lineColor
            setDrawCircles(false)
            lineWidth = 1.5f
            setDrawValues(false)
            setDrawFilled(true)
            fillDrawable = ContextCompat.getDrawable(chart.context, shadeColor)
        }
        chart.data = LineData(dataSet)
        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawLabels(false)
            setDrawGridLines(false)
        }
        chart.axisLeft.isEnabled = false
        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = false
        chart.description.isEnabled = false
        chart.setTouchEnabled(false)
        chart.invalidate()
    }

    override fun onDestroy() {
        super.onDestroy()
        currentAnimator?.cancel()
    }
}