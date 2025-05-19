package viewmodels.google_login;

import android.app.Activity;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import firebase_manager.FirebaseAuthManager;
import models.User;

public class AuthViewModel extends ViewModel {
    private final FirebaseAuthManager firebaseAuthManager;
    private final MutableLiveData<AuthState> authState = new MutableLiveData<>(AuthState.UNAUTHENTICATED);
    private final MutableLiveData<User> currentUser = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private String googleWebClientIdCache;

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
            }

            @Override
            public void onAuthFailed(String errorMsg) {
                errorMessage.postValue(errorMsg);
                authState.postValue(AuthState.ERROR);
                loadingContext.postValue(LoadingContext.NONE); // Reset context
            }
        });
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
}
