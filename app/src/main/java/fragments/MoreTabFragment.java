package fragments;

import android.content.Intent;
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

import com.bumptech.glide.Glide;
import com.claw.ai.R;
import com.claw.ai.databinding.FragmentMoreTabBinding;

import java.util.Arrays;
import java.util.stream.Collectors;

import accounts.SignUpBottomSheet;
import models.User;
import pricing.OnboardingPricingPageSheetFragment;
import pricing.SubscriptionPlanSheetFragment;
import settings.SettingsActivity;
import settings.about.AboutActivity;
import viewmodels.google_login.AuthViewModel;

public class MoreTabFragment extends Fragment {
    private FragmentMoreTabBinding binding;
    private AuthViewModel authViewModel;
    private User currentUser;
    private static final String TAG = "MoreTabFragment";

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
        setupObservers();
        setupClickListeners();
    }

    private void setupObservers() {
        // Observe auth state changes
        authViewModel.getAuthState().observe(getViewLifecycleOwner(), authState -> {
            if (binding == null || !isAdded()) return;

            Log.d(TAG, "Auth state changed to: " + authState);

            AuthViewModel.LoadingContext context = authViewModel.getLoadingContext().getValue();
            if (context == null) context = AuthViewModel.LoadingContext.NONE;

            handleAuthState(authState, context);
        });

        // Observe loading context changes
        authViewModel.getLoadingContext().observe(getViewLifecycleOwner(), loadingContext -> {
            if (binding == null || !isAdded()) return;

            Log.d(TAG, "Loading context changed to: " + loadingContext);

            AuthViewModel.AuthState authState = authViewModel.getAuthState().getValue();
            if (authState != null) {
                handleAuthState(authState, loadingContext);
            }
        });

        // Observe real-time user changes
        authViewModel.getCurrentUser().observe(getViewLifecycleOwner(), user -> {
            if (!isAdded()) return;

            AuthViewModel.AuthState currentAuthState = authViewModel.getAuthState().getValue();
            if (user != null && currentAuthState == AuthViewModel.AuthState.AUTHENTICATED) {
                this.currentUser = user;
                Log.d(TAG, "User data updated in real-time. Plan: " + user.getSubscriptionType());
                updateProfileUI();
            } else if (user == null && currentAuthState != AuthViewModel.AuthState.LOADING) {
                this.currentUser = null;
                Log.d(TAG, "User data cleared");
            }
        });

        // Observe error messages
        authViewModel.getErrorMessage().observe(getViewLifecycleOwner(), errorMessage -> {
            if (errorMessage != null && !errorMessage.isEmpty() && isAdded()) {
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void handleAuthState(AuthViewModel.AuthState authState, AuthViewModel.LoadingContext loadingContext) {
        Log.d(TAG, "Handling auth state: " + authState + " with context: " + loadingContext);

        switch (authState) {
            case LOADING:
                handleLoadingState(loadingContext);
                break;

            case AUTHENTICATED:
                handleAuthenticatedState();
                break;

            case UNAUTHENTICATED:
                showLoadingState(false);
                showLoginPage();
                break;

            case ERROR:
                showLoadingState(false);
                if (!authViewModel.isUserSignedIn()) {
                    showLoginPage();
                } else {
                    // Stay on current page but stop loading
                    Log.d(TAG, "Error state but user is signed in - maintaining current view");
                }
                break;
        }
    }

    private void handleLoadingState(AuthViewModel.LoadingContext context) {
        switch (context) {
            case BOTTOM_SHEET_SIGN_UP:
                // Don't show loading spinner for bottom sheet sign up
                // The bottom sheet itself should handle its own loading state
                Log.d(TAG, "Loading for bottom sheet sign up - not showing spinner");
                break;

            case GENERAL_SIGN_IN:
            case SIGN_OUT:
            case INITIALIZATION:
                showLoadingState(true);
                break;

            case NONE:
            default:
                // Keep current loading state or hide if no specific context
                break;
        }
    }

    private void handleAuthenticatedState() {
        showLoadingState(false);

        if (!isAdded() || isStateSaved()) return;

        Log.d(TAG, "Handling authenticated state");
        User authenticatedUser = authViewModel.getCurrentUserValue();

        if (authenticatedUser != null) {
            this.currentUser = authenticatedUser;
            Log.d(TAG, "User authenticated: " + this.currentUser.getUuid() +
                    ", plan: " + this.currentUser.getSubscriptionType());

            showProfilePage();

            // Check if this is a new user
            Boolean isNew = authViewModel.getIsNewUser().getValue();
            if (isNew != null && isNew) {
                openOnboardingPricingPage();
            }
        } else {
            Log.w(TAG, "Authenticated state but user data is null");
            showLoginPage();
        }
    }

    private void showLoadingState(boolean isLoading) {
        if (binding == null) return;

        Log.d(TAG, "Setting loading state: " + isLoading);

        if (isLoading) {
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.loginPage.getRoot().setVisibility(View.GONE);
            binding.profilePage.getRoot().setVisibility(View.GONE);
        } else {
            binding.progressBar.setVisibility(View.GONE);
        }
    }

    private void showLoginPage() {
        if (binding == null) return;

        Log.d(TAG, "Showing login page");
        binding.loginPage.getRoot().setVisibility(View.VISIBLE);
        binding.profilePage.getRoot().setVisibility(View.GONE);
    }

    private void showProfilePage() {
        if (binding == null) return;

        Log.d(TAG, "Showing profile page");
        binding.loginPage.getRoot().setVisibility(View.GONE);
        binding.profilePage.getRoot().setVisibility(View.VISIBLE);

        updateProfileUI();
    }

    private void updateProfileUI() {
        if (binding == null || currentUser == null) return;

        Log.d(TAG, "Updating profile UI for user: " + currentUser.getUuid() +
                ", plan: " + currentUser.getSubscriptionType());

        // Update user info
        binding.profilePage.userName.setText(currentUser.getDisplayName());
        binding.profilePage.userEmail.setText(currentUser.getEmail());

        // Update subscription info
        String subscriptionType = currentUser.getSubscriptionType();
        if (subscriptionType != null && !subscriptionType.isEmpty()) {
            binding.profilePage.planFrame.setVisibility(View.VISIBLE);
            binding.profilePage.planName.setText(formatPlanName(subscriptionType));
            Log.d(TAG, "Plan frame visible with plan: " + formatPlanName(subscriptionType));
        } else {
            binding.profilePage.planFrame.setVisibility(View.GONE);
            Log.d(TAG, "Plan frame hidden - no subscription type");
        }

        // Update subscribe button visibility
        boolean shouldShowSubscribeButton = subscriptionType != null &&
                (subscriptionType.equals("free") || subscriptionType.equals("test_drive"));

        binding.profilePage.subscribeButton.setVisibility(
                shouldShowSubscribeButton ? View.VISIBLE : View.GONE
        );

        Log.d(TAG, "Subscribe button visibility: " + (shouldShowSubscribeButton ? "VISIBLE" : "GONE") +
                " for plan: " + subscriptionType);

        // Load profile image
        if (currentUser.getAvatarUrl() != null) {
            Glide.with(this)
                    .load(currentUser.getAvatarUrl())
                    .into(binding.profilePage.userProfilePhoto);
        }
    }

    private String formatPlanName(String subscriptionType) {
        if (subscriptionType == null || subscriptionType.isEmpty()) {
            return "Free Plan";
        }

        // Replace underscores with spaces and capitalize each word
        return Arrays.stream(subscriptionType.split("_"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    private void setupClickListeners() {
        binding.loginPage.signInWithGoogle.setOnClickListener(v -> {
            Log.d(TAG, "Sign in with Google clicked");
            signInWithGoogle();
        });

        binding.loginPage.signup.setOnClickListener(v -> {
            Log.d(TAG, "Sign up clicked");
            openSignUpBottomSheet();
        });

        binding.profilePage.signOut.setOnClickListener(v -> {
            Log.d(TAG, "Sign out clicked");
            signOut();
        });

        binding.profilePage.settingsButton.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), SettingsActivity.class)));

        binding.profilePage.subscribeButton.setOnClickListener(v -> {
            Log.d(TAG, "Subscribe button clicked");
            createPlan();
        });

        binding.profilePage.about.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AboutActivity.class)));
    }

    private void openOnboardingPricingPage() {
        OnboardingPricingPageSheetFragment fragment = OnboardingPricingPageSheetFragment.newInstance();
        fragment.show(getParentFragmentManager(), fragment.getTag());
    }

    private void createPlan() {
        SubscriptionPlanSheetFragment subPage = SubscriptionPlanSheetFragment.newInstance();
        subPage.show(getParentFragmentManager(), subPage.getTag());
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}