package viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

import backend.requests.AddWatchlistRequest;
import backend.results.WatchlistUpdateResult;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import models.Symbol;
import repositories.SymbolRepository;
import retrofit2.Call;
import timber.log.Timber;


/**
 * ViewModel for handling cryptocurrency data operations and exposing data to the UI.
 */
public class HomeViewModel extends ViewModel {
    private final MutableLiveData<WatchlistUpdateResult> watchlistUpdateResult = new MutableLiveData<>();

    public LiveData<WatchlistUpdateResult> getWatchlistUpdateResult() {
        return watchlistUpdateResult;
    }

    private final MutableLiveData<List<Symbol>> cryptoList = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    // For top cryptos pagination
    private int currentPage = 1;
    private final int PAGE_SIZE = 20;
    private final List<Symbol> allCryptos = new ArrayList<>();

    // Add these class variables
    private Call<List<Symbol>> currentSearchCall;
    private final SymbolRepository repository; // Add this

    private final MutableLiveData<List<Symbol>> searchResults = new MutableLiveData<>();

    private final MutableLiveData<List<Symbol>> watchlist = new MutableLiveData<>();

    public HomeViewModel() {
        repository = new SymbolRepository();
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

    public void addToWatchlist(String userID, Symbol symbol, String source) {
        AddWatchlistRequest request = new AddWatchlistRequest(
                userID,
                symbol.getSymbol(),
                symbol.getBaseCurrency(),
                symbol.getAsset(),
                source
        );

        repository.addToWatchlist(request)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> {
                            Timber.d("Added %s to watchlist", symbol.getSymbol());
                            watchlistUpdateResult.setValue(new WatchlistUpdateResult(symbol, true, true, null));
                        },
                        throwable -> {
                            Timber.e(throwable, "Failed to add %s to watchlist", symbol.getSymbol());
                            watchlistUpdateResult.setValue(new WatchlistUpdateResult(symbol, false, true, throwable));
                        }
                );
    }

    public void removeFromWatchlist(String userID, String symbol) {
        repository.removeFromWatchlist(userID, symbol)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> {
                            Timber.d("Removed %s from watchlist", symbol);
                        },
                        throwable -> {
                            Timber.e(throwable, "Failed to remove %s from watchlist", symbol);
                        }
                );
    }



    // LiveData getters

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


