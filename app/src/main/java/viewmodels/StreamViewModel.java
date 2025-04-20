// presentation/viewmodel/StreamViewModel.java
package viewmodels;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import models.StreamMarketData;
import repositories.StreamRepository;
import timber.log.Timber;

public class StreamViewModel extends ViewModel implements LifecycleEventObserver {
    private final StreamRepository repository;

    // Add to StreamViewModel.java
    private boolean isConnected = false;
    private boolean inBackground = false;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public StreamViewModel(StreamRepository repository) {
        this.repository = repository;
    }

    public LiveData<StreamMarketData> getMarketDataStream() {
        return repository.getMarketDataStream();
    }

    public LiveData<String> getErrorStream() {
        return repository.getErrorStream();
    }

    public void connect(String symbol, String interval, boolean include_ohlcv) {
        repository.connect(symbol, interval, include_ohlcv);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        repository.disconnect();
    }


    public boolean isConnected() {
        return isConnected;
    }

    public void setInBackground(boolean inBackground) {
        this.inBackground = inBackground;
    }

    public void disconnect() {
        isConnected = false;
        repository.disconnect();
    }

    /**
     * @param lifecycleOwner
     * @param event
     */
    @Override
    public void onStateChanged(@NonNull LifecycleOwner lifecycleOwner, @NonNull Lifecycle.Event event) {
        if (event == Lifecycle.Event.ON_PAUSE) {
            handleBackgroundState();
        } else if (event == Lifecycle.Event.ON_RESUME) {
            handleForegroundState();
        }
    }

    private void handleBackgroundState() {
        // Pause WebSocket updates
        setInBackground(true);
        Timber.d("App moved to background");
    }

    private void handleForegroundState() {
        // Resume WebSocket updates
        setInBackground(false);
        Timber.d("App returned to foreground");
    }
}
