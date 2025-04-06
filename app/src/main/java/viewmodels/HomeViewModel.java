package viewmodels;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

import backend.CryptoApiService;
import backend.MainClient;
import models.Symbol;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;


/**
 * ViewModel for handling cryptocurrency data operations and exposing data to the UI.
 */
public class HomeViewModel extends ViewModel {
    private final MutableLiveData<List<Symbol>> cryptoList = new MutableLiveData<>();
    private final MutableLiveData<List<Symbol>> searchResults = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    private final CryptoApiService apiService;

    // For top cryptos pagination
    private int currentPage = 1;
    private final int PAGE_SIZE = 20;
    private final List<Symbol> allCryptos = new ArrayList<>();

    public HomeViewModel() {
        apiService = MainClient.getInstance().create(CryptoApiService.class);
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
     * Fetches top cryptocurrencies from the backend with pagination.
     * @param limit Number of items to fetch initially
     */
    public void loadTopCryptos(int limit) {
        Boolean loading = isLoading.getValue();
        if (Boolean.TRUE.equals(loading)) return;

        isLoading.postValue(true);
        apiService.getTopCryptos(limit, null).enqueue(new Callback<List<Symbol>>() {
            @Override
            public void onResponse(Call<List<Symbol>> call, Response<List<Symbol>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    allCryptos.clear();
                    allCryptos.addAll(response.body());
                    currentPage = 1;
                    cryptoList.postValue(getPaginatedData());
                }
                isLoading.postValue(false);
            }

            @Override
            public void onFailure(Call<List<Symbol>> call, Throwable t) {
                isLoading.postValue(false);
                errorMessage.postValue("Network error: " + t.getMessage());
            }
        });
    }

    /**
     * Searches cryptocurrencies based on user query.
     * @param query Search string input by user
     * @param limit Maximum number of search results
     */
    public void searchCryptos(String query, int limit) {
        Timber.d("Initiating search for: %s", query);
        if (query.length() < 2) {
            Timber.w("Search query too short");
            searchResults.postValue(new ArrayList<>());
            return;
        }

        apiService.searchCrypto(query, limit).enqueue(new Callback<List<Symbol>>() {
            @Override
            public void onResponse(@NonNull Call<List<Symbol>> call, @NonNull Response<List<Symbol>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    searchResults.postValue(response.body());
                    Timber.i("Search found %d results", response.body().size());
                } else {
                    errorMessage.postValue("Search failed: " + response.code());
                    Timber.e("Search request failed: %s", response.message());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Symbol>> call, @NonNull Throwable t) {
                errorMessage.postValue("Search error: " + t.getMessage());
                Timber.e(t, "Search network failure");
            }
        });
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


