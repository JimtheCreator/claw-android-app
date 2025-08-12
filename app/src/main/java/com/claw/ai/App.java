package com.claw.ai;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.net.Uri;

import com.google.firebase.FirebaseApp;
import com.stripe.android.PaymentConfiguration;

import backend.ApiService;
import backend.MainClient;
import database.roomDB.AppDatabase;
import repositories.SyncRepository;
import settings.notifications.NotificationSettings;

public class App extends Application {

    public static final String PRICE_ALERTS_CHANNEL_ID = "price_alerts_channel";
    public static final String PATTERN_ALERTS_CHANNEL_ID = "pattern_alerts_channel";

    public void onCreate() {
        super.onCreate();
        // Retrieve the key from strings.xml
        String stripePublishableKey = getApplicationContext().getString(R.string.TEST_STRIPE_PUBLISHABLE_KEY);

        // Initialize the Stripe SDK with the key
        PaymentConfiguration.init(
                getApplicationContext(),
                stripePublishableKey
        );

        FirebaseApp.initializeApp(this);

        createNotificationChannels();

        // Initialize periodic sync
        initializePeriodicSync();
    }

    private void createNotificationChannels() {
        createPriceAlertChannel();
        createPatternAlertChannel();
    }

    private void createPatternAlertChannel() {
        CharSequence name = "Pattern Alerts";
        String description = "Get notified on symbol pattern alerts.";
        int importance = NotificationManager.IMPORTANCE_HIGH;

        NotificationChannel channel = new NotificationChannel(PATTERN_ALERTS_CHANNEL_ID, name, importance);
        channel.setDescription(description);

        // Check if sound is enabled in settings
        if (NotificationSettings.Companion.isNotificationSoundEnabled(this)) {
            // Set the custom sound for the channel
            Uri soundUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.price_alert_sample_two);
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();
            channel.setSound(soundUri, audioAttributes);
        } else {
            channel.setSound(null, null);
        }

        // Check if vibration is enabled in settings
        if (NotificationSettings.Companion.isVibrationEnabled(this)) {
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 300, 200, 300});
        } else {
            channel.enableVibration(false);
        }

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void createPriceAlertChannel() {
        CharSequence name = "Price Alerts";
        String description = "Notifications for symbol price alerts.";
        int importance = NotificationManager.IMPORTANCE_HIGH;

        NotificationChannel channel = new NotificationChannel(PRICE_ALERTS_CHANNEL_ID, name, importance);
        channel.setDescription(description);

        // Check if sound is enabled in settings
        if (NotificationSettings.Companion.isNotificationSoundEnabled(this)) {
            // Set the custom sound for the channel
            Uri soundUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.default_notification);
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();
            channel.setSound(soundUri, audioAttributes);
        } else {
            channel.setSound(null, null);
        }

        // Check if vibration is enabled in settings
        if (NotificationSettings.Companion.isVibrationEnabled(this)) {
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 300, 200, 300});
        } else {
            channel.enableVibration(false);
        }

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

    /**
     * Method to recreate notification channels when settings change
     * Call this from NotificationSettings activity when user changes preferences
     */
    public static void recreateNotificationChannels(Context context) {
        App app = (App) context.getApplicationContext();

        // Delete existing channels
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.deleteNotificationChannel(PRICE_ALERTS_CHANNEL_ID);
            notificationManager.deleteNotificationChannel(PATTERN_ALERTS_CHANNEL_ID);
        }

        // Recreate channels with new settings
        app.createNotificationChannels();
    }
}
