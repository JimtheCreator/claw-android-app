package viewmodels.google_login;

import android.app.Activity;
import android.util.Log;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import java.util.HashMap;
import java.util.Map;

import firebase_manager.FirebaseAuthManager;
import models.UsageData;
import models.User;
import repositories.plan_usage_limits.SupabaseRepository;

public class AuthViewModel extends ViewModel {
    private final FirebaseAuthManager firebaseAuthManager;
    private final MutableLiveData<AuthState> authState = new MutableLiveData<>(AuthState.UNAUTHENTICATED);
    private final MutableLiveData<User> currentUser = new MutableLiveData<>();
    private static final String TAG = "AuthViewModel";
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<UsageData> usageData = new MutableLiveData<>();
    private String googleWebClientIdCache;
    private SupabaseRepository supabaseRepository;

    private final MutableLiveData<Boolean> isNewUser = new MutableLiveData<>(false);

    public LiveData<Boolean> getIsNewUser() {
        return isNewUser;
    }

    public enum AuthState {
        LOADING,
        AUTHENTICATED,
        UNAUTHENTICATED,
        ERROR
    }

    public enum LoadingContext {
        NONE,
        GENERAL_SIGN_IN,
        BOTTOM_SHEET_SIGN_UP
    }

    private final MutableLiveData<LoadingContext> loadingContext = new MutableLiveData<>(LoadingContext.NONE);

    public AuthViewModel() {
        firebaseAuthManager = new FirebaseAuthManager();
        setupAuthCallbacks();
    }

    public void initialize(Activity activity, String googleWebClientId) {
        this.googleWebClientIdCache = googleWebClientId;
        firebaseAuthManager.initialize(activity, googleWebClientId);
        if (firebaseAuthManager.isUserSignedIn()) {
            authState.setValue(AuthState.AUTHENTICATED);
            // Ensure repository exists even if user was already signed in
            if (supabaseRepository == null) {
                supabaseRepository = new SupabaseRepository();
            }
            currentUser.setValue(firebaseAuthManager.getUserData());
        }
    }

    private void setupAuthCallbacks() {
        firebaseAuthManager.setAuthCallback(new FirebaseAuthManager.AuthCallback() {
            @Override
            public void onAuthInProgress() {
                authState.postValue(AuthState.LOADING); // This will trigger spinners
            }

            @Override
            public void onAuthSuccess(User user, boolean newUser) {
                currentUser.postValue(user);
                isNewUser.postValue(FirebaseAuthManager.wasUserNewlyCreated());
                authState.postValue(AuthState.AUTHENTICATED);
                loadingContext.postValue(LoadingContext.NONE); // Reset context
                supabaseRepository = new SupabaseRepository(); // Already in your code
            }

            @Override
            public void onAuthFailed(String errorMsg) {
                errorMessage.postValue(errorMsg);
                authState.postValue(AuthState.ERROR);
                loadingContext.postValue(LoadingContext.NONE); // Reset context
            }
        });
    }

    public LiveData<UsageData> getUsageData() {
        return usageData;
    }

    public LiveData<User> getCurrentUser() {
        return currentUser;
    }

    public LiveData<AuthState> getAuthState() {
        return authState;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<LoadingContext> getLoadingContext() {
        return loadingContext;
    }

    public boolean isUserSignedIn() {
        return firebaseAuthManager.isUserSignedIn();
    }

    public void signInWithGoogleFromBottomSheet(Fragment fragment) {
        if (fragment.isAdded() && fragment.getActivity() != null) {
            loadingContext.setValue(LoadingContext.BOTTOM_SHEET_SIGN_UP);
            // AuthState.LOADING will be set by the onAuthInProgress callback from FirebaseAuthManager
            firebaseAuthManager.startGoogleSignIn(fragment.getActivity());
        } else {
            errorMessage.setValue("Fragment not attached to a valid activity.");
            authState.setValue(AuthState.ERROR);
            loadingContext.setValue(LoadingContext.NONE);
        }
    }

    public void signInWithGoogleFromGeneral(Fragment fragment) {
        if (fragment.isAdded() && fragment.getActivity() != null) {
            loadingContext.setValue(LoadingContext.GENERAL_SIGN_IN);
            authState.setValue(AuthState.LOADING); // General sign-in can show immediate loading
            firebaseAuthManager.startGoogleSignIn(fragment.getActivity());
        } else {
            errorMessage.setValue("Fragment not attached to a valid activity.");
            authState.setValue(AuthState.ERROR);
            loadingContext.setValue(LoadingContext.NONE);
        }
    }

    public void signOut(Activity activity) {
        loadingContext.setValue(LoadingContext.GENERAL_SIGN_IN);
        authState.setValue(AuthState.LOADING);
        firebaseAuthManager.signOut(activity, task -> {
            authState.postValue(AuthState.UNAUTHENTICATED);
            currentUser.postValue(null);
            loadingContext.postValue(LoadingContext.NONE);
        });
    }

    public void cancelAuthentication() {
        // Only reset if we're in a loading state
        if (authState.getValue() == AuthState.LOADING) {
            // Reset the state
            loadingContext.setValue(LoadingContext.NONE);
            authState.setValue(AuthState.UNAUTHENTICATED);
            // Call the Firebase auth manager to cancel if possible
            firebaseAuthManager.cancelAuthentication();
        }
    }

    public void fetchUsageCounts(String userId, String subscriptionType) {
        Log.d(TAG, "Method called");
        if ("free".equals(subscriptionType)) {
            Log.d(TAG, "Fetching free tier usage counts");
            // Handle free tier locally
            UsageData data = new UsageData();
            data.setPriceAlertsUsed(0);
            data.setPatternDetectionUsed(0);
            data.setWatchlistUsed(0);
            data.setMarketAnalysisUsed(0);
            data.setVideoDownloadsUsed(0);
            usageData.setValue(data);
        } else {
            Log.d(TAG, "Fetching paid tier usage counts");
            if (supabaseRepository == null) {
                supabaseRepository = new SupabaseRepository();
                Log.d(TAG, "SupabaseRepository initialized in fetchUsageCounts");
            }

            // Fetch from Supabase via API
            LiveData<UsageData> repoResponseLiveData = supabaseRepository.getSubscriptionLimits(userId); //

            if (repoResponseLiveData == null) return;
            // Observe the LiveData returned by the repository.
            // Since the LiveData from getSubscriptionLimits is new for each call and emits once,
            // this observer will update usageData and then remove itself.
            repoResponseLiveData.observeForever(new Observer<>() {
                @Override
                public void onChanged(UsageData dataFromRepo) {
                    Log.d(TAG, "Fetching...");
                    usageData.setValue(dataFromRepo);
                    // Clean up the observer from this specific LiveData instance
                    // to prevent potential leaks or multiple observations on the same temporary LiveData.
                    Log.d(TAG, "Fetched...");
                    repoResponseLiveData.removeObserver(this);
                }
            });
        }
    }

    // PLAN_LIMITS as a static map (simplified; in practice, sync with backend)
    private static final Map<String, Map<String, Object>> PLAN_LIMITS = new HashMap<>() {{
        put("free", new HashMap<>() {{
            put("price_alerts_limit", 1);
            put("pattern_detection_limit", 1);
            put("watchlist_limit", 1);
            put("market_analysis_limit", 3);
            put("journaling_enabled", false);
            put("video_download_limit", 0);
        }});
        // Add other plans similarly...
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
}
