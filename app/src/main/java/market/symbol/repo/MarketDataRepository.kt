package market.symbol.repo

import android.util.Log
import backend.ApiService
import backend.MainClient
import backend.WebSocketService
import com.google.gson.Gson
import com.google.gson.JsonObject
import data.remote.WebSocketServiceImpl
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import models.MarketDataEntity
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Data classes for tick data and candles
data class TickData(
    val price: Double,
    val change: Double
)

data class Candle(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

// A new data class to hold either type of update
sealed class MarketUpdate {
    data class Tick(val data: TickData) : MarketUpdate()
    data class CandleUpdate(val data: Candle) : MarketUpdate()
}

class MarketDataRepository(
    private val TAG: String = "MarketDataRepository",
    private val apiService: ApiService = MainClient.getInstance().create(ApiService::class.java),
    private val webSocketService: WebSocketService = WebSocketServiceImpl(OkHttpClient(), OkHttpClient())
) {
    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val gson = Gson()

    // Track active WebSocket connections separately
    private var tickDataWebSocket: WebSocket? = null
    private var candleDataWebSocket: WebSocket? = null

    // Track active streams
    private var activeStreamSymbol: String? = null
    private var activeStreamInterval: String? = null
    private var isStreamActive: Boolean = false

    // A single subscription method
    fun subscribeToMarketUpdates(symbol: String, interval: String): Flow<MarketUpdate> {
        return callbackFlow {
            Log.d(TAG, "Creating market update WebSocket flow for $symbol with interval $interval")

            // Update active stream tracking
            activeStreamSymbol = symbol
            activeStreamInterval = interval
            isStreamActive = true

            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    Log.d(TAG, "Market update WebSocket opened for $symbol")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d(TAG, "Market update WebSocket message: $text")
                    try {
                        val jsonObject = gson.fromJson(text, JsonObject::class.java)
                        if (jsonObject.has("type") && jsonObject.get("type").asString == "price") {
                            val tickData = parseTickData(text)
                            if (tickData != null) {
                                trySend(MarketUpdate.Tick(tickData)).isSuccess
                            }
                        } else { // Assuming anything else is a candle
                            val candle = parseCandleData(text)
                            if (candle != null) {
                                trySend(MarketUpdate.CandleUpdate(candle)).isSuccess
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing market update message: $text", e)
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "Market update WebSocket closed for $symbol: $reason")
                    isStreamActive = false
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                    Log.e(TAG, "Market update WebSocket failure for $symbol", t)
                    isStreamActive = false
                }
            }

            // Connect once with include_ohlcv = true
            webSocketService.connectToStream(symbol, interval, true, listener)

            awaitClose {
                Log.d(TAG, "Market update flow closed for $symbol")
                isStreamActive = false
                activeStreamSymbol = null
                activeStreamInterval = null
                webSocketService.disconnect()
            }
        }
    }

    /**
     * Cancel the currently active stream
     * This function can be called from outside to stop the current market data stream
     */
    fun cancelStream() {
        Log.d(TAG, "Cancelling active stream for symbol: $activeStreamSymbol")

        if (isStreamActive) {
            // Close WebSocket connections
            tickDataWebSocket?.close(1000, "Stream cancelled")
            tickDataWebSocket = null

            candleDataWebSocket?.close(1000, "Stream cancelled")
            candleDataWebSocket = null

            // Disconnect the WebSocket service
            webSocketService.disconnect()

            // Reset tracking variables
            isStreamActive = false
            activeStreamSymbol = null
            activeStreamInterval = null

            Log.d(TAG, "Stream cancelled successfully")
        } else {
            Log.d(TAG, "No active stream to cancel")
        }
    }

    /**
     * Check if there's an active stream
     */
    fun isStreamActive(): Boolean = isStreamActive

    /**
     * Get information about the current active stream
     */
    fun getActiveStreamInfo(): Pair<String?, String?> = Pair(activeStreamSymbol, activeStreamInterval)

    /**
     * Parse tick data (price updates) from WebSocket message
     */
    private fun parseTickData(text: String): TickData? {
        return try {
            val jsonObject = gson.fromJson(text, JsonObject::class.java)
            Log.d(TAG, "Parsing tick data JSON: $jsonObject")
            when {
                jsonObject.has("type") && jsonObject.get("type").asString == "price" -> {
                    val price = jsonObject.get("price")?.asDouble ?: 0.0
                    val change = jsonObject.get("change")?.asDouble ?: 0.0
                    TickData(price, change)
                }
                jsonObject.has("price") -> {
                    val price = jsonObject.get("price").asDouble
                    val change = jsonObject.get("change")?.asDouble ?: 0.0
                    TickData(price, change)
                }
                jsonObject.has("p") -> { // Adjust for different key names
                    val price = jsonObject.get("p").asDouble
                    val change = jsonObject.get("c")?.asDouble ?: 0.0
                    TickData(price, change)
                }
                else -> {
                    Log.w(TAG, "Unknown tick data format: $text")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing tick data JSON: $text", e)
            null
        }
    }

    /**
     * Parse candle data (OHLCV) from WebSocket message
     */
    private fun parseCandleData(text: String): Candle? {
        return try {
            val jsonObject = gson.fromJson(text, JsonObject::class.java)

            // Log the structure for debugging
            Log.d(TAG, "Parsing candle data JSON: $jsonObject")

            when {
                jsonObject.has("type") && jsonObject.get("type").asString == "candle" -> {
                    // Structured message with type
                    val ohlcv = jsonObject.getAsJsonObject("ohlcv")
                    parseCandleFromOhlcv(ohlcv)
                }
                jsonObject.has("ohlcv") -> {
                    // Direct OHLCV message
                    val ohlcv = jsonObject.getAsJsonObject("ohlcv")
                    parseCandleFromOhlcv(ohlcv)
                }
                // Check if this is a direct candle format
                // Modify this branch
                jsonObject.has("open_time") && jsonObject.has("open") -> {
                    Candle(
                        time = jsonObject.get("open_time").asLong / 1000, // Convert ms to seconds
                        open = jsonObject.get("open").asDouble,
                        high = jsonObject.get("high").asDouble,
                        low = jsonObject.get("low").asDouble,
                        close = jsonObject.get("close").asDouble,
                        volume = jsonObject.get("volume")?.asDouble ?: 0.0
                    )
                }
                else -> {
                    Log.w(TAG, "Unknown candle data format: $text")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing candle data JSON: $text", e)
            null
        }
    }

    private fun parseCandleFromOhlcv(ohlcv: JsonObject?): Candle? {
        return if (ohlcv != null) {
            try {
                Candle(
                    // Also modify this part
                    time = ohlcv.get("open_time")?.asLong?.div(1000) ?: 0L, // Convert ms to seconds
                    open = ohlcv.get("open")?.asDouble ?: 0.0,
                    high = ohlcv.get("high")?.asDouble ?: 0.0,
                    low = ohlcv.get("low")?.asDouble ?: 0.0,
                    close = ohlcv.get("close")?.asDouble ?: 0.0,
                    volume = ohlcv.get("volume")?.asDouble ?: 0.0
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing OHLCV object: $ohlcv", e)
                null
            }
        } else {
            Log.w(TAG, "OHLCV object is null")
            null
        }
    }

    suspend fun getHistoricalCandles(
        symbol: String,
        interval: String,
        startTime: Date,
        endTime: Date
    ): List<Candle> = withContext(Dispatchers.IO) {
        val formattedStart = apiDateFormat.format(startTime)
        val formattedEnd = apiDateFormat.format(endTime)

        Log.d(TAG, "Fetching historical candles for $symbol, interval: $interval, start: $formattedStart, end: $formattedEnd")

        return@withContext try {
            val response = apiService.getMarketData(symbol, interval, formattedStart, formattedEnd, 1, 1000).execute()

            if (response.isSuccessful) {
                val body = response.body()
                Log.d(TAG, "API Response successful, data size: ${body?.data?.size}")

                body?.data?.map { entity ->
                    val timestampMillis = when {
                        entity.timestamp is Date -> entity.timestamp.time
                        else -> {
                            try {
                                apiDateFormat.parse(entity.timestamp.toString())?.time ?: 0L
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to parse timestamp: ${entity.timestamp}", e)
                                0L
                            }
                        }
                    }

                    Candle(
                        time = timestampMillis / 1000, // Convert to seconds for chart
                        open = entity.open,
                        high = entity.high,
                        low = entity.low,
                        close = entity.close,
                        volume = entity.volume
                    )
                } ?: emptyList()
            } else {
                Log.e(TAG, "API Error: ${response.code()} - ${response.message()}")
                Log.e(TAG, "Error body: ${response.errorBody()?.string()}")
                emptyList()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Exception in getHistoricalCandles", e)
            emptyList()
        }
    }

    fun unsubscribe() {
        Log.d(TAG, "Unsubscribing from all WebSocket connections")

        tickDataWebSocket?.close(1000, "Unsubscribe")
        tickDataWebSocket = null

        candleDataWebSocket?.close(1000, "Unsubscribe")
        candleDataWebSocket = null

        // Also disconnect the service
        webSocketService.disconnect()

        // Reset tracking variables
        isStreamActive = false
        activeStreamSymbol = null
        activeStreamInterval = null
    }
}