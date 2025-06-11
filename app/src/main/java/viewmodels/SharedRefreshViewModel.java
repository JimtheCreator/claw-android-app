package viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import utils.Event;

public class SharedRefreshViewModel extends ViewModel {
    private final MutableLiveData<Event<Boolean>> _priceAlertsRefreshRequest = new MutableLiveData<>();
    public final LiveData<Event<Boolean>> priceAlertsRefreshRequest = _priceAlertsRefreshRequest;

    public void requestPriceAlertsRefresh() {
        _priceAlertsRefreshRequest.setValue(new Event<>(true));
    }
}
