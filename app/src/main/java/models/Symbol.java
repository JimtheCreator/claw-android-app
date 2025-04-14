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

    // Getters
    public String getSymbol() { return symbol; }
    public String getAsset() { return asset; }
    public String getPair() { return pair; }
    public String getBaseCurrency() { return baseCurrency; }
    public double getCurrentPrice() { return currentPrice; }
    public double get_24hChange() { return _24hChange; }
    public double get_24hVolume() { return _24hVolume; }
    public List<Double> getSparkline() { return sparkline; }
}

