package models;

import com.google.gson.annotations.SerializedName;

public class UsageData {

    @SerializedName("price_alerts_used")
    private int priceAlertsUsed;

    @SerializedName("pattern_detection_used")
    private int patternDetectionUsed;

    @SerializedName("watchlist_used")
    private int watchlistUsed;

    @SerializedName("market_analysis_used")
    private int marketAnalysisUsed;

    @SerializedName("video_downloads_used")
    private int videoDownloadsUsed;

    @SerializedName("sr_analysis_used")
    private int srAnalysisUsed;

    @SerializedName("trendline_analysis_used")
    private int trendlineAnalysisUsed;


    public int getPriceAlertsUsed() {
        return priceAlertsUsed;
    }

    public void setPriceAlertsUsed(int priceAlertsUsed) {
        this.priceAlertsUsed = priceAlertsUsed;
    }

    public int getPatternDetectionUsed() {
        return patternDetectionUsed;
    }

    public void setPatternDetectionUsed(int patternDetectionUsed) {
        this.patternDetectionUsed = patternDetectionUsed;
    }

    public int getWatchlistUsed() {
        return watchlistUsed;
    }

    public void setWatchlistUsed(int watchlistUsed) {
        this.watchlistUsed = watchlistUsed;
    }

    public int getMarketAnalysisUsed() {
        return marketAnalysisUsed;
    }

    public void setMarketAnalysisUsed(int marketAnalysisUsed) {
        this.marketAnalysisUsed = marketAnalysisUsed;
    }

    public int getVideoDownloadsUsed() {
        return videoDownloadsUsed;
    }

    public void setVideoDownloadsUsed(int videoDownloadsUsed) {
        this.videoDownloadsUsed = videoDownloadsUsed;
    }

    public int getSrAnalysisUsed() {
        return srAnalysisUsed;
    }

    public void setSrAnalysisUsed(int srAnalysisUsed) {
        this.srAnalysisUsed = srAnalysisUsed;
    }

    public int getTrendlineAnalysisUsed() {
        return trendlineAnalysisUsed;
    }

    public void setTrendlineAnalysisUsed(int trendlineAnalysisUsed) {
        this.trendlineAnalysisUsed = trendlineAnalysisUsed;
    }
}
