package backend;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

import models.Pattern;

@Dao
public interface PatternDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Pattern> patterns);

    @Query("SELECT * FROM patterns")
    LiveData<List<Pattern>> getAllPatterns();

    /**
     * Deletes all patterns from the table.
     */
    @Query("DELETE FROM patterns")
    void clearPatterns();

    /**
     * Replaces all existing patterns with a new list. This operation is atomic.
     *
     * @param patterns The new list of patterns to insert.
     */
    @Transaction
    default void syncPatterns(List<Pattern> patterns) {
        clearPatterns();
        insertAll(patterns);
    }
}
