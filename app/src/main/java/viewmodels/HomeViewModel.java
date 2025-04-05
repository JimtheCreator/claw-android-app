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
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final CryptoApiService apiService;
    // For top cryptos pagination
    private int currentPage = 1;
    private final int PAGE_SIZE = 20;
    private final List<Symbol> allCryptos = new ArrayList<>();
    private boolean isLoading = false;


    private List<Symbol> getPaginatedData() {
        int end = Math.min(currentPage * PAGE_SIZE, allCryptos.size());
        return allCryptos.subList(0, end);
    }

    public void loadNextPage() {
        if (!isLoading && (currentPage * PAGE_SIZE) < allCryptos.size()) {
            currentPage++;
            cryptoList.postValue(getPaginatedData());
        }
    }

    public HomeViewModel() {
        apiService = MainClient.getInstance().create(CryptoApiService.class);
    }

    /**
     * Fetches top cryptocurrencies from the backend
     *
     * @param limit Number of items per page
     */
    public void loadTopCryptos(int limit) {
        if (isLoading) return;

        isLoading = true;
        apiService.getTopCryptos(limit, null).enqueue(new Callback<List<Symbol>>() {
            @Override
            public void onResponse(Call<List<Symbol>> call, Response<List<Symbol>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    allCryptos.addAll(response.body());
                    List<Symbol> pageData = getPaginatedData();
                    cryptoList.postValue(pageData);
                }
                isLoading = false;
            }

            @Override
            public void onFailure(Call<List<Symbol>> call, Throwable t) {
                isLoading = false;
                errorMessage.postValue("Network error: " + t.getMessage());
            }
        });
    }

    /**
     * Searches cryptocurrencies based on user query
     *
     * @param query Search string input by user
     */
    public void searchCryptos(String query, int limit) {
        Timber.d("Initiating search for: %s", query);
        if (query.length() < 2) {
            Timber.w("Search query too short");
            return;
        }

        apiService.searchCrypto(query, limit).enqueue(new Callback<List<Symbol>>() {
            @Override
            public void onResponse(@NonNull Call<List<Symbol>> call, @NonNull Response<List<Symbol>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    cryptoList.postValue(response.body());
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

    public LiveData<List<Symbol>> getSearchedCryptoList() {
        return cryptoList;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }
}

