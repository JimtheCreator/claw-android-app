package bottomsheets;

import android.content.Context;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.claw.ai.R;
import com.claw.ai.databinding.FragmentPriceAlertsBottomSheetBinding;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.slider.Slider;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import viewmodels.alerts.PriceAlertsViewModel;

public class PriceAlertsBottomSheetFragment extends BottomSheetDialogFragment {

    private FragmentPriceAlertsBottomSheetBinding binding;
    private double price;
    private String userId;
    private String symbol;
    private DecimalFormat priceFormat;
    private DecimalFormat inputFormat;
    private PriceAlertsViewModel viewModel;
    private static final String TAG = "PriceAlertsBottomSheet";

    public static PriceAlertsBottomSheetFragment newInstance(String userId, String symbol, double price) {
        Bundle args = new Bundle();
        args.putString("user_id", userId);
        args.putString("symbol", symbol);
        args.putDouble("symbol_price", price);
        PriceAlertsBottomSheetFragment fragment = new PriceAlertsBottomSheetFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            userId = getArguments().getString("user_id");
            symbol = getArguments().getString("symbol");
            price = getArguments().getDouble("symbol_price");
        }

        priceFormat = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        inputFormat = new DecimalFormat("#,##0.####", DecimalFormatSymbols.getInstance(Locale.US));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentPriceAlertsBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(PriceAlertsViewModel.class);
        observeViewModel();
        initializeViews();
        setupSlider();
        onClicks();
    }

    private void initializeViews() {
        binding.currentPrice.setText("US$" + priceFormat.format(price));
        binding.expectedPrice.setText(inputFormat.format(price));
        binding.priceSlider.setValueFrom(0.0f);
        binding.priceSlider.setValueTo((float) (price * 2));
        binding.priceSlider.setStepSize(0.0f);
        binding.priceSlider.setValue((float) price);
//        binding.createNewAlert.setEnabled(false);

        binding.expectedPrice.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    double inputPrice = Double.parseDouble(s.toString());
//                    binding.createNewAlert.setEnabled(inputPrice != price);
                } catch (NumberFormatException e) {
//                    binding.createNewAlert.setEnabled(false);
                }
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupSlider() {
        binding.priceSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                binding.expectedPrice.setText(inputFormat.format(value));
//                binding.createNewAlert.setEnabled(value != price);
            }
        });

        binding.priceSlider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {
                triggerHapticFeedback();
            }
            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                triggerHapticFeedback();
            }
        });
    }

    private void onClicks() {
        binding.createAlert.setOnClickListener(v -> {
            Log.d(TAG, "CreateNewAlert clicked");
            String expectedPriceText = binding.expectedPrice.getText().toString().replace(",", "");
            try {
                double expectedPrice = Double.parseDouble(expectedPriceText);
                String conditionType = expectedPrice > price ? "price_above" : "price_below";
                viewModel.createAlert(userId, symbol, conditionType, expectedPrice);
            } catch (NumberFormatException e) {
                Toast.makeText(getContext(), "Invalid price input " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        try {
            View.OnClickListener numberClickListener = v -> {
                try {
                    String currentText = binding.expectedPrice.getText().toString();
                    String newText = getString(v, currentText);
                    binding.expectedPrice.setText(newText);

                    try {
                        float newValue = Float.parseFloat(newText);
                        if (newValue >= binding.priceSlider.getValueFrom() && newValue <= binding.priceSlider.getValueTo()) {
                            binding.priceSlider.setValue(newValue);
                        }
                    } catch (NumberFormatException e) {
                        // Ignore invalid numbers
                    }

                    updateActionButtonsVisibility(newText);
                } catch (Exception e) {
                    Log.e(TAG, "Error in numberClickListener: ", e);
                }
            };

            binding.btn1.setOnClickListener(numberClickListener);
            binding.btn2.setOnClickListener(numberClickListener);
            binding.btn3.setOnClickListener(numberClickListener);
            binding.btn4.setOnClickListener(numberClickListener);
            binding.btn5.setOnClickListener(numberClickListener);
            binding.btn6.setOnClickListener(numberClickListener);
            binding.btn7.setOnClickListener(numberClickListener);
            binding.btn8.setOnClickListener(numberClickListener);
            binding.btn9.setOnClickListener(numberClickListener);
            binding.btn0.setOnClickListener(numberClickListener);
            binding.btnPeriod.setOnClickListener(numberClickListener);

            binding.btnBackspace.setOnClickListener(v -> {
                Log.d(TAG, "Backspace clicked");
                try {
                    String currentText = binding.expectedPrice.getText().toString();
                    if (!currentText.isEmpty()) {
                        String newText = currentText.substring(0, currentText.length() - 1);
                        binding.expectedPrice.setText(newText);

                        try {
                            float newValue = newText.isEmpty() ? 0 : Float.parseFloat(newText);
                            if (newValue >= binding.priceSlider.getValueFrom() && newValue <= binding.priceSlider.getValueTo()) {
                                binding.priceSlider.setValue(newValue);
                            }
                        } catch (NumberFormatException e) {
                            // Ignore invalid numbers
                        }

                        updateActionButtonsVisibility(newText);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in backspace click listener: ", e);
                }
            });

            binding.btnBackspace.setOnLongClickListener(v -> {
                try {
                    binding.expectedPrice.setText("");
                    binding.priceSlider.setValue(0);
                    updateActionButtonsVisibility("");
                    triggerHapticFeedback();
                    return true;
                } catch (Exception e) {
                    Log.e(TAG, "Error in backspace long press listener: ", e);
                    return false;
                }
            });
        }

        catch (Exception e) {
            Log.e(TAG, "Error in onClicks: ", e);
        }
    }

    @NonNull
    private static String getString(View v, String currentText) {
        String newDigit = "";

        int id = v.getId();
        if (id == R.id.btn_1) newDigit = "1";
        else if (id == R.id.btn_2) newDigit = "2";
        else if (id == R.id.btn_3) newDigit = "3";
        else if (id == R.id.btn_4) newDigit = "4";
        else if (id == R.id.btn_5) newDigit = "5";
        else if (id == R.id.btn_6) newDigit = "6";
        else if (id == R.id.btn_7) newDigit = "7";
        else if (id == R.id.btn_8) newDigit = "8";
        else if (id == R.id.btn_9) newDigit = "9";
        else if (id == R.id.btn_0) newDigit = "0";
        else if (id == R.id.btn_period) newDigit = ".";

        return currentText + newDigit;
    }

    private void observeViewModel() {
        viewModel.getMessages().observe(getViewLifecycleOwner(), message -> {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            if (message.contains("successfully")) {
                dismiss(); // Close the bottom sheet on success
            }
        });

        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.alertcontent.setVisibility(isLoading ? View.GONE : View.VISIBLE);
            if (isLoading){
                binding.createAlert.setEnabled(false);
                binding.createAlert.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.button_disabled_background));
            }else {
                binding.createAlert.setEnabled(true);
                binding.createAlert.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.alert_button_shape));
            }
        });
    }

    private void updateActionButtonsVisibility(String text) {
        int visibility = text.isEmpty() ? View.INVISIBLE : View.VISIBLE;
        binding.btnBackspace.setVisibility(visibility);
    }

    private void triggerHapticFeedback() {
        Vibrator vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE));
        }
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
    public void onDestroy() {
        super.onDestroy();
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