package pricing;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.claw.ai.R;
import com.claw.ai.databinding.FragmentSubscriptionPlanBottomSheetBinding;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class SubscriptionPlanSheetFragment extends BottomSheetDialogFragment {

    FragmentSubscriptionPlanBottomSheetBinding binding;
    String selected_starter_package, selected_pro_package, starter_pattern_alerts_increase, starter_market_analysis_increase,
            starter_market_analysis_days_increase;


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
        initiateViews();
        onClicks();
    }

    private void initiateViews() {
        starter_market_analysis_days_increase = "7 each day";
        starter_market_analysis_increase = "49 weekly Market Analysis";
        starter_pattern_alerts_increase = "7 weekly Pattern Alerts";
    }

    private void onClicks() {
        binding.closebutton.setOnClickListener(v -> dismiss());

        binding.starterWeeklyPlan.setOnClickListener(v -> {
            selected_starter_package = "starterWeeklyPlan";
            binding.starterWeeklyRadioHolder.setVisibility(View.VISIBLE);
            binding.starterWeeklyRadio.setChecked(true);

            binding.starterMonthlyRadioHolder.setVisibility(View.INVISIBLE);
            binding.starterMonthlyRadio.setChecked(false);
            binding.starterWeeklyPlan.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.transparent_beige_stroke));
            binding.starterMonthlyPlan.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.invicibleColor));

            binding.starterMarketAnalysis.setText(starter_market_analysis_increase);
            binding.starterMarketAnalysisDays.setVisibility(View.VISIBLE);
            binding.starterPatternAlerts.setText(starter_pattern_alerts_increase);
            binding.starterWatchlist.setText(R.string.watchlist);
        });

        binding.starterMonthlyPlan.setOnClickListener(v -> {
            selected_starter_package = "starterMonthlyPlan";
            binding.starterMonthlyRadioHolder.setVisibility(View.VISIBLE);
            binding.starterMonthlyRadio.setChecked(true);

            binding.starterWeeklyRadioHolder.setVisibility(View.INVISIBLE);
            binding.starterWeeklyRadio.setChecked(false);

            binding.starterMonthlyPlan.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.transparent_beige_stroke));
            binding.starterWeeklyPlan.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.invicibleColor));

            binding.starterMarketAnalysis.setText(R.string.starter_monthly_market_analysis);
            binding.starterMarketAnalysisDays.setVisibility(View.GONE);
            binding.starterPatternAlerts.setText(R.string.starter_monthly_pattern_alerts);
            binding.starterWatchlist.setText(R.string.starter_monthly_watchlist);
        });

        binding.proWeeklyPlan.setOnClickListener(v -> {
            selected_pro_package = "proWeeklyPlan";
            binding.proWeeklyRadioHolder.setVisibility(View.VISIBLE);
            binding.proWeeklyRadio.setChecked(true);

            binding.proMonthlyRadioHolder.setVisibility(View.INVISIBLE);
            binding.proMonthlyRadio.setChecked(false);

            binding.proWeeklyPlan.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.transparent_beige_stroke));
            binding.proMonthlyPlan.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.invicibleColor));
        });

        binding.proMonthlyPlan.setOnClickListener(v -> {
            selected_pro_package = "proMonthlyPlan";
            binding.proMonthlyRadioHolder.setVisibility(View.VISIBLE);
            binding.proMonthlyRadio.setChecked(true);

            binding.proWeeklyRadioHolder.setVisibility(View.INVISIBLE);
            binding.proWeeklyRadio.setChecked(false);

            binding.proMonthlyPlan.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.transparent_beige_stroke));
            binding.proWeeklyPlan.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.invicibleColor));
        });

        binding.initiateStarterPay.setOnClickListener(v -> {
            if (binding.starterWeeklyRadio.isChecked() || binding.starterMonthlyRadio.isChecked()){
                if (selected_starter_package == null){
                    Toast toast = Toast.makeText(requireContext(), "Kindly select your preferred plan", Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.CENTER, 0,0);
                    toast.show();
                }else {
                    initiatePayment("fromStarterPlan");
                }
            }else {
                Toast toast = Toast.makeText(requireContext(), "Kindly select your preferred Starter plan", Toast.LENGTH_LONG);
                toast.setGravity(Gravity.CENTER, 0,0);
                toast.show();
            }
        });

        binding.initiateProPay.setOnClickListener(v -> {
            if (binding.proMonthlyRadio.isChecked() || binding.proWeeklyRadio.isChecked()){
                if (selected_pro_package == null){
                    Toast toast = Toast.makeText(requireContext(), "Kindly select your preferred plan", Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.CENTER, 0,0);
                    toast.show();
                }else {
                    initiatePayment("fromProPlan");
                }
            }else {
                Toast toast = Toast.makeText(requireContext(), "Kindly select your preferred Pro plan", Toast.LENGTH_LONG);
                toast.setGravity(Gravity.CENTER, 0,0);
                toast.show();
            }
        });
    }

    private void initiatePayment(String selectedPlan) {
        if (selectedPlan.equals("fromProPlan")){
            Toast toast = Toast.makeText(requireContext(), "Selected packages \n" + selected_pro_package, Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER, 0,0);
            toast.show();
        }else if (selectedPlan.equals("fromStarterPlan")){
            Toast toast = Toast.makeText(requireContext(), "Selected \n" + selected_starter_package, Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER, 0,0);
            toast.show();
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
    public void onStart() {
        super.onStart();

        View view = getView();
        if (view != null) {
            View parent = (View) view.getParent();
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(parent);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
        }
    }

}
