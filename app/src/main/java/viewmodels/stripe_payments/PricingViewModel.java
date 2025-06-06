package viewmodels.stripe_payments;

import android.app.Application;
import android.icu.text.NumberFormat;
import android.icu.util.Currency;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import backend.results.NativeCheckoutResponse;
import models.Plan;
import models.StripePrice;
import repositories.stripe_payments.StripeRepository;

public class PricingViewModel extends AndroidViewModel {
    private static final String TAG = "PricingViewModel";
    private final StripeRepository stripeRepository;
    private final MutableLiveData<List<StripePrice>> rawPricesLiveData;
    public final LiveData<List<Plan>> testDrivePlanLiveData;
    public final LiveData<List<Plan>> starterPlansLiveData;
    public final LiveData<List<Plan>> proPlansLiveData;
    private final MutableLiveData<String> _selectedPlanId = new MutableLiveData<>();
    public LiveData<String> selectedPlanId = _selectedPlanId;
    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    public LiveData<Boolean> isLoading = _isLoading;
    private final MutableLiveData<String> _error = new MutableLiveData<>();
    public LiveData<String> error = _error;

    private final MutableLiveData<Event<String>> _checkoutUrlEvent = new MutableLiveData<>();
    public LiveData<Event<String>> checkoutUrlEvent = _checkoutUrlEvent;

    private final FirebaseAuth firebaseAuth;

    private final MutableLiveData<Event<NativeCheckoutResponse>> _paymentSheetParametersEvent = new MutableLiveData<>();
    public LiveData<Event<NativeCheckoutResponse>> paymentSheetParametersEvent = _paymentSheetParametersEvent;

    private final MutableLiveData<Event<String>> _paymentResultEvent = new MutableLiveData<>();
    public LiveData<Event<String>> paymentResultEvent = _paymentResultEvent;

    // Added for subscription status listening
    private final MutableLiveData<String> subscriptionStatus = new MutableLiveData<>();
    private ValueEventListener subscriptionListener;
    private DatabaseReference subscriptionRef;

    private static final String TYPE_TEST_DRIVE = "test_drive";
    private static final String TYPE_STARTER_WEEKLY = "starter_weekly";
    private static final String TYPE_STARTER_MONTHLY = "starter_monthly";
    private static final String TYPE_PRO_WEEKLY = "pro_weekly";
    private static final String TYPE_PRO_MONTHLY = "pro_monthly";

    public PricingViewModel(@NonNull Application application) {
        super(application);
        stripeRepository = new StripeRepository();
        firebaseAuth = FirebaseAuth.getInstance();
        rawPricesLiveData = new MutableLiveData<>();

        fetchPricesFromRepository();

        testDrivePlanLiveData = Transformations.map(rawPricesLiveData, this::filterAndMapTestDrive);
        starterPlansLiveData = Transformations.map(rawPricesLiveData, this::filterAndMapStarterPlans);
        proPlansLiveData = Transformations.map(rawPricesLiveData, this::filterAndMapProPlans);
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

    private Plan mapStripePriceToPlan(StripePrice stripePrice) {
        String priceText = formatPrice(stripePrice.getAmount(), stripePrice.getCurrency());
        String billingCycleText = "";
        String savePercentage = "";

        switch (stripePrice.getPlanType().toLowerCase()) {
            case TYPE_TEST_DRIVE:
                billingCycleText = "Billed once";
                break;
            case TYPE_STARTER_WEEKLY:
                billingCycleText = "Billed weekly";
                break;
            case TYPE_STARTER_MONTHLY:
                billingCycleText = "Billed monthly";
                savePercentage = "Save 10%";
                break;
            case TYPE_PRO_WEEKLY:
                billingCycleText = "Billed weekly";
                break;
            case TYPE_PRO_MONTHLY:
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

    private List<Plan> filterAndMapTestDrive(List<StripePrice> prices) {
        if (prices == null) return new ArrayList<>();
        return prices.stream()
                .filter(p -> TYPE_TEST_DRIVE.equals(p.getPlanType()))
                .map(this::mapStripePriceToPlan)
                .collect(Collectors.toList());
    }

    private List<Plan> filterAndMapStarterPlans(List<StripePrice> prices) {
        if (prices == null) return new ArrayList<>();
        return prices.stream()
                .filter(p -> TYPE_STARTER_WEEKLY.equals(p.getPlanType()) || TYPE_STARTER_MONTHLY.equals(p.getPlanType()))
                .map(this::mapStripePriceToPlan)
                .sorted((p1, p2) -> p1.getType().equals(TYPE_STARTER_WEEKLY) ? -1 : 1)
                .collect(Collectors.toList());
    }

    private List<Plan> filterAndMapProPlans(List<StripePrice> prices) {
        if (prices == null) return new ArrayList<>();
        return prices.stream()
                .filter(p -> TYPE_PRO_WEEKLY.equals(p.getPlanType()) || TYPE_PRO_MONTHLY.equals(p.getPlanType()))
                .map(this::mapStripePriceToPlan)
                .sorted((p1, p2) -> p1.getType().equals(TYPE_PRO_WEEKLY) ? -1 : 1)
                .collect(Collectors.toList());
    }

    public void fetchPricesFromRepository() {
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

    public void selectPlan(String planId) {
        _selectedPlanId.setValue(planId);
        Log.d(TAG, "Selected plan ID: " + planId);
    }

    public void initiatePaymentSheetFlow() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            _error.setValue("User not signed in. Please sign in to subscribe.");
            return;
        }
        String userId = currentUser.getUid();
        String planId = _selectedPlanId.getValue();

        if (planId == null || planId.isEmpty()) {
            _error.setValue("Please select a plan to proceed.");
            return;
        }

        _isLoading.setValue(true);
        stripeRepository.getPaymentSheetParameters(userId, planId).observeForever(response -> {
            _isLoading.setValue(false);
            if (response != null && response.getClientSecret() != null && response.getPublishableKey() != null) {
                _paymentSheetParametersEvent.setValue(new Event<>(response));
                _error.setValue(null);
            } else {
                _error.setValue("Failed to initialize payment. Please try again.");
                Log.e(TAG, "PaymentSheet parameters response was null or incomplete.");
            }
        });
    }

    public void handlePaymentResult(String statusMessage, boolean success) {
        _isLoading.setValue(false);
        if (success) {
            _paymentResultEvent.setValue(new Event<>("success: " + statusMessage));
            _error.setValue(null);
        } else {
            _error.setValue("Payment failed or canceled: " + statusMessage);
        }
    }

    // New methods for subscription status listening
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
            if (hasBeenHandled) {
                return null;
            } else {
                hasBeenHandled = true;
                return content;
            }
        }

        public T peekContent() {
            return content;
        }
    }
}