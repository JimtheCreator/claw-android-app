package com.claw.ai;

import android.app.Application;

import java.util.Arrays;
import java.util.List;

import repositories.CryptoRepository;
import timber.log.Timber;

public class App extends Application {

    // In Application class
    public void onCreate() {
        super.onCreate();
        preloadPopularSearches();
    }

    private void preloadPopularSearches() {
        new Thread(() -> {
            List<String> popular = Arrays.asList("BTC", "ETH", "BNB");
            CryptoRepository repo = new CryptoRepository();
            for (String term : popular) {
                repo.searchCrypto(term, 10);
            }
        }).start();
    }
}
