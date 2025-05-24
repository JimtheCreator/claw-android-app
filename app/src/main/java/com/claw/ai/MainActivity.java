package com.claw.ai;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.claw.ai.databinding.ActivityMainBinding;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.Stack;

import fragments.AlertTabFragment;
import fragments.HomeTabFragment;
import fragments.MoreTabFragment;
import recent_tabs.RecentTabsFragment;

public class MainActivity extends AppCompatActivity {

    ActivityMainBinding binding;
    private final Fragment homeFragment = new HomeTabFragment();
    private final Stack<Integer> tabBackStack = new Stack<>();
    private boolean isSwitchingTabs = false;

    private final Fragment recentTabsFragment = new RecentTabsFragment();
    private final Fragment alertTabFragment = new AlertTabFragment();
    private final Fragment moreTabFragment = new MoreTabFragment();
    private FirebaseAnalytics mFirebaseAnalytics;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        // Initialize view binding
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        googleAnalytics();

        // Load default fragment and push to back stack
        if (savedInstanceState == null) {
            loadFragment(homeFragment);
            binding.bottomNavigation.setSelectedItemId(R.id.home);
            tabBackStack.push(R.id.home); // 👈 FIX: Push home tab to back stack
        }

        // Set up bottom navigation
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (!isSwitchingTabs && (tabBackStack.isEmpty() || tabBackStack.peek() != itemId)) {
                tabBackStack.push(itemId);
            }

            isSwitchingTabs = false;

            Fragment selectedFragment = null;

            // Select the appropriate fragment based on the item clicked
            if (itemId == R.id.home) {
                selectedFragment = homeFragment;
                binding.bottomNavigation.setBackground(ContextCompat.getDrawable(MainActivity.this, R.color.black_shade));
            } else if (itemId == R.id.alert_signals) {
                selectedFragment = alertTabFragment;
                binding.bottomNavigation.setBackground(ContextCompat.getDrawable(MainActivity.this, R.color.black2_0));
            } else if (itemId == R.id.more) {
                selectedFragment = moreTabFragment;
                binding.bottomNavigation.setBackground(ContextCompat.getDrawable(MainActivity.this, R.color.black2_0));
            } else {
                selectedFragment = homeFragment;
            }

            loadFragment(selectedFragment);
            return true;
        });

        // Handle back press
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleCustomBackPress();
            }
        });
    }

    private void googleAnalytics() {
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
    }

    private void loadFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        hideAllFragments(transaction);

        if (!fragment.isAdded()) {
            transaction.add(R.id.fragment_container, fragment);
        } else {
            transaction.show(fragment);
        }

        transaction.commit();
    }

    private void hideAllFragments(FragmentTransaction transaction) {
        if (homeFragment.isAdded()) transaction.hide(homeFragment);
        if (recentTabsFragment.isAdded()) transaction.hide(recentTabsFragment);
        if (alertTabFragment.isAdded()) transaction.hide(alertTabFragment);
        if (moreTabFragment.isAdded()) transaction.hide(moreTabFragment);
    }

    public void switchToTab(int itemId) {
        BottomNavigationView navView = findViewById(R.id.bottom_navigation);
        navView.setSelectedItemId(itemId);
    }

    private void handleCustomBackPress() {
        BottomNavigationView navView = findViewById(R.id.bottom_navigation);

        if (tabBackStack.size() > 1) {
            tabBackStack.pop(); // Remove current
            int previousTab = tabBackStack.peek();
            isSwitchingTabs = true;
            navView.setSelectedItemId(previousTab);
        } else {
            finish(); // Exit app
        }
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        tabBackStack.clear();
        tabBackStack.push(R.id.home); // Reset to default tab
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
