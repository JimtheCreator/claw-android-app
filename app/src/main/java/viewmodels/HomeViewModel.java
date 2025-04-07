package viewmodels;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

import backend.CryptoApiService;
import backend.MainClient;
import models.Symbol;
import repositories.CryptoRepository;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;


/**
 * ViewModel for handling cryptocurrency data operations and exposing data to the UI.
 */
public class HomeViewModel extends ViewModel {
    private final MutableLiveData<List<Symbol>> cryptoList = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    // For top cryptos pagination
    private int currentPage = 1;
    private final int PAGE_SIZE = 20;
    private final List<Symbol> allCryptos = new ArrayList<>();

    // Add these class variables
    private Call<List<Symbol>> currentSearchCall;
    private final CryptoRepository repository; // Add this

    private final MutableLiveData<List<Symbol>> searchResults = new MutableLiveData<>();

    public HomeViewModel() {
        repository = new CryptoRepository();
    }

    private List<Symbol> getPaginatedData() {
        int end = Math.min(currentPage * PAGE_SIZE, allCryptos.size());
        return allCryptos.subList(0, end);
    }

    /**
     * Loads the next page of popular cryptocurrencies if available.
     */
    public void loadNextPage() {
        Boolean loading = isLoading.getValue();
        if (Boolean.TRUE.equals(loading) || (currentPage * PAGE_SIZE) >= allCryptos.size()) {
            return;
        }

        currentPage++;
        cryptoList.postValue(getPaginatedData());
    }

    // Add this method to check if more data is available
    public boolean hasMoreData() {
        return currentPage * PAGE_SIZE < allCryptos.size();
    }

    /**
     * Searches cryptocurrencies based on user query.
     * @param query Search string input by user
     * @param limit Maximum number of search results
     */
    public void searchCryptos(String query, int limit) {
        isLoading.postValue(true);

        LiveData<List<Symbol>> liveData = repository.searchCrypto(query, limit);
        Observer<List<Symbol>> observer = new Observer<List<Symbol>>() {
            @Override
            public void onChanged(List<Symbol> results) {
                // Update search results even if null
                searchResults.postValue(results);
                isLoading.postValue(false); // Hide loading regardless

                // Clean up observer
                liveData.removeObserver(this);

                // Optionally handle errors
                if (results == null) {
                    errorMessage.postValue("Failed to fetch results");
                }
            }
        };

        liveData.observeForever(observer);
    }


    // LiveData getters
    public LiveData<List<Symbol>> getCryptoList() {
        return cryptoList;
    }

    public LiveData<List<Symbol>> getSearchResults() {
        return searchResults;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

}


