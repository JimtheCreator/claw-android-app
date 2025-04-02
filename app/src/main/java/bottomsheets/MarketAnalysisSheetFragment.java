package bottomsheets;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.claw.ai.databinding.FragmentMarketAnalysisBottomSheetBinding;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class MarketAnalysisSheetFragment extends BottomSheetDialogFragment {

    FragmentMarketAnalysisBottomSheetBinding binding;

    public static MarketAnalysisSheetFragment newInstance() {
        Bundle args = new Bundle();

        MarketAnalysisSheetFragment fragment = new MarketAnalysisSheetFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentMarketAnalysisBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

//    @Override
//    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
//        super.onViewCreated(view, savedInstanceState);
//
//        // Get the BottomSheetBehavior to control how the sheet appears
//        View bottomSheet = (View) view.getParent();
//        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
//
//        // Set initial state to half-expanded
//        behavior.setState(BottomSheetBehavior.STATE_HALF_EXPANDED);
//
//        // You can also set the fraction of the screen height to show
//        // This is only available in newer versions of the Material Components library
//        behavior.setHalfExpandedRatio(0.5f); // Show 50% of the screen
//
//        // Ensure the peek height is set appropriately
//        int screenHeight = getResources().getDisplayMetrics().heightPixels;
//        behavior.setPeekHeight(screenHeight / 2);
//    }




    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }
}
