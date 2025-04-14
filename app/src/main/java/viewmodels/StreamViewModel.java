// presentation/viewmodel/StreamViewModel.java
package viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import models.StreamMarketData;
import repositories.StreamRepository;

public class StreamViewModel extends ViewModel {
    private final StreamRepository repository;

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
}
