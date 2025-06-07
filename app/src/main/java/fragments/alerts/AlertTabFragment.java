package fragments.alerts;

import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.claw.ai.R;
import com.claw.ai.databinding.FragmentAlertTabBinding;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class AlertTabFragment extends Fragment {
    private FragmentAlertTabBinding binding;
    private PriceAlertsFragment priceAlertsFragment;
    private PatternAlertsFragment patternAlertsFragment;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAlertTabBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Set offscreen limit to keep both fragments in memory
        binding.viewPager.setOffscreenPageLimit(2);
        String[] tabTitles = {"Price", "Pattern"};

        // Create adapter with persistent fragments
        FragmentStateAdapter adapter = new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                if (position == 0) {
                    if (priceAlertsFragment == null) {
                        priceAlertsFragment = new PriceAlertsFragment();
                    }
                    return priceAlertsFragment;
                } else {
                    if (patternAlertsFragment == null) {
                        patternAlertsFragment = new PatternAlertsFragment();
                    }
                    return patternAlertsFragment;
                }
            }

            @Override
            public int getItemCount() {
                return tabTitles.length;
            }
        };

        binding.viewPager.setAdapter(adapter);

        // Setup TabLayoutMediator
        new TabLayoutMediator(binding.tabLayout, binding.viewPager,
                (tab, position) -> tab.setText(tabTitles[position])
        ).attach();

        TabLayout tabLayout = binding.tabLayout;

        // Setup custom tab styling
        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = tabLayout.getTabAt(i);
            if (tab != null && tab.getCustomView() == null) {
                ContextThemeWrapper styledContext = new ContextThemeWrapper(requireContext(), R.style.TabTextStyle);
                TextView textView = new TextView(styledContext);
                textView.setText(tab.getText());

                if (i == tabLayout.getSelectedTabPosition()) {
                    textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.dark_beige));
                } else {
                    textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_inactive));
                }

                tab.setCustomView(textView);
            }
        }

        // Tab selection listener
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                TextView text = (TextView) tab.getCustomView();
                if (text != null) {
                    text.setTextColor(ContextCompat.getColor(requireContext(), R.color.dark_beige));
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                TextView text = (TextView) tab.getCustomView();
                if (text != null) {
                    text.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_inactive));
                }
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // Optional: refresh data when tab is reselected
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh the currently visible tab when returning to this fragment
        if (binding != null) {
            int currentTab = binding.viewPager.getCurrentItem();
            if (currentTab == 0 && priceAlertsFragment != null) {
                priceAlertsFragment.refreshData();
            }
            // Add similar logic for pattern alerts if needed
            // else if (currentTab == 1 && patternAlertsFragment != null) {
            //     patternAlertsFragment.refreshData();
            // }
        }
    }
}