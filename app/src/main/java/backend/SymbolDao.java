// backend/SymbolDao.java
package backend;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Upsert;

import java.util.List;

import models.CachedSymbol;

@Dao
public interface SymbolDao {
    @Upsert // Using @Upsert is more robust than @Insert with REPLACE strategy
    void insertAll(List<CachedSymbol> symbols);

    // MODIFIED: Query now only searches the 'symbol' column
    @Query("SELECT * FROM symbols WHERE symbol LIKE :query")
    LiveData<List<CachedSymbol>> searchSymbols(String query);

    // NEW: Added function to get a single symbol by its ticker
    @Query("SELECT * FROM symbols WHERE symbol = :ticker LIMIT 1")
    CachedSymbol getSymbolByTicker(String ticker);

    @Query("SELECT * FROM symbols WHERE symbol IN (:tickers)")
    List<CachedSymbol> getSymbolsByTickersSync(List<String> tickers);
}
