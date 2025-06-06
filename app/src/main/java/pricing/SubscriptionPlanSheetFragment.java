package pricing;

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
import com.claw.ai.databinding.FragmentSubscriptionPlanBottomSheetBinding;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.stripe.android.paymentsheet.PaymentSheet;
import com.stripe.android.paymentsheet.PaymentSheetResult;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bottomsheets.PlanChangeWarningBottomSheetFragment;
import backend.results.NativeCheckoutResponse;
import models.Plan;
import models.User;
import viewmodels.stripe_payments.SubscriptionViewModel;

public class SubscriptionPlanSheetFragment extends BottomSheetDialogFragment {
    private FragmentSubscriptionPlanBottomSheetBinding binding;
    private SubscriptionViewModel viewModel;
    private PaymentSheet paymentSheet;
    private ProgressBar activeProgressBar;
    private TextView buttonText;
    private static final String TAG = "SubscriptionPlanSheetFragment";
    private RelativeLayout initiatedPayButton;
    private ProgressBar testDriveProgressBar, starterProgressBar, proProgressBar;
    private TextView getTestDrivePlan, getStarterPlan, getProPlan;
    private final Map<CardView, RadioButton> planCardToRadioMap = new HashMap<>();
    private final Map<CardView, RelativeLayout> planCardToRadioHolderMap = new HashMap<>();
    private CardView currentlySelectedCard = null;
    private User currentUser; // Store the user data here

    public static SubscriptionPlanSheetFragment newInstance() {
        Bundle args = new Bundle();
        SubscriptionPlanSheetFragment fragment = new SubscriptionPlanSheetFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSubscriptionPlanBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SubscriptionViewModel.class);

        // Observe the LiveData
        viewModel.getCurrentUser().observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
                currentUser = user; // Update the stored user when it changes
            }
        });

        setupPlanCardMappings();
        paymentSheet = new PaymentSheet(this, this::onPaymentSheetResult);

        // Initialize UI elements
        testDriveProgressBar = binding.testDriveProgressBar;
        starterProgressBar = binding.starterProgressBar;
        proProgressBar = binding.proProgressBar;
        getTestDrivePlan = binding.getTestDrivePlan;
        getStarterPlan = binding.getStarterPlan;
        getProPlan = binding.getProPlan;

        setupClickListeners();
        observeViewModel();
    }

    // In SubscriptionPlanSheetFragment.java, update setupClickListeners()
    private void setupClickListeners() {
        binding.initiateTestDrivePay.setOnClickListener(v -> {
            if (currentUser != null){
                User user = currentUser;
                boolean shouldShowWarning = (user.isUserPaid() || !"free".equals(user.getSubscriptionType()));
                resetState();
                activeProgressBar = testDriveProgressBar;
                buttonText = getTestDrivePlan;
                initiatedPayButton = binding.initiateTestDrivePay;
                initiatedPayButton.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.grey_rounded_view));
                buttonText.setVisibility(View.GONE);
                activeProgressBar.setVisibility(View.VISIBLE);
                selectPlanCard(binding.testDrivePlan);
                disableAllInteractiveElements();

                if (viewModel.testDrivePlanLiveData.getValue() != null) {
                    String selectedPlanId = viewModel.testDrivePlanLiveData.getValue().getId();
                    viewModel.selectPlan(selectedPlanId);
                    if (shouldShowWarning) {
                        String changeType = viewModel.getChangeType(selectedPlanId);
                        PlanChangeWarningBottomSheetFragment dialog = PlanChangeWarningBottomSheetFragment.newInstance(changeType);
                        dialog.setOnConfirmListener(() -> viewModel.initiatePaymentSheetFlow());
                        dialog.show(getChildFragmentManager(), "warning");
                    } else {
                        viewModel.initiatePaymentSheetFlow();
                    }
                }
            }
        });

        binding.initiateStarterPay.setOnClickListener(v -> {
            List<Plan> starterPlans = viewModel.starterPlansLiveData.getValue();
            User user = viewModel.getCurrentUser().getValue();
            boolean shouldShowWarning = user != null && (user.isUserPaid() || !"free".equals(user.getSubscriptionType()));
            if (starterPlans != null) {
                String selectedPlanId;
                if (starterPlans.size() == 1) {
                    Plan plan = starterPlans.get(0);
                    viewModel.selectPlan(plan.getId());
                    selectedPlanId = plan.getId();
                    if (plan.getType().equals("starter_weekly")) {
                        selectPlanCard(binding.starterWeeklyPlan);
                    } else {
                        selectPlanCard(binding.starterMonthlyPlan);
                    }
                } else {
                    selectedPlanId = viewModel.selectedPlanId.getValue();
                    if (selectedPlanId == null || !starterPlans.stream().anyMatch(p -> p.getId().equals(selectedPlanId))) {
                        Toast.makeText(getContext(), "Please select a Starter plan.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                Runnable proceed = () -> {
                    resetState();
                    activeProgressBar = starterProgressBar;
                    buttonText = getStarterPlan;
                    initiatedPayButton = binding.initiateStarterPay;
                    initiatedPayButton.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.grey_rounded_view));
                    buttonText.setVisibility(View.GONE);
                    activeProgressBar.setVisibility(View.VISIBLE);
                    disableAllInteractiveElements();
                    viewModel.initiatePaymentSheetFlow();
                };

                String currentPlan = user != null ? user.getSubscriptionType() : "free";
                String selectedPlanType = viewModel.getPlanTypeFromId(selectedPlanId);
                Log.d(TAG, "Current Plan: " + currentPlan + ", Selected Plan Type: " + selectedPlanType + ", Selected Plan ID: " + selectedPlanId);
                if ("pro_weekly".equals(currentPlan) && "starter_monthly".equals(selectedPlanType)) {
                    Log.d(TAG, "Triggering special_downgrade_expensive for pro_weekly to starter_monthly");
                    PlanChangeWarningBottomSheetFragment dialog = PlanChangeWarningBottomSheetFragment.newInstance("special_downgrade_expensive");
                    dialog.setOnConfirmListener(proceed);
                    dialog.show(getChildFragmentManager(), "warning");
                } else if (shouldShowWarning) {
                    String changeType = viewModel.getChangeType(selectedPlanId);
                    Log.d(TAG, "Fallback changeType: " + changeType);
                    PlanChangeWarningBottomSheetFragment dialog = PlanChangeWarningBottomSheetFragment.newInstance(changeType);
                    dialog.setOnConfirmListener(proceed);
                    dialog.show(getChildFragmentManager(), "warning");
                } else {
                    Log.d(TAG, "No warning needed, proceeding directly");
                    proceed.run();
                }
            }
        });

        binding.initiateProPay.setOnClickListener(v -> {
            List<Plan> proPlans = viewModel.proPlansLiveData.getValue();
            User user = viewModel.getCurrentUser().getValue();
            boolean shouldShowWarning = user != null && (user.isUserPaid() || !"free".equals(user.getSubscriptionType()));
            if (proPlans != null) {
                String selectedPlanId;
                if (proPlans.size() == 1) {
                    Plan plan = proPlans.get(0);
                    viewModel.selectPlan(plan.getId());
                    selectedPlanId = plan.getId();
                    if (plan.getType().equals("pro_weekly")) {
                        selectPlanCard(binding.proWeeklyPlan);
                    } else {
                        selectPlanCard(binding.proMonthlyPlan);
                    }
                } else {
                    selectedPlanId = viewModel.selectedPlanId.getValue();
                    if (selectedPlanId == null || proPlans.stream().noneMatch(p -> p.getId().equals(selectedPlanId))) {
                        Toast.makeText(getContext(), "Please select a Pro plan.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                Runnable proceed = () -> {
                    resetState();
                    activeProgressBar = proProgressBar;
                    buttonText = getProPlan;
                    initiatedPayButton = binding.initiateProPay;
                    initiatedPayButton.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.grey_rounded_view));
                    buttonText.setVisibility(View.GONE);
                    activeProgressBar.setVisibility(View.VISIBLE);
                    disableAllInteractiveElements();
                    viewModel.initiatePaymentSheetFlow();
                };

                String currentPlan = user != null ? user.getSubscriptionType() : "free";
                String selectedPlanType = viewModel.getPlanTypeFromId(selectedPlanId);
                if ("starter_monthly".equals(currentPlan) && "pro_weekly".equals(selectedPlanType)) {
                    // Special case: starter_monthly to pro_weekly
                    PlanChangeWarningBottomSheetFragment dialog = PlanChangeWarningBottomSheetFragment.newInstance("special_downgrade");
                    dialog.setOnConfirmListener(proceed);
                    dialog.show(getChildFragmentManager(), "warning");
                } else if (shouldShowWarning) {
                    String changeType = viewModel.getChangeType(selectedPlanId);
                    PlanChangeWarningBottomSheetFragment dialog = PlanChangeWarningBottomSheetFragment.newInstance(changeType);
                    dialog.setOnConfirmListener(proceed);
                    dialog.show(getChildFragmentManager(), "warning");
                } else {
                    proceed.run();
                }
            }
        });
    }


    private void observeViewModel() {
        viewModel.testDrivePlanLiveData.observe(getViewLifecycleOwner(), plan -> {
            if (plan != null) {
                bindPlanToUi(plan, binding.testDrivePlanName, binding.testDrivePrice, binding.testDriveBilling, binding.testDrivePlan);
                binding.testDrivePlanCardview.setVisibility(View.VISIBLE);
            } else {
                binding.testDrivePlanCardview.setVisibility(View.GONE);
            }
        });

        viewModel.starterPlansLiveData.observe(getViewLifecycleOwner(), plans -> {
            if (plans != null && !plans.isEmpty()) {
                boolean hasWeekly = plans.stream().anyMatch(p -> "starter_weekly".equals(p.getType()));
                boolean hasMonthly = plans.stream().anyMatch(p -> "starter_monthly".equals(p.getType()));
                if (hasWeekly) {
                    Plan weekly = plans.stream().filter(p -> "starter_weekly".equals(p.getType())).findFirst().get();
                    bindPlanToUi(weekly, null, binding.starterWeeklyPrice, binding.starterWeeklyBilling, binding.starterWeeklyPlan);
                    binding.starterWeeklyPlan.setVisibility(View.VISIBLE);
                } else {
                    binding.starterWeeklyPlan.setVisibility(View.GONE);
                }
                if (hasMonthly) {
                    Plan monthly = plans.stream().filter(p -> "starter_monthly".equals(p.getType())).findFirst().get();
                    bindPlanToUi(monthly, null, binding.starterMonthlyPrice, binding.starterMonthlyBilling, binding.starterMonthlyPlan);
                    binding.starterMonthlyPlan.setVisibility(View.VISIBLE);
                } else {
                    binding.starterMonthlyPlan.setVisibility(View.GONE);
                }
                binding.initiateStarterPay.setVisibility(View.VISIBLE);
            } else {
                binding.starterWeeklyPlan.setVisibility(View.GONE);
                binding.starterMonthlyPlan.setVisibility(View.GONE);
                binding.initiateStarterPay.setVisibility(View.GONE);
            }
        });

        viewModel.proPlansLiveData.observe(getViewLifecycleOwner(), plans -> {
            if (plans != null && !plans.isEmpty()) {
                boolean hasWeekly = plans.stream().anyMatch(p -> "pro_weekly".equals(p.getType()));
                boolean hasMonthly = plans.stream().anyMatch(p -> "pro_monthly".equals(p.getType()));
                if (hasWeekly) {
                    Plan weekly = plans.stream().filter(p -> "pro_weekly".equals(p.getType())).findFirst().get();
                    bindPlanToUi(weekly, null, binding.proWeeklyPrice, binding.proWeeklyBilling, binding.proWeeklyPlan);
                    binding.proWeeklyPlan.setVisibility(View.VISIBLE);
                } else {
                    binding.proWeeklyPlan.setVisibility(View.GONE);
                }
                if (hasMonthly) {
                    Plan monthly = plans.stream().filter(p -> "pro_monthly".equals(p.getType())).findFirst().get();
                    bindPlanToUi(monthly, null, binding.proMonthlyPrice, binding.proMonthlyBilling, binding.proMonthlyPlan);
                    binding.proMonthlyPlan.setVisibility(View.VISIBLE);
                } else {
                    binding.proMonthlyPlan.setVisibility(View.GONE);
                }
                binding.initiateProPay.setVisibility(View.VISIBLE);
            } else {
                binding.proWeeklyPlan.setVisibility(View.GONE);
                binding.proMonthlyPlan.setVisibility(View.GONE);
                binding.initiateProPay.setVisibility(View.GONE);
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
                } else if (!response.isPaymentRequired()){
                    // No payment required, handle success directly
                    Toast.makeText(getContext(), response.getMessage(), Toast.LENGTH_SHORT).show();
                    enableAllInteractiveElements();
                    if (activeProgressBar != null) activeProgressBar.setVisibility(View.GONE);
                    if (buttonText != null) buttonText.setVisibility(View.VISIBLE);
                    if (initiatedPayButton != null) {
                        initiatedPayButton.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.rounded_beige));
                    }
                    dismiss(); // Close the sheet
                }
            }
        });

        viewModel.paymentResultEvent.observe(getViewLifecycleOwner(), event -> {
            String result = event.getContentIfNotHandled();
            if (result != null) {
                Toast.makeText(getContext(), result, Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getSubscriptionStatus().observe(getViewLifecycleOwner(), status -> {
            if (status != null) {
                if (!status.equals("free") && !status.equals("payment_failed")) {
                    enableAllInteractiveElements();
                    if (activeProgressBar != null) {
                        activeProgressBar.setVisibility(View.GONE);
                    }
                    if (buttonText != null) {
                        buttonText.setVisibility(View.VISIBLE);
                    }
                    if (initiatedPayButton != null) {
                        initiatedPayButton.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.rounded_beige));
                    }
                    dismiss();
                    Toast.makeText(getContext(), "Subscription successful!", Toast.LENGTH_SHORT).show();
                    viewModel.stopListeningToSubscriptionStatus();
                } else if (status.equals("payment_failed")) {
                    enableAllInteractiveElements();
                    if (activeProgressBar != null) {
                        activeProgressBar.setVisibility(View.GONE);
                    }
                    if (buttonText != null) {
                        buttonText.setVisibility(View.VISIBLE);
                    }
                    if (initiatedPayButton != null) {
                        initiatedPayButton.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.rounded_beige));
                    }
                    Toast.makeText(getContext(), "Subscription failed, insufficient funds.", Toast.LENGTH_SHORT).show();
                    viewModel.stopListeningToSubscriptionStatus();
                }
            }
        });

        viewModel.error.observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindPlanToUi(Plan plan, TextView nameView, TextView priceView, TextView billingView, CardView cardView) {
        if (nameView != null) nameView.setText(plan.getName());
        priceView.setText(plan.getPriceText());
        billingView.setText(plan.getBillingCycleText());
        cardView.setOnClickListener(v -> {
            viewModel.selectPlan(plan.getId());
            selectPlanCard(cardView);
        });
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

    private void onPaymentSheetResult(PaymentSheetResult paymentSheetResult) {
        if (paymentSheetResult instanceof PaymentSheetResult.Completed) {
            Log.i("SubscriptionSheet", "Payment completed!");
            viewModel.handlePaymentResult("Payment completed!", true);
            viewModel.startListeningToSubscriptionStatus();
        } else {
            enableAllInteractiveElements();
            if (activeProgressBar != null) {
                activeProgressBar.setVisibility(View.GONE);
            }
            if (buttonText != null) {
                buttonText.setVisibility(View.VISIBLE);
            }
            if (initiatedPayButton != null) {
                initiatedPayButton.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.rounded_beige));
            }
            if (paymentSheetResult instanceof PaymentSheetResult.Canceled) {
                viewModel.handlePaymentResult("Payment canceled.", false);
            } else if (paymentSheetResult instanceof PaymentSheetResult.Failed) {
                viewModel.handlePaymentResult(((PaymentSheetResult.Failed) paymentSheetResult).getError().getMessage(), false);
            }
        }
    }

    private void resetState() {
        testDriveProgressBar.setVisibility(View.GONE);
        starterProgressBar.setVisibility(View.GONE);
        proProgressBar.setVisibility(View.GONE);
        getTestDrivePlan.setVisibility(View.VISIBLE);
        getStarterPlan.setVisibility(View.VISIBLE);
        getProPlan.setVisibility(View.VISIBLE);
        binding.initiateTestDrivePay.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.rounded_beige));
        binding.initiateStarterPay.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.rounded_beige));
        binding.initiateProPay.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.rounded_beige));
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
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
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
