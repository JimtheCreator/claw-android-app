package fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.claw.ai.R;
import com.claw.ai.databinding.FragmentMoreTabBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

import accounts.SignUpBottomSheet;
import bottomsheets.DisclaimerBottomSheetFragment;
import firebase_manager.FirebaseAuthManager;
import models.CancellationResponseSchema;
import models.User;
import pricing.OnboardingPricingPageSheetFragment;
import pricing.SubscriptionPlanSheetFragment;
import viewmodels.google_login.AuthViewModel;
import viewmodels.stripe_payments.SubscriptionViewModel;

public class MoreTabFragment extends Fragment {
    private FragmentMoreTabBinding binding;
    private AuthViewModel authViewModel;
    private SubscriptionViewModel subscriptionViewModel;
    private User currentUser;

    private static final Map<String, Map<String, Object>> PLAN_LIMITS = new HashMap<>() {{
        put("free", new HashMap<>() {{
            put("price_alerts_limit", 1);
            put("pattern_detection_limit", 1);
            put("watchlist_limit", 1);
            put("market_analysis_limit", 3);
            put("journaling_enabled", false);
            put("video_download_limit", 0);
        }});
        put("test_drive", new HashMap<>() {{
            put("price_alerts_limit", 5);
            put("pattern_detection_limit", 2);
            put("watchlist_limit", 1);
            put("market_analysis_limit", 7);
            put("journaling_enabled", false);
            put("video_download_limit", 1);
        }});
        put("starter_weekly", new HashMap<>() {{
            put("price_alerts_limit", -1);
            put("pattern_detection_limit", 7);
            put("watchlist_limit", 3);
            put("market_analysis_limit", 49);
            put("journaling_enabled", false);
            put("video_download_limit", 0);
        }});
        put("starter_monthly", new HashMap<>() {{
            put("price_alerts_limit", -1);
            put("pattern_detection_limit", 60);
            put("watchlist_limit", 6);
            put("market_analysis_limit", 300);
            put("journaling_enabled", false);
            put("video_download_limit", 0);
        }});
        put("pro_weekly", new HashMap<>() {{
            put("price_alerts_limit", -1);
            put("pattern_detection_limit", -1);
            put("watchlist_limit", -1);
            put("market_analysis_limit", -1);
            put("journaling_enabled", true);
            put("video_download_limit", -1);
        }});
        put("pro_monthly", new HashMap<>() {{
            put("price_alerts_limit", -1);
            put("pattern_detection_limit", -1);
            put("watchlist_limit", -1);
            put("market_analysis_limit", -1);
            put("journaling_enabled", true);
            put("video_download_limit", -1);
        }});
    }};

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentMoreTabBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        authViewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        authViewModel.initialize(requireActivity(), getString(R.string.web_client_id));
        subscriptionViewModel = new ViewModelProvider(this).get(SubscriptionViewModel.class);
        setupObservers();
        initializeViews();
        setupClickListeners();
    }

    private void setupObservers() {
        authViewModel.getAuthState().observe(getViewLifecycleOwner(), authState -> {
            AuthViewModel.LoadingContext context = authViewModel.getLoadingContext().getValue();
            if (authState == AuthViewModel.AuthState.LOADING) {
                // Using the loading logic from your provided snippet
                showLoadingState(context != AuthViewModel.LoadingContext.BOTTOM_SHEET_SIGN_UP);
            } else {
                showLoadingState(false);
                switch (authState) {
                    case AUTHENTICATED:
                        if (isAdded() && !isStateSaved()) {
                            Log.d("MoreTabFragment", "User is authenticated (from AuthState observer).");
                            // Attempt to get the user directly from the ViewModel's LiveData current value
                            User authenticatedUser = authViewModel.getCurrentUser().getValue();

                            if (authenticatedUser != null) {
                                this.currentUser = authenticatedUser; // Update the fragment's currentUser
                                Log.d("MoreTabFragment", "User data retrieved from ViewModel: " + this.currentUser.getUuid() + ". Showing profile and updating limits.");

                                showProfilePage();
                                updateUsageLimits(this.currentUser);

                                Boolean isNew = authViewModel.getIsNewUser().getValue(); //
                                if (isNew != null && isNew) {
                                    openOnboardingPricingPage();
                                }
                            } else {
                                // This case means AuthState is AUTHENTICATED, but getCurrentUser().getValue() is null.
                                // This could be a timing issue where currentUser LiveData in AuthViewModel hasn't been updated yet,
                                // or the user data is genuinely not available.
                                Log.w("MoreTabFragment", "User is authenticated, but user data is currently null in ViewModel. Usage limits might not be updated immediately by this block.");
                                // The authViewModel.getCurrentUser().observe block below might still pick it up if it fires later.
                            }
                        }
                        break;
                    case UNAUTHENTICATED:
                        showLoginPage();
                        break;
                    case ERROR:
                        if (!authViewModel.isUserSignedIn()) { //
                            showLoginPage();
                        }
                        break;
                }
            }
        });

        authViewModel.getErrorMessage().observe(getViewLifecycleOwner(), errorMessage -> { //
            if (errorMessage != null && !errorMessage.isEmpty()) {
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
            }
        });

        subscriptionViewModel.isSubscriptionLoading.observe(getViewLifecycleOwner(), isLoading -> { //
            binding.profilePage.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.profilePage.cancelSubscriptionLayout.setEnabled(!isLoading);
        });

        subscriptionViewModel.cancellationResultEvent.observe(getViewLifecycleOwner(), event -> { //
            CancellationResponseSchema response = event.getContentIfNotHandled();
            if (response != null) {
                Toast.makeText(requireContext(), response.isSuccess() ? response.getMessage() : "Cancellation failed: " + response.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        subscriptionViewModel.error.observe(getViewLifecycleOwner(), error -> { //
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateUsageLimits(User user) {
        if (user == null){
            Log.d("MoreTabFragment", "User is null in updateUsageLimits.");

            FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
            FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();

            if (firebaseUser == null) return;
            FirebaseDatabase.getInstance().getReference().child("users")
                    .child(firebaseUser.getUid()).addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    User currentUser = snapshot.getValue(User.class);
                    if (currentUser == null) return;
                    String subscriptionType = currentUser.getSubscriptionType();
                    Map<String, Object> limits = PLAN_LIMITS.get(subscriptionType);
                    if (limits == null) {
                        Toast.makeText(requireContext(), "Unknown subscription type", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    binding.profilePage.usageLimitsProgress.setVisibility(View.VISIBLE);
                    binding.profilePage.usageLimitsList.setVisibility(View.GONE);

                    authViewModel.fetchUsageCounts(currentUser.getUuid(), subscriptionType);

                    authViewModel.getUsageData().observe(getViewLifecycleOwner(), usageData -> {
                        binding.profilePage.usageLimitsProgress.setVisibility(View.GONE);
                        binding.profilePage.usageLimitsList.setVisibility(View.VISIBLE);

                        int priceAlertsLimit = (int) limits.get("price_alerts_limit");
                        if (priceAlertsLimit == -1) {
                            binding.profilePage.priceAlertsText.setText("Price Alerts: Unlimited");
                        } else {
                            binding.profilePage.priceAlertsText.setText("Price Alerts: " + usageData.getPriceAlertsUsed() + "/" + priceAlertsLimit);
                        }

                        int patternDetectionLimit = (int) limits.get("pattern_detection_limit");
                        if (patternDetectionLimit == -1) {
                            binding.profilePage.patternDetectionText.setText("Pattern Detections: Unlimited");
                        } else {
                            binding.profilePage.patternDetectionText.setText("Pattern Detections: " + usageData.getPatternDetectionUsed() + "/" + patternDetectionLimit);
                        }

                        int watchlistLimit = (int) limits.get("watchlist_limit");
                        if (watchlistLimit == -1) {
                            binding.profilePage.watchlistText.setText("Watchlist: Unlimited");
                        } else {
                            binding.profilePage.watchlistText.setText("Watchlist: " + usageData.getWatchlistUsed() + "/" + watchlistLimit);
                        }

                        int marketAnalysisLimit = (int) limits.get("market_analysis_limit");
                        if (marketAnalysisLimit == -1) {
                            binding.profilePage.marketAnalysisText.setText("Market Analysis: Unlimited");
                        } else {
                            binding.profilePage.marketAnalysisText.setText("Market Analysis: " + usageData.getMarketAnalysisUsed() + "/" + marketAnalysisLimit);
                        }

                        boolean journalingEnabled = (boolean) limits.get("journaling_enabled");
                        binding.profilePage.journalingText.setText("Journaling: " + (journalingEnabled ? "Enabled" : "Disabled"));

                        int videoDownloadLimit = (int) limits.get("video_download_limit");
                        if (videoDownloadLimit == -1) {
                            binding.profilePage.videoDownloadsText.setText("Video Downloads: Unlimited");
                        } else if (videoDownloadLimit == 0) {
                            binding.profilePage.videoDownloadsText.setText("Video Downloads: Not Available");
                        } else {
                            binding.profilePage.videoDownloadsText.setText("Video Downloads: " + usageData.getVideoDownloadsUsed() + "/" + videoDownloadLimit);
                        }
                    });
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {

                }
            });

           return;
        }

        Log.d("MoreTabFragment", "User is not null in updateUsageLimits.");

        String subscriptionType = user.getSubscriptionType();
        Map<String, Object> limits = PLAN_LIMITS.get(subscriptionType);
        if (limits == null) {
            Toast.makeText(requireContext(), "Unknown subscription type", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.profilePage.usageLimitsProgress.setVisibility(View.VISIBLE);
        binding.profilePage.usageLimitsList.setVisibility(View.GONE);

        authViewModel.fetchUsageCounts(user.getUuid(), subscriptionType);

        authViewModel.getUsageData().observe(getViewLifecycleOwner(), usageData -> {
            binding.profilePage.usageLimitsProgress.setVisibility(View.GONE);
            binding.profilePage.usageLimitsList.setVisibility(View.VISIBLE);

            int priceAlertsLimit = (int) limits.get("price_alerts_limit");
            if (priceAlertsLimit == -1) {
                binding.profilePage.priceAlertsText.setText("Price Alerts: Unlimited");
            } else {
                binding.profilePage.priceAlertsText.setText("Price Alerts: " + usageData.getPriceAlertsUsed() + "/" + priceAlertsLimit);
            }

            int patternDetectionLimit = (int) limits.get("pattern_detection_limit");
            if (patternDetectionLimit == -1) {
                binding.profilePage.patternDetectionText.setText("Pattern Detections: Unlimited");
            } else {
                binding.profilePage.patternDetectionText.setText("Pattern Detections: " + usageData.getPatternDetectionUsed() + "/" + patternDetectionLimit);
            }

            int watchlistLimit = (int) limits.get("watchlist_limit");
            if (watchlistLimit == -1) {
                binding.profilePage.watchlistText.setText("Watchlist: Unlimited");
            } else {
                binding.profilePage.watchlistText.setText("Watchlist: " + usageData.getWatchlistUsed() + "/" + watchlistLimit);
            }

            int marketAnalysisLimit = (int) limits.get("market_analysis_limit");
            if (marketAnalysisLimit == -1) {
                binding.profilePage.marketAnalysisText.setText("Market Analysis: Unlimited");
            } else {
                binding.profilePage.marketAnalysisText.setText("Market Analysis: " + usageData.getMarketAnalysisUsed() + "/" + marketAnalysisLimit);
            }

            boolean journalingEnabled = (boolean) limits.get("journaling_enabled");
            binding.profilePage.journalingText.setText("Journaling: " + (journalingEnabled ? "Enabled" : "Disabled"));

            int videoDownloadLimit = (int) limits.get("video_download_limit");
            if (videoDownloadLimit == -1) {
                binding.profilePage.videoDownloadsText.setText("Video Downloads: Unlimited");
            } else if (videoDownloadLimit == 0) {
                binding.profilePage.videoDownloadsText.setText("Video Downloads: Not Available");
            } else {
                binding.profilePage.videoDownloadsText.setText("Video Downloads: " + usageData.getVideoDownloadsUsed() + "/" + videoDownloadLimit);
            }
        });

    }

    private void showLoadingState(boolean isLoading) {
        if (binding == null) return;
        if (isLoading) {
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.loginPage.getRoot().setVisibility(View.GONE);
            binding.profilePage.getRoot().setVisibility(View.GONE);
        } else {
            binding.progressBar.setVisibility(View.GONE);
        }
    }

    private void showDisclaimerBottomSheet() {
        DisclaimerBottomSheetFragment bottomSheet = DisclaimerBottomSheetFragment.newInstance();
        bottomSheet.setOnConfirmListener(() -> {
            subscriptionViewModel.cancelSubscription(true);
            bottomSheet.dismiss();
        });
        bottomSheet.show(getParentFragmentManager(), "DisclaimerBottomSheet");
    }

    private void openOnboardingPricingPage() {
        OnboardingPricingPageSheetFragment fragment = OnboardingPricingPageSheetFragment.newInstance();
        fragment.show(getParentFragmentManager(), fragment.getTag());
    }

    private void showLoginPage() {
        if (binding == null) return;
        binding.loginPage.getRoot().setVisibility(View.VISIBLE);
        binding.profilePage.getRoot().setVisibility(View.GONE);
    }

    private void showProfilePage() {
        if (binding == null) return;
        binding.loginPage.getRoot().setVisibility(View.GONE);
        binding.profilePage.getRoot().setVisibility(View.VISIBLE);
    }

    private void setupClickListeners() {
        binding.loginPage.signInWithGoogle.setOnClickListener(v -> signInWithGoogle());
        binding.loginPage.signup.setOnClickListener(v -> openSignUpBottomSheet());
        binding.profilePage.signOut.setOnClickListener(v -> signOut());
        binding.profilePage.subscribe.setOnClickListener(v -> testingPayment());
        binding.profilePage.cancelSubscriptionLayout.setOnClickListener(v -> showDisclaimerBottomSheet());
    }

    private void testingPayment() {
        SubscriptionPlanSheetFragment sub_page = SubscriptionPlanSheetFragment.newInstance();
        sub_page.show(getParentFragmentManager(), sub_page.getTag());
    }

    private void signInWithGoogle() {
        authViewModel.signInWithGoogleFromGeneral(this);
    }

    private void openSignUpBottomSheet() {
        SignUpBottomSheet signUpBottomSheet = SignUpBottomSheet.newInstance();
        signUpBottomSheet.show(getParentFragmentManager(), signUpBottomSheet.getTag());
    }

    private void signOut() {
        if (getActivity() != null) {
            authViewModel.signOut(requireActivity());
        }
    }

    private void initializeViews() {
        if (authViewModel.isUserSignedIn()) {
            showProfilePage();
            updateUsageLimits(authViewModel.getCurrentUser().getValue());
        } else {
            showLoginPage();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}