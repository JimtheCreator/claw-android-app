package market.symbol

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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.claw.ai.R
import com.claw.ai.databinding.ActivitySymbolMarketDataBinding
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.material.tabs.TabLayout
import com.tradingview.lightweightcharts.api.chart.models.color.IntColor
import com.tradingview.lightweightcharts.api.chart.models.color.surface.SolidColor
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi
import com.tradingview.lightweightcharts.api.options.models.CandlestickSeriesOptions
import com.tradingview.lightweightcharts.api.options.models.GridLineOptions
import com.tradingview.lightweightcharts.api.options.models.GridOptions
import com.tradingview.lightweightcharts.api.options.models.HistogramSeriesOptions
import com.tradingview.lightweightcharts.api.options.models.LayoutOptions
import com.tradingview.lightweightcharts.api.options.models.PriceScaleMargins
import com.tradingview.lightweightcharts.api.options.models.PriceScaleOptions
import com.tradingview.lightweightcharts.api.options.models.TimeScaleOptions
import com.tradingview.lightweightcharts.api.series.enums.LineWidth
import com.tradingview.lightweightcharts.api.series.models.CandlestickData
import com.tradingview.lightweightcharts.api.series.models.HistogramData
import com.tradingview.lightweightcharts.api.series.models.PriceScaleId
import com.tradingview.lightweightcharts.api.series.models.Time
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import market.symbol.repo.Candle
import market.symbol.repo.MarketDataRepository
import market.symbol.viewmodel.SymbolMarketDataViewModel
import java.util.Locale

class SymbolMarketDataActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySymbolMarketDataBinding
    private lateinit var viewModel: SymbolMarketDataViewModel
    private var candleSeries: SeriesApi? = null
    private var volumeSeries: SeriesApi? = null

    // Store initial data from intent
    private var initialPrice: Double? = null
    private var initialChange: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySymbolMarketDataBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Extract and display initial data immediately
        extractAndDisplayInitialData()

        initializeViewModel()
        initializeChart()
        initializeTimeIntervalTabs()
        setupObservers()

        // Initialize with symbol and interval after UI is set up
        initializeWithSymbol()
    }

    private fun extractAndDisplayInitialData() {
        val symbol = intent.getStringExtra("SYMBOL") ?: "BTCUSDT"
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
            binding.marketChartLayout.percentagePriceChange.text = String.format(Locale.US, "%.2f%%", change)
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
                    @Suppress("UNCHECKED_CAST")
                    return SymbolMarketDataViewModel(repository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
        viewModel = ViewModelProvider(this, factory)[SymbolMarketDataViewModel::class.java]
    }

    private fun initializeWithSymbol() {
        val symbol = intent.getStringExtra("SYMBOL") ?: "BTCUSDT"
        Log.d("SymbolMarketDataActivity", "Initializing with symbol: $symbol")
        viewModel.setSymbol(symbol)
        viewModel.setInterval("1m") // Default interval
    }

    private fun setupObservers() {
        // Observe historical data with proper clearing
        lifecycleScope.launch {
            viewModel.historicalCandles.collectLatest { candles ->
                Log.d("SymbolMarketDataActivity", "Received ${candles.size} historical candles")
                if (candles.isNotEmpty()) {
                    candleSeries?.setData(candles.map { it.toCandlestickData() })
                    volumeSeries?.setData(candles.map { it.toVolumeData() })
                } else {
                    candleSeries?.setData(emptyList())
                    volumeSeries?.setData(emptyList())
                }
            }
        }

        // Observe loading state
        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                Log.d("SymbolMarketDataActivity", "Loading state: $isLoading")
                binding.marketChartLayout.progressBar.visibility =
                    if (isLoading) View.VISIBLE else View.GONE
            }
        }

        // Observe real-time price updates (only update if different from initial)
        lifecycleScope.launch {
            viewModel.price.collect { price ->
                price?.let {
                    Log.d("SymbolMarketDataActivity", "Received price update: $it")
                    binding.marketChartLayout.currentPrice.text =
                        String.format(Locale.US, "US$%.2f", it)
                }
            }
        }

        // Observe real-time change updates
        lifecycleScope.launch {
            viewModel.change.collect { change ->
                change?.let {
                    Log.d("SymbolMarketDataActivity", "Received change update: $it")
                    binding.marketChartLayout.percentagePriceChange.text =
                        String.format(Locale.US, "%.2f%%", it)
                    val colorRes = if (it >= 0) R.color.green_chart_color else R.color.crimson_red
                    binding.marketChartLayout.percentagePriceChange.setTextColor(
                        ContextCompat.getColor(this@SymbolMarketDataActivity, colorRes)
                    )
                }
            }
        }

        // Observe real-time candle updates
        lifecycleScope.launch {
            viewModel.candleUpdates.collect { candle ->
                candle?.let {
                    Log.d("SymbolMarketDataActivity", "Received candle update: $it")
                    candleSeries?.update(it.toCandlestickData())
                }
            }
        }

        // Observe errors
        lifecycleScope.launch {
            viewModel.error.collect { error ->
                error?.let {
                    Log.e("SymbolMarketDataActivity", "Error: $it")
                    Toast.makeText(this@SymbolMarketDataActivity, it, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun initializeTimeIntervalTabs() {
        val intervals = listOf(
            "1m", "5m", "15m", "30m", "1h", "2h", "4h", "6h", "1d", "3d", "1w", "1M"
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

            tabHolder.background = ContextCompat.getDrawable(applicationContext, R.drawable.bg_unselected)
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
        when {
            i == 0 -> {
                params.marginStart = 0
                params.marginEnd = 15
            }
            i == binding.marketChartLayout.timeIntervalTabLayout.tabCount - 1 -> {
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

        // Apply chart options
        chartsView.api.applyOptions {
            layout = LayoutOptions().apply {
                background = SolidColor(ContextCompat.getColor(applicationContext, R.color.darkTheme))
                textColor = IntColor(Color.WHITE)
            }
            grid = GridOptions().apply {
                vertLines = GridLineOptions().apply {
                    color = IntColor(0xFF1c1c1c.toInt())
                }
                horzLines = GridLineOptions().apply {
                    color = IntColor(0xFF1c1c1c.toInt())
                }
            }
            timeScale = TimeScaleOptions().apply {
                timeVisible = true
                borderVisible = false
            }
            rightPriceScale = PriceScaleOptions().apply {
                borderVisible = false
                scaleMargins = PriceScaleMargins().apply {
                    top = 0.1f
                    bottom = 0.3f
                }
            }
        }

        // Add candlestick series (uses the rightPriceScale by default)
        val candleOptions = CandlestickSeriesOptions().apply {
            upColor = IntColor(Color.parseColor("#26a69a"))      // Green for up candles
            downColor = IntColor(Color.parseColor("#ef5350"))    // Red for down candles
            borderUpColor = IntColor(Color.parseColor("#26a69a"))
            borderDownColor = IntColor(Color.parseColor("#ef5350"))
            wickUpColor = IntColor(Color.parseColor("#26a69a"))
            wickDownColor = IntColor(Color.parseColor("#ef5350"))
            borderVisible = true
            wickVisible = true
        }

        chartsView.api.addCandlestickSeries(candleOptions) { series ->
            candleSeries = series
            Log.d("SymbolMarketDataActivity", "Candlestick series initialized")
        }

        // Add histogram series for volume with a new price scale
        val volumeOptions = HistogramSeriesOptions().apply {
            color = IntColor(Color.parseColor("#26a69a")) // Green
            base = 0.0f                            // Start from zero
            baseLineWidth = LineWidth.TWO          // Line width, assuming enum exists
            priceScaleId = PriceScaleId("left")    // Use the left price scale
            scaleMargins = PriceScaleMargins().apply {
                top = 0.85f
                bottom = 0.02f
            }
        }

        chartsView.api.addHistogramSeries(volumeOptions) { series ->
            volumeSeries = series
        }
    }

    private fun Candle.toCandlestickData(): CandlestickData {
        return CandlestickData(
            Time.Utc(time),
            open.toFloat(),
            high.toFloat(),
            low.toFloat(),
            close.toFloat()
        )
    }

    private fun Candle.toVolumeData(): HistogramData {
        return HistogramData(
            time = Time.Utc(time),
            value = volume.toFloat()
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