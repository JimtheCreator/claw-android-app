package com.claw.ai;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import java.util.Arrays;
import java.util.List;

import repositories.SymbolRepository;

public class App extends Application {

    public static final String PRICE_ALERTS_CHANNEL_ID = "price_alerts_channel";

    // In Application class
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
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

    private void createNotificationChannels() {
        // This check is important. It ensures this code only runs on Android 8.0+
        CharSequence name = "Price Alerts"; // The user-visible name of the channel.
        String description = "Notifications for symbol price alerts.";
        int importance = NotificationManager.IMPORTANCE_HIGH; // Set importance. HIGH makes it a heads-up notification.

        NotificationChannel channel = new NotificationChannel(PRICE_ALERTS_CHANNEL_ID, name, importance);
        channel.setDescription(description);

        // Register the channel with the system.
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }
}
