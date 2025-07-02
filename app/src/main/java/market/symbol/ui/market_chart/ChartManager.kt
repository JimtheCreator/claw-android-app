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
import com.tradingview.lightweightcharts.api.options.models.*
import com.tradingview.lightweightcharts.api.series.enums.LineWidth
import com.tradingview.lightweightcharts.api.series.models.CandlestickData
import com.tradingview.lightweightcharts.api.series.models.HistogramData
import com.tradingview.lightweightcharts.api.series.models.PriceScaleId
import com.tradingview.lightweightcharts.api.series.models.Time
import market.symbol.repo.Candle
import java.util.*

class ChartManager(
    private val context: Context,
    private val chartBinding: MarketChartBinding,
    private val onVisibleTimeRangeChanged: (Time.Utc, Time.Utc) -> Unit
) {

    var candleSeries: SeriesApi? = null
    var volumeSeries: SeriesApi? = null

    private val upColor = IntColor(Color.parseColor("#26a69a"))
    private val downColor = IntColor(Color.parseColor("#ef5350"))

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
            upColor = this@ChartManager.upColor
            downColor = this@ChartManager.downColor
            borderUpColor = this@ChartManager.upColor
            borderDownColor = this@ChartManager.downColor
            wickUpColor = this@ChartManager.upColor
            wickDownColor = this@ChartManager.downColor
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
            timeRange?.let {
                onVisibleTimeRangeChanged(it.from as Time.Utc, it.to as Time.Utc)
            }
        }
    }

    fun updateSparkline(sparklineArray: DoubleArray?) {
        if (sparklineArray == null || sparklineArray.isEmpty()) return
        val chart = chartBinding.sparklineChart
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

    fun setCandleData(candles: List<Candle>) {
        candleSeries?.setData(candles.map { it.toCandlestickData() })
    }

    fun setVolumeData(candles: List<Candle>) {
        volumeSeries?.setData(candles.map { it.toVolumeData() })
    }

    fun scrollToRealTime() {
        chartBinding.candlesStickChart.api.timeScale.scrollToRealTime()
    }

    fun scrollToPosition(position: Float, animated: Boolean) {
        chartBinding.candlesStickChart.api.timeScale.scrollToPosition(position, animated)
    }

    private fun Candle.toCandlestickData(): CandlestickData = CandlestickData(
        Time.Utc(this.time),
        this.open.toFloat(),
        this.high.toFloat(),
        this.low.toFloat(),
        this.close.toFloat()
    )

    private fun Candle.toVolumeData(): HistogramData = HistogramData(
        Time.Utc(this.time),
        this.volume.toFloat(),
        if (close >= open) upColor else downColor
    )
}