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

import bottomsheets.PricingPackageSheetFragment;
import fragments.AlertTabFragment;
import fragments.HomeTabFragment;
import fragments.MoreTabFragment;
import recent_tabs.RecentTabsFragment;

public class MainActivity extends AppCompatActivity {

    ActivityMainBinding binding;

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


        // Load default fragment
        if (savedInstanceState == null) {
            loadFragment(new HomeTabFragment());
            binding.bottomNavigation.setSelectedItemId(R.id.home);
        }


        // Set up bottom navigation with view binding
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            Fragment fragment;
            int itemId = item.getItemId();

            if (itemId == R.id.recent_tabs) {
                fragment = new RecentTabsFragment();
                binding.bottomNavigation.setBackground(ContextCompat.getDrawable(MainActivity.this, R.color.black2_0));
            } else if (itemId == R.id.home) {
                fragment = new HomeTabFragment();
                binding.bottomNavigation.setBackground(ContextCompat.getDrawable(MainActivity.this, R.color.black_shade));
            } else if (itemId == R.id.alert_signals) {
                fragment = new AlertTabFragment();
                binding.bottomNavigation.setBackground(ContextCompat.getDrawable(MainActivity.this, R.color.black2_0));
            } else if (itemId == R.id.more) {
                fragment = new MoreTabFragment();
                PricingPackageSheetFragment pricingPackageSheetFragment = PricingPackageSheetFragment.newInstance();
                pricingPackageSheetFragment.show(getSupportFragmentManager(), "PricingPackageSheetFragment");
                binding.bottomNavigation.setBackground(ContextCompat.getDrawable(MainActivity.this, R.color.black2_0));
            } else {
                fragment = new HomeTabFragment();
            }

            loadFragment(fragment);
            return true;
        });
    }

    private void loadFragment(Fragment fragment) {
        // Get current fragment to determine animation direction
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);

        // Initialize transaction
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        // Apply animations based on navigation direction
//        if (currentFragment != null) {
//            if (fragment instanceof HomeTabFragment) {
//                transaction.setCustomAnimations(
//                        R.anim.slide_in_left,
//                        R.anim.slide_out_right
//                );
//            } else if (fragment instanceof MoreTabFragment) {
//                transaction.setCustomAnimations(
//                        R.anim.slide_in_right,
//                        R.anim.slide_out_left
//                );
//            } else {
//                // Default animations
//                transaction.setCustomAnimations(
//                        R.anim.slide_in_right,
//                        R.anim.slide_out_left
//                );
//            }
//        }

        // Replace fragment and commit
        transaction.replace(R.id.fragment_container, fragment);
        transaction.commit();
    }

    // Clean up view binding when activity is destroyed
    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}