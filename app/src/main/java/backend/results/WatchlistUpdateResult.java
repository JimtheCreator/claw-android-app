package backend.results;

import models.Symbol;

public class WatchlistUpdateResult {
    public final Symbol symbol;
    public final String tickerSymbol;
    public final boolean success;
    public final boolean isAdded;
    public final Throwable error;

    // Constructor for Symbol object (keep for addToWatchlist)
    public WatchlistUpdateResult(Symbol symbol, boolean success, boolean isAdded, Throwable error) {
        this.symbol = symbol;
        this.tickerSymbol = symbol != null ? symbol.getSymbol() : null;
        this.success = success;
        this.isAdded = isAdded;
        this.error = error;
    }

    // Constructor for just ticker string (for removeFromWatchlist)
    public WatchlistUpdateResult(String tickerSymbol, boolean success, boolean isAdded, Throwable error) {
        this.symbol = null;
        this.tickerSymbol = tickerSymbol;
        this.success = success;
        this.isAdded = isAdded;
        this.error = error;
    }

    public Symbol getSymbol() {
        return symbol;
    }

    public String getTickerSymbol() {
        return tickerSymbol;
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isAdded() {
        return isAdded;
    }

    public Throwable getError() {
        return error;
    }
}