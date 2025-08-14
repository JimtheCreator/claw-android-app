package settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import bottomsheets.showPolicyBottomSheet
import com.claw.ai.R
import com.claw.ai.databinding.ActivitySettingsBinding
import models.PolicyType
import settings.charts.ChartsTileActivity
import settings.notifications.NotificationSettings
import settings.subscriptios.BillingPlanSettings

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.onClicks(
            finishActivity = { finish() }
        )
    }

    private fun ActivitySettingsBinding.onClicks(
        finishActivity: () -> Unit
    ) {
        closeButton.setOnClickListener { finishActivity() }
        billingTile.setOnClickListener{
            val intent = Intent(this@SettingsActivity, BillingPlanSettings::class.java)
            startActivity(intent)
        }
        pushNotificationTile.setOnClickListener{
            val intent = Intent(this@SettingsActivity, NotificationSettings::class.java)
            startActivity(intent)
        }
        termsOfService.setOnClickListener{
            showPolicyBottomSheet(PolicyType.TERMS_OF_SERVICE)
        }
        privacyPolicy.setOnClickListener{
            showPolicyBottomSheet(PolicyType.PRIVACY_POLICY)
        }
        billingPolicy.setOnClickListener{
            showPolicyBottomSheet(PolicyType.BILLING_POLICY)
        }
    }
}