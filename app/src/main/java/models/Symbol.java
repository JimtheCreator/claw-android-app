package models;

import java.util.List;

import com.google.gson.annotations.SerializedName;


/**
 * Represents a cryptocurrency symbol
 */
public class Symbol {
    private String symbol;
    @SerializedName("asset")
    private String asset;
    private String pair;

    @SerializedName("base_currency")
    private String baseCurrency;

    @SerializedName("current_price")
    private double currentPrice;

    @SerializedName("24h_change")
    private double _24hChange;

    @SerializedName("24h_volume")
    private double _24hVolume;

    private List<Double> sparkline;
    private boolean isInWatchlist;

    public Symbol(String symbol, String asset, String pair, String baseCurrency, double currentPrice, double _24hChange, double _24hVolume, List<Double> sparkline, boolean isInWatchlist) {
        this.symbol = symbol;
        this.asset = asset;
        this.pair = pair;
        this.baseCurrency = baseCurrency;
        this.currentPrice = currentPrice;
        this._24hChange = _24hChange;
        this._24hVolume = _24hVolume;
        this.sparkline = sparkline;
        this.isInWatchlist = false;
    }

    // Getters
    public String getSymbol() { return symbol; }
    public String getAsset() { return asset; }
    public String getPair() { return pair; }
    public String getBaseCurrency() { return baseCurrency; }
    public double getCurrentPrice() { return currentPrice; }
    public double get_24hChange() { return _24hChange; }
    public double get_24hVolume() { return _24hVolume; }
    public List<Double> getSparkline() { return sparkline; }

    public boolean isInWatchlist() {
        return isInWatchlist;
    }

    public void setInWatchlist(boolean inWatchlist) {
        this.isInWatchlist = inWatchlist;
    }
}

