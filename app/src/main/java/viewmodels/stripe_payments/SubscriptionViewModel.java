package viewmodels.stripe_payments;

import repositories.plan_usage_limits.SupabaseRepository;

import android.app.Application;
import android.icu.text.NumberFormat;
import android.icu.util.Currency;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import models.CancellationResponseSchema;
import models.NativeCheckoutResponse;
import models.Plan;
import models.StripePrice;
import models.UsageData;
import models.User;
import repositories.stripe_payments.StripeRepository;

public class SubscriptionViewModel extends AndroidViewModel {
    private static final String TAG = "SubscriptionViewModel";
    private final StripeRepository stripeRepository;
    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    public LiveData<Boolean> isLoading = _isLoading;

    private final MutableLiveData<Boolean> _isSubscriptionLoading = new MutableLiveData<>(false);
    public LiveData<Boolean> isSubscriptionLoading = _isSubscriptionLoading;
    private final MutableLiveData<String> _error = new MutableLiveData<>();
    public LiveData<String> error = _error;
    private final MutableLiveData<List<StripePrice>> rawPricesLiveData = new MutableLiveData<>();
    public final LiveData<Plan> testDrivePlanLiveData;
    public final LiveData<List<Plan>> starterPlansLiveData;
    public final LiveData<List<Plan>> proPlansLiveData;
    private final MutableLiveData<String> _selectedPlanId = new MutableLiveData<>();
    public final LiveData<String> selectedPlanId = _selectedPlanId;
    private final MutableLiveData<Event<NativeCheckoutResponse>> _paymentSheetParametersEvent = new MutableLiveData<>();
    public final LiveData<Event<NativeCheckoutResponse>> paymentSheetParametersEvent = _paymentSheetParametersEvent;
    private final MutableLiveData<Event<String>> _paymentResultEvent = new MutableLiveData<>();
    public final LiveData<Event<String>> paymentResultEvent = _paymentResultEvent;

    private DatabaseReference subscriptionRef;
    private final MutableLiveData<String> subscriptionStatus = new MutableLiveData<>();
    private ValueEventListener subscriptionListener;

    private final FirebaseAuth firebaseAuth;

    private final MutableLiveData<User> currentUser = new MutableLiveData<>();

    private final MutableLiveData<Event<CancellationResponseSchema>> _cancellationResultEvent = new MutableLiveData<>();
    public final LiveData<Event<CancellationResponseSchema>> cancellationResultEvent = _cancellationResultEvent;

    public LiveData<User> getCurrentUser() {
        return currentUser;
    }

    public SubscriptionViewModel(@NonNull Application application) {
        super(application);
        stripeRepository = new StripeRepository();
        // Initialize repository (e.g., via dependency injection)

        firebaseAuth = FirebaseAuth.getInstance();
        fetchUserData();
        fetchPricesFromRepository();

        testDrivePlanLiveData = createPlanLiveData(this::filterAndMapTestDrive);
        starterPlansLiveData = createPlanLiveData(this::filterAndMapStarterPlans);
        proPlansLiveData = createPlanLiveData(this::filterAndMapProPlans);
    }


    public void cancelSubscription(Boolean cancelAtPeriodEnd) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            _error.setValue("User not signed in.");
            return;
        }

        String userId = currentUser.getUid();
        _isSubscriptionLoading.setValue(true);
        stripeRepository.cancelSubscription(userId, null, cancelAtPeriodEnd).observeForever(response -> {
            _isSubscriptionLoading.setValue(false);
            if (response != null) {
                _cancellationResultEvent.setValue(new Event<>(response));
            } else {
                _error.setValue("Failed to cancel subscription.");
            }
        });
    }

    private <T> LiveData<T> createPlanLiveData(BiFunction<List<StripePrice>, User, T> mapper) {
        MediatorLiveData<T> liveData = new MediatorLiveData<>();
        liveData.addSource(rawPricesLiveData, prices -> {
            User user = currentUser.getValue();
            if (prices != null && user != null) {
                liveData.setValue(mapper.apply(prices, user));
            }
        });
        liveData.addSource(currentUser, user -> {
            List<StripePrice> prices = rawPricesLiveData.getValue();
            if (prices != null && user != null) {
                liveData.setValue(mapper.apply(prices, user));
            }
        });
        return liveData;
    }

    private void fetchUserData() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null) {
            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(firebaseUser.getUid());
            userRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    User user = snapshot.getValue(User.class);
                    currentUser.setValue(user);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Failed to fetch user data", error.toException());
                    _error.setValue("Failed to fetch user data.");
                }
            });
        }
    }

    private void fetchPricesFromRepository() {
        _isLoading.setValue(true);
        stripeRepository.fetchPrices().observeForever(prices -> {
            _isLoading.setValue(false);
            if (prices != null) {
                rawPricesLiveData.setValue(prices);
                _error.setValue(null);
            } else {
                rawPricesLiveData.setValue(new ArrayList<>());
                _error.setValue("Failed to load plans. Please check your connection.");
            }
        });
    }

    private Plan filterAndMapTestDrive(List<StripePrice> prices, User user) {
        if (user == null || user.isUserPaid() || "test_drive".equals(user.getSubscriptionType())) {
            return null; // Hide Test Drive if userPaid is true or already on Test Drive
        }
        return prices.stream()
                .filter(p -> "test_drive".equals(p.getPlanType()))
                .map(this::mapStripePriceToPlan)
                .findFirst()
                .orElse(null);
    }

    private List<Plan> filterAndMapStarterPlans(List<StripePrice> prices, User user) {
        if (user == null) return new ArrayList<>();
        String currentSubscription = user.getSubscriptionType();
        return prices.stream()
                .filter(p -> ("starter_weekly".equals(p.getPlanType()) || "starter_monthly".equals(p.getPlanType()))
                        && !p.getPlanType().equals(currentSubscription))
                .map(this::mapStripePriceToPlan)
                .collect(Collectors.toList());
    }

    private List<Plan> filterAndMapProPlans(List<StripePrice> prices, User user) {
        if (user == null) return new ArrayList<>();
        String currentSubscription = user.getSubscriptionType();
        return prices.stream()
                .filter(p -> ("pro_weekly".equals(p.getPlanType()) || "pro_monthly".equals(p.getPlanType()))
                        && !p.getPlanType().equals(currentSubscription))
                .map(this::mapStripePriceToPlan)
                .collect(Collectors.toList());
    }

    private Plan mapStripePriceToPlan(StripePrice stripePrice) {
        String priceText = formatPrice(stripePrice.getAmount(), stripePrice.getCurrency());
        String billingCycleText = "";
        String savePercentage = "";

        switch (stripePrice.getPlanType().toLowerCase()) {
            case "test_drive":
                billingCycleText = "Billed once";
                break;
            case "starter_weekly":
                billingCycleText = "Billed weekly";
                break;
            case "starter_monthly":
                billingCycleText = "Billed monthly";
                savePercentage = "Save 10%";
                break;
            case "pro_weekly":
                billingCycleText = "Billed weekly";
                break;
            case "pro_monthly":
                billingCycleText = "Billed monthly";
                savePercentage = "Save 20%";
                break;
            default:
                billingCycleText = stripePrice.getBillingPeriod();
        }

        return new Plan(
                stripePrice.getId(),
                stripePrice.getName(),
                stripePrice.getName(),
                priceText,
                billingCycleText,
                stripePrice.getDescription(),
                stripePrice.getPlanType(),
                savePercentage
        );
    }

    private String formatPrice(long amount, String currencyCode) {
        try {
            NumberFormat format = NumberFormat.getCurrencyInstance(Locale.getDefault());
            format.setCurrency(Currency.getInstance(currencyCode.toUpperCase(Locale.ROOT)));
            format.setMinimumFractionDigits(2);
            format.setMaximumFractionDigits(2);
            return format.format(amount / 100.0);
        } catch (Exception e) {
            Log.e(TAG, "Error formatting price for currency code: " + currencyCode, e);
            if ("GBP".equalsIgnoreCase(currencyCode)) {
                return String.format(Locale.UK, "£%.2f", amount / 100.0);
            }
            return String.format(Locale.getDefault(), "%s %.2f", currencyCode.toUpperCase(), amount / 100.0);
        }
    }

    public void selectPlan(String planId) {
        _selectedPlanId.setValue(planId);
        Log.d(TAG, "Selected plan ID: " + planId);
    }

    public void initiatePaymentSheetFlow() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            _error.setValue("User not signed in.");
            return;
        }
        String userId = currentUser.getUid();
        String planId = _selectedPlanId.getValue();
        if (planId == null) {
            _error.setValue("No plan selected.");
            return;
        }
        _isLoading.setValue(true);
        stripeRepository.getPaymentSheetParameters(userId, planId).observeForever(response -> {
            _isLoading.setValue(false);
            if (response != null) {
                _paymentSheetParametersEvent.setValue(new Event<>(response));
            } else {
                _isLoading.setValue(false);
                _error.setValue("Failed to initiate payment.");
            }
        });
    }

    public void handlePaymentResult(String statusMessage, boolean success) {
        _isLoading.setValue(false);
        if (success) {
            _paymentResultEvent.setValue(new Event<>("success: " + statusMessage));
        } else {
            _error.setValue("Payment failed: " + statusMessage);
        }
    }

    public void startListeningToSubscriptionStatus() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "User not signed in.");
            return;
        }
        String userId = currentUser.getUid();
        subscriptionRef = FirebaseDatabase.getInstance().getReference("users").child(userId).child("subscriptionType");
        subscriptionListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                String status = dataSnapshot.getValue(String.class);
                Log.d(TAG, "onDataChange triggered. Value: " + status);
                subscriptionStatus.setValue(status);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Subscription status listener cancelled: " + databaseError.getMessage());
            }
        };
        subscriptionRef.addValueEventListener(subscriptionListener);
    }

    public void stopListeningToSubscriptionStatus() {
        if (subscriptionRef != null && subscriptionListener != null) {
            subscriptionRef.removeEventListener(subscriptionListener);
            subscriptionListener = null;
            subscriptionRef = null;
        }
    }

    // In SubscriptionViewModel.java, add these methods
    public String getChangeType(String selectedPlanId) {
        String currentPlan = currentUser.getValue() != null ? currentUser.getValue().getSubscriptionType() : "free";
        if (currentPlan == null) currentPlan = "free";
        String selectedPlanType = getPlanTypeFromId(selectedPlanId);

        // Get price details
        List<StripePrice> prices = rawPricesLiveData.getValue();
        long currentPlanAmount = 0;
        long selectedPlanAmount = 0;
        if (prices != null) {
            for (StripePrice price : prices) {
                if (price.getPlanType().equals(currentPlan)) {
                    currentPlanAmount = price.getAmount();
                }
                if (price.getId().equals(selectedPlanId)) {
                    selectedPlanAmount = price.getAmount();
                }
            }
        }

        int currentRank = getPlanRank(currentPlan);
        int selectedRank = getPlanRank(selectedPlanType);

        // Special case: pro_weekly to starter_monthly
        if ("pro_weekly".equals(currentPlan) && "starter_monthly".equals(selectedPlanType)) {
            return "special_downgrade_expensive";
        }
        // Special case: starter_monthly to pro_weekly
        else if ("starter_monthly".equals(currentPlan) && "pro_weekly".equals(selectedPlanType)) {
            return "special_upgrade";
        } else {
            // General logic considering rank and price
            if (selectedRank > currentRank && selectedPlanAmount >= currentPlanAmount) {
                return "upgrade";
            } else if (selectedRank < currentRank || selectedPlanAmount < currentPlanAmount) {
                return "downgrade";
            } else {
                return "lateral";
            }

        }
    }

    private int getPlanRank(String planType) {
        switch (planType != null ? planType : "free") {
            case "free":
                return 0;
            case "test_drive":
                return 1;
            case "starter_weekly":
                return 2;
            case "starter_monthly":
                return 3;
            case "pro_weekly":
                return 4;
            case "pro_monthly":
                return 5;
            default:
                return 0; // Default to free if unknown
        }
    }

    public String getPlanTypeFromId(String planId) {
        List<StripePrice> prices = rawPricesLiveData.getValue();
        if (prices != null) {
            for (StripePrice price : prices) {
                if (price.getId().equals(planId)) {
                    return price.getPlanType();
                }
            }
        }
        return "free"; // Fallback
    }

    public LiveData<String> getSubscriptionStatus() {
        return subscriptionStatus;
    }

    public static class Event<T> {
        private final T content;
        private boolean hasBeenHandled = false;

        public Event(T content) {
            this.content = content;
        }

        public T getContentIfNotHandled() {
            if (hasBeenHandled) return null;
            hasBeenHandled = true;
            return content;
        }

        public T peekContent() {
            return content;
        }
    }

}