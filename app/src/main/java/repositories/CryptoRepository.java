package repositories;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import backend.ApiEndpoints;
import backend.MainClient;
import models.Symbol;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CryptoRepository {
    private final ApiEndpoints api;
    private final Map<String, List<Symbol>> searchCache = new ConcurrentHashMap<>();
    private Call<List<Symbol>> currentSearchCall;

    public CryptoRepository() {
        api = MainClient.getInstance().create(ApiEndpoints.class);
    }


    // Fixed searchCrypto implementation
    public LiveData<List<Symbol>> searchCrypto(String query, int limit) {
        MutableLiveData<List<Symbol>> liveData = new MutableLiveData<>();

        // 1. Cancel previous request
        if (currentSearchCall != null) {
            currentSearchCall.cancel();
        }

        // 2. Check cache first
        if (searchCache.containsKey(query)) {
            liveData.postValue(searchCache.get(query));
            return liveData;
        }

        // 3. Make new network request
        currentSearchCall = api.searchCrypto(query, limit);
        currentSearchCall.enqueue(new Callback<List<Symbol>>() {
            @Override
            public void onResponse(@NonNull Call<List<Symbol>> call, @NonNull Response<List<Symbol>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    searchCache.put(query, response.body());
                    liveData.postValue(response.body());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Symbol>> call, @NonNull Throwable t) {
                if (!call.isCanceled()) {
                    liveData.postValue(Collections.emptyList());
                }
            }
        });

        return liveData;
    }
}

