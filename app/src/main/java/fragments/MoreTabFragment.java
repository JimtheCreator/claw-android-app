package fragments;

import android.os.Bundle;
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

import accounts.SignUpBottomSheet;
import pricing.OnboardingPricingPageSheetFragment;
import pricing.SubscriptionPlanSheetFragment;
import viewmodels.google_login.AuthViewModel;


public class MoreTabFragment extends Fragment {

    private FragmentMoreTabBinding binding;
    private AuthViewModel authViewModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentMoreTabBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        authViewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        // Ensure web_client_id is correctly defined in your strings.xml
        authViewModel.initialize(requireActivity(), getString(R.string.web_client_id));
        setupObservers();
        initializeViews();
        setupClickListeners();
    }

    private void setupObservers() {
        authViewModel.getAuthState().observe(getViewLifecycleOwner(), authState -> {
            AuthViewModel.LoadingContext context = authViewModel.getLoadingContext().getValue();

            if (authState == AuthViewModel.AuthState.LOADING) {
                // Only show MoreTabFragment's progress bar if the loading context is not for the bottom sheet
                if (context == AuthViewModel.LoadingContext.BOTTOM_SHEET_SIGN_UP) {
                    showLoadingState(false); // Explicitly hide MoreTabFragment's progress bar
                } else {
                    // General loading (e.g., from MoreTabFragment's own sign-in button or sign-out)
                    showLoadingState(true);
                }
            } else {
                // For AUTHENTICATED, UNAUTHENTICATED, ERROR states, hide the loading progress.
                showLoadingState(false);
                switch (authState) {
                    case AUTHENTICATED:
                        if (isAdded() && !isStateSaved()) {
                            // Check if this is a new user
                            Boolean isNew = authViewModel.getIsNewUser().getValue();
                            if (isNew != null && isNew) {
                                showProfilePage();
                                // Only show onboarding for new users
                                openOnboardingPricingPage();
                                // updateProfileUI(user); // Your existing method to populate user details
                            } else {
                                showProfilePage();
                                // updateProfileUI(user); // Your existing method to populate user details
                            }
                        }
                        break;
                    case UNAUTHENTICATED:
                        showLoginPage();
                        break;
                    case ERROR:
                        // Error message is handled by its own observer.
                        // Ensure UI is in a reasonable state, e.g., show login page if not authenticated.
                        if (!authViewModel.isUserSignedIn()) {
                            showLoginPage();
                        }
                        break;
                }
            }
        });

//        authViewModel.getCurrentUser().observe(getViewLifecycleOwner(), user -> {
//            if (user != null && authViewModel.getAuthState().getValue() == AuthViewModel.AuthState.AUTHENTICATED) {
//                // Update UI with user data, ensure profile page is shown
//                showProfilePage(); // Might be redundant if AuthState observer handles it, but good for direct user updates
////                updateProfileUI(user); // Your existing method to populate user details
//            }
//        });

        authViewModel.getErrorMessage().observe(getViewLifecycleOwner(), errorMessage -> {
            if (errorMessage != null && !errorMessage.isEmpty()) {
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showLoadingState(boolean isLoading) {
        if (binding == null) return; // Guard against calls after onDestroyView
        if (isLoading) {
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.loginPage.getRoot().setVisibility(View.GONE);
            binding.profilePage.getRoot().setVisibility(View.GONE);
        } else {
            binding.progressBar.setVisibility(View.GONE);
            // Visibility of loginPage/profilePage will be handled by AuthState changes
        }
    }

    private void openOnboardingPricingPage() {
        OnboardingPricingPageSheetFragment onboardingPricingPageSheetFragment = OnboardingPricingPageSheetFragment.newInstance();
        onboardingPricingPageSheetFragment.show(getParentFragmentManager(), onboardingPricingPageSheetFragment.getTag());
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

//    private void updateProfileUI(models.User user) {
//        // Update profile UI with user data
//        binding.profilePage.displayName.setText(user.getDisplayName());
//        binding.profilePage.email.setText(user.getEmail());
//
//        // Load profile image with Glide
//        if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
//            Glide.with(this)
//                    .load(user.getAvatarUrl())
//                    .placeholder(R.drawable.default_avatar)
//                    .into(binding.profilePage.profileImage);
//        }
//
//        // Update subscription info
//        binding.profilePage.subscriptionType.setText(user.getSubscriptionType());
//    }

    private void setupClickListeners() {
        // Login page click listeners
        binding.loginPage.signInWithGoogle.setOnClickListener(v -> signInWithGoogle());
        binding.loginPage.signup.setOnClickListener(v -> openSignUpBottomSheet());

        // Profile page click listeners
        binding.profilePage.signOut.setOnClickListener(v -> signOut());

        binding.profilePage.subscribe.setOnClickListener(v -> testingPayment());
    }

    private void testingPayment() {
        SubscriptionPlanSheetFragment sub_page = SubscriptionPlanSheetFragment.newInstance();
        sub_page.show(getParentFragmentManager(), sub_page.getTag());
    }

    private void signInWithGoogle() {
        // Call the general sign-in method from MoreTabFragment
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
        // Check if user is already authenticated
        if (authViewModel.isUserSignedIn()) {
            showProfilePage();
        } else {
            showLoginPage();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Clean up view binding
        binding = null;
    }
}