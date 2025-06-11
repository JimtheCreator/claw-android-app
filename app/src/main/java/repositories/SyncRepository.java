package repositories;

import android.app.Application;
import java.util.concurrent.TimeUnit;

import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import backend.ApiService;
import backend.PatternDao;
import backend.SymbolDao;
import database.roomDB.AppDatabase;
import services.room_background_tasks.SyncWorker;

import android.app.Application;
import java.util.concurrent.TimeUnit;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import backend.ApiService;
import backend.PatternDao;
import backend.SymbolDao;
import database.roomDB.AppDatabase;
import services.room_background_tasks.SyncWorker;

public class SyncRepository {
    private final PatternDao patternDao;
    private final SymbolDao symbolDao;
    private final ApiService apiService;
    private final WorkManager workManager;
    private static final String SYNC_WORK_TAG = "PeriodicPatternSync"; // Unique name for the work

    public SyncRepository(Application application, AppDatabase database, ApiService apiService) {
        this.patternDao = database.patternDao();
        this.symbolDao = database.symbolDao();
        this.apiService = apiService;
        this.workManager = WorkManager.getInstance(application);
    }

    public void startPeriodicSync() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest syncWorkRequest = new PeriodicWorkRequest.Builder(
                SyncWorker.class, 24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build();

        // Use enqueueUniquePeriodicWork to avoid scheduling duplicates
        workManager.enqueueUniquePeriodicWork(
                SYNC_WORK_TAG,
                ExistingPeriodicWorkPolicy.KEEP, // Keep the existing work if it's already scheduled
                syncWorkRequest
        );
    }
}
