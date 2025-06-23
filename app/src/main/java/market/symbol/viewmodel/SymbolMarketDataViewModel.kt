package market.symbol.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import market.symbol.repo.Candle
import market.symbol.repo.MarketDataRepository
import timber.log.Timber
import java.util.Calendar
import java.util.Date

class SymbolMarketDataViewModel(
    private val repository: MarketDataRepository
) : ViewModel() {

    // StateFlows to expose UI state to the Activity
    private val _price = MutableStateFlow<Double?>(null)
    val price: StateFlow<Double?> = _price

    private val _change = MutableStateFlow<Double?>(null)
    val change: StateFlow<Double?> = _change

    private val _candleUpdates = MutableStateFlow<Candle?>(null)
    val candleUpdates: StateFlow<Candle?> = _candleUpdates

    private val _historicalCandles = MutableStateFlow<List<Candle>>(emptyList())
    val historicalCandles: StateFlow<List<Candle>> = _historicalCandles

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var currentSymbol: String? = null
    private var currentInterval: String = "1m"

    // Separate jobs for different streams
    private var historicalDataJob: Job? = null
    private var tickDataJob: Job? = null
    private var candleDataJob: Job? = null

    fun setSymbol(symbol: String) {
        if (currentSymbol == symbol) return

        Timber.d("Setting symbol to: $symbol")
        currentSymbol = symbol

        // Clear previous states
        clearStates()

        loadData()
    }

    fun setInterval(interval: String) {
        if (currentInterval == interval && currentSymbol != null) return

        Timber.d("Setting interval to: $interval")
        currentInterval = interval

        // Clear previous states for clean transition
        clearStates()

        loadData()
    }

    private fun clearStates() {
        // Clear previous error and candle updates
        _error.value = null
        _candleUpdates.value = null
        // Don't clear price/change as they should persist until new data arrives
        // Clear historical candles for immediate visual feedback
        _historicalCandles.value = emptyList()
    }

    private fun loadData() {
        val symbol = currentSymbol ?: return

        Timber.d("Loading data for symbol: $symbol, interval: $currentInterval")

        // Cancel all previous jobs
        cancelAllJobs()

        // Start fresh data loading
        loadHistoricalData(symbol)
        startTickDataStream(symbol)
        startCandleDataStream(symbol)
    }


    private fun loadHistoricalData(symbol: String) {
        historicalDataJob = viewModelScope.launch {
            _isLoading.value = true

            try {
                val calendar = Calendar.getInstance()
                val endTime = Date()

                // Adjust time range based on interval
                val hoursToSubtract = when (currentInterval) {
                    "1m", "5m" -> 24 // 1 day for minute intervals
                    "15m", "30m" -> 72 // 3 days for short intervals
                    "1h", "2h" -> 168 // 7 days for hour intervals
                    "4h", "6h" -> 336 // 14 days for longer intervals
                    "1d" -> 720 // 30 days for daily
                    "3d" -> 2160 // 90 days for 3-day
                    "1w" -> 4320 // 180 days for weekly
                    "1M" -> 8760 // 365 days for monthly
                    else -> 168 // Default to 7 days
                }

                calendar.add(Calendar.HOUR, -hoursToSubtract)
                val startTime = calendar.time

                Timber.d("Fetching historical data from $startTime to $endTime")

                val candles = repository.getHistoricalCandles(symbol, currentInterval, startTime, endTime)

                Timber.d("Received ${candles.size} historical candles")

                if (candles.isNotEmpty()) {
                    _historicalCandles.value = candles
                } else {
                    _error.value = "No historical data available for this symbol and interval"
                }

            } catch (e: Exception) {
                val errorMsg = "Failed to load historical data: ${e.message}"
                _error.value = errorMsg
                Timber.e(e, errorMsg)
                _historicalCandles.value = emptyList() // Ensure empty list on error
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun startTickDataStream(symbol: String) {
        tickDataJob = viewModelScope.launch {
            try {
                Timber.d("Starting tick data stream for $symbol")
                repository.subscribeToTickData(symbol)
                    .catch { e ->
                        Timber.e(e, "Tick data stream error for $symbol")
                        _error.value = "Price stream error: ${e.message}"
                    }
                    .collect { tickData ->
                        Timber.d("Received tick data: price=${tickData.price}, change=${tickData.change}")
                        _price.value = tickData.price
                        _change.value = tickData.change

                        // Clear any previous errors when we successfully receive data
                        if (_error.value?.contains("Price stream error") == true) {
                            _error.value = null
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "Failed to start tick data stream for $symbol")
                _error.value = "Failed to connect to price stream: ${e.message}"
            }
        }
    }

    private fun startCandleDataStream(symbol: String) {
        candleDataJob = viewModelScope.launch {
            try {
                Timber.d("Starting candle data stream for $symbol with interval $currentInterval")
                repository.subscribeToCandleUpdates(symbol, currentInterval)
                    .catch { e ->
                        Timber.e(e, "Candle data stream error for $symbol")
                        _error.value = "Candle stream error: ${e.message}"
                    }
                    .collect { candle ->
                        Timber.d("Received candle update: $candle")

                        // Replace the last candle in the list with the new one
                        val updatedCandles = _historicalCandles.value.toMutableList()
                        if (updatedCandles.isNotEmpty() && updatedCandles.last().time == candle.time) {
                            updatedCandles[updatedCandles.size - 1] = candle
                        } else {
                            updatedCandles.add(candle)
                        }
                        _historicalCandles.value = updatedCandles
                        _candleUpdates.value = candle

                        if (_error.value?.contains("Candle stream error") == true) {
                            _error.value = null
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "Failed to start candle data stream for $symbol")
                _error.value = "Failed to connect to candle stream: ${e.message}"
            }
        }
    }

    private fun cancelAllJobs() {
        Timber.d("Cancelling all active jobs")
        historicalDataJob?.cancel()
        tickDataJob?.cancel()
        candleDataJob?.cancel()

        // Reset job references
        historicalDataJob = null
        tickDataJob = null
        candleDataJob = null
    }

    override fun onCleared() {
        Timber.d("ViewModel cleared, cleaning up resources")
        cancelAllJobs()
        repository.unsubscribe()
        super.onCleared()
    }
}