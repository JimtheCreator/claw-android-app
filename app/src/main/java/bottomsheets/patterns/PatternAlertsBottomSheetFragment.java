package bottomsheets.patterns;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.transition.TransitionManager;

import com.claw.ai.R;
import com.claw.ai.databinding.FragmentPatternAlertBottomSheetBinding;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.Objects;

import adapters.CachedSymbolSearchAdapter;
import models.CachedSymbol;
import viewmodels.alerts.PatternAlertViewModel;

public class PatternAlertsBottomSheetFragment extends BottomSheetDialogFragment {

    FragmentPatternAlertBottomSheetBinding binding;
    private PatternAlertViewModel viewModel;
    private CachedSymbolSearchAdapter symbolAdapter;
    private boolean isSymbolSearchVisible = false;
    private boolean isTimeIntervalTabVisible = false;
    private boolean isPatternStateDrawerVisible = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    public static PatternAlertsBottomSheetFragment newInstance() {
        Bundle args = new Bundle();
        PatternAlertsBottomSheetFragment fragment = new PatternAlertsBottomSheetFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPatternAlertBottomSheetBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(PatternAlertViewModel.class);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initiateViews();
        initiatePatternState();
        onClicks(); // Make sure this is called
        setupSymbolSearch();
        observeLoadingState();
        time_interval_tabs();
        observeViewModel();
    }

    private void observeViewModel() {
        viewModel.isLoading.observe(getViewLifecycleOwner(), isLoading -> {
            binding.createAlertProgressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.createAlertText.setVisibility(isLoading ? View.INVISIBLE : View.VISIBLE);

            if (isLoading){
                binding.createPatternAlert.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.button_disabled_background));
            }else {
                binding.createPatternAlert.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.alert_button_shape));
            }

            binding.createPatternAlert.setEnabled(!isLoading);
        });

        viewModel.error.observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(getContext(), "Error: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void onClicks() {
        binding.closeButton.setOnClickListener(v -> closeDialog());
        binding.cancelAction.setOnClickListener(v -> closeDialog());
        binding.symbolLayout.setOnClickListener(v -> toggleSymbolSearchVisibility());
        binding.patternTypeLayout.setOnClickListener(v -> {
            PatternSelectionBottomSheetFragment.newInstance()
                    .show(getParentFragmentManager(), "PatternSelection");
        });
        binding.timeframe.setOnClickListener(v -> toggleTabLayoutVisibility());
        binding.patternStateHolder.setOnClickListener(v -> togglePatternStateDrawerVisibility());
        binding.pressedPatternState.setOnClickListener(v -> changePatternStateDrawer());

        // Add the create alert button click listener
        binding.createPatternAlert.setOnClickListener(v -> createAlert());

        getParentFragmentManager().setFragmentResultListener("pattern_selection_key", this, (requestKey, bundle) -> {
            String selectedPatternName = bundle.getString("selected_pattern_name");
            if (selectedPatternName != null) {
                binding.selectedPatternTextView.setText(selectedPatternName);
            }
        });
    }

    private void createAlert() {
        String userId = getCurrentUserId();
        String symbol = binding.selectedSymbol.getText().toString();
        String patternName = binding.selectedPatternTextView.getText().toString();
        String timeInterval = binding.selectedTime.getText().toString();
        String patternState = binding.selectedPatternState.getText().toString();
        String notification_method = binding.selectedNotificationMethod.getText().toString();

        if (TextUtils.isEmpty(symbol) || symbol.equals("Select")) {
            Toast.makeText(getContext(), "Please select a symbol", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(patternName) || patternName.equals("Select")) {
            Toast.makeText(getContext(), "Please select a pattern", Toast.LENGTH_SHORT).show();
            return;
        }

        viewModel.createPatternAlert(userId, symbol, patternName, timeInterval, patternState, notification_method)
                .observe(getViewLifecycleOwner(), patternAlert -> {
                    if (patternAlert != null) {
                        Toast.makeText(getContext(), "Alert created successfully!", Toast.LENGTH_SHORT).show();
                        // Refresh the list in the background fragment
                        viewModel.refreshAlerts(userId);
                        dismiss();
                    }
                    // Error handling is managed by the main error observer
                });
    }

    private String getCurrentUserId() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return (user != null) ? user.getUid() : null;
    }

    private void initiatePatternState(){
        if (binding.selectedPatternState.getText().equals("Fully Formed")){
            binding.unselectedPatternStateStatus.setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.pattern_half_formed)
            );

            binding.unselectedPatternState.setText("Half-Way Formed");
        }

        else if (binding.selectedPatternState.getText().equals("Half-Way Formed")){
            binding.unselectedPatternStateStatus.setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.pattern_fully_formed)
            );

            binding.unselectedPatternState.setText("Fully Formed");
        }
    }

    private void togglePatternStateDrawerVisibility() {
        TransitionManager.beginDelayedTransition(binding.getRoot());

        if (isPatternStateDrawerVisible) {
            binding.patternStateArrow.setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.drop_down_icon)
            );

            binding.patternStateDrawer.setVisibility(View.GONE);
            isPatternStateDrawerVisible = false;
        } else {
            binding.patternStateArrow.setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.baseline_arrow_drop_down_24)
            );

            binding.patternStateDrawer.setVisibility(View.VISIBLE);
            isPatternStateDrawerVisible = true;
        }
    }

    private void changePatternStateDrawer() {
        if (binding.selectedPatternState.getText().equals("Fully Formed")){
            binding.selectedPatternStateStatus.setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.pattern_half_formed)
            );

            binding.selectedPatternState.setText("Half-Way Formed");

            TransitionManager.beginDelayedTransition(binding.getRoot());
            binding.patternStateDrawer.setVisibility(View.GONE);
            isPatternStateDrawerVisible = false;

            binding.unselectedPatternStateStatus.setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.pattern_fully_formed)
            );

            binding.unselectedPatternState.setText("Fully Formed");
        }

        else if (binding.selectedPatternState.getText().equals("Half-Way Formed")){
            binding.selectedPatternStateStatus.setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.pattern_fully_formed)
            );

            binding.selectedPatternState.setText("Fully Formed");

            TransitionManager.beginDelayedTransition(binding.getRoot());
            binding.patternStateDrawer.setVisibility(View.GONE);
            isPatternStateDrawerVisible = false;

            binding.unselectedPatternStateStatus.setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.pattern_half_formed)
            );

            binding.unselectedPatternState.setText("Half-Way Formed");
        }
    }

    private void toggleTabLayoutVisibility() {
        TransitionManager.beginDelayedTransition(binding.getRoot());

        if (isTimeIntervalTabVisible) {
            binding.tabLayoutArrowState.setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.drop_down_icon)
            );

            binding.tabHolder.setVisibility(View.GONE);
            isTimeIntervalTabVisible = false;
        } else {
            binding.tabLayoutArrowState.setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.baseline_arrow_drop_down_24)
            );

            binding.tabHolder.setVisibility(View.VISIBLE);
            isTimeIntervalTabVisible = true;
        }
    }

    private void toggleSymbolSearchVisibility() {
        TransitionManager.beginDelayedTransition(binding.getRoot());

        if (isSymbolSearchVisible) {
            // Clear the RecyclerView
            symbolAdapter.updateSymbols(new ArrayList<>());

            binding.symbolArrowState.setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.drop_down_icon)
            );
            binding.symbolArrowState.invalidate();  // Force redraw

            // Hide the search layout
            binding.symbolSearchLayout.setVisibility(View.GONE);
            isSymbolSearchVisible = false;

            // Clear search input
            binding.symbolSearchEditText.setText("");

        } else {
            // Clear the RecyclerView
            symbolAdapter.updateSymbols(new ArrayList<>());

            binding.symbolArrowState.setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.baseline_arrow_drop_down_24)
            );
            binding.symbolArrowState.invalidate();  // Force redraw

            // Show the search layout
            binding.symbolSearchLayout.setVisibility(View.VISIBLE);
            isSymbolSearchVisible = true;

            // Focus on the search input
            binding.symbolSearchEditText.requestFocus();
        }

    }

    private void closeDialog() {
        dismiss();
    }

    private void initiateViews() {
        // Setup symbol search RecyclerView
        symbolAdapter = new CachedSymbolSearchAdapter(new ArrayList<>(), this::onSymbolSelected);
        binding.symbolRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.symbolRecyclerView.setAdapter(symbolAdapter);
    }

    private void setupSymbolSearch() {
        binding.symbolSearchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (searchRunnable != null) {
                    handler.removeCallbacks(searchRunnable);
                }
                searchRunnable = () -> {
                    if (s.length() > 0) {
                        viewModel.searchSymbols(s.toString()).observe(getViewLifecycleOwner(), symbols -> {
                            symbolAdapter.updateSymbols(symbols);
                        });
                    } else {
                        symbolAdapter.updateSymbols(new ArrayList<>());
                    }
                };
                handler.postDelayed(searchRunnable, 300);  // Wait 300ms
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    /**
     * Observes the symbol search loading state from the ViewModel
     * and shows/hides a progress bar.
     */
    private void observeLoadingState() {
        viewModel.isSymbolSearchLoading.observe(getViewLifecycleOwner(), isLoading -> {
            if (isLoading != null && isLoading) {
                // TODO: Make sure you have a ProgressBar with this ID in your layout
                binding.symbolSearchProgressBar.setVisibility(View.VISIBLE);
                binding.symbolRecyclerView.setVisibility(View.GONE);
            } else {
                binding.symbolSearchProgressBar.setVisibility(View.GONE);
                binding.symbolRecyclerView.setVisibility(View.VISIBLE);
            }
        });
    }

    private void onSymbolSelected(CachedSymbol symbol) {
        binding.selectedSymbol.setText(symbol.symbol); // Adjust based on your CachedSymbol model
        binding.symbolSearchEditText.setText("");
        symbolAdapter.updateSymbols(new ArrayList<>());

        // Hide the search layout
        TransitionManager.beginDelayedTransition(binding.getRoot());
        binding.symbolSearchLayout.setVisibility(View.GONE);
        isSymbolSearchVisible = false;
    }

    /**
     * Sets up the time interval tabs (1m, 5m, 15m, etc.)
     */
    private void time_interval_tabs() {
        String[] intervals = {
                "1m", "5m",
                "15m", "30m",
                "1h", "2h",
                "1d", "1w",
                "1M"
        };

        // Clear any tabs first if needed
        binding.timeIntervalTabLayout.removeAllTabs();

        // Set tab mode before adding tabs - use scrollable to ensure start alignment works properly
        binding.timeIntervalTabLayout.setTabMode(TabLayout.MODE_SCROLLABLE);
        binding.timeIntervalTabLayout.setTabGravity(TabLayout.GRAVITY_START);

        // First, find the HorizontalScrollView inside the TabLayout
        // The structure typically is: TabLayout -> HorizontalScrollView -> LinearLayout (with tabs)
        ViewGroup tabLayoutGroup = binding.timeIntervalTabLayout;

        // Loop through children to find the HorizontalScrollView
        for (int i = 0; i < tabLayoutGroup.getChildCount(); i++) {
            View child = tabLayoutGroup.getChildAt(i);
            if (child.getClass().getSimpleName().contains("ScrollView")) {
                // Found the scroll view, cast it to ViewGroup to use setClipToPadding
                ViewGroup scrollView = (ViewGroup) child;
                scrollView.setClipToPadding(false);
                scrollView.setPadding(0, 0, 0, 0);

                // Set negative margins to allow scrolling to edges
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) scrollView.getLayoutParams();
                params.setMarginStart(-15);
                params.setMarginEnd(-15);
                scrollView.setLayoutParams(params);
                break;
            }
        }

        for (String interval : intervals) {
            TabLayout.Tab tab = binding.timeIntervalTabLayout.newTab();
            View customTab = LayoutInflater.from(requireContext()).inflate(R.layout.tab_item, binding.timeIntervalTabLayout, false);

            // No extra padding on the custom tab
            TextView text = customTab.findViewById(R.id.tabTitle);
            LinearLayout tabHolder = customTab.findViewById(R.id.tab_holder);

            tabHolder.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_unselected));
            text.setText(interval);
            text.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_inactive));

            tab.setCustomView(customTab);
            binding.timeIntervalTabLayout.addTab(tab);
        }

        // Adjust spacing after tabs are added
        for (int i = 0; i < binding.timeIntervalTabLayout.getTabCount(); i++) {
            View tabView = ((ViewGroup) binding.timeIntervalTabLayout.getChildAt(0)).getChildAt(i);
            ViewGroup.MarginLayoutParams params = getMarginLayoutParams(tabView, i);

            tabView.setLayoutParams(params);
        }

        binding.timeIntervalTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                TextView text = Objects.requireNonNull(tab.getCustomView()).findViewById(R.id.tabTitle);
                LinearLayout tabHolder = Objects.requireNonNull(tab.getCustomView()).findViewById(R.id.tab_holder);
                tabHolder.setBackgroundResource(R.drawable.bg_selected);
                text.setTextColor(ContextCompat.getColor(requireContext(), R.color.off_white));

                // Reset historical state and update the current interval
                String interval = ((TextView) tab.getCustomView().findViewById(R.id.tabTitle)).getText().toString();
                binding.selectedTime.setText(interval);
                toggleTabLayoutVisibility();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                TextView text = Objects.requireNonNull(tab.getCustomView()).findViewById(R.id.tabTitle);
                LinearLayout tabHolder = Objects.requireNonNull(tab.getCustomView()).findViewById(R.id.tab_holder);
                tabHolder.setBackgroundResource(R.drawable.bg_unselected);
                text.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_inactive));
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // Optional: do nothing or refresh
            }
        });

        // After setting up tabs, select first tab
        TabLayout.Tab firstTab = binding.timeIntervalTabLayout.getTabAt(0);
        if (firstTab != null) {
            // Select tab without triggering listener
            binding.timeIntervalTabLayout.selectTab(firstTab, false);

            // Then manually update the visual state
            TextView text = Objects.requireNonNull(firstTab.getCustomView()).findViewById(R.id.tabTitle);
            LinearLayout tabHolder = Objects.requireNonNull(firstTab.getCustomView()).findViewById(R.id.tab_holder);
            tabHolder.setBackgroundResource(R.drawable.bg_selected);
            text.setTextColor(ContextCompat.getColor(requireContext(), R.color.off_white));
        }

    }

    @NonNull
    private ViewGroup.MarginLayoutParams getMarginLayoutParams(View tabView, int i) {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) tabView.getLayoutParams();

        // For all tabs except first and last, add equal margins
        if (i > 0 && i < binding.timeIntervalTabLayout.getTabCount() - 1) {
            params.setMarginStart(15);
            params.setMarginEnd(15);
        }
        // First tab gets standard margin
        else if (i == 0) {
            params.setMarginStart(0);
            params.setMarginEnd(15);
        }
        // Last tab gets standard margin
        else {
            params.setMarginStart(15);
            params.setMarginEnd(0);
        }
        return params;
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

            if (behavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                behavior.setDraggable(false);
            }

            ViewGroup.LayoutParams layoutParams = parent.getLayoutParams();
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            parent.setLayoutParams(layoutParams);
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
        binding = null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (searchRunnable != null) {
            handler.removeCallbacks(searchRunnable);
        }

        binding = null;
    }
}
