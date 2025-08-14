package settings.about

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.claw.ai.R
import com.claw.ai.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initView()
        onClicks()
    }

    private fun initView() {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }

            val versionName = packageInfo.versionName ?: "Unknown"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }

            binding.appBuildVersionCode.text = "Version $versionName build $versionCode"

        } catch (e: PackageManager.NameNotFoundException) {
            // Handle the case where package is not found
            binding.appBuildVersionCode.text = "Version info unavailable"
        }
    }

    private fun onClicks() {
        binding.closeButton.setOnClickListener{ finish() }
        binding.requirementsTile.setOnClickListener{ openRequirementsPage() }
        binding.licensesTile.setOnClickListener{ openLicensesPage() }
    }

    private fun openLicensesPage() {
        val intent = Intent(this, LicensesActivity::class.java)
        startActivity(intent)
    }

    private fun openRequirementsPage() {
        val intent = Intent(this, RequirementsActivity::class.java)
        startActivity(intent)
    }
}