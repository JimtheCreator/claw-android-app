package market.symbol.model

import com.google.gson.annotations.SerializedName


/**
 * The main request body sent to the analysis endpoint.
 */
data class AnalysisRequest(
    val user_id: String,
    val symbol: String,
    val interval: String,
    val timeframe: String
)

/**
 * The root object for the new analysis API response.
 */
data class AnalysisResult(
    @SerializedName("support_levels") val supportLevels: List<Level>,
    @SerializedName("resistance_levels") val resistanceLevels: List<Level>,
    @SerializedName("demand_zones") val demandZones: List<Zone>,
    @SerializedName("supply_zones") val supplyZones: List<Zone>,
    @SerializedName("psychological_levels") val psychologicalLevels: List<PsychologicalLevel>,
    @SerializedName("volume_profile") val volumeProfile: VolumeProfile,
    @SerializedName("market_structure") val marketStructure: MarketStructure,
    @SerializedName("confluence_zones") val confluenceZones: List<ConfluenceZone>,
    val meta: Meta
)

/**
 * Represents the response from POST /analyze/trendlines
 */
data class AnalysisTaskResponse(
    @SerializedName("analysis_id") val analysisId: String,
    val message: String
)

/**
 * Represents a single event message from the SSE stream.
 */
data class AnalysisProgressUpdate(
    @SerializedName("analysis_id") val analysisId: String,
    val status: String,
    val progress: String? = null,
    @SerializedName("chart_url") val chartUrl: String? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

/**
 * Represents a single support or resistance level.
 */
data class Level(
    val price: Double,
    val type: String,
    @SerializedName("confidence_score") val confidenceScore: Double,
    val touches: Int,
    @SerializedName("first_touch_ts") val firstTouchTs: String,
    @SerializedName("last_touch_ts") val lastTouchTs: String,
    @SerializedName("score_details") val scoreDetails: ScoreDetails
)

/**
 * Detailed scoring metrics for a level.
 */
data class ScoreDetails(
    @SerializedName("touch_count_score") val touchCountScore: Double,
    @SerializedName("volume_score") val volumeScore: Double,
    @SerializedName("age_score") val ageScore: Double,
    @SerializedName("time_span_score") val timeSpanScore: Double,
    @SerializedName("confluence_score") val confluenceScore: Double
)

/**
 * Represents a demand or supply zone.
 */
data class Zone(
    val top: Double,
    val bottom: Double,
    val strength: Double,
    @SerializedName("touch_count") val touchCount: Int,
    @SerializedName("last_timestamp") val lastTimestamp: String,
    @SerializedName("is_fresh") val isFresh: Boolean,
    val id: String
)

/**
 * Represents a psychological price level (e.g., round numbers).
 */
data class PsychologicalLevel(
    val price: Double,
    val type: String
)

/**
 * Contains volume profile analysis data.
 */
data class VolumeProfile(
    val poc: Double,
    @SerializedName("value_area_high") val valueAreaHigh: Double,
    @SerializedName("value_area_low") val valueAreaLow: Double,
    val profile: Map<String, Double>
)

/**
 * Contains market structure information like trend and VWAP.
 */
data class MarketStructure(
    val trend: String,
    @SerializedName("break_of_structure") val breakOfStructure: String?,
    val vwap: Double
)

/**
 * Represents an area where multiple technical factors converge.
 */
data class ConfluenceZone(
    val type: String,
    val level: Double?,
    val range: List<Double>?,
    val score: Double
)

/**
 * Metadata about the analysis performed.
 */
data class Meta(
    val interval: String,
    @SerializedName("window_used") val windowUsed: Int,
    @SerializedName("timestamp_range") val timestampRange: List<String>,
    @SerializedName("asset_type") val assetType: String,
    val config: Map<String, Any> // Using a map for flexible config
)
