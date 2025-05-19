package viewmodels.telegram_login;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import backend.TelegramAuthService;
import models.User;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class TelegramAuthViewModel extends ViewModel {
    private static final String TAG = "TelegramAuthViewModel";

    // Enum to track auth state
    public enum AuthState {
        LOADING,
        AUTHENTICATED,
        UNAUTHENTICATED,
        ERROR
    }

    // LiveData for UI state tracking
    private final MutableLiveData<AuthState> authState = new MutableLiveData<>(AuthState.UNAUTHENTICATED);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>("");
    private final MutableLiveData<User> currentUser = new MutableLiveData<>(null);

    // API service
    private TelegramAuthService telegramAuthService;

    // Constants
    private static final String BASE_API_URL = "https://stable-wholly-crappie.ngrok-free.app/api/v1/auth/"; // Update with your actual API URL

    public TelegramAuthViewModel() {
        initApiService();
    }

    // Initialize Retrofit service
    private void initApiService() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_API_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        telegramAuthService = retrofit.create(TelegramAuthService.class);
    }

    // Process Telegram auth data received from WebView
    public void processTelegramAuthData(String authDataJson) {
        authState.setValue(AuthState.LOADING);
        clearErrorMessage();

        try {
            // Verify and authenticate with backend
            Map<String, String> authData = parseAuthData(authDataJson);
            verifyTelegramAuthWithBackend(authData);
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing Telegram auth data", e);
            setAuthError("Failed to parse authentication data");
        }
    }

    // Parse JSON auth data into Map
    private Map<String, String> parseAuthData(String authDataJson) throws JSONException {
        JSONObject jsonObject = new JSONObject(authDataJson);
        Map<String, String> authData = new HashMap<>();

        // Extract all fields from the Telegram auth data
        authData.put("id", jsonObject.getString("id"));
        authData.put("email", jsonObject.optString("email", ""));
        authData.put("first_name", jsonObject.optString("first_name", ""));
        authData.put("last_name", jsonObject.optString("last_name", ""));
        authData.put("username", jsonObject.optString("username", ""));
        authData.put("photo_url", jsonObject.optString("photo_url", ""));
        authData.put("auth_date", jsonObject.getString("auth_date"));
        authData.put("hash", jsonObject.getString("hash"));

        return authData;
    }

    // Verify Telegram auth data with backend
    private void verifyTelegramAuthWithBackend(Map<String, String> authData) {
        Log.d(TAG, "Sending auth data to backend: " + authData.toString()); // <-- ADD THIS
        telegramAuthService.verifyTelegramAuth(authData).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<TelegramAuthResponse> call, @NonNull Response<TelegramAuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Log.d(TAG, "Sending auth data to backend: " + authData.toString()); // <-- ADD THIS
                    TelegramAuthResponse authResponse = response.body();
                    if (authResponse.isSuccess()) {
                        // Auth successful, sign in with Firebase custom token
                        signInWithFirebaseCustomToken(authResponse.getFirebaseToken(), authResponse.getUser());
                    } else {
                        // Backend verification failed
                        setAuthError(authResponse.getMessage());
                    }
                } else {
                    // HTTP error
                    setAuthError("Server authentication failed: " +
                            (response.errorBody() != null ? response.code() : "Unknown error"));
                }
            }

            @Override
            public void onFailure(Call<TelegramAuthResponse> call, Throwable t) {
                Log.e(TAG, "API call failed", t);
                setAuthError("Network error: " + t.getMessage());
            }
        });
    }

    // Sign in to Firebase with custom token from backend
    private void signInWithFirebaseCustomToken(String token, User user) {
        FirebaseAuth.getInstance()
                .signInWithCustomToken(token)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Firebase custom token sign-in successful");
                        // Update UI state
                        currentUser.setValue(user);
                        authState.setValue(AuthState.AUTHENTICATED);
                    } else {
                        Log.e(TAG, "Firebase sign-in failed", task.getException());
                        setAuthError("Firebase authentication failed: " +
                                (task.getException() != null ? task.getException().getMessage() : "Unknown error"));
                    }
                });
    }

    // Helper method to set error state and message
    public void setErrorMessage(String message) {
        errorMessage.setValue(message);
    }

    private void setAuthError(String message) {
        errorMessage.setValue(message);
        authState.setValue(AuthState.ERROR);
    }

    private void clearErrorMessage() {
        errorMessage.setValue("");
    }

    // Getters for LiveData
    public LiveData<AuthState> getAuthState() {
        return authState;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<User> getCurrentUser() {
        return currentUser;
    }

    // Telegram Auth Response POJO
    public static class TelegramAuthResponse {
        private boolean success;
        private String message;
        private String firebaseToken;
        private User user;

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getFirebaseToken() {
            return firebaseToken;
        }

        public User getUser() {
            return user;
        }
    }
}
