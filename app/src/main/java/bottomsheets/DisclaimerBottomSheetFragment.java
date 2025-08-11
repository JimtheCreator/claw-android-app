package bottomsheets;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.claw.ai.R;
import com.claw.ai.databinding.FragmentDisclaimerBottomSheetBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import viewmodels.stripe_payments.SubscriptionViewModel;

public class DisclaimerBottomSheetFragment extends BottomSheetDialogFragment {
    private SubscriptionViewModel subscriptionViewModel;
    private Button confirmButton;
    private Button cancelButton;
    private ProgressBar progressBar;

    private FragmentDisclaimerBottomSheetBinding binding;

    public static DisclaimerBottomSheetFragment newInstance() {
        Bundle args = new Bundle();
        DisclaimerBottomSheetFragment fragment = new DisclaimerBottomSheetFragment();
        fragment.setArguments(args);
        return fragment;
    }


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentDisclaimerBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Initialize ViewModel
        subscriptionViewModel = new ViewModelProvider(requireActivity()).get(SubscriptionViewModel.class);

        TextView disclaimerText = binding.disclaimerText;
        confirmButton = binding.confirmButton;
        cancelButton = binding.cancelButton;
        progressBar = binding.progressBar;

        disclaimerText.setText("Cancelling your subscription will end your access to premium features at the end of the current billing period. Are you sure you want to proceed?");

        confirmButton.setOnClickListener(v -> {
            showLoading(true);
            // Cancel subscription with cancelAtPeriodEnd = true
            subscriptionViewModel.cancelSubscription(true);
        });

        cancelButton.setOnClickListener(v -> dismiss());

        // Observe loading state
        subscriptionViewModel.isSubscriptionLoading.observe(this, this::showLoading);

        subscriptionViewModel.cancellationResultEvent.observe(this, event -> {
            if (event != null) {
                var result = event.getContentIfNotHandled(); // The fragment will now successfully consume the event
                if (result != null) {
                    // Use the message directly from the backend result
                    String message = result.getMessage() != null ? result.getMessage() : "Subscription status updated.";
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();

                    // Always dismiss after the result is handled
                    dismiss();
                }
            }
        });

        // Observe errors
        subscriptionViewModel.error.observe(this, error -> {
            if (error != null) {
                showLoading(false);
                // Show error toast and dismiss the bottom sheet
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
                dismiss();
            }
        });
    }

    private void showLoading(boolean isLoading) {
        if (isLoading) {
            confirmButton.setVisibility(View.GONE);
            progressBar.setVisibility(View.VISIBLE);
            cancelButton.setEnabled(false);
        } else {
            confirmButton.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);
            cancelButton.setEnabled(true);
        }
    }
}