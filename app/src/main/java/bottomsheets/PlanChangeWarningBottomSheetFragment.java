package bottomsheets;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.claw.ai.R;
import com.claw.ai.databinding.FragmentPlanChangeWarningBottomSheetBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class PlanChangeWarningBottomSheetFragment extends BottomSheetDialogFragment {
    private String changeType;
    private Runnable onConfirmListener;
    FragmentPlanChangeWarningBottomSheetBinding binding;

    public static PlanChangeWarningBottomSheetFragment newInstance(String changeType) {
        PlanChangeWarningBottomSheetFragment fragment = new PlanChangeWarningBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString("change_type", changeType);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            changeType = getArguments().getString("change_type");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentPlanChangeWarningBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextView description = binding.description;
        RelativeLayout confirmButton = binding.confirmButton;
        TextView title = binding.title;

        Log.d("PlanChangeWarning", "Received changeType: " + changeType);
        setupWarningMessage(description, changeType, title);

        confirmButton.setOnClickListener(v -> {
            if (onConfirmListener != null) {
                onConfirmListener.run();
            }
            dismiss();
        });

        // cancelButton.setOnClickListener(v -> dismiss());
    }

    private void setupWarningMessage(TextView description, String changeType, TextView titleText) {
        String message, title;
        switch (changeType) {
            case "upgrade":
                message = "Your plan will be upgraded immediately.";
                title = "Confirm Upgrade";
                break;
            case "downgrade":
                message = "Your current plan will remain active until the end of the billing period.";
                title = "Confirm Downgrade";
                break;
            case "lateral":
                message = "Your plan will be changed immediately.";
                title = "Confirm change";
                break;
            case "special_downgrade":
                message = "Even though this is an upgrade in features, since the new plan is cheaper, the change will take effect at the end of your current billing period.";
                title = "Confirm Upgrade";
                break;
            case "special_downgrade_expensive":
                message = "This is a downgrade in features but has a higher cost. The change will take effect at the end of your current billing period.";
                title="Confirm Downgrade";
                break;
            default:
                message = "Are you sure you want to change your plan?";
                title = "Confirm change";
                Log.w("PlanChangeWarning", "Unknown changeType: " + changeType);
        }

        titleText.setText(title);
        description.setText(message);
    }

    public void setOnConfirmListener(Runnable listener) {
        this.onConfirmListener = listener;
    }
}