package settings.subscriptios

import android.animation.ObjectAnimator
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import bottomsheets.DisclaimerBottomSheetFragment
import com.claw.ai.R
import com.claw.ai.databinding.ActivityBillingPlanSettingsBinding
import com.claw.ai.databinding.ItemUsageBarBinding
import com.claw.ai.databinding.LayoutUsageDropdownBinding
import models.User
import pricing.SubscriptionPlanSheetFragment
import viewmodels.google_login.AuthViewModel
import viewmodels.stripe_payments.SubscriptionViewModel

class BillingPlanSettings : AppCompatActivity() {

    private lateinit var binding: ActivityBillingPlanSettingsBinding
    private lateinit var authViewModel: AuthViewModel
    private lateinit var subscriptionViewModel: SubscriptionViewModel
    private var currentUser: User? = null
    private var isUsageDataLoaded = false
    private var lastSubscriptionType: String? = null

    // ADDED: Flags to prevent excessive observer triggers
    private var isInitialLoad = true
    private var isObservingUsageData = false
    private var lastUserUuid: String? = null

    private val planLimits = mapOf(
        "free" to mapOf(
            "price_alerts_limit" to 1,
            "pattern_alerts_limit" to 1,
            "watchlist_limit" to 1,
            "market_analysis_limit" to 3,
            "trendline_analysis_limit" to 3,
            "sr_analysis_limit" to 8,
            "journaling_enabled" to false,
            "video_download_limit" to 0
        ),
        "test_drive" to mapOf(
            "price_alerts_limit" to 5,
            "pattern_alerts_limit" to 2,
            "watchlist_limit" to 1,
            "market_analysis_limit" to 7,
            "trendline_analysis_limit" to 7,
            "sr_analysis_limit" to 12,
            "journaling_enabled" to false,
            "video_download_limit" to 1
        ),
        "starter_weekly" to mapOf(
            "price_alerts_limit" to -1,
            "pattern_alerts_limit" to 7,
            "watchlist_limit" to 3,
            "market_analysis_limit" to 49,
            "trendline_analysis_limit" to 49,
            "sr_analysis_limit" to 54,
            "journaling_enabled" to false,
            "video_download_limit" to 0
        ),
        "starter_monthly" to mapOf(
            "price_alerts_limit" to -1,
            "pattern_alerts_limit" to 60,
            "watchlist_limit" to 6,
            "market_analysis_limit" to 300,
            "trendline_analysis_limit" to 300,
            "sr_analysis_limit" to 305,
            "journaling_enabled" to false,
            "video_download_limit" to 0
        ),
        "pro_weekly" to mapOf(
            "price_alerts_limit" to -1,
            "pattern_alerts_limit" to -1,
            "watchlist_limit" to -1,
            "market_analysis_limit" to -1,
            "trendline_analysis_limit" to -1,
            "sr_analysis_limit" to -1,
            "journaling_enabled" to true,
            "video_download_limit" to -1
        ),
        "pro_monthly" to mapOf(
            "price_alerts_limit" to -1,
            "pattern_alerts_limit" to -1,
            "watchlist_limit" to -1,
            "market_analysis_limit" to -1,
            "trendline_analysis_limit" to -1,
            "sr_analysis_limit" to -1,
            "journaling_enabled" to true,
            "video_download_limit" to -1
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityBillingPlanSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initializeViewModels()
        setupObservers()
        setupClickListeners()

        // Force initial user data fetch if authenticated but currentUser is null
        if (authViewModel.isUserSignedIn() && currentUser == null) {
            Log.d("BillingPlanSettings", "User is signed in but currentUser is null, forcing user data refresh")
            authViewModel.refreshCurrentUser()
        }
    }

    private fun initializeViewModels() {
        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        authViewModel.initialize(this, getString(R.string.web_client_id))
        subscriptionViewModel = ViewModelProvider(this)[SubscriptionViewModel::class.java]
    }

    private fun setupObservers() {
        // FIXED: More controlled currentUser observer
        authViewModel.currentUser.observe(this) { user ->
            val userChanged = user?.uuid != lastUserUuid
            val subscriptionChanged = (user?.subscriptionType ?: "free") != lastSubscriptionType

            Log.d("BillingPlanSettings", "Current user observer triggered: ${user?.uuid ?: "null"}")
            Log.d("BillingPlanSettings", "User changed: $userChanged, Subscription changed: $subscriptionChanged")

            currentUser = user

            if (user != null) {
                lastUserUuid = user.uuid
                val currentSubscriptionType = user.subscriptionType ?: "free"

                // Only update if this is initial load, user changed, or subscription changed
                if (isInitialLoad || userChanged || subscriptionChanged) {
                    Log.d("BillingPlanSettings", "Updating usage limits - Initial: $isInitialLoad, User changed: $userChanged, Sub changed: $subscriptionChanged")
                    lastSubscriptionType = currentSubscriptionType
                    updateUsageLimits(user)
                    isInitialLoad = false
                }

                // Update UI for cancel button visibility
                updateCancelButtonVisibility(currentSubscriptionType)
            } else {
                Log.w("BillingPlanSettings", "Current user is null")
                lastUserUuid = null
                handleNullUser()
            }
        }

        authViewModel.authState.observe(this) { authState ->
            Log.d("BillingPlanSettings", "Auth state changed to: $authState")
            when (authState) {
                AuthViewModel.AuthState.AUTHENTICATED -> {
                    Log.d("BillingPlanSettings", "Auth state: AUTHENTICATED")
                    if (currentUser == null) {
                        Log.d("BillingPlanSettings", "Authenticated but no user data, attempting to fetch user")
                        fetchUserDataWhenAuthenticated()
                    }
                }
                AuthViewModel.AuthState.UNAUTHENTICATED -> {
                    Log.w("BillingPlanSettings", "Auth state: UNAUTHENTICATED")
                    Toast.makeText(this, "Please log in to continue", Toast.LENGTH_SHORT).show()
                    finish()
                }
                AuthViewModel.AuthState.ERROR -> {
                    Log.e("BillingPlanSettings", "Auth state: ERROR")
                    Toast.makeText(this, "Authentication error", Toast.LENGTH_SHORT).show()
                }
                AuthViewModel.AuthState.LOADING -> {
                    Log.d("BillingPlanSettings", "Auth state: LOADING")
                    if (!isUsageDataLoaded) {
                        binding.usageLimitsProgress.visibility = View.VISIBLE
                        binding.usageLayout.root.visibility = View.GONE
                    }
                }
            }
        }

        authViewModel.errorMessage.observe(this) { errorMessage ->
            if (!errorMessage.isNullOrEmpty()) {
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
            }
        }

        subscriptionViewModel.isSubscriptionLoading.observe(this) { isLoading ->
            Log.d("BillingPlanSettings", "Subscription loading state: $isLoading")
        }

        subscriptionViewModel.error.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            }
        }

        // FIXED: More controlled usageData observer
        authViewModel.usageData.observe(this) { usageData ->
            // Only process if we're expecting usage data (not observing already)
            if (!isObservingUsageData) {
                Log.d("BillingPlanSettings", "Ignoring usage data update - not actively requesting")
                return@observe
            }

            Log.d("BillingPlanSettings", "Processing usage data update: ${usageData != null}")
            binding.usageLimitsProgress.visibility = View.GONE
            binding.usageLayout.root.visibility = View.VISIBLE
            isUsageDataLoaded = true
            isObservingUsageData = false // Reset flag

            usageData?.let {
                hideAllUsageViews()

                var hasUnlimitedFeatures = false
                val subscriptionType = currentUser?.subscriptionType ?: "free"
                val limits = planLimits[subscriptionType] ?: planLimits["free"] ?: mapOf()

                Log.d("BillingPlanSettings", "Processing usage data for subscription: $subscriptionType")

                // Setup usage bars for each feature
                if (setupFeatureUsage("watchlist_limit", "Watchlist", usageData.watchlistUsed, limits)) {
                    hasUnlimitedFeatures = true
                }
                if (setupFeatureUsage("price_alerts_limit", "Price Alerts", usageData.priceAlertsUsed, limits)) {
                    hasUnlimitedFeatures = true
                }
                if (setupFeatureUsage("pattern_alerts_limit", "Pattern Alerts", usageData.patternDetectionUsed, limits)) {
                    hasUnlimitedFeatures = true
                }
                if (setupFeatureUsage("sr_analysis_limit", "S/R Analysis", usageData.srAnalysisUsed, limits)) {
                    hasUnlimitedFeatures = true
                }
                if (setupFeatureUsage("trendline_analysis_limit", "Trendline Analysis", usageData.trendlineAnalysisUsed, limits)) {
                    hasUnlimitedFeatures = true
                }

                if (hasUnlimitedFeatures) {
                    binding.usageLayout.unlimitedFeaturesTitle.visibility = View.VISIBLE
                }
            } ?: run {
                Log.e("BillingPlanSettings", "Usage data was null. Could not display usage.")
                Toast.makeText(this, "Could not load usage details", Toast.LENGTH_SHORT).show()
                binding.usageLayout.usageDetailsContainer.visibility = View.GONE
            }
        }
    }

    private fun fetchUserDataWhenAuthenticated() {
        authViewModel.getCurrentUserValue()?.let { user ->
            Log.d("BillingPlanSettings", "Found user in getCurrentUserValue: ${user.uuid}")
            currentUser = user
            lastUserUuid = user.uuid
            val currentSubscriptionType = user.subscriptionType ?: "free"
            if (lastSubscriptionType != currentSubscriptionType || !isUsageDataLoaded) {
                lastSubscriptionType = currentSubscriptionType
                updateUsageLimits(user)
            }
        } ?: run {
            Log.d("BillingPlanSettings", "getCurrentUserValue is null, attempting to refresh user data")
            authViewModel.refreshCurrentUser()

            if (authViewModel.isUserSignedIn()) {
                Log.w("BillingPlanSettings", "User is signed in but no user data available")
                Toast.makeText(this, "Loading user data...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleNullUser() {
        binding.root.postDelayed({
            if (currentUser == null && authViewModel.isUserSignedIn()) {
                Log.w("BillingPlanSettings", "User data still null after delay, showing error")
                Toast.makeText(this, "Unable to load user data. Please try again.", Toast.LENGTH_LONG).show()
            }
        }, 2000)
    }

    private fun setupClickListeners() {
        binding.closeButton.setOnClickListener { finish() }
        binding.changeBillingPlan.setOnClickListener { openSubscriptionPlanSheet() }
        binding.cancelBillingPlan.setOnClickListener {
            try{
                Log.d("BottomSheet", "Clicked cancel billing plan")
                showDisclaimerBottomSheet()
            }catch (e: Exception) {
                Log.e("BottomSheet", "Error showing bottom sheet", e)
            }
        }

        val usageBinding = binding.usageLayout
        usageBinding.usageHeaderLayout.setOnClickListener {
            if (usageBinding.usageDetailsContainer.visibility == View.VISIBLE) {
                usageBinding.usageDetailsContainer.visibility = View.GONE
                ObjectAnimator.ofFloat(usageBinding.usageDropdownArrow, "rotation", 90f, 0f).start()
            } else {
                usageBinding.usageDetailsContainer.visibility = View.VISIBLE
                ObjectAnimator.ofFloat(usageBinding.usageDropdownArrow, "rotation", 0f, 90f).start()

                // FIXED: More controlled usage data fetching
                when {
                    currentUser != null && isUsageDataLoaded -> {
                        Log.d("BillingPlanSettings", "Usage data already loaded, showing existing data")
                        // Data is already loaded and visible, no need to fetch again
                    }
                    currentUser != null && !isUsageDataLoaded -> {
                        Log.d("BillingPlanSettings", "Fetching usage limits for user: ${currentUser?.uuid}")
                        updateUsageLimits(currentUser!!)
                    }
                    authViewModel.isUserSignedIn() -> {
                        Log.w("BillingPlanSettings", "currentUser is null but user is signed in, attempting to fetch user data")
                        authViewModel.getCurrentUserValue()?.let { user ->
                            Log.d("BillingPlanSettings", "Retrieved user from AuthViewModel: ${user.uuid}")
                            currentUser = user
                            lastUserUuid = user.uuid
                            val currentSubscriptionType = user.subscriptionType ?: "free"
                            if (lastSubscriptionType != currentSubscriptionType || !isUsageDataLoaded) {
                                lastSubscriptionType = currentSubscriptionType
                                updateUsageLimits(user)
                            }
                        } ?: run {
                            Log.e("BillingPlanSettings", "Failed to fetch user data despite signed-in state")
                            Toast.makeText(this, "Unable to load user data. Please refresh the page.", Toast.LENGTH_LONG).show()
                            authViewModel.refreshCurrentUser()
                        }
                    }
                    else -> {
                        Log.e("BillingPlanSettings", "User is not signed in")
                        Toast.makeText(this, "Please log in to view usage details", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
        }
    }

    private fun updateUsageLimits(user: User) {
        val subscriptionType = user.subscriptionType ?: "free"
        Log.d("BillingPlanSettings", "User subscription type: $subscriptionType")

        var limits = planLimits[subscriptionType]
        if (limits == null) {
            Log.w("BillingPlanSettings", "Unknown subscription type: $subscriptionType, falling back to 'free'")
            limits = planLimits["free"] ?: mapOf()
        }

        // Only show loading spinner if usage data hasn't been loaded yet
        if (!isUsageDataLoaded) {
            binding.usageLimitsProgress.visibility = View.VISIBLE
            binding.usageLayout.root.visibility = View.GONE
        }

        // MOVED: Cancel button visibility logic to separate method
        updateCancelButtonVisibility(subscriptionType)

        Log.d("BillingPlanSettings", "Fetching usage counts for user: ${user.uuid}")
        isObservingUsageData = true // Set flag to indicate we're expecting usage data
        authViewModel.fetchUsageCounts(user.uuid, subscriptionType)
    }

    // ADDED: Separate method for cancel button visibility
    private fun updateCancelButtonVisibility(subscriptionType: String) {
        if (subscriptionType.equals("free", ignoreCase = true) || subscriptionType.equals("test_drive", ignoreCase = true)) {
            binding.cancelBillingPlan.visibility = View.GONE
            binding.cancelViewBorder.visibility = View.GONE
        } else {
            binding.cancelBillingPlan.visibility = View.VISIBLE
            binding.cancelViewBorder.visibility = View.VISIBLE
        }
    }

    private fun setupFeatureUsage(
        limitKey: String,
        featureName: String,
        used: Int,
        limits: Map<String, Any>
    ): Boolean {
        val limit = limits[limitKey] as? Int ?: 0

        return if (limit == -1) {
            when (featureName) {
                "Watchlist" -> {
                    binding.usageLayout.unlimitedWatchlist.visibility = View.VISIBLE
                }
                "Price Alerts" -> {
                    binding.usageLayout.unlimitedPriceAlerts.visibility = View.VISIBLE
                }
                "Pattern Alerts" -> {
                    binding.usageLayout.unlimitedPatternAlerts.visibility = View.VISIBLE
                }
                "S/R Analysis" -> {
                    binding.usageLayout.unlimitedSrAnalysis.visibility = View.VISIBLE
                }
                "Trendline Analysis" -> {
                    binding.usageLayout.unlimitedTrendlineAnalysis.visibility = View.VISIBLE
                }
            }
            true
        } else {
            when (featureName) {
                "Watchlist" -> {
                    binding.usageLayout.usageWatchlist.root.visibility = View.VISIBLE
                    setupUsageBar(binding.usageLayout.usageWatchlist, featureName, used, limit)
                }
                "Price Alerts" -> {
                    binding.usageLayout.usagePriceAlerts.root.visibility = View.VISIBLE
                    setupUsageBar(binding.usageLayout.usagePriceAlerts, featureName, used, limit)
                }
                "Pattern Alerts" -> {
                    binding.usageLayout.usagePatternAlerts.root.visibility = View.VISIBLE
                    setupUsageBar(binding.usageLayout.usagePatternAlerts, featureName, used, limit)
                }
                "S/R Analysis" -> {
                    binding.usageLayout.usageSrAnalysis.root.visibility = View.VISIBLE
                    setupUsageBar(binding.usageLayout.usageSrAnalysis, featureName, used, limit)
                }
                "Trendline Analysis" -> {
                    binding.usageLayout.usageTrendlineAnalysis.root.visibility = View.VISIBLE
                    setupUsageBar(binding.usageLayout.usageTrendlineAnalysis, featureName, used, limit)
                }
            }
            false
        }
    }

    private fun hideAllUsageViews() {
        val usageBinding: LayoutUsageDropdownBinding = binding.usageLayout

        // Hide all usage bars
        usageBinding.usageWatchlist.root.visibility = View.GONE
        usageBinding.usagePriceAlerts.root.visibility = View.GONE
        usageBinding.usagePatternAlerts.root.visibility = View.GONE
        usageBinding.usageSrAnalysis.root.visibility = View.GONE
        usageBinding.usageTrendlineAnalysis.root.visibility = View.GONE

        // Hide all unlimited text views and the title
        usageBinding.unlimitedFeaturesTitle.visibility = View.GONE
        usageBinding.unlimitedWatchlist.visibility = View.GONE
        usageBinding.unlimitedPriceAlerts.visibility = View.GONE
        usageBinding.unlimitedPatternAlerts.visibility = View.GONE
        usageBinding.unlimitedSrAnalysis.visibility = View.GONE
        usageBinding.unlimitedTrendlineAnalysis.visibility = View.GONE
    }

    private fun setupUsageBar(
        barBinding: ItemUsageBarBinding,
        featureName: String,
        used: Int,
        limit: Int
    ) {
        barBinding.featureName.text = featureName
        val limitText = if (limit == -1) "∞" else limit.toString()
        barBinding.usageText.text = String.format("%d/%s Used", used, limitText)

        val progressPercentage = when {
            limit == -1 || limit == 0 -> 0f
            else -> ((used.toFloat() / limit) * 100).coerceIn(0f, 100f)
        }

        val usedParams = barBinding.usedBar.layoutParams as LinearLayout.LayoutParams
        val leftParams = barBinding.leftBar.layoutParams as LinearLayout.LayoutParams

        usedParams.weight = progressPercentage
        leftParams.weight = 100 - progressPercentage

        barBinding.usedBar.layoutParams = usedParams
        barBinding.leftBar.layoutParams = leftParams
    }

    private fun openSubscriptionPlanSheet() {
        val subPage = SubscriptionPlanSheetFragment.newInstance()
        subPage.show(supportFragmentManager, subPage.tag)
    }

    private fun showDisclaimerBottomSheet() {
        try {
            if (isFinishing || isDestroyed) {
                Log.w("BottomSheet", "Activity is finishing/destroyed, cannot show bottom sheet")
                return
            }

            if (supportFragmentManager.isStateSaved) {
                Log.w("BottomSheet", "Fragment manager state is saved, cannot show bottom sheet")
                return
            }

            val bottomSheet = DisclaimerBottomSheetFragment.newInstance()
            bottomSheet.show(supportFragmentManager, "disclaimer_bottom_sheet")
            Log.d("BottomSheet", "Bottom sheet show() called successfully")
        } catch (e: Exception) {
            Log.e("BottomSheet", "Error showing bottom sheet", e)
        }
    }
}