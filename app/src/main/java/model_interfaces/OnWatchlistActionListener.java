package model_interfaces;

import models.Symbol;

// Interface for watchlist actions
public interface OnWatchlistActionListener {
    void onAddToWatchlist(String user_id, Symbol symbol, String source);
    void onRemoveFromWatchlist(String user_id, String symbol);
}