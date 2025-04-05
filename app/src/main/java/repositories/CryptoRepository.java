package repositories;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;

import backend.CryptoApiService;
import backend.MainClient;
import models.Symbol;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CryptoRepository {
    private final CryptoApiService api;

    public CryptoRepository() {
        api = MainClient.getInstance().create(CryptoApiService.class);
    }

    public LiveData<List<Symbol>> getTopCryptos(int limit) {
        MutableLiveData<List<Symbol>> data = new MutableLiveData<>();
        api.getTopCryptos(limit, null).enqueue(new Callback<List<Symbol>>() {
            @Override
            public void onResponse(Call<List<Symbol>> call, Response<List<Symbol>> response) {
                data.setValue(response.body());
            }

            @Override
            public void onFailure(Call<List<Symbol>> call, Throwable t) {
                data.setValue(null); // handle error appropriately
            }
        });
        return data;
    }

    public LiveData<List<Symbol>> searchCrypto(String query, int limit) {
        MutableLiveData<List<Symbol>> data = new MutableLiveData<>();
        api.searchCrypto(query, limit).enqueue(new Callback<List<Symbol>>() {
            @Override
            public void onResponse(Call<List<Symbol>> call, Response<List<Symbol>> response) {
                data.setValue(response.body());
            }

            @Override
            public void onFailure(Call<List<Symbol>> call, Throwable t) {
                data.setValue(null);
            }
        });
        return data;
    }
}

