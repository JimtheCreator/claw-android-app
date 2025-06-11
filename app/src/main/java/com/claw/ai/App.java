package com.claw.ai;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.media.AudioAttributes;
import android.net.Uri;

import java.util.Arrays;
import java.util.List;

import backend.ApiService;
import backend.MainClient;
import database.roomDB.AppDatabase;
import repositories.SymbolRepository;
import repositories.SyncRepository;

public class App extends Application {

    public static final String PRICE_ALERTS_CHANNEL_ID = "price_alerts_channel";

    public void onCreate() {
        super.onCreate();
        createNotificationChannels();

        // Initialize periodic sync
        initializePeriodicSync();
    }

    private void createNotificationChannels() {
        CharSequence name = "Price Alerts";
        String description = "Notifications for symbol price alerts.";
        int importance = NotificationManager.IMPORTANCE_HIGH;

        NotificationChannel channel = new NotificationChannel(PRICE_ALERTS_CHANNEL_ID, name, importance);
        channel.setDescription(description);

        // Set the custom sound for the channel
        Uri soundUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.price_alert_sample_one);
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build();
        channel.setSound(soundUri, audioAttributes);

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void initializePeriodicSync() {
        AppDatabase database = AppDatabase.getInstance(this);
        ApiService apiService = MainClient.getApiService();
        SyncRepository syncRepository = new SyncRepository(this, database, apiService);
        syncRepository.startPeriodicSync();
    }
}
