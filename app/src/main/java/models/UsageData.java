package models;

public class UsageData {

    private int priceAlertsUsed, patternDetectionUsed, watchlistUsed, marketAnalysisUsed, videoDownloadsUsed;

    // Getters and setters
    public int getPriceAlertsUsed() { return priceAlertsUsed; }
    public void setPriceAlertsUsed(int value) { this.priceAlertsUsed = value; }
    public int getPatternDetectionUsed() { return patternDetectionUsed; }
    public void setPatternDetectionUsed(int value) { this.patternDetectionUsed = value; }
    public int getWatchlistUsed() { return watchlistUsed; }
    public void setWatchlistUsed(int value) { this.watchlistUsed = value; }
    public int getMarketAnalysisUsed() { return marketAnalysisUsed; }
    public void setMarketAnalysisUsed(int value) { this.marketAnalysisUsed = value; }
    public int getVideoDownloadsUsed() { return videoDownloadsUsed; }
    public void setVideoDownloadsUsed(int value) { this.videoDownloadsUsed = value; }
}
