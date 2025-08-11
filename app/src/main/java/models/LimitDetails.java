package models;

public class LimitDetails {
    private int watchlist_limit;
    private int price_alerts_limit;
    private int pattern_detection_limit;
    private int sr_analysis_limit;
    private int trendline_analysis_limit;

    public int getWatchlist_limit() {
        return watchlist_limit;
    }

    public void setWatchlist_limit(int watchlist_limit) {
        this.watchlist_limit = watchlist_limit;
    }

    public int getPrice_alerts_limit() {
        return price_alerts_limit;
    }

    public void setPrice_alerts_limit(int price_alerts_limit) {
        this.price_alerts_limit = price_alerts_limit;
    }

    public int getPattern_detection_limit() {
        return pattern_detection_limit;
    }

    public void setPattern_detection_limit(int pattern_detection_limit) {
        this.pattern_detection_limit = pattern_detection_limit;
    }

    public int getSr_analysis_limit() {
        return sr_analysis_limit;
    }

    public void setSr_analysis_limit(int sr_analysis_limit) {
        this.sr_analysis_limit = sr_analysis_limit;
    }

    public int getTrendline_analysis_limit() {
        return trendline_analysis_limit;
    }

    public void setTrendline_analysis_limit(int trendline_analysis_limit) {
        this.trendline_analysis_limit = trendline_analysis_limit;
    }
}
