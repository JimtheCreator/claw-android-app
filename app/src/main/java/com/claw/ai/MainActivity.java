package com.claw.ai;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.claw.ai.databinding.ActivityMainBinding;
import com.google.firebase.analytics.FirebaseAnalytics;

import pricing.SubscriptionPlanSheetFragment;
import fragments.AlertTabFragment;
import archives.ArchivedHomeTabFragment;
import fragments.HomeTabFragment;
import fragments.MoreTabFragment;
import recent_tabs.RecentTabsFragment;

public class MainActivity extends AppCompatActivity {

    ActivityMainBinding binding;
    private final Fragment homeFragment = new HomeTabFragment();
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

        // Load default fragment
        if (savedInstanceState == null) {
            loadFragment(homeFragment);
            binding.bottomNavigation.setSelectedItemId(R.id.home);
        }

        // Set up bottom navigation with view binding
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;

            int itemId = item.getItemId();

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
    }

    private void googleAnalytics() {
        // Obtain the FirebaseAnalytics instance.
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
    }

    private void loadFragment(Fragment fragment) {
        // Begin a transaction
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        // Hide all fragments before showing the selected one
        hideAllFragments(transaction);

        // Show the selected fragment
        if (!fragment.isAdded()) {
            transaction.add(R.id.fragment_container, fragment);
        } else {
            transaction.show(fragment);
        }

        // Commit the transaction
        transaction.commit();
    }

    private void hideAllFragments(FragmentTransaction transaction) {
        // Hide all fragments from the fragment manager
        if (homeFragment.isAdded()) transaction.hide(homeFragment);
        if (recentTabsFragment.isAdded()) transaction.hide(recentTabsFragment);
        if (alertTabFragment.isAdded()) transaction.hide(alertTabFragment);
        if (moreTabFragment.isAdded()) transaction.hide(moreTabFragment);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}