package services.room_background_tasks;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;

import backend.ApiService;
import backend.MainClient;
import database.roomDB.AppDatabase;
import models.CachedSymbol;
import models.Pattern;
import retrofit2.Response;

public class SyncWorker extends Worker {

    private static final String TAG = "SyncWorker";

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    // In SyncWorker.java -> doWork()
    // In SyncWorker.java -> doWork()
    @NonNull
    @Override
    public Result doWork() {
        try {
            ApiService apiService = MainClient.getApiService();

            // Fetch patterns from the backend
            Response<List<Pattern>> patternsResponse = apiService.getPatterns().execute();

            if (patternsResponse.isSuccessful() && patternsResponse.body() != null) {
                Log.d(TAG, "Patterns fetched successfully from backend.");
                AppDatabase database = AppDatabase.getInstance(getApplicationContext());

                // Use the new transaction method to prevent duplicates
                database.patternDao().syncPatterns(patternsResponse.body());

                Log.d(TAG, "Pattern data saved to local database.");
                return Result.success();
            } else {
                Log.e(TAG, "Backend response was not successful.");
                return Result.retry();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during sync: " + e.getMessage());
            return Result.failure();
        }
    }
}
