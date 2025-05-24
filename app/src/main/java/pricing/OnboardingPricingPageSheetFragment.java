package pricing;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.claw.ai.R;
import com.claw.ai.databinding.FragmentOnboardingPricePageBottomSheetBinding;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.stripe.android.PaymentConfiguration;
import com.stripe.android.paymentsheet.PaymentSheet;
import com.stripe.android.paymentsheet.PaymentSheetResult;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import models.NativeCheckoutResponse;
import models.Plan;
import viewmodels.stripe_payments.PricingViewModel;

public class OnboardingPricingPageSheetFragment extends BottomSheetDialogFragment {

    FragmentOnboardingPricePageBottomSheetBinding binding;
    private static final String TAG = "OnboardingPricing";
    private ProgressBar activeProgressBar;
    private TextView button_text;
    private PricingViewModel viewModel;
    private RelativeLayout global_initiated_pay_button;
    private PaymentSheet paymentSheet;
    private String currentPaymentClientSecret;
    private final Map<CardView, RadioButton> planCardToRadioMap = new HashMap<>();
    private final Map<CardView, RelativeLayout> planCardToRadioHolderMap = new HashMap<>();
    private CardView currentlySelectedCard = null;
    private ProgressBar testDriveProgressBar, starterProgressBar, proProgressBar;

    public static OnboardingPricingPageSheetFragment newInstance() {
        Bundle args = new Bundle();
        OnboardingPricingPageSheetFragment fragment = new OnboardingPricingPageSheetFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(PricingViewModel.class);
        paymentSheet = new PaymentSheet(this, this::onPaymentSheetResult);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentOnboardingPricePageBottomSheetBinding.inflate(inflater, container, false);
        testDriveProgressBar = binding.testDriveProgressBar; // Ensure this ID exists in your layout
        starterProgressBar = binding.starterProgressBar; // Ensure this ID exists in your layout
        proProgressBar = binding.proProgressBar; // Ensure this ID exists in your layout
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initiateViews();
        setupPlanCardMappings();
        observeViewModel();
        setupClickListeners();
        binding.closebutton.setOnClickListener(v -> dismiss());

        // Disable buttons until plans are loaded
        binding.initiateTestDrivePay.setEnabled(false);
        binding.initiateStarterPay.setEnabled(false);
        binding.initiateProPay.setEnabled(false);
    }

    private void setupPlanCardMappings() {
        planCardToRadioMap.put(binding.testDrivePlan, binding.testDriveRadio);
        planCardToRadioHolderMap.put(binding.testDrivePlan, binding.testDriveRadioHolder);
        planCardToRadioMap.put(binding.starterWeeklyPlan, binding.starterWeeklyRadio);
        planCardToRadioHolderMap.put(binding.starterWeeklyPlan, binding.starterWeeklyRadioHolder);
        planCardToRadioMap.put(binding.starterMonthlyPlan, binding.starterMonthlyRadio);
        planCardToRadioHolderMap.put(binding.starterMonthlyPlan, binding.starterMonthlyRadioHolder);
        planCardToRadioMap.put(binding.proWeeklyPlan, binding.proWeeklyRadio);
        planCardToRadioHolderMap.put(binding.proWeeklyPlan, binding.proWeeklyRadioHolder);
        planCardToRadioMap.put(binding.proMonthlyPlan, binding.proMonthlyRadio);
        planCardToRadioHolderMap.put(binding.proMonthlyPlan, binding.proMonthlyRadioHolder);
    }

    private void observeViewModel() {
        viewModel.isLoading.observe(getViewLifecycleOwner(), isLoading -> {
            // Handle loading state if needed elsewhere
        });

        viewModel.error.observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
                resetState();
            }
        });

        viewModel.testDrivePlanLiveData.observe(getViewLifecycleOwner(), plans -> {
            if (plans != null && !plans.isEmpty()) {
                bindPlanToUi(plans.get(0),
                        binding.testDrivePlanName, binding.testDrivePlanDescription,
                        binding.testDrivePrice, binding.testDriveBilling,
                        binding.testDrivePlan, binding.testDriveRadio, binding.testDriveRadioHolder, null);
                binding.initiateTestDrivePay.setEnabled(true);
            }
        });

        viewModel.starterPlansLiveData.observe(getViewLifecycleOwner(), plans -> {
            if (plans != null && plans.size() >= 2) {
                bindPlanToUi(plans.get(0), null, null,
                        binding.starterWeeklyPrice, binding.starterWeeklyBilling,
                        binding.starterWeeklyPlan, binding.starterWeeklyRadio, binding.starterWeeklyRadioHolder, null);
                bindPlanToUi(plans.get(1), null, null,
                        binding.starterMonthlyPrice, binding.starterMonthlyBilling,
                        binding.starterMonthlyPlan, binding.starterMonthlyRadio, binding.starterMonthlyRadioHolder, binding.starterMonthlySavePercentage);
                binding.initiateStarterPay.setEnabled(true); // Enable button
            }
        });

        viewModel.proPlansLiveData.observe(getViewLifecycleOwner(), plans -> {
            if (plans != null && plans.size() >= 2) {
                bindPlanToUi(plans.get(0), null, null,
                        binding.proWeeklyPrice, binding.proWeeklyBilling,
                        binding.proWeeklyPlan, binding.proWeeklyRadio, binding.proWeeklyRadioHolder, null);
                bindPlanToUi(plans.get(1), null, null,
                        binding.proMonthlyPrice, binding.proMonthlyBilling,
                        binding.proMonthlyPlan, binding.proMonthlyRadio, binding.proMonthlyRadioHolder, binding.proMonthlySavePercentage);
                binding.initiateProPay.setEnabled(true); // Enable button
            }
        });

        viewModel.selectedPlanId.observe(getViewLifecycleOwner(), selectedId -> {
            Log.d(TAG, "Observed selected plan ID: " + selectedId);

            if (selectedId != null) {
                // Check Starter plans
                List<Plan> starterPlans = viewModel.starterPlansLiveData.getValue();
                boolean isStarterSelected = starterPlans != null && starterPlans.stream()
                        .anyMatch(p -> p.getId().equals(selectedId));
                binding.initiateStarterPay.setEnabled(isStarterSelected);

                // Check Pro plans
                List<Plan> proPlans = viewModel.proPlansLiveData.getValue();
                boolean isProSelected = proPlans != null && proPlans.stream()
                        .anyMatch(p -> p.getId().equals(selectedId));
                binding.initiateProPay.setEnabled(isProSelected);
            } else {
                // No plan selected, disable both
                binding.initiateStarterPay.setEnabled(false);
                binding.initiateProPay.setEnabled(false);
            }
        });

        viewModel.paymentSheetParametersEvent.observe(getViewLifecycleOwner(), event -> {
            NativeCheckoutResponse response = event.getContentIfNotHandled();
            if (response != null) {
                if (response.isPaymentRequired()) {
                    paymentSheet.presentWithPaymentIntent(
                            response.getClientSecret(),
                            new PaymentSheet.Configuration(getString(R.string.app_name),
                                    new PaymentSheet.CustomerConfiguration(response.getCustomerId(), response.getEphemeralKeySecret()))
                    );
                } else {
                    // No payment required, handle success directly
                    Toast.makeText(getContext(), response.getMessage(), Toast.LENGTH_SHORT).show();
                    enableAllInteractiveElements();
                    if (activeProgressBar != null) activeProgressBar.setVisibility(View.GONE);
                    if (button_text != null) button_text.setVisibility(View.VISIBLE);
                    if (global_initiated_pay_button != null) {
                        global_initiated_pay_button.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.rounded_beige));
                    }
                    dismiss(); // Close the sheet
                }
            }
        });

        viewModel.paymentResultEvent.observe(getViewLifecycleOwner(), event -> {
            String resultMessage = event.getContentIfNotHandled();
            if (resultMessage != null) {
                if (resultMessage.startsWith("success:")) {
                    // Do not dismiss here; wait for subscription status
                } else {
                    Toast.makeText(getContext(), resultMessage, Toast.LENGTH_LONG).show();
                }
            }
        });

        // Observe subscription status
        viewModel.getSubscriptionStatus().observe(getViewLifecycleOwner(), status -> {
            if (status != null) {
                if (!status.equals("free") && !status.equals("payment_failed")) {
                    enableAllInteractiveElements();
                    if (activeProgressBar != null) {
                        activeProgressBar.setVisibility(View.GONE); // Hide when status is received
                    }

                    if (global_initiated_pay_button != null)
                        global_initiated_pay_button.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.rounded_beige));
                    if (button_text != null)
                        button_text.setVisibility(View.VISIBLE);

                    // Subscription successful
                    dismiss();
                    Toast.makeText(getContext(), "Subscription successful!", Toast.LENGTH_SHORT).show();
                    viewModel.stopListeningToSubscriptionStatus();
                } else if (status.equals("payment_failed")) {
                    enableAllInteractiveElements();
                    if (activeProgressBar != null) {
                        activeProgressBar.setVisibility(View.GONE); // Hide when status is received
                    }

                    if (global_initiated_pay_button != null)
                        global_initiated_pay_button.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.rounded_beige));
                    if (button_text != null)
                        button_text.setVisibility(View.VISIBLE);

                    // Subscription failed
                    Toast.makeText(requireContext(), "Subscription failed, insufficient funds.", Toast.LENGTH_SHORT).show();
                    viewModel.stopListeningToSubscriptionStatus();
                }
            }
        });
    }

    private void onPaymentSheetResult(PaymentSheetResult paymentSheetResult) {
        if (paymentSheetResult instanceof PaymentSheetResult.Completed) {
            Log.i(TAG, "Payment completed!");
            // Show processing UI and start listening to Firebase
            viewModel.startListeningToSubscriptionStatus();
        } else {
            enableAllInteractiveElements();
            if (activeProgressBar != null) {
                activeProgressBar.setVisibility(View.GONE); // Hide on failure or cancellation
            }

            if (global_initiated_pay_button != null)
                global_initiated_pay_button.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.rounded_beige));
            if (button_text != null)
                button_text.setVisibility(View.VISIBLE);


            if (paymentSheetResult instanceof PaymentSheetResult.Canceled) {
                Log.i(TAG, "Payment canceled.");
                Toast.makeText(getContext(), "Payment canceled.", Toast.LENGTH_SHORT).show();
            } else if (paymentSheetResult instanceof PaymentSheetResult.Failed) {
                PaymentSheetResult.Failed failedResult = (PaymentSheetResult.Failed) paymentSheetResult;
                Log.e(TAG, "Payment failed", failedResult.getError());
                Toast.makeText(getContext(), "Payment failed: " + failedResult.getError().getLocalizedMessage(), Toast.LENGTH_LONG).show();
            }
            viewModel.handlePaymentResult(
                    paymentSheetResult instanceof PaymentSheetResult.Completed ? "Payment completed!" :
                            paymentSheetResult instanceof PaymentSheetResult.Canceled ? "Payment canceled." :
                                    "Payment failed.", paymentSheetResult instanceof PaymentSheetResult.Completed);
        }
    }

    private void bindPlanToUi(Plan plan, TextView nameView, TextView descriptionView,
                              TextView priceView, TextView billingView, CardView planCard,
                              RadioButton radioButton, RelativeLayout radioHolder, TextView savePercentageView) {
        if (nameView != null) nameView.setText(plan.getDisplayName());
        if (descriptionView != null) descriptionView.setText(plan.getDescription());
        if (priceView != null) priceView.setText(plan.getPriceText());
        if (billingView != null) billingView.setText(plan.getBillingCycleText());
        if (savePercentageView != null && plan.getSavePercentageText() != null && !plan.getSavePercentageText().isEmpty()) {
            savePercentageView.setText(plan.getSavePercentageText());
            savePercentageView.setVisibility(View.VISIBLE);
        } else if (savePercentageView != null) {
            savePercentageView.setVisibility(View.GONE);
        }
        planCard.setOnClickListener(v -> {
            viewModel.selectPlan(plan.getId());
            selectPlanCard(planCard);
        });
    }

    private void selectPlanCard(CardView selectedCard) {
        if (currentlySelectedCard != null && currentlySelectedCard != selectedCard) {
            currentlySelectedCard.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.invicibleColor));
            RadioButton prevRadio = planCardToRadioMap.get(currentlySelectedCard);
            if (prevRadio != null) prevRadio.setChecked(false);
            RelativeLayout prevRadioHolder = planCardToRadioHolderMap.get(currentlySelectedCard);
            if (prevRadioHolder != null) prevRadioHolder.setVisibility(View.INVISIBLE);
        }
        selectedCard.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.transparent_beige_stroke));
        RadioButton currentRadio = planCardToRadioMap.get(selectedCard);
        if (currentRadio != null) currentRadio.setChecked(true);
        RelativeLayout currentRadioHolder = planCardToRadioHolderMap.get(selectedCard);
        if (currentRadioHolder != null) currentRadioHolder.setVisibility(View.VISIBLE);
        currentlySelectedCard = selectedCard;
    }

    private void setupClickListeners() {
        binding.initiateTestDrivePay.setOnClickListener(v -> {
            resetState();
            activeProgressBar = binding.testDriveProgressBar;
            global_initiated_pay_button = binding.initiateTestDrivePay;
            button_text = binding.getTestDrivePlan;
            global_initiated_pay_button.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.grey_rounded_view));
            button_text.setVisibility(View.GONE);
            activeProgressBar.setVisibility(View.VISIBLE);

            Plan testDrive = (viewModel.testDrivePlanLiveData.getValue() != null && !viewModel.testDrivePlanLiveData.getValue().isEmpty())
                    ? viewModel.testDrivePlanLiveData.getValue().get(0) : null;
            if (testDrive != null) {
                viewModel.selectPlan(testDrive.getId());
                selectPlanCard(binding.testDrivePlan);
                disableAllInteractiveElements();
                viewModel.initiatePaymentSheetFlow();
            } else {
                Toast.makeText(getContext(), "Test Drive plan not available.", Toast.LENGTH_SHORT).show();
                resetState();
            }
        });

        binding.initiateStarterPay.setOnClickListener(v -> {
            resetState();
            activeProgressBar = binding.starterProgressBar;
            button_text = binding.getStarterPlan;
            global_initiated_pay_button = binding.initiateStarterPay;
            global_initiated_pay_button.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.grey_rounded_view));
            button_text.setVisibility(View.GONE);
            activeProgressBar.setVisibility(View.VISIBLE);

            String selectedPlanId = viewModel.selectedPlanId.getValue();
            List<Plan> starterPlans = viewModel.starterPlansLiveData.getValue();
            if (selectedPlanId != null && starterPlans != null && starterPlans.stream()
                    .anyMatch(p -> p.getId().equals(selectedPlanId))) {
                disableAllInteractiveElements();
                viewModel.initiatePaymentSheetFlow();
            } else {
                Toast.makeText(getContext(), "Please select a Starter plan option.", Toast.LENGTH_SHORT).show();
                resetState();
            }
        });

        binding.initiateProPay.setOnClickListener(v -> {
            resetState(); // Hide all progress bars first
            activeProgressBar = binding.proProgressBar; // Set the active one

            global_initiated_pay_button = binding.initiateProPay;
            button_text = binding.getProPlan;
            global_initiated_pay_button.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.grey_rounded_view));

            button_text.setVisibility(View.GONE);
            activeProgressBar.setVisibility(View.VISIBLE);

            String selectedPlanId = viewModel.selectedPlanId.getValue();
            String proWeeklyId = getPlanIdByType(viewModel.proPlansLiveData.getValue(), "pro_weekly");
            String proMonthlyId = getPlanIdByType(viewModel.proPlansLiveData.getValue(), "pro_monthly");
            if (selectedPlanId != null && (selectedPlanId.equals(proWeeklyId) || selectedPlanId.equals(proMonthlyId))) {
                disableAllInteractiveElements();
                viewModel.initiatePaymentSheetFlow();
            } else {
                Toast.makeText(getContext(), "Please select a Pro plan option.", Toast.LENGTH_SHORT).show();
                resetState();
            }
        });
    }

    private void resetState() {
        binding.testDriveProgressBar.setVisibility(View.GONE);
        binding.starterProgressBar.setVisibility(View.GONE);
        binding.proProgressBar.setVisibility(View.GONE);

        binding.getTestDrivePlan.setVisibility(View.VISIBLE);
        binding.getStarterPlan.setVisibility(View.VISIBLE);
        binding.getProPlan.setVisibility(View.VISIBLE);

        binding.initiateProPay.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.rounded_beige));
        binding.initiateStarterPay.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.rounded_beige));
        binding.initiateTestDrivePay.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.rounded_beige));
    }

    private String getPlanIdByType(List<Plan> plans, String type) {
        if (plans == null) return null;
        for (Plan p : plans) {
            if (p.getType().equalsIgnoreCase(type)) {
                return p.getId();
            }
        }
        return null;
    }

    private void initiateViews() {
        // Initialize additional views if needed
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    private void disableAllInteractiveElements() {
        List<RelativeLayout> payButtons = Arrays.asList(
                binding.initiateTestDrivePay,
                binding.initiateStarterPay,
                binding.initiateProPay
        );
        for (RelativeLayout button : payButtons) {
            button.setClickable(false);
        }
        List<CardView> planCards = Arrays.asList(
                binding.testDrivePlan,
                binding.starterWeeklyPlan,
                binding.starterMonthlyPlan,
                binding.proWeeklyPlan,
                binding.proMonthlyPlan
        );
        for (CardView card : planCards) {
            card.setClickable(false);
            card.setAlpha(0.5f); // Visually indicate disabled state
        }
    }

    private void enableAllInteractiveElements() {
        List<RelativeLayout> payButtons = Arrays.asList(
                binding.initiateTestDrivePay,
                binding.initiateStarterPay,
                binding.initiateProPay
        );
        for (RelativeLayout button : payButtons) {
            button.setClickable(true);
        }
        List<CardView> planCards = Arrays.asList(
                binding.testDrivePlan,
                binding.starterWeeklyPlan,
                binding.starterMonthlyPlan,
                binding.proWeeklyPlan,
                binding.proMonthlyPlan
        );
        for (CardView card : planCards) {
            card.setClickable(true);
            card.setAlpha(1.0f); // Restore normal appearance
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        View view = getView();
        if (view != null) {
            View parent = (View) view.getParent();
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(parent);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
            ViewGroup.LayoutParams layoutParams = parent.getLayoutParams();
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            parent.setLayoutParams(layoutParams);
        }
    }
}