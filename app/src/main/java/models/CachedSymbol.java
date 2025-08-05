// models/CachedSymbol.java
package models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

@Entity(tableName = "symbols")
public class CachedSymbol {

    @PrimaryKey
    @NonNull
    public String symbol = "";
    public String asset;
    public String pair;
    public String baseCurrency;
    public double currentPrice;
    public double _24hChange;
    public double price;
    public double change;
    public double _24hVolume;
    public String sparklineJson; // Store sparkline as a JSON string

    // Default constructor required by Room
    public CachedSymbol() {}

    /**
     * Convenience constructor to create a CachedSymbol from a network Symbol.
     * @param symbolObj The Symbol object from the API.
     */
    public CachedSymbol(@NonNull Symbol symbolObj) {
        this.symbol = symbolObj.getSymbol();
        this.asset = symbolObj.getAsset();
        this.pair = symbolObj.getPair();
        this.baseCurrency = symbolObj.getBaseCurrency();
        this.currentPrice = symbolObj.getCurrentPrice();
        this._24hChange = symbolObj.get_24hChange();
        this.price = symbolObj.getPrice();
        this.change = symbolObj.getChange();
        this._24hVolume = symbolObj.get_24hVolume();
        this.sparklineJson = new Gson().toJson(symbolObj.getSparkline());
    }

    /**
     * Converts this CachedSymbol back to a network Symbol.
     * @return A Symbol object.
     */
    public Symbol toSymbol() {
        Gson gson = new Gson();
        Type type = new TypeToken<List<Double>>() {}.getType();
        List<Double> sparklineList = gson.fromJson(sparklineJson, type);
        if (sparklineList == null) {
            sparklineList = Collections.emptyList();
        }

        return new Symbol(
                this.symbol,
                this.asset,
                this.pair,
                this.baseCurrency,
                this.currentPrice,
                this._24hChange,
                this.price,
                this.change,
                this._24hVolume,
                sparklineList,
                false // isInWatchlist is a transient state, not cached
        );
    }
}