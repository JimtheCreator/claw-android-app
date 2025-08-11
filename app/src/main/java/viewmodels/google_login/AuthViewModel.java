package viewmodels.google_login;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

import backend.results.UsageResponse;
import database.firebaseDB.FirebaseAuthManager;
import models.UsageData;
import models.User;
import repositories.plan_usage_limits.SupabaseRepository;

public class AuthViewModel extends ViewModel {
    private final FirebaseAuthManager firebaseAuthManager;
    private final MutableLiveData<AuthState> authState = new MutableLiveData<>(AuthState.UNAUTHENTICATED);
    private final MutableLiveData<User> currentUser = new MutableLiveData<>();
    private static final String TAG = "AuthViewModel";
    LiveData<UsageResponse> repoResponseLiveData;
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<UsageData> usageData = new MutableLiveData<>();
    private String googleWebClientIdCache;
    private SupabaseRepository supabaseRepository;

    private final MutableLiveData<Boolean> isNewUser = new MutableLiveData<>(false);

    // Fields for the real-time listener
    private DatabaseReference userRef;
    private ValueEventListener userValueEventListener;


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
            Log.d(TAG, "User is signed in, attempting to get user data");

            // Try to get user data
            User userData = firebaseAuthManager.getUserData();
            if (userData != null) {
                Log.d(TAG, "User data retrieved successfully: " + userData.getUuid());
                authState.setValue(AuthState.AUTHENTICATED);
                currentUser.setValue(userData);
                if (supabaseRepository == null) {
                    supabaseRepository = new SupabaseRepository();
                }
                // Start listening for real-time updates
                startListeningForUserUpdates(userData.getUuid());
            } else {
                Log.w(TAG, "User is signed in but getUserData() returned null, attempting to refresh user data");
                authState.setValue(AuthState.LOADING);
                // Try to refresh user data
                refreshCurrentUser();
            }
        } else {
            Log.d(TAG, "No signed-in user detected");
            authState.setValue(AuthState.UNAUTHENTICATED);
            currentUser.setValue(null);
        }
    }

    /**
     * Attempts to refresh the current user data from Firebase
     */
    public void refreshCurrentUser() {
        Log.d(TAG, "Attempting to refresh current user data");

        if (!firebaseAuthManager.isUserSignedIn()) {
            Log.w(TAG, "Cannot refresh user data - user is not signed in");
            authState.setValue(AuthState.UNAUTHENTICATED);
            currentUser.setValue(null);
            return;
        }

        authState.setValue(AuthState.LOADING);

        // Try different approaches to get user data
        firebaseAuthManager.refreshUserData(new FirebaseAuthManager.UserDataCallback() {
            @Override
            public void onUserDataRetrieved(User user) {
                if (user != null) {
                    Log.d(TAG, "User data refreshed successfully: " + user.getUuid());
                    currentUser.postValue(user);
                    authState.postValue(AuthState.AUTHENTICATED);
                    if (supabaseRepository == null) {
                        supabaseRepository = new SupabaseRepository();
                    }
                    // Ensure listener is running after refresh
                    startListeningForUserUpdates(user.getUuid());
                } else {
                    Log.e(TAG, "Failed to refresh user data - received null user");
                    // Try one more time with a direct fetch
                    attemptDirectUserFetch();
                }
            }

            @Override
            public void onUserDataFailed(String error) {
                Log.e(TAG, "Failed to refresh user data: " + error);
                attemptDirectUserFetch();
            }
        });
    }

    /**
     * Direct attempt to fetch user data - fallback method
     */
    private void attemptDirectUserFetch() {
        Log.d(TAG, "Attempting direct user data fetch");

        try {
            // This might be a synchronous call or you might need to implement it differently
            // based on your FirebaseAuthManager implementation
            User userData = firebaseAuthManager.getUserData();

            if (userData != null) {
                Log.d(TAG, "Direct fetch successful: " + userData.getUuid());
                currentUser.postValue(userData);
                authState.postValue(AuthState.AUTHENTICATED);
                if (supabaseRepository == null) {
                    supabaseRepository = new SupabaseRepository();
                }
            } else {
                Log.e(TAG, "Direct fetch also returned null - there may be an issue with Firebase Auth");
                errorMessage.postValue("Unable to load user data. Please sign in again.");
                authState.postValue(AuthState.ERROR);
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception during direct user fetch: " + e.getMessage());
            errorMessage.postValue("Error loading user data: " + e.getMessage());
            authState.postValue(AuthState.ERROR);
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
                Log.d(TAG, "Auth success callback - User: " + (user != null ? user.getUuid() : "null"));

                if (user != null) {
                    currentUser.postValue(user);
                    isNewUser.postValue(FirebaseAuthManager.wasUserNewlyCreated());
                    authState.postValue(AuthState.AUTHENTICATED);
                    loadingContext.postValue(LoadingContext.NONE); // Reset context
                    supabaseRepository = new SupabaseRepository();
                    // Start listening for real-time updates to the user object
                    startListeningForUserUpdates(user.getUuid());
                } else {
                    Log.e(TAG, "Auth success but user is null - this is unexpected");
                    errorMessage.postValue("Authentication successful but unable to retrieve user data");
                    authState.postValue(AuthState.ERROR);
                    loadingContext.postValue(LoadingContext.NONE);
                }
            }

            @Override
            public void onAuthFailed(String errorMsg) {
                Log.e(TAG, "Auth failed: " + errorMsg);
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

    /**
     * Get current user value synchronously (can return null)
     */
    public User getCurrentUserValue() {
        return currentUser.getValue();
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
        // Stop listening before signing out
        stopListeningForUserUpdates();
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
        Log.d(TAG, "Method called for subscription type: " + subscriptionType);
        Log.d(TAG, "Fetching paid tier usage counts for userId: " + userId);

        // Ensure repository is initialized. This is a safety check.
        if (supabaseRepository == null) {
            Log.w(TAG, "SupabaseRepository was null. Initializing now.");
            supabaseRepository = new SupabaseRepository();
        }

        // Fetch from Supabase via API. The returned LiveData is for UsageResponse.
        repoResponseLiveData = supabaseRepository.getSubscriptionLimits(userId);

        // Observe the LiveData from the repository.
        repoResponseLiveData.observeForever(new Observer<>() {
            @Override
            public void onChanged(UsageResponse responseFromRepo) {
                // Check if the response and the nested usage data are valid
                if (responseFromRepo != null && responseFromRepo.getUsage() != null) {
                    Log.d(TAG, "Fetched data. Extracting usage object.");
                    // Extract the UsageData object and post it to the UI-facing LiveData
                    usageData.postValue(responseFromRepo.getUsage());
                } else {
                    Log.e(TAG, "Received null or invalid response from repository.");
                    usageData.postValue(null); // Signal an error or no data
                }

                // IMPORTANT: Clean up the observer to prevent memory leaks,
                // as this LiveData from the repository is single-use.
                if (repoResponseLiveData != null) {
                    repoResponseLiveData.removeObserver(this);
                }
            }
        });
    }

    // *** NEW METHOD to set up the real-time listener ***
    private void startListeningForUserUpdates(String userId) {
        stopListeningForUserUpdates(); // Ensure no duplicate listeners

        if (userId == null || userId.isEmpty()) return;

        userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);
        userValueEventListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                User updatedUser = snapshot.getValue(User.class);
                if (updatedUser != null) {
                    Log.d(TAG, "Real-time user data update received. New plan: " + updatedUser.getSubscriptionType());
                    currentUser.postValue(updatedUser);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "User data listener was cancelled.", error.toException());
            }
        };
        userRef.addValueEventListener(userValueEventListener);
    }

    // *** NEW METHOD to clean up the listener ***
    private void stopListeningForUserUpdates() {
        if (userRef != null && userValueEventListener != null) {
            userRef.removeEventListener(userValueEventListener);
            userRef = null;
            userValueEventListener = null;
        }
    }

    // *** NEW METHOD to ensure no memory leaks ***
    @Override
    protected void onCleared() {
        super.onCleared();
        stopListeningForUserUpdates();
    }
}
