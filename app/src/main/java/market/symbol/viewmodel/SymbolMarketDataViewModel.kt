package market.symbol.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import market.symbol.model.AnalysisResult
import market.symbol.repo.Candle
import market.symbol.repo.MarketDataRepository
import market.symbol.repo.MarketUpdate
import timber.log.Timber
import java.util.Calendar
import java.util.Date
import com.tradingview.lightweightcharts.api.series.models.CandlestickData as TradingViewCandlestickData
import com.tradingview.lightweightcharts.api.series.models.HistogramData as TradingViewHistogramData

import android.graphics.Color
import androidx.lifecycle.viewModelScope
// Aliases to avoid name conflicts with your own models if they exist
import com.tradingview.lightweightcharts.api.series.models.Time as TradingViewTime
import com.tradingview.lightweightcharts.api.chart.models.color.IntColor

// ...

// Add this enum inside or outside the class
enum class AnalysisMode {
    SUPPORT_RESISTANCE,
    TRENDLINES
}

class SymbolMarketDataViewModel(
    private val repository: MarketDataRepository
) : ViewModel() {

    // --- StateFlows to expose UI state to the Activity ---

    // Price and Change remain the same
    private val _price = MutableStateFlow<Double?>(null)
    val price: StateFlow<Double?> = _price

    private val _change = MutableStateFlow<Double?>(null)
    val change: StateFlow<Double?> = _change

    // REVISED: Single source of truth for all candles (historical + real-time)
    private val _candles = MutableStateFlow<List<Candle>>(emptyList())
    val candles: StateFlow<List<Candle>> = _candles

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var currentSymbol: String? = null

    // --- NEW: Exposing interval as a public StateFlow ---
    private val _interval = MutableStateFlow("1m")
    val interval: StateFlow<String> = _interval.asStateFlow()

    private var marketDataJob: Job? = null
    private var historicalDataJob: Job? = null

    // Add variables to track loading state and prevent duplicate requests
    private var isLoadingMore = false
    private var earliestTimestamp: Long? = null

    // NEW: CONVERTED to StateFlow to be observed by the Activity
    private val _hasInitialDataLoaded = MutableStateFlow(false)
    val hasInitialDataLoaded: StateFlow<Boolean> = _hasInitialDataLoaded

    // Add StateFlow to track stream status
    private val _isStreamActive = MutableStateFlow(false)
    val isStreamActive: StateFlow<Boolean> = _isStreamActive

    // New StateFlows for analysis
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing

    private val _analysisStatus = MutableStateFlow("")
    val analysisStatus: StateFlow<String> = _analysisStatus

    private val _isAnalyzingTrendlines = MutableStateFlow(false)
    val isAnalyzingTrendlines: StateFlow<Boolean> = _isAnalyzingTrendlines.asStateFlow()

    // --- State for Analysis Panel Mode ---
    private val _analysisMode = MutableStateFlow(AnalysisMode.SUPPORT_RESISTANCE)
    val analysisMode: StateFlow<AnalysisMode> = _analysisMode.asStateFlow()

    // --- States for S/R Analysis ---
    private val _isSRAnalysisInProgress = MutableStateFlow(false)
    val isSRAnalysisInProgress: StateFlow<Boolean> = _isSRAnalysisInProgress.asStateFlow()
    private val _srAnalysisStatus = MutableStateFlow("")
    val srAnalysisStatus: StateFlow<String> = _srAnalysisStatus.asStateFlow()
    private val _analysisResult = MutableStateFlow<AnalysisResult?>(null)
    val analysisResult: StateFlow<AnalysisResult?> = _analysisResult

    // --- States for Trendline Analysis ---
    private val _isTrendlineAnalysisInProgress = MutableStateFlow(false)
    val isTrendlineAnalysisInProgress: StateFlow<Boolean> = _isTrendlineAnalysisInProgress.asStateFlow()
    private val _trendlineAnalysisStatus = MutableStateFlow("")
    val trendlineAnalysisStatus: StateFlow<String> = _trendlineAnalysisStatus.asStateFlow()
    private val _trendlineChartUrl = MutableStateFlow<String?>(null)
    val trendlineChartUrl: StateFlow<String?> = _trendlineChartUrl.asStateFlow()

    // This will hold the timeframe selected for analysis (e.g., "1h", "4h")
    val selectedAnalysisTimeframe = MutableStateFlow("1h")

    private var sseJob: Job? = null

    // NEW: StateFlows for pre-processed chart data
    private val _candlestickData = MutableStateFlow<List<TradingViewCandlestickData>>(emptyList())
    val candlestickData: StateFlow<List<TradingViewCandlestickData>> = _candlestickData

    private val _volumeData = MutableStateFlow<List<TradingViewHistogramData>>(emptyList())
    val volumeData: StateFlow<List<TradingViewHistogramData>> = _volumeData

    // --- Colors for volume bars ---
    private val upColor = IntColor(Color.parseColor("#26a69a"))
    private val downColor = IntColor(Color.parseColor("#ef5350"))

    // --- Helper functions to map raw Candle data to TradingView's models ---
    // These functions must be inside the ViewModel class to be found.
    private fun Candle.toCandlestickData(): TradingViewCandlestickData =
        TradingViewCandlestickData(TradingViewTime.Utc(this.time), this.open.toFloat(), this.high.toFloat(), this.low.toFloat(), this.close.toFloat())

    private fun Candle.toVolumeData(): TradingViewHistogramData =
        TradingViewHistogramData(TradingViewTime.Utc(this.time), this.volume.toFloat(), if (close >= open) upColor else downColor)

    fun setSymbol(symbol: String) {
        if (currentSymbol == symbol) return
        Timber.d("Setting symbol to: $symbol")
        currentSymbol = symbol
        clearStates()
        loadData()
    }

    fun setInterval(interval: String) {
        if (_interval.value == interval && _candles.value.isNotEmpty()) return
        Timber.d("Setting interval to: $interval")
        _interval.value = interval // Use the new StateFlow
        clearStates()
        loadData()
    }

    private fun clearStates() {
        _error.value = null
        _candles.value = emptyList()
        // Reset the initial data loaded flag
        _hasInitialDataLoaded.value = false
        _isStreamActive.value = false
    }

    private fun loadData() {
        val symbol = currentSymbol ?: return
        Timber.d("Loading data for symbol: $symbol, interval: ${_interval.value}") // Use the new StateFlow
        cancelAllJobs() // Cancel previous jobs
        historicalDataJob = viewModelScope.launch {
            loadHistoricalData(symbol)
            if (_hasInitialDataLoaded.value) {
                startMarketDataStream(symbol)
            }
        }
    }

    private fun startMarketDataStream(symbol: String) {
        marketDataJob = viewModelScope.launch {
            try {
                Timber.d("Starting market data stream for $symbol")
                _isStreamActive.value = true
                repository.subscribeToMarketUpdates(
                    symbol,
                    _interval.value
                ) // Use the new StateFlow
                    .catch { e ->
                        Timber.e(e, "Market data stream error for $symbol")
                        _error.value = "Data stream error: ${e.message}"
                        _isStreamActive.value = false
                    }
                    .collect { marketUpdate ->
                        when (marketUpdate) {
                            is MarketUpdate.Tick -> {
                                _price.value = marketUpdate.data.price
                                _change.value = marketUpdate.data.change
                            }


                            is MarketUpdate.CandleUpdate -> {
                                val newCandle = marketUpdate.data
                                val currentCandles = _candles.value.toMutableList()

                                val updatedList = if (currentCandles.isNotEmpty() && newCandle.time == currentCandles.last().time) {
                                    // Update the last candle
                                    currentCandles[currentCandles.size - 1] = newCandle
                                    currentCandles
                                } else if (currentCandles.isEmpty() || newCandle.time > currentCandles.last().time) {
                                    // Append a new candle
                                    currentCandles.apply { add(newCandle) }
                                } else {
                                    currentCandles // No change
                                }

                                // **THE FIX:** Now update all three StateFlows
                                _candles.value = updatedList

                                // Re-map the updated list to the TradingView models
                                _candlestickData.value = updatedList.map { it.toCandlestickData() }
                                _volumeData.value = updatedList.map { it.toVolumeData() }
                            }
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "Failed to start market data stream for $symbol")
                _error.value = "Failed to connect to data stream: ${e.message}"
                _isStreamActive.value = false
            }
        }
    }

    /**
     * Cancel the active market data stream
     * This function can be called from outside (e.g., from Activity) to stop the stream
     */
    fun cancelStream() {
        Timber.d("Cancelling market data stream")

        // Cancel the coroutine job
        marketDataJob?.cancel()
        marketDataJob = null

        // Cancel the stream at repository level
        repository.cancelStream()

        // Update stream status
        _isStreamActive.value = false

        Timber.d("Market data stream cancelled")
    }

    /**
     * Check if the stream is currently active
     */
    fun isMarketStreamActive(): Boolean {
        return repository.isStreamActive()
    }

    /**
     * Get information about the current active stream
     */
    fun getActiveStreamInfo(): Pair<String?, String?> {
        return repository.getActiveStreamInfo()
    }

    /**
     * Restart the market data stream for current symbol
     */
    fun restartStream() {
        val symbol = currentSymbol ?: return
        Timber.d("Restarting stream for symbol: $symbol")

        cancelStream()

        // Small delay to ensure cleanup is complete
        viewModelScope.launch {
            kotlinx.coroutines.delay(100)
            startMarketDataStream(symbol)
        }
    }

    /**
     * Load historical data until we reach the target time
     * @param targetTimeSeconds The target time in seconds (Unix timestamp)
     * @param onComplete Callback with success status
     */
    fun loadHistoricalDataUntil(targetTimeSeconds: Long, onComplete: (Boolean) -> Unit) {
        val symbol = currentSymbol ?: run {
            onComplete(false)
            return
        }

        if (isLoadingMore) {
            Timber.d("Already loading more data, skipping request")
            onComplete(false)
            return
        }

        historicalDataJob = viewModelScope.launch {
            _isLoading.value = true
            isLoadingMore = true

            try {
                var currentCandles = _candles.value.toMutableList()
                var earliestTime =
                    if (currentCandles.isNotEmpty()) currentCandles.first().time else Long.MAX_VALUE
                val tenYearsAgo = (System.currentTimeMillis() / 1000) - 10L * 365 * 24 * 60 * 60

                while (earliestTime > targetTimeSeconds && earliestTime > tenYearsAgo) {
                    val endTime = Date(earliestTime * 1000L - 1) // Just before the earliest candle
                    val startTime = Date(maxOf(targetTimeSeconds * 1000L, tenYearsAgo * 1000L))
                    val newCandles = repository.getHistoricalCandles(
                        symbol,
                        _interval.value,
                        startTime,
                        endTime
                    ) // Use the new StateFlow
                    if (newCandles.isEmpty()) break // No more data available

                    currentCandles =
                        (newCandles + currentCandles).sortedBy { it.time }.toMutableList()
                    earliestTime = currentCandles.first().time
                }

                // Ensure data extends to current time by appending real-time candles if needed
                val latestTime = currentCandles.lastOrNull()?.time ?: targetTimeSeconds
                val nowSeconds = System.currentTimeMillis() / 1000
                if (latestTime < nowSeconds) {
                    val recentCandles = repository.getHistoricalCandles(
                        symbol,
                        _interval.value, // Use the new StateFlow
                        Date(latestTime * 1000L + 1),
                        Date(nowSeconds * 1000L)
                    )
                    currentCandles.addAll(recentCandles)
                    currentCandles.sortBy { it.time }
                }

                _candles.value = currentCandles.distinctBy { it.time } // Remove duplicates
                onComplete(true)
            } catch (e: Exception) {
                _error.value = "Failed to load historical data: ${e.message}"
                Timber.e(e, "Error loading historical data until target time")
                onComplete(false)
            } finally {
                _isLoading.value = false
                isLoadingMore = false
            }
        }
    }

    private suspend fun loadHistoricalData(symbol: String) {
        _isLoading.value = true
        try {
            val calendar = Calendar.getInstance()
            val endTime = Date()
            calendar.time = endTime

            // ✅ FIX: Fetch a smaller, faster initial chunk of data.
            val limit = 300
            val theoreticalStartCalendar = Calendar.getInstance().apply { time = endTime }
            when (_interval.value) {
                "1m" -> theoreticalStartCalendar.add(Calendar.MINUTE, -limit)
                "5m" -> theoreticalStartCalendar.add(Calendar.MINUTE, -limit * 5)
                "15m" -> theoreticalStartCalendar.add(Calendar.MINUTE, -limit * 15)
                "30m" -> theoreticalStartCalendar.add(Calendar.MINUTE, -limit * 30)
                "1h" -> theoreticalStartCalendar.add(Calendar.HOUR, -limit)
                "2h" -> theoreticalStartCalendar.add(Calendar.HOUR, -limit * 2)
                "4h" -> theoreticalStartCalendar.add(Calendar.HOUR, -limit * 4)
                "6h" -> theoreticalStartCalendar.add(Calendar.HOUR, -limit * 6)
                "1d" -> theoreticalStartCalendar.add(Calendar.DAY_OF_YEAR, -limit)
                "3d" -> theoreticalStartCalendar.add(Calendar.DAY_OF_YEAR, -limit * 3)
                "1w" -> theoreticalStartCalendar.add(Calendar.WEEK_OF_YEAR, -limit)
                "1M" -> theoreticalStartCalendar.add(Calendar.MONTH, -limit)
                else -> theoreticalStartCalendar.add(Calendar.MINUTE, -limit)
            }

            val maxLookbackCalendar = Calendar.getInstance().apply {
                time = endTime
                add(Calendar.YEAR, -10)
            }

            val startTime = if (theoreticalStartCalendar.time.before(maxLookbackCalendar.time)) {
                maxLookbackCalendar.time
            } else {
                theoreticalStartCalendar.time
            }

            Timber.d("Fetching historical data from $startTime to $endTime for interval ${_interval.value}")

            val candles =
                repository.getHistoricalCandles(symbol, _interval.value, startTime, endTime)

            if (candles.isNotEmpty()) {
                val sortedCandles = candles.sortedBy { it.time }
                _candles.value = sortedCandles // Keep raw data for analysis logic

                // Process data into chart models here, in the background
                _candlestickData.value = sortedCandles.map { it.toCandlestickData() }
                _volumeData.value = sortedCandles.map { it.toVolumeData() }

                _hasInitialDataLoaded.value = true
            } else {
                // Clear all data states
                _candles.value = emptyList()
                _candlestickData.value = emptyList()
                _volumeData.value = emptyList()
                _error.value = "No historical data available for this symbol and interval"
            }
        } catch (e: Exception) {
            _error.value = "Failed to load historical data: ${e.message}"
            Timber.e(e, "Failed to load historical data")
        } finally {
            // Hide the loader only AFTER all processing is done
            _isLoading.value = false
        }
    }

    // Function to load more historical data
    fun loadMoreHistoricalData() {
        if (!_hasInitialDataLoaded.value || isLoadingMore || _candles.value.isEmpty()) {
            Timber.d("Skipping loadMoreHistoricalData: hasInitialDataLoaded=${_hasInitialDataLoaded.value}, isLoadingMore=$isLoadingMore, candles.size=${_candles.value.size}")
            return
        }

        val symbol = currentSymbol ?: return
        val earliestCandle = _candles.value.firstOrNull() ?: return
        val earliestTimestamp = earliestCandle.time // in seconds

        historicalDataJob = viewModelScope.launch {
            _isLoading.value = true
            isLoadingMore = true
            try {
                val endTimeMillis = earliestTimestamp * 1000L // Convert to milliseconds

                // ✅ FIX: Fetch a consistent and manageable number of candles.
                val candlesToFetch = 300
                val durationMillis = when (_interval.value) {
                    "1m" -> candlesToFetch * 60 * 1000L
                    "5m" -> candlesToFetch * 5 * 60 * 1000L
                    "15m" -> candlesToFetch * 15 * 60 * 1000L
                    "30m" -> candlesToFetch * 30 * 60 * 1000L
                    "1h" -> candlesToFetch * 60 * 60 * 1000L
                    "2h" -> candlesToFetch * 2 * 60 * 60 * 1000L
                    "1d" -> candlesToFetch * 24 * 60 * 60 * 1000L
                    "1w" -> candlesToFetch * 7 * 24 * 60 * 60 * 1000L
                    "1M" -> candlesToFetch.toLong() * 30 * 24 * 60 * 60 * 1000L // Approx.
                    else -> candlesToFetch * 60 * 1000L
                }
                var startTimeMillis = endTimeMillis - durationMillis

                // Cap start time to 10 years ago
                val tenYearsAgoMillis = System.currentTimeMillis() - 10L * 365 * 24 * 60 * 60 * 1000
                if (startTimeMillis < tenYearsAgoMillis) {
                    startTimeMillis = tenYearsAgoMillis
                }

                val startTime = Date(startTimeMillis)
                val endTime = Date(endTimeMillis)

                Timber.d("Fetching more historical data from $startTime to $endTime for $symbol")
                val newCandles =
                    repository.getHistoricalCandles(symbol, _interval.value, startTime, endTime)

                if (newCandles.isNotEmpty()) {
                    // Combine new and existing candles, then sort by time
                    val allCandles = (newCandles + _candles.value).sortedBy { it.time }

                    // Remove duplicates by keeping only candles with unique timestamps
                    val uniqueCandles = allCandles.fold(mutableListOf<Candle>()) { acc, candle ->
                        if (acc.isEmpty() || candle.time > acc.last().time) {
                            acc.add(candle)
                        }
                        acc
                    }

                    _candles.value = uniqueCandles
                    Timber.d("Prepended ${newCandles.size} candles, total now ${_candles.value.size}")
                }
            } catch (e: Exception) {
                _error.value = "Failed to load more historical data: ${e.message}"
                Timber.e(e, "Error loading more historical data")
            } finally {
                _isLoading.value = false
                isLoadingMore = false
            }
        }
    }

    fun setAnalysisMode(mode: AnalysisMode) {
        _analysisMode.value = mode
        _analysisResult.value = null
        _trendlineChartUrl.value = null
    }

    fun startAnalysis(timeframe: String) {
        // Store the original user-selected timeframe for the UI
        selectedAnalysisTimeframe.value = timeframe

        // Convert years to months for the backend API call
        val processedTimeframe = if (timeframe.endsWith("y", ignoreCase = true)) {
            val years = timeframe.dropLast(1).toIntOrNull()
            if (years != null) {
                "${years * 12}M"
            } else {
                timeframe // Fallback if parsing fails
            }
        } else {
            timeframe
        }
        Timber.d("Starting analysis for original timeframe: $timeframe, processed timeframe: $processedTimeframe")

        when (_analysisMode.value) {
            AnalysisMode.SUPPORT_RESISTANCE -> startSRAnalysis(processedTimeframe)
            AnalysisMode.TRENDLINES -> startTrendlineAnalysis(processedTimeframe)
        }
    }

    private fun startSRAnalysis(timeframe: String) {
        val symbol = currentSymbol ?: return
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        val userid = firebaseUser?.uid ?: return
        _isAnalyzing.value = true
        _analysisStatus.value = "Analyzing market..." // Simplified status
        viewModelScope.launch {
            try {
                // This now represents the entire analysis process
                val result = repository.analyzeMarketData(
                    userid,
                    symbol,
                    _interval.value,
                    timeframe
                ) // Use the new StateFlow

                if (result != null) {
                    _analysisStatus.value = "Generating report..."
                    _analysisResult.value = result // Trigger the rendering
                    _analysisStatus.value = "Analysis complete"
                } else {
                    _analysisStatus.value = "Analysis failed"
                    _analysisResult.value = null // Clear previous results
                }
            } catch (e: Exception) {
                _analysisStatus.value = "Analysis error: ${e.message}"
                _analysisResult.value = null // Clear previous results
            } finally {
                _isAnalyzing.value = false // This will now happen immediately after rendering
            }
        }
    }

    // In SymbolMarketDataViewModel.kt

    private fun startTrendlineAnalysis(timeframe: String) {
        val symbol = currentSymbol ?: return
        val userid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        sseJob?.cancel()
        // Launch network operations on a background thread to avoid resource contention.
        viewModelScope.launch(Dispatchers.IO) {
            // Switch to the main thread to update the UI before the network call.
            withContext(Dispatchers.Main) {
                _isTrendlineAnalysisInProgress.value = true
                _trendlineAnalysisStatus.value = "Initiating..."
                _trendlineChartUrl.value = null
            }

            val taskResponse =
                repository.startTrendlineAnalysisTask(userid, symbol, _interval.value, timeframe)

            if (taskResponse != null) {
                // UI updates from the collecting flow must also be on the main thread.
                withContext(Dispatchers.Main) {
                    _trendlineAnalysisStatus.value = "Analysis in progress..."
                }

                sseJob = launch { // This nested launch inherits the IO context.
                    repository.subscribeToAnalysisUpdates(taskResponse.analysisId)
                        .catch { e ->
                            withContext(Dispatchers.Main) {
                                _trendlineAnalysisStatus.value = "Stream error: ${e.message}"
                                _isTrendlineAnalysisInProgress.value = false
                            }
                        }
                        .collect { update ->
                            // Switch to the main thread for all UI state updates.
                            withContext(Dispatchers.Main) {
                                _trendlineAnalysisStatus.value = update.progress ?: update.status
                                if (update.status == "completed") {
                                    _trendlineChartUrl.value = update.chartUrl
                                    _isTrendlineAnalysisInProgress.value = false
                                    sseJob?.cancel()
                                } else if (update.status == "failed") {
                                    _trendlineAnalysisStatus.value =
                                        "Analysis failed: ${update.errorMessage}"
                                    _isTrendlineAnalysisInProgress.value = false
                                    sseJob?.cancel()
                                }
                            }
                        }
                }
            } else {
                withContext(Dispatchers.Main) {
                    _trendlineAnalysisStatus.value = "Failed to start analysis."
                    _isTrendlineAnalysisInProgress.value = false
                }
            }
        }
    }

    private fun cancelAllJobs() {
        Timber.d("Cancelling all active jobs")
        historicalDataJob?.cancel()
        marketDataJob?.cancel()
        _isStreamActive.value = false
    }

    override fun onCleared() {
        Timber.d("ViewModel cleared, cleaning up resources")
        cancelAllJobs()
        repository.unsubscribe()
        super.onCleared()
    }
}