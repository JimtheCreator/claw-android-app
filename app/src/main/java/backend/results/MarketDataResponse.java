package backend.results;

import java.util.List;

import models.MarketDataEntity;

public class MarketDataResponse {
    private String symbol;
    private String interval;
    private int page;
    private int page_size;
    private List<MarketDataEntity> data;
    private boolean has_more;

    // Getters
    public String getSymbol() { return symbol; }
    public String getInterval() { return interval; }
    public int getPage() { return page; }
    public int getPageSize() { return page_size; }
    public List<MarketDataEntity> getData() { return data; }
    public boolean hasMore() { return has_more; }
}
