package bottomsheets;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;

import com.claw.ai.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class DisclaimerBottomSheetFragment extends BottomSheetDialogFragment {
    private Runnable onConfirmListener;

    public static DisclaimerBottomSheetFragment newInstance() {

        Bundle args = new Bundle();

        DisclaimerBottomSheetFragment fragment = new DisclaimerBottomSheetFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnConfirmListener(Runnable listener) {
        this.onConfirmListener = listener;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_disclaimer_bottom_sheet, container, false);

        TextView disclaimerText = view.findViewById(R.id.disclaimer_text);
        Button confirmButton = view.findViewById(R.id.confirm_button);
        Button cancelButton = view.findViewById(R.id.cancel_button);

        disclaimerText.setText("Cancelling your subscription will end your access to premium features at the end of the current billing period. Are you sure you want to proceed?");

        confirmButton.setOnClickListener(v -> {
            if (onConfirmListener != null) {
                onConfirmListener.run();
            }
        });

        cancelButton.setOnClickListener(v -> dismiss());

        return view;
    }
}
