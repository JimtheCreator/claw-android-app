package market.symbol

import accounts.SignUpBottomSheet
import android.animation.ValueAnimator
import android.graphics.Color
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import bottomsheets.PriceAlertsBottomSheetFragment
import com.claw.ai.R
import com.claw.ai.databinding.ActivitySymbolMarketDataBinding
import com.claw.ai.databinding.MarketChartBinding
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.tradingview.lightweightcharts.api.series.models.Time
import com.tradingview.lightweightcharts.api.series.models.TimeRange
import factory.HomeViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import market.symbol.repo.Candle
import market.symbol.repo.MarketDataRepository
import market.symbol.ui.analysis.AnalysisPanelManager
import market.symbol.ui.market_chart.ChartManager
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
    private lateinit var chartManager: ChartManager
    private var isExpanded = false
    private var currentAnimator: ValueAnimator? = null
    private var symbol: String? = null
    private var initialPrice: Double? = null
    private var initialChange: Double? = null
    private val LOAD_MORE_COOLDOWN_MS = 3000L
    private var lastLoadMoreTime = 0L

    private lateinit var analysisPanelManager: AnalysisPanelManager

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
        chartManager = ChartManager(this, binding.marketChartLayout) { from, to ->
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
            marketChartLayout.aiButton.setBackgroundResource(R.drawable.cool_black_circle)
            marketChartLayout.closePanel.setImageResource(R.drawable.white_close_ic)
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
        marketChartLayout.aiButton.setOnClickListener {
            currentAnimator?.cancel()
            if (!isExpanded) {
                expandPanel(dragHandle, bottomSection, main, topSection, marketChartLayout)
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
            viewModel.candles.collectLatest { candles ->
                if (candles.isNotEmpty()) {
                    chartManager.setCandleData(candles)
                    chartManager.setVolumeData(candles)
                } else {
                    chartManager.setCandleData(emptyList())
                    chartManager.setVolumeData(emptyList())
                }
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

        binding.marketChartLayout.candlesStickChart.api.timeScale.setVisibleRange(timeRange)

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
        // Ensure viewModel and chartManager are initialized before this function is called.
        analysisPanelManager = AnalysisPanelManager(
            binding,
            { selectedTimeframe ->
                scrollChartToTimeframe(selectedTimeframe)
            },
            viewModel, // Pass the viewModel instance
            chartManager // Pass the chartManager instance
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        currentAnimator?.cancel()
    }
}