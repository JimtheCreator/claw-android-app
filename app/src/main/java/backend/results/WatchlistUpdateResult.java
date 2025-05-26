package backend.results;

import models.Symbol;

public class WatchlistUpdateResult {
    public final Symbol symbol;
    public final boolean success;
    public final boolean isAdding;
    public final Throwable error;

    public WatchlistUpdateResult(Symbol symbol, boolean success, boolean isAdding, Throwable error) {
        this.symbol = symbol;
        this.success = success;
        this.isAdding = isAdding;
        this.error = error;
    }
}

