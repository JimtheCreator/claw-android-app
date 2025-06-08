package bottomsheets;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.claw.ai.R;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class SetupBottomSheetFragment extends BottomSheetDialogFragment {

    private RelativeLayout btnNotifications, btnBattery, btnAutostart;
    Button btnContinue;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    ViewGroup container_view;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, R.style.BottomSheetDialogTheme);

        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    updateButtonStates();
                    if (!isGranted) {
                        Toast.makeText(getContext(), "Notifications are required for app functionality", Toast.LENGTH_LONG).show();
                    }
                });

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_setup_bottom_sheet, container, false);

        container_view = view.findViewById(R.id.content);
        btnNotifications = view.findViewById(R.id.btn_notifications);
        btnBattery = view.findViewById(R.id.btn_battery_optimization);
        btnAutostart = view.findViewById(R.id.btn_autostart);
        btnContinue = view.findViewById(R.id.btn_continue);

        setupButtons();
        updateButtonStates();

        return view;
    }

    private void setupButtons() {
        btnNotifications.setOnClickListener(v -> requestNotificationPermission());
        btnBattery.setOnClickListener(v -> requestIgnoreBatteryOptimizations());
        btnAutostart.setOnClickListener(v -> openManufacturerSettings());
        btnContinue.setOnClickListener(v -> {
            if (isSetupComplete()) {
                dismiss();
            } else {
                Toast.makeText(getContext(), "Please enable all required permissions", Toast.LENGTH_LONG).show();
            }
        });

        String manufacturer = Build.MANUFACTURER.toLowerCase();
        if (manufacturer.contains("xiaomi") || manufacturer.contains("oppo") ||
                manufacturer.contains("vivo") || manufacturer.contains("huawei")) {
            btnAutostart.setVisibility(View.VISIBLE);
        } else {
            btnAutostart.setVisibility(View.GONE);
        }
    }

    private void updateButtonStates() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean notificationsGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            beginTransitionAnimation();
            btnNotifications.setVisibility(!notificationsGranted ? View.VISIBLE : View.GONE);
        } else {
            beginTransitionAnimation();
            btnNotifications.setVisibility(View.GONE);
        }

        PowerManager pm = (PowerManager) requireContext().getSystemService(Context.POWER_SERVICE);
        boolean batteryOptimized = pm.isIgnoringBatteryOptimizations(requireContext().getPackageName());
        beginTransitionAnimation();
        btnBattery.setVisibility(!batteryOptimized ? View.VISIBLE : View.GONE);

        btnContinue.setEnabled(isSetupComplete());
    }

    private void beginTransitionAnimation(){
        TransitionManager.beginDelayedTransition(container_view);
    }

    private boolean isSetupComplete() {
        boolean notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        PowerManager pm = (PowerManager) requireContext().getSystemService(Context.POWER_SERVICE);
        boolean batteryOptimized = pm.isIgnoringBatteryOptimizations(requireContext().getPackageName());
        return notificationsGranted && batteryOptimized;
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void requestIgnoreBatteryOptimizations() {
        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
        startActivity(intent);
    }

    private void openManufacturerSettings() {
        String manufacturer = Build.MANUFACTURER.toLowerCase();
        Intent intent = new Intent();

        if (manufacturer.contains("xiaomi")) {
            intent.setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"));
        } else if (manufacturer.contains("oppo")) {
            intent.setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"));
        } else if (manufacturer.contains("vivo")) {
            intent.setComponent(new ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"));
        } else if (manufacturer.contains("huawei")) {
            intent.setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"));
        }

        try {
            startActivity(intent);
        } catch (Exception e) {
            Intent fallbackIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            fallbackIntent.setData(Uri.parse("package:" + requireContext().getPackageName()));
            startActivity(fallbackIntent);
            Toast.makeText(getContext(), "Please enable autostart manually in settings", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateButtonStates();
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
            ViewGroup.LayoutParams layoutParams = parent.getLayoutParams();
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            parent.setLayoutParams(layoutParams);
            setCancelable(false);
        }
    }
}
