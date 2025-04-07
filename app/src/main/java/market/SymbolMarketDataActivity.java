package market;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.claw.ai.R;
import com.claw.ai.databinding.ActivityMainBinding;
import com.claw.ai.databinding.ActivitySymbolMarketDataBinding;
import com.google.android.material.tabs.TabLayout;

import java.util.Objects;

public class SymbolMarketDataActivity extends AppCompatActivity {

    ActivitySymbolMarketDataBinding binding;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivitySymbolMarketDataBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        binding.dragHandle.setOnTouchListener(new View.OnTouchListener() {
            float initialY;
            int topInitialHeight;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialY = event.getRawY();
                        topInitialHeight = binding.topSection.getHeight();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dy = event.getRawY() - initialY;
                        int newTopHeight = (int) (topInitialHeight + dy);

                        // Prevent too small/large
                        int minHeight = 100;
                        int maxHeight = binding.topSection.getHeight() + binding.bottomSection.getHeight() - minHeight;

                        newTopHeight = Math.max(minHeight, Math.min(newTopHeight, maxHeight));
                        int newBottomHeight = binding.topSection.getHeight() + binding.bottomSection.getHeight() - newTopHeight;

                        // Apply new heights
                        binding.topSection.getLayoutParams().height = newTopHeight;
                        binding.topSection.requestLayout();

                        binding.bottomSection.getLayoutParams().height = newBottomHeight;
                        binding.bottomSection.requestLayout();
                        return true;
                    default:
                        return false;
                }
            }
        });

        time_interval_tabs();

    }

    private void time_interval_tabs() {
        String[] intervals = {
                "1m", "5m", "15m", "30m",
                "1h", "4h",
                "1d", "1w", "1M"
        };

        for (String interval : intervals) {
            TabLayout.Tab tab = binding.marketChartLayout.timeIntervalTabLayout.newTab();
            View customTab = LayoutInflater.from(this).inflate(R.layout.tab_item, binding.marketChartLayout.timeIntervalTabLayout, false);
            customTab.setPadding(8, 0, 8, 0);  // Adjust these values as needed
            TextView text = customTab.findViewById(R.id.tabTitle);
            customTab.setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.bg_unselected));
            text.setText(interval);
            text.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.gray_inactive));
            tab.setCustomView(customTab);
            binding.marketChartLayout.timeIntervalTabLayout.addTab(tab);
        }


        binding.marketChartLayout.timeIntervalTabLayout.setTabMode(TabLayout.MODE_AUTO);
        binding.marketChartLayout.timeIntervalTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                TextView text = Objects.requireNonNull(tab.getCustomView()).findViewById(R.id.tabTitle);
                LinearLayout tabHolder = Objects.requireNonNull(tab.getCustomView()).findViewById(R.id.tab_holder);
                tabHolder.setBackgroundResource(R.drawable.bg_selected);
                text.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.white));

                // Refresh chart here
                String interval = text.getText().toString();
//                updateChart(interval);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                TextView text = Objects.requireNonNull(tab.getCustomView()).findViewById(R.id.tabTitle);
                LinearLayout tabHolder = Objects.requireNonNull(tab.getCustomView()).findViewById(R.id.tab_holder);
                tabHolder.setBackgroundResource(R.drawable.bg_unselected);
                text.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.gray_inactive));
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // Optional: do nothing or refresh
            }
        });

    }
}