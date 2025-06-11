package backend;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

import models.CachedSymbol;

@Dao
public interface SymbolDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<CachedSymbol> symbols);

    @Query("SELECT * FROM symbols WHERE symbol LIKE :query OR name LIKE :query")
    LiveData<List<CachedSymbol>> searchSymbols(String query);
}
