package bottomsheets;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.claw.ai.databinding.FragmentPricingPackageBottomSheetBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class PricingPackageSheetFragment extends BottomSheetDialogFragment {

    FragmentPricingPackageBottomSheetBinding binding;

    public static PricingPackageSheetFragment newInstance() {
        Bundle args = new Bundle();

        PricingPackageSheetFragment fragment = new PricingPackageSheetFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentPricingPackageBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }




    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }
}
