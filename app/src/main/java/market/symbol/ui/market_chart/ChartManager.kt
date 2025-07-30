package market.symbol.ui.market_chart

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.claw.ai.R
import com.claw.ai.databinding.MarketChartBinding
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.tradingview.lightweightcharts.api.chart.models.color.IntColor
import com.tradingview.lightweightcharts.api.chart.models.color.surface.SolidColor
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi
import com.tradingview.lightweightcharts.api.interfaces.TimeScaleApi
import com.tradingview.lightweightcharts.api.options.models.CandlestickSeriesOptions
import com.tradingview.lightweightcharts.api.options.models.GridLineOptions
import com.tradingview.lightweightcharts.api.options.models.GridOptions
import com.tradingview.lightweightcharts.api.options.models.HandleScaleOptions
import com.tradingview.lightweightcharts.api.options.models.HistogramSeriesOptions
import com.tradingview.lightweightcharts.api.options.models.LayoutOptions
import com.tradingview.lightweightcharts.api.options.models.LocalizationOptions
import com.tradingview.lightweightcharts.api.options.models.PriceLineOptions
import com.tradingview.lightweightcharts.api.options.models.PriceScaleMargins
import com.tradingview.lightweightcharts.api.options.models.PriceScaleOptions
import com.tradingview.lightweightcharts.api.options.models.TimeScaleOptions
import com.tradingview.lightweightcharts.api.series.common.PriceLine
import com.tradingview.lightweightcharts.api.series.enums.LineStyle
import com.tradingview.lightweightcharts.api.series.enums.LineWidth
import com.tradingview.lightweightcharts.api.series.models.CandlestickData
import com.tradingview.lightweightcharts.api.series.models.HistogramData
import com.tradingview.lightweightcharts.api.series.models.PriceScaleId
import com.tradingview.lightweightcharts.api.series.models.SeriesMarker
import com.tradingview.lightweightcharts.api.series.models.Time
import market.symbol.model.AnalysisResult
import market.symbol.repo.Candle
import java.util.Locale

class ChartManager(
    private val context: Context,
    private val chartBinding: MarketChartBinding,
    private val onVisibleTimeRangeChanged: (Time.Utc, Time.Utc) -> Unit
) {
    private var candleSeries: SeriesApi? = null
    private var volumeSeries: SeriesApi? = null
    private var timeScaleApi: TimeScaleApi? = null

    // State for tracking analysis drawings
    private val analysisMarkers = mutableListOf<SeriesMarker>()
    private val analysisPriceLines = mutableListOf<PriceLine>()
    private val analysisSeries = mutableListOf<SeriesApi>()

    // Colors
    private val upColor = IntColor(Color.parseColor("#26a69a"))
    private val downColor = IntColor(Color.parseColor("#ef5350"))
    private val supportColor = IntColor(Color.parseColor("#26a69a")) // Green
    private val resistanceColor = IntColor(Color.parseColor("#ef5350")) // Red
    private val demandZoneColor = IntColor(Color.parseColor("#26a69a"))
    private val supplyZoneColor = IntColor(Color.parseColor("#ef5350"))

    init {
        initializeMainChart()
    }

    private fun initializeMainChart() {
        val chartsView = chartBinding.candlesStickChart
        chartsView.api.applyOptions {
            layout = LayoutOptions().apply {
                background = SolidColor(ContextCompat.getColor(context, R.color.darkTheme))
                textColor = IntColor(Color.WHITE)
            }
            grid = GridOptions(
                vertLines = GridLineOptions(color = IntColor(0xFF1c1c1c.toInt())),
                horzLines = GridLineOptions(color = IntColor(0xFF1c1c1c.toInt()))
            )
            timeScale = TimeScaleOptions().apply {
                timeVisible = true
                borderVisible = false
                fixLeftEdge = false
                rightBarStaysOnScroll = true
                localization = LocalizationOptions(locale = Locale.getDefault().toLanguageTag())
            }
            rightPriceScale = PriceScaleOptions().apply { borderVisible = false }
            leftPriceScale = PriceScaleOptions().apply {
                scaleMargins = PriceScaleMargins(0.85f, 0.02f)
                visible = false
            }
        }

        timeScaleApi = chartsView.api.timeScale

        val candleOptions = CandlestickSeriesOptions(
            upColor = this.upColor,
            downColor = this.downColor,
            borderUpColor = this.upColor,
            borderDownColor = this.downColor,
            wickUpColor = this.upColor,
            wickDownColor = this.downColor,
            borderVisible = true,
            wickVisible = true
        )
        chartsView.api.addCandlestickSeries(candleOptions) { series -> candleSeries = series }

        val volumeOptions = HistogramSeriesOptions(
            base = 0.0f,
            baseLineWidth = LineWidth.TWO,
            priceScaleId = PriceScaleId.LEFT
        )
        chartsView.api.addHistogramSeries(volumeOptions) { series -> volumeSeries = series }

        timeScaleApi?.subscribeVisibleTimeRangeChange { timeRange ->
            timeRange?.let {
                onVisibleTimeRangeChanged(it.from as Time.Utc, it.to as Time.Utc)
            }
        }
    }

    /**
     * UPDATED: This function now accepts a pre-processed list of CandlestickData.
     */
    fun setCandleData(candlestickData: List<CandlestickData>) {
        candleSeries?.setData(candlestickData)
    }

    /**
     * UPDATED: This function now accepts a pre-processed list of HistogramData.
     */
    fun setVolumeData(volumeData: List<HistogramData>) {
        volumeSeries?.setData(volumeData)
    }

    fun scrollToRealTime() {
        timeScaleApi?.scrollToRealTime()
    }

    private fun disableInteraction() {
        chartBinding.candlesStickChart.api.applyOptions {
            handleScale = HandleScaleOptions(pinch = false, mouseWheel = false)
        }
    }

    private fun enableInteraction() {
        chartBinding.candlesStickChart.api.applyOptions {
            handleScale = HandleScaleOptions(pinch = true, mouseWheel = true)
        }
    }

    fun renderAnalysisData(analysis: AnalysisResult, candles: List<Candle>) {
        clearAnalysis()

        // 1. Render Support Levels Only
        analysis.supportLevels.forEach { level ->
            renderPriceLine(
                price = level.price,
                isSupport = true,
                title = "Support (${String.format("%.2f", level.confidenceScore)})"
            )
        }

        // 2. Render Resistance Levels Only
        analysis.resistanceLevels.forEach { level ->
            renderPriceLine(
                price = level.price,
                isSupport = false,
                title = "Resistance (${String.format("%.2f", level.confidenceScore)})"
            )
        }

        scrollToRealTime()
        disableInteraction()
    }

    fun clearAnalysis() {
        enableInteraction()
        analysisPriceLines.forEach { candleSeries?.removePriceLine(it) }
        analysisPriceLines.clear()
        analysisSeries.forEach { chartBinding.candlesStickChart.api.removeSeries(it) }
        analysisSeries.clear()
        analysisMarkers.clear()
        candleSeries?.setMarkers(emptyList())
    }

    private fun renderPriceLine(
        price: Double,
        isSupport: Boolean,
        title: String,
        style: LineStyle = LineStyle.DASHED
    ) {
        val options = PriceLineOptions(
            price = price.toFloat(),
            color = if (isSupport) supportColor else resistanceColor,
            lineWidth = LineWidth.TWO,
            lineStyle = style,
            axisLabelVisible = true,
            title = title
        )
        candleSeries?.createPriceLine(options)?.let { analysisPriceLines.add(it) }
    }

    private fun renderZone(zone: market.symbol.model.Zone, isDemand: Boolean) {
        val zoneColor = if (isDemand) demandZoneColor else supplyZoneColor
        val zoneType = if (isDemand) "Demand" else "Supply"

        val topOptions = PriceLineOptions(
            price = zone.top.toFloat(),
            color = zoneColor,
            lineWidth = LineWidth.ONE,
            lineStyle = LineStyle.SOLID,
            axisLabelVisible = false,
            title = "$zoneType Top"
        )
        val bottomOptions = topOptions.copy(
            price = zone.bottom.toFloat(),
            title = "$zoneType Bottom"
        )

        candleSeries?.createPriceLine(topOptions)?.let { analysisPriceLines.add(it) }
        candleSeries?.createPriceLine(bottomOptions)?.let { analysisPriceLines.add(it) }
    }

    fun updateSparkline(sparklineArray: DoubleArray?) {
        if (sparklineArray == null || sparklineArray.isEmpty()) return
        val chart = chartBinding.sparklineChart
        val entries =
            sparklineArray.mapIndexed { index, value -> Entry(index.toFloat(), value.toFloat()) }
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
}