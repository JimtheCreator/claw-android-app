package market.symbol.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import market.symbol.model.AnalysisResult
import market.symbol.repo.Candle
import market.symbol.repo.MarketDataRepository
import market.symbol.repo.MarketUpdate
import timber.log.Timber
import java.util.Calendar
import java.util.Date

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
    private var currentInterval: String = "1m"
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

    private var sseJob: Job? = null

    fun setSymbol(symbol: String) {
        if (currentSymbol == symbol) return
        Timber.d("Setting symbol to: $symbol")
        currentSymbol = symbol
        clearStates()
        loadData()
    }

    fun setInterval(interval: String) {
        if (currentInterval == interval && _candles.value.isNotEmpty()) return
        Timber.d("Setting interval to: $interval")
        currentInterval = interval
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
        Timber.d("Loading data for symbol: $symbol, interval: $currentInterval")
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
                repository.subscribeToMarketUpdates(symbol, currentInterval)
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

                                if (currentCandles.isNotEmpty() && newCandle.time == currentCandles.last().time) {
                                    currentCandles[currentCandles.size - 1] = newCandle
                                    Timber.d("Updated last candle: ${newCandle.time}")
                                } else if (currentCandles.isEmpty() || newCandle.time > currentCandles.last().time) {
                                    currentCandles.add(newCandle)
                                    Timber.d("Appended new candle: ${newCandle.time}")
                                }
                                _candles.value = currentCandles
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
                var earliestTime = if (currentCandles.isNotEmpty()) currentCandles.first().time else Long.MAX_VALUE
                val tenYearsAgo = (System.currentTimeMillis() / 1000) - 10L * 365 * 24 * 60 * 60

                while (earliestTime > targetTimeSeconds && earliestTime > tenYearsAgo) {
                    val endTime = Date(earliestTime * 1000L - 1) // Just before the earliest candle
                    val startTime = Date(maxOf(targetTimeSeconds * 1000L, tenYearsAgo * 1000L))
                    val newCandles = repository.getHistoricalCandles(symbol, currentInterval, startTime, endTime)
                    if (newCandles.isEmpty()) break // No more data available

                    currentCandles = (newCandles + currentCandles).sortedBy { it.time }.toMutableList()
                    earliestTime = currentCandles.first().time
                }

                // Ensure data extends to current time by appending real-time candles if needed
                val latestTime = currentCandles.lastOrNull()?.time ?: targetTimeSeconds
                val nowSeconds = System.currentTimeMillis() / 1000
                if (latestTime < nowSeconds) {
                    val recentCandles = repository.getHistoricalCandles(
                        symbol,
                        currentInterval,
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

            val limit = 999
            val theoreticalStartCalendar = Calendar.getInstance().apply { time = endTime }
            when (currentInterval) {
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

            Timber.d("Fetching historical data from $startTime to $endTime for interval $currentInterval")

            val candles = repository.getHistoricalCandles(symbol, currentInterval, startTime, endTime)
            Timber.d("Received ${candles.size} historical candles")

            if (candles.isNotEmpty()) {
                _candles.value = candles.sortedBy { it.time }
                _hasInitialDataLoaded.value = true
                _isLoading.value = false // Move here to stop progress bar after successful load
            } else {
                _error.value = "No historical data available for this symbol and interval"
                _isLoading.value = false // Stop progress bar on empty data
            }
        } catch (e: Exception) {
            _error.value = "Failed to load historical data: ${e.message}"
            Timber.e(e, "Failed to load historical data")
            _isLoading.value = false // Stop progress bar on error
        }
        // Remove finally block since we handle all cases explicitly
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
                val durationMillis = when (currentInterval) {
                    "1m" -> 1000L * 60 * 1000 // 1000 minutes
                    "5m" -> 1000L * 5 * 60 * 1000
                    "15m" -> 1000L * 15 * 60 * 1000
                    "30m" -> 1000L * 30 * 60 * 1000
                    "1h" -> 1000L * 60 * 60 * 1000
                    "2h" -> 1000L * 2 * 60 * 60 * 1000
                    "4h" -> 1000L * 4 * 60 * 60 * 1000
                    "1d" -> 1000L * 24 * 60 * 60 * 1000
                    "1w" -> 1000L * 7 * 24 * 60 * 60 * 1000
                    "1M" -> 1000L * 30 * 24 * 60 * 60 * 1000 // Approx 1 month
                    else -> 1000L * 60 * 1000
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
                val newCandles = repository.getHistoricalCandles(symbol, currentInterval, startTime, endTime)

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
        when (_analysisMode.value) {
            AnalysisMode.SUPPORT_RESISTANCE -> startSRAnalysis(timeframe)
            AnalysisMode.TRENDLINES -> startTrendlineAnalysis(timeframe)
        }
    }

    fun startSRAnalysis(timeframe: String) {
        val symbol = currentSymbol ?: return
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        val userid = firebaseUser?.uid ?: return
        _isAnalyzing.value = true
        _analysisStatus.value = "Analyzing market..." // Simplified status
        viewModelScope.launch {
            try {
                // This now represents the entire analysis process
                val result = repository.analyzeMarketData(userid, symbol, currentInterval, timeframe)

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

    private fun startTrendlineAnalysis(timeframe: String) {
        val symbol = currentSymbol ?: return
        val userid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        sseJob?.cancel()
        viewModelScope.launch {
            _isTrendlineAnalysisInProgress.value = true // <<< This triggers the in-panel loading view
            _trendlineAnalysisStatus.value = "Initiating..."
            _trendlineChartUrl.value = null

            val taskResponse = repository.startTrendlineAnalysisTask(userid, symbol, currentInterval, timeframe)

            if (taskResponse != null) {
                _trendlineAnalysisStatus.value = "Analysis in progress..."
                sseJob = launch {
                    repository.subscribeToAnalysisUpdates(taskResponse.analysisId)
                        .catch { e ->
                            _trendlineAnalysisStatus.value = "Stream error: ${e.message}"
                            _isTrendlineAnalysisInProgress.value = false
                        }
                        .collect { update ->
                            _trendlineAnalysisStatus.value = update.progress ?: update.status
                            if (update.status == "completed") {
                                _trendlineChartUrl.value = update.chartUrl // This triggers the result view
                                _isTrendlineAnalysisInProgress.value = false
                                sseJob?.cancel()
                            } else if (update.status == "failed") {
                                _trendlineAnalysisStatus.value = "Analysis failed: ${update.errorMessage}"
                                _isTrendlineAnalysisInProgress.value = false
                                sseJob?.cancel()
                            }
                        }
                }
            } else {
                _trendlineAnalysisStatus.value = "Failed to start analysis."
                _isTrendlineAnalysisInProgress.value = false
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