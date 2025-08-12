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
import timber.log.Timber;
import viewmodels.google_login.AuthViewModel;

public class MoreTabFragment extends Fragment {
    private FragmentMoreTabBinding binding;
    private AuthViewModel authViewModel;
    private User currentUser;


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
        initializeViews();
        setupClickListeners();
    }

    private void setupObservers() {
        // Observe auth state changes
        authViewModel.getAuthState().observe(getViewLifecycleOwner(), authState -> {
            if (binding == null) return;
            AuthViewModel.LoadingContext context = authViewModel.getLoadingContext().getValue();
            switch (authState) {
                case LOADING:
                    Log.d("MoreTab", "LOADING");
                    showLoadingState(context != AuthViewModel.LoadingContext.BOTTOM_SHEET_SIGN_UP);
                case AUTHENTICATED:
                    Log.d("MoreTab", "AUTHENTICATED");
                    showLoadingState(false);
                    if (isAdded() && !isStateSaved()) {
                        Log.d("MoreTabFragment", "User is authenticated (from AuthState observer).");
                        User authenticatedUser = authViewModel.getCurrentUser().getValue();

                        if (authenticatedUser != null) {
                            this.currentUser = authenticatedUser;
                            Log.d("MoreTabFragment", "User data retrieved from ViewModel: " + this.currentUser.getUuid() + ". Showing profile and updating limits.");

                            showProfilePage();

                            Boolean isNew = authViewModel.getIsNewUser().getValue();
                            if (isNew != null && isNew) {
                                openOnboardingPricingPage();
                            }
                        } else {
                            Log.w("MoreTabFragment", "User is authenticated, but user data is currently null in ViewModel. Usage limits might not be updated immediately by this block.");
                            showLoginPage();
                        }
                    }
                    break;
                case UNAUTHENTICATED:
                    showLoadingState(false);
                    showLoginPage();
                    break;
                case ERROR: // Treat ERROR as UNAUTHENTICATED for watchlist display
                    Timber.d("Auth state: %s", authState.toString());
                    showLoadingState(false);
                    Log.d("MoreTab", "ERROR ROOT");
                    if (!authViewModel.isUserSignedIn()) {
                        Log.d("MoreTab", "ERROR");
                        showLoginPage();
                    }
                    break;
            }
        });

        // ADD THIS NEW OBSERVER: Observe real-time user changes
        authViewModel.getCurrentUser().observe(getViewLifecycleOwner(), user -> {
            if (user != null && authViewModel.getAuthState().getValue() == AuthViewModel.AuthState.AUTHENTICATED) {
                this.currentUser = user;
                Log.d("MoreTabFragment", "User data updated in real-time. New plan: " + user.getSubscriptionType());
                showProfilePage(); // This will refresh the UI with the new plan
            }
        });

        authViewModel.getErrorMessage().observe(getViewLifecycleOwner(), errorMessage -> {
            if (errorMessage != null && !errorMessage.isEmpty()) {
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
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

    private void openOnboardingPricingPage() {
        OnboardingPricingPageSheetFragment fragment = OnboardingPricingPageSheetFragment.newInstance();
        fragment.show(getParentFragmentManager(), fragment.getTag());
    }

    private void showLoginPage() {
        if (binding == null) return;
        binding.loginPage.getRoot().setVisibility(View.VISIBLE);
        binding.profilePage.getRoot().setVisibility(View.GONE);
    }

    // Method 1: Simple string replacement and capitalization
    private String formatPlanName(String subscriptionType) {
        if (subscriptionType == null || subscriptionType.isEmpty()) {
            return "Free Plan";
        }

        // Replace underscores with spaces and capitalize each word
        return Arrays.stream(subscriptionType.split("_"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    private void showProfilePage() {
        if (binding == null) return;
        binding.loginPage.getRoot().setVisibility(View.GONE);
        binding.profilePage.getRoot().setVisibility(View.VISIBLE);

        if (currentUser == null) return;

        binding.profilePage.userName.setText(currentUser.getDisplayName());
        binding.profilePage.userEmail.setText(currentUser.getEmail());
        if (currentUser.getSubscriptionType() != null) {
            binding.profilePage.planFrame.setVisibility(View.VISIBLE);
            binding.profilePage.planName.setText(formatPlanName(currentUser.getSubscriptionType()));
        } else {
            binding.profilePage.planFrame.setVisibility(View.GONE);
        }

        if (currentUser.getSubscriptionType().equals("free") || currentUser.getSubscriptionType().equals("test_drive")) {
            binding.profilePage.subscribeButton.setVisibility(View.VISIBLE);
        } else {
            binding.profilePage.subscribeButton.setVisibility(View.GONE);
        }

        Glide.with(this).load(currentUser.getAvatarUrl()).into(binding.profilePage.userProfilePhoto);
    }

    private void setupClickListeners() {
        binding.loginPage.signInWithGoogle.setOnClickListener(v -> signInWithGoogle());
        binding.loginPage.signup.setOnClickListener(v -> openSignUpBottomSheet());
        binding.profilePage.signOut.setOnClickListener(v -> signOut());
        binding.profilePage.settingsButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), SettingsActivity.class)));
        binding.profilePage.subscribeButton.setOnClickListener(v -> createPlan());
    }

    private void createPlan() {
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