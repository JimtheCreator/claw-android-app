package models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "symbols")
public class CachedSymbol {
    @PrimaryKey
    @NonNull
    public String symbol = "";  // Unique identifier, e.g., "BTC"
    public String name;    // e.g., "Bitcoin"

    // Default constructor required by Room
    public CachedSymbol() {
    }

    // Constructor for convenience
    public CachedSymbol(@NonNull String symbol, String name) {
        this.symbol = symbol;
        this.name = name;
    }
}