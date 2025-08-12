package services.notification;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Vibrator;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.claw.ai.App;
import com.claw.ai.MainActivity;
import com.claw.ai.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import settings.notifications.NotificationSettings;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "MyFirebaseMessagingService";

    // Keys for determining notification type from FCM data
    private static final String NOTIFICATION_TYPE_KEY = "notification_type";
    private static final String PRICE_ALERT_TYPE = "price_alert";
    private static final String PATTERN_ALERT_TYPE = "pattern_alert";

    // Broadcast action suffixes
    private static final String REFRESH_PRICE_ALERTS_SUFFIX = ".ACTION_REFRESH_PRICE_ALERTS";
    private static final String REFRESH_PATTERN_ALERTS_SUFFIX = ".ACTION_REFRESH_PATTERN_ALERTS";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            Log.d(TAG, "Token Refreshed Successfully");
            DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference("users").child(user.getUid());
            dbRef.child("fcmToken").setValue(token);
        }
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return; // Don't proceed if user is not logged in

        String title = null;
        String body = null;
        String notificationType = null;

        // ✅ Check for notification payload (works for background or killed)
        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle();
            body = remoteMessage.getNotification().getBody();
        }

        // ✅ Check data payload for content and notification type
        if (!remoteMessage.getData().isEmpty()) {
            if (title == null) title = remoteMessage.getData().get("title");
            if (body == null) body = remoteMessage.getData().get("body");

            // Get the notification type from data payload
            notificationType = remoteMessage.getData().get(NOTIFICATION_TYPE_KEY);
        }

        // ✅ Defensive check
        if (title == null || body == null) {
            Log.w(TAG, "No title/body in message. Skipping notification.");
            return;
        }

        // Check if notifications are enabled for this type
        if (!shouldShowNotification(notificationType)) {
            Log.d(TAG, "Notifications disabled for type: " + notificationType);
            return;
        }

        Log.d(TAG, "FCM message received with title: " + title + ", body: " + body + ", type: " + notificationType);
        showNotification(title, body, notificationType);
        refreshAlertList(notificationType);
    }

    private boolean shouldShowNotification(String notificationType) {
        if (PRICE_ALERT_TYPE.equals(notificationType)) {
            return NotificationSettings.Companion.isPriceAlertsEnabled(this);
        } else if (PATTERN_ALERT_TYPE.equals(notificationType)) {
            return NotificationSettings.Companion.isPatternAlertsEnabled(this);
        }
        // Default to true for unknown types
        return true;
    }

    private void showNotification(String title, String body, String notificationType) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Determine which channel to use based on notification type
        String channelId = determineChannelId(notificationType);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notification) // Use your app's notification icon
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        // Apply sound settings
        if (NotificationSettings.Companion.isNotificationSoundEnabled(this)) {
            Uri soundUri = getSoundForNotificationType(notificationType);
            if (soundUri != null) {
                builder.setSound(soundUri);
            }
        } else {
            builder.setSound(null);
        }

        // Apply vibration settings
        if (NotificationSettings.Companion.isVibrationEnabled(this)) {
            builder.setVibrate(new long[]{0, 300, 200, 300}); // Custom vibration pattern
        } else {
            builder.setVibrate(null);
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // Add extra data to intent if needed for navigation
        if (notificationType != null) {
            intent.putExtra("notification_type", notificationType);
            Log.d(TAG, "Notification type added to intent: " + notificationType);
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        builder.setContentIntent(pendingIntent);

        // Show notification with vibration if enabled
        if (NotificationSettings.Companion.isVibrationEnabled(this)) {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(new long[]{0, 300, 200, 300}, -1);
            }
        }

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }

    /**
     * Get the appropriate sound URI for the notification type
     */
    private Uri getSoundForNotificationType(String notificationType) {
        if (PATTERN_ALERT_TYPE.equals(notificationType)) {
            return Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.price_alert_sample_two);
        } else if (PRICE_ALERT_TYPE.equals(notificationType)) {
            return Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.default_notification);
        } else {
            // Default sound
            return Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.default_notification);
        }
    }

    /**
     * Determines which notification channel to use based on the notification type
     */
    private String determineChannelId(String notificationType) {
        if (PATTERN_ALERT_TYPE.equals(notificationType)) {
            return App.PATTERN_ALERTS_CHANNEL_ID;
        } else if (PRICE_ALERT_TYPE.equals(notificationType)) {
            return App.PRICE_ALERTS_CHANNEL_ID;
        } else {
            // Default to price alerts channel if type is not specified or unknown
            Log.w(TAG, "Unknown or missing notification type: " + notificationType + ". Using price alerts channel as default.");
            return App.PRICE_ALERTS_CHANNEL_ID;
        }
    }

    /**
     * Send specific broadcast based on notification type
     */
    private void refreshAlertList(String notificationType) {
        String action;

        if (PATTERN_ALERT_TYPE.equals(notificationType)) {
            action = getPackageName() + REFRESH_PATTERN_ALERTS_SUFFIX;
        } else if (PRICE_ALERT_TYPE.equals(notificationType)) {
            action = getPackageName() + REFRESH_PRICE_ALERTS_SUFFIX;
        } else {
            // Send both broadcasts if type is unknown
            Intent priceIntent = new Intent(getPackageName() + REFRESH_PRICE_ALERTS_SUFFIX);
            sendBroadcast(priceIntent);

            Intent patternIntent = new Intent(getPackageName() + REFRESH_PATTERN_ALERTS_SUFFIX);
            sendBroadcast(patternIntent);
            return;
        }

        Intent intent = new Intent(action);
        sendBroadcast(intent);
        Log.d(TAG, "Broadcast sent: " + action);
    }
}