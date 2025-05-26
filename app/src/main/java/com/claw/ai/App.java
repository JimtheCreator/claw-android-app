package com.claw.ai;

import android.app.Application;

import java.util.Arrays;
import java.util.List;

import repositories.SymbolRepository;

public class App extends Application {

    // In Application class
    public void onCreate() {
        super.onCreate();
        preloadPopularSearches();
    }

    private void preloadPopularSearches() {
        new Thread(() -> {
            List<String> popular = Arrays.asList("BTC", "ETH", "BNB");
            SymbolRepository repo = new SymbolRepository();
            for (String term : popular) {
                repo.searchCrypto(term, 10);
            }
        }).start();
    }
}
