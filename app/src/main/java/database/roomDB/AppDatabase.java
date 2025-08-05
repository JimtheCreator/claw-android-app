// database/roomDB/AppDatabase.java
// database/roomDB/AppDatabase.java
package database.roomDB;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import backend.PatternDao;
import backend.SymbolDao;
import models.CachedSymbol;
import models.Pattern;

@Database(entities = {Pattern.class, CachedSymbol.class}, version = 3, exportSchema = false) // MIGRATION: Bump version
public abstract class AppDatabase extends RoomDatabase {

    public abstract PatternDao patternDao();

    public abstract SymbolDao symbolDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getInstance(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "pattern_alert_database")
                            // MODIFIED: Use the recommended approach for destructive migration
                            .fallbackToDestructiveMigration(true)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}