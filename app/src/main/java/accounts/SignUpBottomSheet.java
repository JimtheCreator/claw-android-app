package accounts;

import static bottomsheets.PolicyBottomSheetFragmentKt.showPolicyBottomSheet;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.claw.ai.databinding.FragmentSignUpBottomSheetBinding;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import models.PolicyType;
import pricing.OnboardingPricingPageSheetFragment;
import viewmodels.google_login.AuthViewModel;
import viewmodels.telegram_login.TelegramAuthViewModel;

public class SignUpBottomSheet extends BottomSheetDialogFragment {
    private FragmentSignUpBottomSheetBinding binding;
    private AuthViewModel authViewModel;
    private TelegramAuthViewModel telegramAuthViewModel;
    private static final String TAG = "SignUpBottomSheet";

    public static SignUpBottomSheet newInstance() {
        Bundle args = new Bundle();
        SignUpBottomSheet fragment = new SignUpBottomSheet();
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSignUpBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        authViewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        telegramAuthViewModel = new ViewModelProvider(requireActivity()).get(TelegramAuthViewModel.class);
        setupObservers();
        setupClickListeners();
    }

    private void setupObservers() {
        authViewModel.getAuthState().observe(getViewLifecycleOwner(), authState -> {
            AuthViewModel.LoadingContext currentLoadingContext = authViewModel.getLoadingContext().getValue();
            switch (authState) {
                case LOADING:
                    // Show loading spinner only if this bottom sheet initiated the action.
                    if (currentLoadingContext == AuthViewModel.LoadingContext.BOTTOM_SHEET_SIGN_UP) {
                        showLoading(true);
                    }
                    // DO NOT DISMISS HERE. Let the Google UI appear on top.
                    break;
                case AUTHENTICATED:
                    // If authentication was successful (potentially initiated by this sheet or elsewhere)
                    // and this sheet was the one loading, or just generally on auth success if it's open.
                    showLoading(false);
                    if (isAdded() && !isStateSaved()) {
                        Toast.makeText(getContext(), "Sign-up successful!", Toast.LENGTH_SHORT).show();
                        dismiss();
                    }
                    break;
                case ERROR:
                    // Stop loading spinner if this bottom sheet was the one loading.
                    if (currentLoadingContext == AuthViewModel.LoadingContext.BOTTOM_SHEET_SIGN_UP) {
                        showLoading(false);
                    }
                    // Do not dismiss on error. The error message will be shown via the errorMessage observer.
                    // The user can then retry or close the bottom sheet manually.
                    break;
                case UNAUTHENTICATED:
                    // E.g., if user signs out from another part of the app while this is open.
                    showLoading(false);
                    break;
            }
        });

        authViewModel.getErrorMessage().observe(getViewLifecycleOwner(), errorMessage -> {
            if (errorMessage != null && !errorMessage.isEmpty()) {
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
                // Ensure spinner is stopped if an error occurs during its specific operation
                if (authViewModel.getLoadingContext().getValue() == AuthViewModel.LoadingContext.BOTTOM_SHEET_SIGN_UP) {
                    showLoading(false);
                }
            }
        });
    }


    private void showLoading(boolean isLoading) {
        if (binding == null) return; // View could be destroyed
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.signUpWithGoogle.setEnabled(!isLoading);
        binding.dismissSignupWithGoogleLayout.setVisibility(isLoading ? View.GONE : View.VISIBLE);

        if (isLoading){
            setCancelable(false);
        }else {
            setCancelable(true);
        }
    }

    private void setupClickListeners() {
        binding.termsOfServiceTag.setOnClickListener(v -> {
            showPolicyBottomSheet(requireActivity(), PolicyType.TERMS_OF_SERVICE);
        });

        binding.signUpWithGoogle.setOnClickListener(v -> signUpWithGoogle());
    }

    private void cancelAuthentication() {
        // Only attempt to cancel if authentication is in progress
        if (authViewModel.getAuthState().getValue() == AuthViewModel.AuthState.LOADING &&
                authViewModel.getLoadingContext().getValue() == AuthViewModel.LoadingContext.BOTTOM_SHEET_SIGN_UP) {
            // Reset the authentication state
            authViewModel.cancelAuthentication();
        }
    }

    private void signUpWithGoogle() {
        // We don't call showLoading(true) here directly.
        // The AuthState.LOADING state (triggered by onAuthInProgress) will handle it,
        // ensuring the spinner is tied to the ViewModel's state for this specific context.
        authViewModel.signInWithGoogleFromBottomSheet(this);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onStart() {
        super.onStart();
        // Ensures the bottom sheet is expanded
        View view = getView();
        if (view != null) {
            View parent = (View) view.getParent();
            if (parent != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(parent);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true); // Optional: prevents it from being collapsed
            }
        }
    }
}
