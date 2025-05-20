package firebase_manager;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Tasks;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Date;
import models.User;
import viewmodels.telegram_login.TelegramAuthViewModel;

public class FirebaseAuthManager {
    private static final String TAG = "FirebaseAuthManager";
    private final FirebaseAuth firebaseAuth;
    private AuthCallback authCallback;
    private CredentialManager credentialManager;
    private String googleWebClientId;
    private boolean isAuthInProgress = false;
    // Add this to FirebaseAuthManager class
    private static boolean isUserNewlyCreated = false;

    public FirebaseAuthManager() {
        firebaseAuth = FirebaseAuth.getInstance();
    }

    public void initialize(Activity activity, String googleWebClientId) {
        if (activity != null) {
            this.credentialManager = CredentialManager.create(activity);
            this.googleWebClientId = googleWebClientId;
            Log.d(TAG, String.format("Initialized FirebaseAuthManager with web client ID: %s", googleWebClientId));
        } else {
            Log.e(TAG, "Cannot initialize FirebaseAuthManager with null activity");
        }
    }

    public void setAuthCallback(AuthCallback callback) {
        this.authCallback = callback;
    }

    public boolean isUserSignedIn() {
        return firebaseAuth.getCurrentUser() != null;
    }

    public FirebaseUser getCurrentUser() {
        return firebaseAuth.getCurrentUser();
    }

    public void signOut(Activity activity, OnCompleteListener<Void> onCompleteListener) {
        firebaseAuth.signOut();

        if (credentialManager != null && activity != null) {
            ClearCredentialStateRequest clearRequest = new ClearCredentialStateRequest();
            credentialManager.clearCredentialStateAsync(
                    clearRequest,
                    null,
                    ContextCompat.getMainExecutor(activity),
                    new CredentialManagerCallback<>() {
                        @Override
                        public void onResult(Void result) {
                            Log.d(TAG, "Credential state cleared successfully.");
                            if (onCompleteListener != null) {
                                onCompleteListener.onComplete(Tasks.forResult(null));
                            }
                        }

                        @Override
                        public void onError(@NonNull ClearCredentialException e) {
                            Log.e(TAG, String.format("Error clearing credential state: %s", e.getMessage()), e);
                            if (onCompleteListener != null) {
                                onCompleteListener.onComplete(Tasks.forException(e));
                            }
                        }
                    }
            );

        } else if (onCompleteListener != null) {
            onCompleteListener.onComplete(Tasks.forResult(null));
        }
    }

    // Modify the startGoogleSignIn method
    public void startGoogleSignIn(Activity activity) {
        if (isAuthInProgress) {
            Log.w(TAG, "Google Sign-In already in progress, ignoring request");
            return;
        }

        if (activity == null || activity.isDestroyed() || activity.isFinishing()) {
            Log.e(TAG, "Activity is invalid for Google Sign-In");
            if (authCallback != null) {
                authCallback.onAuthFailed("Activity is not available");
            }
            return;
        }

        isAuthInProgress = true;

        if (authCallback != null) {
            authCallback.onAuthInProgress();
        }

        Log.d(TAG, "Starting Google Sign-In process");


        if (this.googleWebClientId == null || this.googleWebClientId.isEmpty()) {
            isAuthInProgress = false;
            Log.e(TAG, "Google Web Client ID is not set or empty");
            if (authCallback != null) {
                authCallback.onAuthFailed("Google Web Client ID is not set. Initialize FirebaseAuthManager first.");
            }
            return;
        }

        Log.d(TAG, String.format("Web Client ID: %s", this.googleWebClientId));

        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(this.googleWebClientId)
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        if (credentialManager == null) {
            Log.e(TAG, "CredentialManager is null, initializing now");
            credentialManager = CredentialManager.create(activity);
        }

        try {
            Log.d(TAG, "Calling getCredentialAsync");

            credentialManager.getCredentialAsync(
                    activity,
                    request,
                    null,
                    ContextCompat.getMainExecutor(activity),
                    new CredentialManagerCallback<>() {
                        @Override
                        public void onResult(@NonNull GetCredentialResponse result) {
                            Log.d(TAG, "GetCredentialResponse received successfully");

                            try {
                                Credential credential = result.getCredential();
                                if (credential instanceof CustomCredential) {
                                    CustomCredential customCredential = (CustomCredential) credential;
                                    if (GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(customCredential.getType())) {
                                        Log.d(TAG, "GoogleIdTokenCredential received");

                                        GoogleIdTokenCredential googleIdTokenCredential =
                                                GoogleIdTokenCredential.createFrom(customCredential.getData());
                                        String idToken = googleIdTokenCredential.getIdToken();

                                        String truncatedToken = idToken.substring(0, Math.min(10, idToken.length())) + "...";
                                        Log.d(TAG, "ID Token received (truncated): " + truncatedToken);

                                        firebaseAuthWithGoogle(idToken);
                                    } else {
                                        isAuthInProgress = false;
                                        Log.e(TAG, "Unexpected custom credential type: " + customCredential.getType());
                                        if (authCallback != null) {
                                            authCallback.onAuthFailed("Unexpected custom credential type: " + customCredential.getType());
                                        }
                                    }
                                } else {
                                    isAuthInProgress = false;
                                    Log.e(TAG, "Unexpected credential type: " + (credential != null ? credential.getClass().getName() : "null"));
                                    if (authCallback != null) {
                                        authCallback.onAuthFailed("Unexpected credential type received.");
                                    }
                                }
                            } catch (Exception e) {
                                isAuthInProgress = false;
                                Log.e(TAG, "Exception while processing credential response", e);
                                if (authCallback != null) {
                                    authCallback.onAuthFailed("Error processing Google Sign-In response: " + e.getMessage());
                                }
                            }
                        }

                        @Override
                        public void onError(@NonNull GetCredentialException e) {
                            isAuthInProgress = false;
                            Log.e(TAG, "GetCredentialException: " + e.getMessage(), e);

                            if (authCallback != null) {
                                String errorMessage;

                                if (e instanceof GetCredentialCancellationException) {
                                    errorMessage = "Sign-in was canceled by the user";
                                    Log.w(TAG, "Here is what happened " + e.getMessage());
                                } else if (e instanceof NoCredentialException) {
                                    NoCredentialException nce = (NoCredentialException) e;
                                    errorMessage = "No accounts found or user canceled. Message: " + nce.getMessage();
                                    Log.w(TAG, "NoCredentialException message: " + nce.getMessage());
                                } else {
                                    errorMessage = "Google Sign-In failed: " + e.getMessage();
                                }

                                authCallback.onAuthFailed(errorMessage);
                            }
                        }
                    }
            );
        } catch (Exception e) {
            isAuthInProgress = false;
            Log.e(TAG, "Exception during getCredentialAsync call", e);
            if (authCallback != null) {
                authCallback.onAuthFailed("Exception in Google Sign-In: " + e.getMessage());
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    isAuthInProgress = false;
                    if (task.isSuccessful()) {
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        checkUserInDatabaseAndUpdate(user);
                    } else {
                        if (authCallback != null) {
                            String errorMessage = "Firebase Authentication failed";
                            if (task.getException() != null && task.getException().getMessage() != null) {
                                errorMessage += ": " + task.getException().getMessage();
                            }
                            authCallback.onAuthFailed(errorMessage);
                        }
                        Log.e(TAG, "Firebase signInWithCredential failed", task.getException());
                    }
                });
    }

    private void checkUserInDatabaseAndUpdate(FirebaseUser firebaseUser) {
        // Reset the flag for each new check
        isUserNewlyCreated = false;

        if (firebaseUser == null) {
            if (authCallback != null) {
                authCallback.onAuthFailed("FirebaseUser is null after successful sign-in.");
            }
            return;
        }

        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference()
                .child("users")
                .child(firebaseUser.getUid());

        userRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                if (task.getResult() != null && task.getResult().exists()) {
                    User user = task.getResult().getValue(User.class);
                    if (authCallback != null && user != null) {
                        authCallback.onAuthSuccess(user, false);
                    } else if (authCallback != null) {
                        authCallback.onAuthFailed("Failed to parse existing user data from database.");
                    }
                } else {
                    // Flag that this is a new user being created
                    isUserNewlyCreated = true;

                    User newUser = new User();
                    newUser.setUuid(firebaseUser.getUid());
                    newUser.setEmail(firebaseUser.getEmail());

                    if (firebaseUser.getDisplayName() != null) {
                        String[] names = firebaseUser.getDisplayName().split(" ", 2);
                        newUser.setFirstname(names[0]);
                        newUser.setSecondname(names.length > 1 ? names[1] : "");
                        newUser.setDisplayName(firebaseUser.getDisplayName());
                    }
                    newUser.setAvatarUrl(firebaseUser.getPhotoUrl() != null ?
                            firebaseUser.getPhotoUrl().toString() : "");
                    newUser.setCreatedTime(String.valueOf(new Date().getTime()));
                    newUser.setSubscriptionType("free");
                    newUser.setUsingTestDrive(false);
                    newUser.setUserPaid(false);

                    userRef.setValue(newUser).addOnCompleteListener(saveTask -> {
                        if (saveTask.isSuccessful()) {
                            if (authCallback != null) {
                                authCallback.onAuthSuccess(newUser, true);
                            }
                        } else {
                            if (authCallback != null) {
                                String errorMessage = "Failed to save new user data to database";
                                if (saveTask.getException() != null && saveTask.getException().getMessage() != null) {
                                    errorMessage += ": " + saveTask.getException().getMessage();
                                }
                                authCallback.onAuthFailed(errorMessage);
                            }
                            Log.e(TAG, "Failed to save new user data", saveTask.getException());
                        }
                    });
                }
            } else {
                if (authCallback != null) {
                    String errorMessage = "Database error while checking user";
                    if (task.getException() != null && task.getException().getMessage() != null) {
                        errorMessage += ": " + task.getException().getMessage();
                    }
                    authCallback.onAuthFailed(errorMessage);
                }
                Log.e(TAG, "Database error checking user", task.getException());
            }
        });
    }

    // Add a public method to check if this was a newly created user
    public static boolean wasUserNewlyCreated() {
        return isUserNewlyCreated;
    }

    public void cancelAuthentication() {
        if (isAuthInProgress) {
            isAuthInProgress = false;
            Log.d(TAG, "Authentication process cancelled by user");
            // If there's an active credential request, it can't be directly cancelled
            // but we can mark that we don't want to process the result
            // The Google popup itself is managed by the OS/Google and will close itself
            // if the user cancels from there
        }
    }

    // In FirebaseAuthManager.java, update the AuthCallback interface:
    public interface AuthCallback {
        void onAuthInProgress();
        void onAuthSuccess(User user, boolean isNewUser);
        void onAuthFailed(String errorMessage);
    }
}