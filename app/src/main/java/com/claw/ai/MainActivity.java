package com.claw.ai;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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

import bottomsheets.SetupBottomSheetFragment;
import fragments.alerts.AlertTabFragment;
import fragments.HomeTabFragment;
import fragments.MoreTabFragment;
import recent_tabs.RecentTabsFragment;
import android.Manifest;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.claw.ai.databinding.ActivityMainBinding;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.Stack;

import fragments.alerts.AlertTabFragment;
import fragments.HomeTabFragment;
import fragments.MoreTabFragment;
import recent_tabs.RecentTabsFragment;
import viewmodels.SharedRefreshViewModel;

import android.Manifest;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    ActivityMainBinding binding;
    private final Fragment homeFragment = new HomeTabFragment();
    private final Stack<Integer> tabBackStack = new Stack<>();
    private boolean isSwitchingTabs = false;

    private final Fragment recentTabsFragment = new RecentTabsFragment();
    private final Fragment alertTabFragment = new AlertTabFragment();
    private final Fragment moreTabFragment = new MoreTabFragment();
    private FirebaseAnalytics mFirebaseAnalytics;

    private SharedRefreshViewModel sharedRefreshViewModel;
    private BroadcastReceiver refreshReceiver;
    private static final String REFRESH_ACTION_SUFFIX = ".ACTION_REFRESH_ALERTS";

    @Override
    protected void onStart() {
        super.onStart();

        // Check if first launch and show setup
        askNotificationPermission();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        googleAnalytics();

        if (savedInstanceState == null) {
            loadFragment(homeFragment);
            binding.bottomNavigation.setSelectedItemId(R.id.home);
            tabBackStack.push(R.id.home);
        }

        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (!isSwitchingTabs && (tabBackStack.isEmpty() || tabBackStack.peek() != itemId)) {
                tabBackStack.push(itemId);
            }

            isSwitchingTabs = false;

            Fragment selectedFragment = null;

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

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleCustomBackPress();
            }
        });

        // Get the shared ViewModel scoped to the Activity's lifecycle
        sharedRefreshViewModel = new ViewModelProvider(this).get(SharedRefreshViewModel.class);

        // Setup the receiver to listen for refresh requests from the messaging service
        setupBroadcastReceiver();
    }

    private void setupBroadcastReceiver() {
        final String refreshAction = getPackageName() + REFRESH_ACTION_SUFFIX;
        IntentFilter filter = new IntentFilter(refreshAction);

        refreshReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (refreshAction.equals(intent.getAction())) {
                    // When a broadcast is received, tell the shared ViewModel to fire the event
                    sharedRefreshViewModel.requestPriceAlertsRefresh();
                }
            }
        };

        // Register the receiver with the NOT_EXPORTED flag for security
        ContextCompat.registerReceiver(this, refreshReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

//    private boolean isFirstLaunch() {
//        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
//        boolean isFirst = prefs.getBoolean("isFirstLaunch", true);
//        if (isFirst) {
//            prefs.edit().putBoolean("isFirstLaunch", false).apply();
//        }
//        return isFirst;
//    }

    private void showSetupBottomSheet() {
        SetupBottomSheetFragment fragment = new SetupBottomSheetFragment();
        fragment.show(getSupportFragmentManager(), "setup");
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED) {
                // Permission granted
            } else {
                showSetupBottomSheet();
            }
        }else {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            boolean batteryOptimized = pm.isIgnoringBatteryOptimizations(getApplicationContext().getPackageName());

            if (!batteryOptimized){
                showSetupBottomSheet();
            }
        }
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
            tabBackStack.pop();
            int previousTab = tabBackStack.peek();
            isSwitchingTabs = true;
            navView.setSelectedItemId(previousTab);
        } else {
            finish();
        }
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        tabBackStack.clear();
        tabBackStack.push(R.id.home);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
