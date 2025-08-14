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

    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<UsageData> usageData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isNewUser = new MutableLiveData<>(false);

    private String googleWebClientIdCache;
    private SupabaseRepository supabaseRepository;

    // Real-time listener fields
    private DatabaseReference userRef;
    private ValueEventListener userValueEventListener;

    // Usage response observer management
    private Observer<UsageResponse> currentUsageObserver;
    private LiveData<UsageResponse> currentUsageResponseLiveData;

    // State management flags
    private boolean isInitialized = false;
    private boolean isSigningOut = false;
    private boolean isSigningIn = false;

    public enum AuthState {
        LOADING,
        AUTHENTICATED,
        UNAUTHENTICATED,
        ERROR
    }

    public enum LoadingContext {
        NONE,
        GENERAL_SIGN_IN,
        BOTTOM_SHEET_SIGN_UP,
        SIGN_OUT,
        INITIALIZATION
    }

    private final MutableLiveData<LoadingContext> loadingContext = new MutableLiveData<>(LoadingContext.NONE);

    public AuthViewModel() {
        firebaseAuthManager = new FirebaseAuthManager();
        setupAuthCallbacks();
    }

    // Public getters
    public LiveData<Boolean> getIsNewUser() {
        return isNewUser;
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

    public User getCurrentUserValue() {
        return currentUser.getValue();
    }

    public boolean isUserSignedIn() {
        return firebaseAuthManager.isUserSignedIn() && !isSigningOut;
    }

    public void initialize(Activity activity, String googleWebClientId) {
        if (isInitialized) {
            Log.d(TAG, "AuthViewModel already initialized, skipping...");
            return;
        }

        Log.d(TAG, "Initializing AuthViewModel");
        this.googleWebClientIdCache = googleWebClientId;

        // Set loading state for initialization
        setLoadingState(LoadingContext.INITIALIZATION, true);

        firebaseAuthManager.initialize(activity, googleWebClientId);

        if (firebaseAuthManager.isUserSignedIn()) {
            Log.d(TAG, "User is signed in, attempting to get user data");
            loadCurrentUser();
        } else {
            Log.d(TAG, "No signed-in user detected");
            setUnauthenticatedState();
        }

        isInitialized = true;
    }

    private void loadCurrentUser() {
        User userData = firebaseAuthManager.getUserData();
        if (userData != null) {
            Log.d(TAG, "User data retrieved successfully: " + userData.getUuid());
            setAuthenticatedState(userData);
        } else {
            Log.w(TAG, "User is signed in but getUserData() returned null, attempting to refresh");
            refreshCurrentUser();
        }
    }

    public void refreshCurrentUser() {
        Log.d(TAG, "Attempting to refresh current user data");

        if (!firebaseAuthManager.isUserSignedIn() || isSigningOut) {
            Log.w(TAG, "Cannot refresh user data - user is not signed in or signing out");
            setUnauthenticatedState();
            return;
        }

        if (authState.getValue() != AuthState.LOADING) {
            authState.setValue(AuthState.LOADING);
        }

        firebaseAuthManager.refreshUserData(new FirebaseAuthManager.UserDataCallback() {
            @Override
            public void onUserDataRetrieved(User user) {
                if (user != null && !isSigningOut) {
                    Log.d(TAG, "User data refreshed successfully: " + user.getUuid());
                    setAuthenticatedState(user);
                } else {
                    Log.e(TAG, "Failed to refresh user data - received null user");
                    attemptDirectUserFetch();
                }
            }

            @Override
            public void onUserDataFailed(String error) {
                Log.e(TAG, "Failed to refresh user data: " + error);
                if (!isSigningOut) {
                    attemptDirectUserFetch();
                }
            }
        });
    }

    private void attemptDirectUserFetch() {
        if (isSigningOut) return;

        Log.d(TAG, "Attempting direct user data fetch");

        try {
            User userData = firebaseAuthManager.getUserData();
            if (userData != null) {
                Log.d(TAG, "Direct fetch successful: " + userData.getUuid());
                setAuthenticatedState(userData);
            } else {
                Log.e(TAG, "Direct fetch also returned null - there may be an issue with Firebase Auth");
                setErrorState("Unable to load user data. Please sign in again.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception during direct user fetch: " + e.getMessage());
            setErrorState("Error loading user data: " + e.getMessage());
        }
    }

    private void setupAuthCallbacks() {
        firebaseAuthManager.setAuthCallback(new FirebaseAuthManager.AuthCallback() {
            @Override
            public void onAuthInProgress() {
                if (!isSigningOut) {
                    Log.d(TAG, "Auth in progress");
                    // Don't override loading state if we're already in a specific loading context
                    if (loadingContext.getValue() == LoadingContext.NONE) {
                        authState.postValue(AuthState.LOADING);
                    }
                }
            }

            @Override
            public void onAuthSuccess(User user, boolean newUser) {
                Log.d(TAG, "Auth success callback - User: " + (user != null ? user.getUuid() : "null"));

                if (isSigningOut) {
                    Log.d(TAG, "Ignoring auth success callback - currently signing out");
                    return;
                }

                if (user != null) {
                    isNewUser.postValue(FirebaseAuthManager.wasUserNewlyCreated());
                    setAuthenticatedState(user);
                    isSigningIn = false;
                } else {
                    Log.e(TAG, "Auth success but user is null - this is unexpected");
                    setErrorState("Authentication successful but unable to retrieve user data");
                    isSigningIn = false;
                }
            }

            @Override
            public void onAuthFailed(String errorMsg) {
                Log.e(TAG, "Auth failed: " + errorMsg);
                if (!isSigningOut) {
                    setErrorState(errorMsg);
                }
                isSigningIn = false;
            }
        });
    }

    // State management helper methods
    private void setLoadingState(LoadingContext context, boolean showLoading) {
        loadingContext.postValue(context);
        if (showLoading) {
            authState.postValue(AuthState.LOADING);
        }
    }

    private void setAuthenticatedState(User user) {
        currentUser.postValue(user);
        authState.postValue(AuthState.AUTHENTICATED);
        loadingContext.postValue(LoadingContext.NONE);

        // Initialize repository if needed
        if (supabaseRepository == null) {
            supabaseRepository = new SupabaseRepository();
        }

        // Start listening for real-time updates
        startListeningForUserUpdates(user.getUuid());
    }

    private void setUnauthenticatedState() {
        stopListeningForUserUpdates();
        currentUser.postValue(null);
        authState.postValue(AuthState.UNAUTHENTICATED);
        loadingContext.postValue(LoadingContext.NONE);
        isNewUser.postValue(false);
    }

    private void setErrorState(String message) {
        errorMessage.postValue(message);
        authState.postValue(AuthState.ERROR);
        loadingContext.postValue(LoadingContext.NONE);
    }

    // Sign in methods
    public void signInWithGoogleFromBottomSheet(Fragment fragment) {
        if (!isFragmentValid(fragment)) return;

        if (isSigningIn) {
            Log.d(TAG, "Sign in already in progress");
            return;
        }

        isSigningIn = true;
        setLoadingState(LoadingContext.BOTTOM_SHEET_SIGN_UP, true);
        firebaseAuthManager.startGoogleSignIn(fragment.getActivity());
    }

    public void signInWithGoogleFromGeneral(Fragment fragment) {
        if (!isFragmentValid(fragment)) return;

        if (isSigningIn) {
            Log.d(TAG, "Sign in already in progress");
            return;
        }

        isSigningIn = true;
        setLoadingState(LoadingContext.GENERAL_SIGN_IN, true);
        firebaseAuthManager.startGoogleSignIn(fragment.getActivity());
    }

    private boolean isFragmentValid(Fragment fragment) {
        if (fragment == null || !fragment.isAdded() || fragment.getActivity() == null) {
            setErrorState("Fragment not attached to a valid activity.");
            isSigningIn = false;
            return false;
        }
        return true;
    }

    public void signOut(Activity activity) {
        if (isSigningOut) {
            Log.d(TAG, "Sign out already in progress");
            return;
        }

        Log.d(TAG, "Starting sign out process");
        isSigningOut = true;
        isSigningIn = false;

        setLoadingState(LoadingContext.SIGN_OUT, true);

        // Stop listening immediately
        stopListeningForUserUpdates();

        firebaseAuthManager.signOut(activity, task -> {
            Log.d(TAG, "Sign out completed");
            isSigningOut = false;
            setUnauthenticatedState();
        });
    }

    public void cancelAuthentication() {
        if (authState.getValue() == AuthState.LOADING) {
            Log.d(TAG, "Cancelling authentication");
            isSigningIn = false;
            loadingContext.setValue(LoadingContext.NONE);
            authState.setValue(AuthState.UNAUTHENTICATED);
            firebaseAuthManager.cancelAuthentication();
        }
    }

    // Usage data management
    public void fetchUsageCounts(String userId, String subscriptionType) {
        Log.d(TAG, "Fetching usage counts for userId: " + userId + ", plan: " + subscriptionType);

        if (supabaseRepository == null) {
            Log.w(TAG, "SupabaseRepository was null. Initializing now.");
            supabaseRepository = new SupabaseRepository();
        }

        // Clean up previous observer
        cleanupUsageObserver();

        // Get new LiveData and observe it
        currentUsageResponseLiveData = supabaseRepository.getSubscriptionLimits(userId);
        currentUsageObserver = new Observer<UsageResponse>() {
            @Override
            public void onChanged(UsageResponse responseFromRepo) {
                if (responseFromRepo != null && responseFromRepo.getUsage() != null) {
                    Log.d(TAG, "Fetched usage data successfully");
                    usageData.postValue(responseFromRepo.getUsage());
                } else {
                    Log.e(TAG, "Received null or invalid usage response from repository");
                    usageData.postValue(null);
                }

                // Clean up this single-use observer
                cleanupUsageObserver();
            }
        };

        currentUsageResponseLiveData.observeForever(currentUsageObserver);
    }

    private void cleanupUsageObserver() {
        if (currentUsageResponseLiveData != null && currentUsageObserver != null) {
            currentUsageResponseLiveData.removeObserver(currentUsageObserver);
            currentUsageResponseLiveData = null;
            currentUsageObserver = null;
        }
    }

    // Real-time user updates
    private void startListeningForUserUpdates(String userId) {
        stopListeningForUserUpdates(); // Ensure no duplicate listeners

        if (userId == null || userId.isEmpty()) {
            Log.w(TAG, "Cannot start listening - userId is null or empty");
            return;
        }

        Log.d(TAG, "Starting real-time listener for user: " + userId);
        userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);
        userValueEventListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isSigningOut) {
                    Log.d(TAG, "Ignoring user data update - signing out");
                    return;
                }

                User updatedUser = snapshot.getValue(User.class);
                if (updatedUser != null) {
                    Log.d(TAG, "Real-time user data update received. Plan: " + updatedUser.getSubscriptionType());

                    // Only update if we're in authenticated state to prevent race conditions
                    if (authState.getValue() == AuthState.AUTHENTICATED) {
                        currentUser.postValue(updatedUser);
                    }
                } else {
                    Log.w(TAG, "Received null user data from real-time update");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "User data listener was cancelled: " + error.getMessage());
                if (!isSigningOut && authState.getValue() == AuthState.AUTHENTICATED) {
                    // Only show error if we were expecting this listener to work
                    setErrorState("Connection to user data lost: " + error.getMessage());
                }
            }
        };

        userRef.addValueEventListener(userValueEventListener);
    }

    private void stopListeningForUserUpdates() {
        if (userRef != null && userValueEventListener != null) {
            Log.d(TAG, "Stopping real-time user listener");
            userRef.removeEventListener(userValueEventListener);
            userRef = null;
            userValueEventListener = null;
        }
    }

    @Override
    protected void onCleared() {
        Log.d(TAG, "AuthViewModel cleared - cleaning up resources");
        super.onCleared();
        stopListeningForUserUpdates();
        cleanupUsageObserver();
        isSigningOut = false;
        isSigningIn = false;
        isInitialized = false;
    }
}