package utils

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.claw.ai.App
import settings.notifications.NotificationSettings


/**
 * Utility class for handling notification-related operations
 */
object NotificationUtils {
    /**
     * Check if notifications are enabled at the system level
     */
    fun areNotificationsEnabled(context: Context): Boolean {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        return notificationManager.areNotificationsEnabled()
    }

    /**
     * Check if a specific notification channel is enabled
     */
    fun isChannelEnabled(context: Context, channelId: String?): Boolean {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel =
            notificationManager.getNotificationChannel(channelId)
        return channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /**
     * Open notification settings for the app
     */
    fun openNotificationSettings(context: Context) {
        val intent = Intent()

        intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

        context.startActivity(intent)
    }

    /**
     * Open notification channel settings
     */
    fun openChannelSettings(context: Context, channelId: String?) {
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        intent.putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
        context.startActivity(intent)
    }

    /**
     * Check if price alerts should be shown
     */
    fun shouldShowPriceAlert(context: Context): Boolean {
        return areNotificationsEnabled(context) &&
                NotificationSettings.isPriceAlertsEnabled(context) &&
                isChannelEnabled(context, App.PRICE_ALERTS_CHANNEL_ID)
    }

    /**
     * Check if pattern alerts should be shown
     */
    fun shouldShowPatternAlert(context: Context): Boolean {
        return areNotificationsEnabled(context) &&
                NotificationSettings.isPatternAlertsEnabled(context) &&
                isChannelEnabled(context, App.PATTERN_ALERTS_CHANNEL_ID)
    }

    /**
     * Get notification status summary for debugging
     */
    fun getNotificationStatusSummary(context: Context): String {
        val status = StringBuilder()
        status.append("Notifications Enabled: ").append(areNotificationsEnabled(context))
            .append("\n")
        status.append("Price Alerts App Setting: ")
            .append(NotificationSettings.isPriceAlertsEnabled(context)).append("\n")
        status.append("Pattern Alerts App Setting: ")
            .append(NotificationSettings.isPatternAlertsEnabled(context)).append("\n")
        status.append("Sound Enabled: ")
            .append(NotificationSettings.isNotificationSoundEnabled(context)).append("\n")
        status.append("Vibration Enabled: ")
            .append(NotificationSettings.isVibrationEnabled(context)).append("\n")
        status.append("Price Channel Enabled: ")
            .append(isChannelEnabled(context, App.PRICE_ALERTS_CHANNEL_ID)).append("\n")
        status.append("Pattern Channel Enabled: ")
            .append(isChannelEnabled(context, App.PATTERN_ALERTS_CHANNEL_ID)).append("\n")

        return status.toString()
    }

    /**
     * Update notification channels when settings change
     */
    fun updateNotificationChannels(context: Context?) {
        App.recreateNotificationChannels(context)
    }
}