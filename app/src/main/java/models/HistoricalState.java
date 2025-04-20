package models;

import java.util.Date;

// First, let's create a better HistoricalState class to track state for each interval
// 1. Update the HistoricalState class to include necessary fields
// Add this to your imports
import java.util.concurrent.atomic.AtomicBoolean;

// Replace or enhance your HistoricalState class (move outside the activity if it's an inner class)
public class HistoricalState {
    public Date oldestLoadedTimestamp;
    public Date currentStart;
    public int currentPage = 1;
    public final int CHUNK_SIZE = 100;
    public volatile boolean isLoading = false;
    public boolean noMoreData = false;
    public int backfilledChunks = 0;
    public int maxBackfillChunks = 10; // Limit how far back we can go

    // Reset state when changing intervals
    public void reset() {
        oldestLoadedTimestamp = null;
        currentStart = null;
        currentPage = 1;
        isLoading = false;
        noMoreData = false;
        backfilledChunks = 0;
    }
}
