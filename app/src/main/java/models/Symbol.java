package models;

import java.util.List;
import java.util.Objects;

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

    @SerializedName("price")
    private double price;

    @SerializedName("change")
    private double change;

    @SerializedName("24h_volume")
    private double _24hVolume;

    private List<Double> sparkline;
    private boolean isInWatchlist;

    public Symbol(String symbol, String asset, String pair, String baseCurrency, double currentPrice, double _24hChange, double price, double change, double _24hVolume, List<Double> sparkline, boolean isInWatchlist) {
        this.symbol = symbol;
        this.asset = asset;
        this.pair = pair;
        this.baseCurrency = baseCurrency;
        this.currentPrice = currentPrice;
        this._24hChange = _24hChange;
        this.price = price;
        this.change = change;
        this._24hVolume = _24hVolume;
        this.sparkline = sparkline;
        this.isInWatchlist = isInWatchlist;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getAsset() {
        return asset;
    }

    public void setAsset(String asset) {
        this.asset = asset;
    }

    public String getPair() {
        return pair;
    }

    public void setPair(String pair) {
        this.pair = pair;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public void setBaseCurrency(String baseCurrency) {
        this.baseCurrency = baseCurrency;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(double currentPrice) {
        this.currentPrice = currentPrice;
    }

    public double get_24hChange() {
        return _24hChange;
    }

    public void set_24hChange(double _24hChange) {
        this._24hChange = _24hChange;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getChange() {
        return change;
    }

    public void setChange(double change) {
        this.change = change;
    }

    public double get_24hVolume() {
        return _24hVolume;
    }

    public void set_24hVolume(double _24hVolume) {
        this._24hVolume = _24hVolume;
    }

    public List<Double> getSparkline() {
        return sparkline;
    }

    public void setSparkline(List<Double> sparkline) {
        this.sparkline = sparkline;
    }

    public boolean isInWatchlist() {
        return isInWatchlist;
    }

    public void setInWatchlist(boolean inWatchlist) {
        isInWatchlist = inWatchlist;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Symbol symbol = (Symbol) o;
        return Double.compare(symbol.price, price) == 0 &&
                Double.compare(symbol.change, change) == 0 &&
                isInWatchlist == symbol.isInWatchlist && // Include this
                Objects.equals(symbol, symbol.symbol) &&
                Objects.equals(asset, symbol.asset) &&
                Objects.equals(baseCurrency, symbol.baseCurrency) &&
                Objects.equals(sparkline, symbol.sparkline);
    }
}

