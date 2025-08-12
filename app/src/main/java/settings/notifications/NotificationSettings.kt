package settings.notifications


import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.claw.ai.App
import com.claw.ai.R
import com.claw.ai.databinding.ActivityNotificationSettingsBinding

class NotificationSettings : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationSettingsBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var notificationManager: NotificationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityNotificationSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initializeComponents()
        loadSavedPreferences()
        setupSwitchListeners()
        onClicks()
    }

    private fun initializeComponents() {
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private fun loadSavedPreferences() {
        // Load saved preferences and set switch states
        binding.priceAlertsSwitch.isChecked =
            sharedPreferences.getBoolean(PRICE_ALERTS_ENABLED, true)
        binding.patternAlertsSwitch.isChecked =
            sharedPreferences.getBoolean(PATTERN_ALERTS_ENABLED, true)
        binding.vibrationSwitch.isChecked = sharedPreferences.getBoolean(VIBRATION_ENABLED, true)
    }

    private fun setupSwitchListeners() {
        // Price Alerts Switch
        binding.priceAlertsSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveBooleanPreference(PRICE_ALERTS_ENABLED, isChecked)
            updateNotificationChannelSettings(App.PRICE_ALERTS_CHANNEL_ID, isChecked)
        }

        // Pattern Alerts Switch
        binding.patternAlertsSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveBooleanPreference(PATTERN_ALERTS_ENABLED, isChecked)
            updateNotificationChannelSettings(App.PATTERN_ALERTS_CHANNEL_ID, isChecked)
        }

        // Vibration Switch
        binding.vibrationSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveBooleanPreference(VIBRATION_ENABLED, isChecked)
            updateVibrationSettings(isChecked)
        }

        // Make the entire RelativeLayout clickable to toggle switches
        binding.priceAlertsButton.setOnClickListener {
            binding.priceAlertsSwitch.toggle()
        }

        binding.patternAlertsButton.setOnClickListener {
            binding.patternAlertsSwitch.toggle()
        }

//        binding.notificationSoundButton.setOnClickListener {
//            binding.notificationSoundSwitch.toggle()
//        }

        binding.vibrationButton.setOnClickListener {
            binding.vibrationSwitch.toggle()
        }
    }

    private fun saveBooleanPreference(key: String, value: Boolean) {
        sharedPreferences.edit().putBoolean(key, value).apply()
    }

    private fun updateNotificationChannelSettings(channelId: String, enabled: Boolean) {
        val channel = notificationManager.getNotificationChannel(channelId)
        channel?.let {
            // Note: You cannot programmatically enable/disable channels in Android O+
            // The user must do this in system settings
            // This method is here for future compatibility or custom handling

            // You can redirect users to channel settings if needed
            // val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            // intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            // intent.putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            // startActivity(intent)
        }
    }

    private fun updateSoundSettings(enabled: Boolean) {
        // Recreate notification channels with new sound settings
        recreateNotificationChannels()
    }

    private fun updateVibrationSettings(enabled: Boolean) {
        // Recreate notification channels with new vibration settings
        recreateNotificationChannels()
    }

    private fun recreateNotificationChannels() {
        // Delete existing channels
        notificationManager.deleteNotificationChannel(App.PRICE_ALERTS_CHANNEL_ID)
        notificationManager.deleteNotificationChannel(App.PATTERN_ALERTS_CHANNEL_ID)

        // Recreate channels with current settings
        App.recreateNotificationChannels(this)
    }

    private fun onClicks() {
        binding.closeButton.setOnClickListener { finish() }
    }

    companion object {
        // Preference keys
        const val PREFS_NAME = "notification_settings"
        const val PRICE_ALERTS_ENABLED = "price_alerts_enabled"
        const val PATTERN_ALERTS_ENABLED = "pattern_alerts_enabled"
        const val NOTIFICATION_SOUND_ENABLED = "notification_sound_enabled"
        const val VIBRATION_ENABLED = "vibration_enabled"

        // Static methods to check preferences from other classes
        fun isPriceAlertsEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(PRICE_ALERTS_ENABLED, true)
        }

        fun isPatternAlertsEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(PATTERN_ALERTS_ENABLED, true)
        }

        fun isNotificationSoundEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(NOTIFICATION_SOUND_ENABLED, true)
        }

        fun isVibrationEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(VIBRATION_ENABLED, true)
        }
    }
}