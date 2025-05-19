package archives;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.claw.ai.R;
import com.claw.ai.databinding.ActivitySymbolMarketDataBinding;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.android.material.tabs.TabLayout;
import com.tradingview.lightweightcharts.api.chart.models.color.IntColor;
import com.tradingview.lightweightcharts.api.chart.models.color.surface.SolidColor;
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi;
import com.tradingview.lightweightcharts.api.options.models.AxisPressedMouseMoveOptions;
import com.tradingview.lightweightcharts.api.options.models.CandlestickSeriesOptions;
import com.tradingview.lightweightcharts.api.options.models.GridLineOptions;
import com.tradingview.lightweightcharts.api.options.models.GridOptions;
import com.tradingview.lightweightcharts.api.options.models.HandleScaleOptions;
import com.tradingview.lightweightcharts.api.options.models.HandleScrollOptions;
import com.tradingview.lightweightcharts.api.options.models.KineticScrollOptions;
import com.tradingview.lightweightcharts.api.options.models.LayoutOptions;
import com.tradingview.lightweightcharts.api.options.models.PriceScaleOptions;
import com.tradingview.lightweightcharts.api.options.models.TimeScaleOptions;
import com.tradingview.lightweightcharts.api.series.models.CandlestickData;
import com.tradingview.lightweightcharts.api.series.models.Time;
import com.tradingview.lightweightcharts.api.series.models.TimeRange;
import com.tradingview.lightweightcharts.view.ChartsView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import backend.SymbolMarketEndpoints;
import backend.MainClient;
import backend.WebSocketService;
import data.remote.WebSocketServiceImpl;
import factory.StreamViewModelFactory;
import kotlin.Unit;
import models.HistoricalState;
import models.MarketDataEntity;
import models.MarketDataResponse;
import models.StreamMarketData;
import okhttp3.OkHttpClient;
import repositories.StreamRepository;
import retrofit2.Response;
import timber.log.Timber;
import viewmodels.StreamViewModel;

public class ArchivedSymbolMarketDataActivity extends AppCompatActivity {

    ActivitySymbolMarketDataBinding binding;
    private StreamViewModel viewModel;
    double[] sparklineArray;
    String asset;
    String symbol;
    private final Set<Long> closedCandleTimes = new HashSet<>();
    // TradingView chart components
    private ChartsView chartsView;
    // Existing variables
    private SeriesApi candlestickSeries;

    private HistoricalState historicalState = new HistoricalState();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final List<CandlestickData> allCandles = new ArrayList<>();
    private final SimpleDateFormat apiDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
    String[] intervals;

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

        // Get reference to TradingView chart
        chartsView = binding.marketChartLayout.candlesStickChart;

        initViewModel();
        globalInit();
        onClicksAndDrags();
        setupObservers();
        time_interval_tabs();
    }

    private void initViewModel() {
        WebSocketService wsService = new WebSocketServiceImpl(new OkHttpClient());
        StreamRepository repository = new StreamRepository(wsService, getApplicationContext());
        viewModel = new ViewModelProvider(this, new StreamViewModelFactory(repository))
                .get(StreamViewModel.class);
    }

    private void globalInit() {
        symbol = getIntent().getStringExtra("SYMBOL");
        asset = getIntent().getStringExtra("ASSET");
        sparklineArray = getIntent().getDoubleArrayExtra("SPARKLINE");
        double initialPrice = getIntent().getDoubleExtra("CURRENT_PRICE", 0.0);
        double initialChange = getIntent().getDoubleExtra("CHANGE_24H", 0.0);
        if (symbol != null) {
            viewModel.connect(symbol, "1m", true);

            binding.marketChartLayout.symbol.setText(symbol);
            binding.marketChartLayout.asset.setText(asset);

            // Set initial price and change
            binding.marketChartLayout.currentPrice.setText(
                    String.format(Locale.getDefault(), "US$%.2f", initialPrice)
            );
            binding.marketChartLayout.percentagePriceChange.setText(
                    String.format(Locale.getDefault(), "%.2f%%", initialChange)
            );

            // Set initial color
            int colorRes = initialChange >= 0 ?
                    R.color.green_chart_color : R.color.crimson_red;
            binding.marketChartLayout.percentagePriceChange.setTextColor(
                    ContextCompat.getColor(this, colorRes)
            );

            // Update sparkline
            updateSparkline(sparklineArray);

            // Initialize chart after a short delay to ensure view is fully laid out
            binding.marketChartLayout.candlesStickChart.post(() -> {
                initializeTradingViewChart();
            });
        } else {
            Timber.e("Symbol is null");
        }
    }

    private void onClicksAndDrags() {
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

        binding.closePage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // Assuming you have a button with id "showBottomSectionButton"
        binding.marketChartLayout.showBottomSectionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Show both the bottom section and drag handle
                binding.bottomSection.setVisibility(View.VISIBLE);
                binding.dragHandle.setVisibility(View.VISIBLE);

                // Set initial heights (e.g., 70% top, 30% bottom)
                int totalHeight = binding.main.getHeight() - binding.main.getPaddingTop() - binding.main.getPaddingBottom();
                int actionBarHeight = ((RelativeLayout) binding.main.getChildAt(0)).getHeight();
                int availableHeight = totalHeight - actionBarHeight;

                ViewGroup.LayoutParams topParams = binding.topSection.getLayoutParams();
                topParams.height = (int) (availableHeight * 0.7);
                binding.topSection.setLayoutParams(topParams);

                ViewGroup.LayoutParams bottomParams = binding.bottomSection.getLayoutParams();
                bottomParams.height = (int) (availableHeight * 0.3);
                binding.bottomSection.setLayoutParams(bottomParams);
            }
        });
    }

    // 1. Improved logging in setupObservers() to track data flow
    private void setupObservers() {
        viewModel.getMarketDataStream().observe(this, data -> {
            if (data != null) {
                // Log receipt of data
                Timber.d("Received stream data: price=%s, has_ohlcv=%s",
                        data.getPrice(),
                        (data.getOhlcv() != null ? "yes" : "no"));

                updateUI(data);

                // Only update chart if candlestickSeries is initialized
                if (candlestickSeries != null) {
                    updateChartWithLiveData(data);
                } else {
                    Timber.e("Cannot update chart - candlestickSeries is null. Initializing chart...");
                    // Try to initialize chart if not already initialized
                    binding.marketChartLayout.candlesStickChart.post(() -> initializeTradingViewChart());
                }
            } else {
                Timber.w("Received null market data stream update");
            }
        });

        viewModel.getErrorStream().observe(this, error -> {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            Timber.e("WebSocket error: %s", error);
        });
    }

    private void time_interval_tabs() {
        intervals = new String[] {
                "1m", "5m",
                "15m", "30m",
                "1h", "2h",
                "4h", "6h",
                "1d", "3d",
                "1w", "1M"
        };

        // Clear any tabs first if needed
        binding.marketChartLayout.timeIntervalTabLayout.removeAllTabs();

        // Set tab mode before adding tabs - use scrollable to ensure start alignment works properly
        binding.marketChartLayout.timeIntervalTabLayout.setTabMode(TabLayout.MODE_SCROLLABLE);
        binding.marketChartLayout.timeIntervalTabLayout.setTabGravity(TabLayout.GRAVITY_START);

        // First, find the HorizontalScrollView inside the TabLayout
        // The structure typically is: TabLayout -> HorizontalScrollView -> LinearLayout (with tabs)
        ViewGroup tabLayoutGroup = binding.marketChartLayout.timeIntervalTabLayout;

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
            TabLayout.Tab tab = binding.marketChartLayout.timeIntervalTabLayout.newTab();
            View customTab = LayoutInflater.from(this).inflate(R.layout.tab_item, binding.marketChartLayout.timeIntervalTabLayout, false);

            // No extra padding on the custom tab
            TextView text = customTab.findViewById(R.id.tabTitle);
            LinearLayout tabHolder = customTab.findViewById(R.id.tab_holder);

            tabHolder.setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.bg_unselected));
            text.setText(interval);
            text.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.gray_inactive));

            tab.setCustomView(customTab);
            binding.marketChartLayout.timeIntervalTabLayout.addTab(tab);
        }

        // Adjust spacing after tabs are added
        for (int i = 0; i < binding.marketChartLayout.timeIntervalTabLayout.getTabCount(); i++) {
            View tabView = ((ViewGroup) binding.marketChartLayout.timeIntervalTabLayout.getChildAt(0)).getChildAt(i);
            ViewGroup.MarginLayoutParams params = getMarginLayoutParams(tabView, i);

            tabView.setLayoutParams(params);
        }

        binding.marketChartLayout.timeIntervalTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                TextView text = Objects.requireNonNull(tab.getCustomView()).findViewById(R.id.tabTitle);
                LinearLayout tabHolder = Objects.requireNonNull(tab.getCustomView()).findViewById(R.id.tab_holder);
                tabHolder.setBackgroundResource(R.drawable.bg_selected);
                text.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.white));

                // Get the selected interval
                String interval = text.getText().toString();

                // Reset state for new interval
                allCandles.clear();

                // Clear existing data before loading new
                if (candlestickSeries != null) {
                    candlestickSeries.setData(new ArrayList<>());
                }

                allCandles.clear();

                historicalState = new HistoricalState();

                // Reconfigure chart - will load historical data
                // Use the new method to change interval properly
                changeInterval(interval);
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

        // After setting up tabs, select first tab
        TabLayout.Tab firstTab = binding.marketChartLayout.timeIntervalTabLayout.getTabAt(0);
        if (firstTab != null) {
            firstTab.select();
        }
    }

    @NonNull
    private ViewGroup.MarginLayoutParams getMarginLayoutParams(View tabView, int i) {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) tabView.getLayoutParams();

        // For all tabs except first and last, add equal margins
        if (i > 0 && i < binding.marketChartLayout.timeIntervalTabLayout.getTabCount() - 1) {
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

    // Update sparkline using existing MPAndroidChart
    private void updateSparkline(double[] sparklineArray) {
        LineChart chart = binding.marketChartLayout.sparklineChart;

        // Convert to Entry list
        List<Entry> entries = new ArrayList<>();

        if (sparklineArray != null) {
            for (int i = 0; i < sparklineArray.length; i++) {
                entries.add(new Entry(i, (float) sparklineArray[i]));
            }
        }

        // Reuse adapter's chart setup logic
        setupSparklineChart(chart, entries);
    }

    private void setupSparklineChart(LineChart chart, List<Entry> entries) {
        // Replicate the logic from CryptosAdapter's setupChart
        if (entries.isEmpty()) return;

        // Determine trend
        boolean isDowntrend = entries.get(entries.size() - 1).getY() < entries.get(0).getY();

        // Change color based on +/- change
        int lineColor = isDowntrend ? Color.RED : Color.GREEN;
        int shadeColor = isDowntrend ? R.drawable.chart_fill_red : R.drawable.chart_fill_green;

        LineDataSet dataSet = new LineDataSet(entries, "Price");
        dataSet.setColor(lineColor);
        dataSet.setDrawCircles(false);
        dataSet.setLineWidth(1.5f);
        dataSet.setDrawValues(false);
        dataSet.setDrawFilled(true);
        dataSet.setFillDrawable(ContextCompat.getDrawable(chart.getContext(), shadeColor));

        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);

        // Configure axis similar to CryptosAdapter
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawLabels(false);
        xAxis.setDrawGridLines(false);

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawLabels(false);
        leftAxis.setEnabled(false);

        chart.getAxisRight().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(false);

        chart.invalidate();
    }

    // Updated method for adding new data to the chart
    private void updateUI(StreamMarketData data) {
        // Update price
        binding.marketChartLayout.currentPrice.setText(
                String.format(Locale.getDefault(), "US$%.2f", data.getPrice())
        );

        // Update percentage change
        double change = data.getChange();
        String changeText = String.format(Locale.getDefault(), "%.2f%%", change);
        binding.marketChartLayout.percentagePriceChange.setText(changeText);

        // Update color
        int colorRes = change >= 0 ?
                R.color.green_chart_color : R.color.crimson_red;
        binding.marketChartLayout.percentagePriceChange.setTextColor(
                ContextCompat.getColor(this, colorRes)
        );

        // Visual feedback for rapid changes
        if (Math.abs(change) > 0.5) {
            animatePriceChange(change >= 0);
        }
    }

    private void animatePriceChange(boolean isPositive) {
        ValueAnimator colorAnim = ValueAnimator.ofArgb(
                ContextCompat.getColor(this, R.color.gray_inactive),
                ContextCompat.getColor(this, isPositive ?
                        R.color.green_chart_color : R.color.crimson_red)
        );

        colorAnim.addUpdateListener(animator -> {
            binding.getRoot().setBackgroundColor((int) animator.getAnimatedValue());
        });

        colorAnim.setDuration(300);
        colorAnim.start();
    }

    // 5. Add method to check if we're really getting WebSocket data
//    @Override
//    protected void onResume() {
//        super.onResume();
//        if (symbol != null) {
//            // Get current selected interval from the selected tab
//            String interval = getCurrentIntervalFromSelectedTab();
//
//            // Reconnect only if we're not already connected
//            if (!viewModel.isConnected()) {
//                Timber.d("Reconnecting to WebSocket with symbol: %s, interval: %s", symbol, interval);
//                viewModel.connect(symbol, interval, true);
//            } else {
//                Timber.d("WebSocket already connected");
//            }
//        }
//    }

    @Override
    protected void onResume() {
        super.onResume();
        if (symbol != null) {
            // Force fresh data load to avoid gaps
            viewModel.disconnect();
            viewModel.connect(symbol, getCurrentIntervalFromSelectedTab(), true);

            // Clear existing data and reload
            allCandles.clear();
            closedCandleTimes.clear();
            loadHistoricalData(getCurrentIntervalFromSelectedTab(), new Date(), null);
            initializeTradingViewChart();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Don't disconnect, just note we're in background
        viewModel.setInBackground(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Only disconnect when actually destroying the activity
        viewModel.disconnect();

        // Clean up executor
        if (!executor.isShutdown()) {
            executor.shutdown();
        }
    }

    private String getCurrentIntervalFromSelectedTab() {
        int selectedPosition = binding.marketChartLayout.timeIntervalTabLayout.getSelectedTabPosition();
        if (selectedPosition >= 0 && selectedPosition < intervals.length) {
            return intervals[selectedPosition];
        }
        return "1m"; // Default
    }

    /**
     * TradingView chart implementation
     */
    private void initializeTradingViewChart() {
        // Ensure chartsView is not null
        chartsView = binding.marketChartLayout.candlesStickChart;

        // Apply chart options
        chartsView.getApi().applyOptions(chartOptions -> {
            // Layout options
            LayoutOptions layout = new LayoutOptions();
            layout.setBackground(new SolidColor(ContextCompat.getColor(getApplicationContext(), R.color.darkTheme)));
            layout.setTextColor(new IntColor(Color.WHITE));
            chartOptions.setLayout(layout);

            // Time scale options
            TimeScaleOptions timeScaleOptions = new TimeScaleOptions();
            timeScaleOptions.setBorderVisible(false);
            // Set right margin to push current position to the left
            timeScaleOptions.setRightBarStaysOnScroll(true);
//            timeScaleOptions.setRightOffset(100F); // Space on the right side (in bars)
            timeScaleOptions.setFixLeftEdge(true); // Prevent scrolling beyond the left edge
            // Set default visible range (controls how many candles are visible)
            timeScaleOptions.setBarSpacing(12F); // Increase space between bars for larger candles

            timeScaleOptions.setSecondsVisible(false);
            timeScaleOptions.setTimeVisible(true);

            chartOptions.setTimeScale(timeScaleOptions);

            // Price scale options
            PriceScaleOptions priceScaleOptions = new PriceScaleOptions();
            priceScaleOptions.setBorderVisible(false);
            // Auto scale prices based on visible range
            priceScaleOptions.setAutoScale(true);
            chartOptions.setRightPriceScale(priceScaleOptions);

            // Grid options
            GridLineOptions vertGrid = new GridLineOptions();
            vertGrid.setColor(new IntColor(Color.parseColor("#1C1C1C")));
            GridLineOptions horzGrid = new GridLineOptions();
            horzGrid.setColor(new IntColor(Color.parseColor("#1C1C1C")));
            GridOptions grid = new GridOptions();
            grid.setVertLines(vertGrid);
            grid.setHorzLines(horzGrid);
            chartOptions.setGrid(grid);

            // Configure touch interactions
            KineticScrollOptions kineticScroll = new KineticScrollOptions();
            kineticScroll.setTouch(true);
            kineticScroll.setMouse(true);

            HandleScrollOptions handleScroll = new HandleScrollOptions();
            handleScroll.setMouseWheel(true);
            handleScroll.setPressedMouseMove(true);
            handleScroll.setHorzTouchDrag(true);
            handleScroll.setVertTouchDrag(true);

            HandleScaleOptions handleScale = new HandleScaleOptions();
            handleScale.setPinch(true);
            handleScale.setMouseWheel(false);
            handleScale.setAxisPressedMouseMove(new AxisPressedMouseMoveOptions(true, true));

            chartOptions.setKineticScroll(kineticScroll);
            chartOptions.setHandleScroll(handleScroll);
            chartOptions.setHandleScale(handleScale);

            return Unit.INSTANCE;
        });

        // Candlestick series options
        CandlestickSeriesOptions options = new CandlestickSeriesOptions();
        options.setUpColor(new IntColor(Color.parseColor("#26a69a")));
        options.setDownColor(new IntColor(Color.parseColor("#ef5350")));
        options.setBorderUpColor(new IntColor(Color.parseColor("#26a69a")));
        options.setBorderDownColor(new IntColor(Color.parseColor("#ef5350")));
        options.setWickUpColor(new IntColor(Color.parseColor("#26a69a")));
        options.setWickDownColor(new IntColor(Color.parseColor("#ef5350")));
        options.setBorderVisible(true);
        options.setWickVisible(true);

        // Add candlestick series and store the reference
        Timber.d("Adding candlestick series to chart");
        chartsView.getApi().addCandlestickSeries(options, series -> {
            // Store the series reference
            candlestickSeries = series;
            Timber.d("Candlestick series created successfully");

            // Load initial data
            loadHistoricalData(getCurrentIntervalFromSelectedTab(), new Date(), null);

            // Apply custom visible range after data is loaded
            // Scroll to latest data
            // Apply custom visible range after data is loaded
            // Make sure we have data before setting the visible range
            if (!allCandles.isEmpty()) {
                try {
                    // Get the timestamp of the latest candle
                    Time.Utc latestTime = (Time.Utc) allCandles.get(allCandles.size() - 1).getTime();
                    // Get the timestamp of the candle ~50 bars back (or the oldest if we have fewer)
                    int targetIndex = Math.max(0, allCandles.size() - 50);
                    Time.Utc oldTime = (Time.Utc) allCandles.get(targetIndex).getTime();

                    // Apply the visible range
                    chartsView.getApi().getTimeScale().setVisibleRange(
                            new TimeRange(oldTime, latestTime)
                    );

                    Timber.d("Set visible range from %s to %s", oldTime.getTimestamp(), latestTime.getTimestamp());
                } catch (Exception e) {
                    Timber.e(e, "Error setting visible range");
                }
            } else {
                Timber.d("No data to set visible range");
            }

            return Unit.INSTANCE;
        });

        // Add data loading on scroll
        chartsView.getApi().getTimeScale().subscribeVisibleTimeRangeChange((timeRange) -> {
            if (timeRange != null) {
                try {
                    // When user scrolls near the left edge, load more historical data
                    Time.Utc leftEdge = (Time.Utc) timeRange.getFrom();
                    Time.Utc oldestLoaded = (Time.Utc) allCandles.get(0).getTime();

                    if (leftEdge.getTimestamp() - oldestLoaded.getTimestamp() < 10) {
                        // If we're close to the edge, load more data
                        Date endDate = new Date(oldestLoaded.getTimestamp() * 1000);
                        loadHistoricalData(getCurrentIntervalFromSelectedTab(), null, endDate);
                    }
                } catch (Exception e) {
                    Timber.e(e, "Error in time range change handler");
                }
            }
            return Unit.INSTANCE;
        });
    }

    private String updateTimeFormatForInterval(String interval) {
        String format;
        switch (interval) {
            case "1m":
            case "5m":
            case "15m":
            case "30m":
                format = "HH:mm";
                break;
            case "1h":
            case "4h":
                format = "MMM dd, HH:mm";
                break;
            case "1d":
                format = "MMM dd";
                break;
            default:
                format = "yyyy-MM-dd";
                break;
        }

        return format;
    }

    // Helper method to get a date X bars in the past based on the current interval
    private Date getDateMinusBars(int bars) {
        Calendar cal = Calendar.getInstance();
        String interval = getCurrentIntervalFromSelectedTab();

        // Adjust based on your interval types
        switch (interval) {
            case "1m":
                cal.add(Calendar.MINUTE, -bars);
                break;
            case "5m":
                cal.add(Calendar.MINUTE, -bars * 5);
                break;
            case "15m":
                cal.add(Calendar.MINUTE, -bars * 15);
                break;
            case "1h":
                cal.add(Calendar.HOUR, -bars);
                break;
            case "4h":
                cal.add(Calendar.HOUR, -bars * 4);
                break;
            case "1d":
                cal.add(Calendar.DAY_OF_MONTH, -bars);
                break;
            default:
                cal.add(Calendar.HOUR, -bars);
        }

        return cal.getTime();
    }

    // This method can remain mostly the same, but add a sorting step at the end
    private List<CandlestickData> convertToCandles(List<MarketDataEntity> marketData) {
        List<CandlestickData> candles = new ArrayList<>();

        for (MarketDataEntity entity : marketData) {
            try {
                // Ensure timestamp is valid
                if (entity.getTimestamp() == null) {
                    Timber.w("Skipping entry with null timestamp");
                    continue;
                }

                // Create the candle data
                CandlestickData candle = new CandlestickData(
                        new Time.Utc(entity.getTimestamp().getTime() / 1000),
                        (float) entity.getOpen(),
                        (float) entity.getHigh(),
                        (float) entity.getLow(),
                        (float) entity.getClose(),
                        null, null, null
                );

                candles.add(candle);
            } catch (Exception e) {
                Timber.e(e, "Error converting market data to candle");
            }
        }

        // Sort candles by timestamp to ensure they're in ascending order
        candles.sort((a, b) -> {
            long timeA = ((Time.Utc) a.getTime()).getTimestamp();
            long timeB = ((Time.Utc) b.getTime()).getTimestamp();
            return Long.compare(timeA, timeB);
        });

        return candles;
    }

    private Date calculateNewEndTime(String interval, Date currentEnd) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(currentEnd);
        // Subtract interval minutes multiplied by chunk size
        calendar.add(Calendar.MINUTE, -getIntervalMinutes(interval) * historicalState.CHUNK_SIZE);
        return calendar.getTime();
    }

    private int getIntervalMinutes(String interval) {
        switch (interval) {
            case "1m":
                return 1;
            case "5m":
                return 5;
            case "15m":
                return 15;
            case "30m":
                return 30;
            case "1h":
                return 60;
            case "4h":
                return 240;
            case "1d":
                return 1440;
            default:
                return 1;
        }
    }

    // 3. Add debug logging to the shouldUpdateLastCandle method
    private boolean shouldUpdateLastCandle(CandlestickData newCandle) {
        if (allCandles.isEmpty()) {
            Timber.d("No existing candles to update");
            return false;
        }

        try {
            // Get timestamp from Time object correctly
            long newCandleTime = ((Time.Utc) newCandle.getTime()).getTimestamp() * 1000;
            long lastCandleTime = ((Time.Utc) allCandles.get(allCandles.size() - 1).getTime()).getTimestamp() * 1000;

            String interval = getCurrentIntervalFromSelectedTab();
            long intervalMillis = getIntervalMillis(interval);

            Timber.d("Candle comparison - New: %d, Last: %d, Interval: %s (%d ms)",
                    newCandleTime, lastCandleTime, interval, intervalMillis);

            boolean isSameInterval = (newCandleTime / intervalMillis) == (lastCandleTime / intervalMillis);
            Timber.d("Is same interval: %s", isSameInterval);

            return isSameInterval;
        } catch (Exception e) {
            Timber.e(e, "Error checking if should update last candle");
            return false;
        }
    }

    private void updateTimeRangeState(List<MarketDataEntity> newData) {
        if (!newData.isEmpty()) {
            historicalState.oldestLoadedTimestamp = newData.get(0).getTimestamp();
            historicalState.currentStart = newData.get(newData.size() - 1).getTimestamp();
            Timber.d("Updated time range state: oldest=%s, current=%s",
                    historicalState.oldestLoadedTimestamp,
                    historicalState.currentStart);
        }
    }

    private long getIntervalMillis(String interval) {
        switch (interval) {
            case "1m":
                return 60 * 1000;
            case "5m":
                return 5 * 60 * 1000;
            case "15m":
                return 15 * 60 * 1000;
            case "30m":
                return 30 * 60 * 1000;
            case "1h":
                return 60 * 60 * 1000;
            case "4h":
                return 4 * 60 * 60 * 1000;
            case "1d":
                return 24 * 60 * 60 * 1000;
            default:
                return 60 * 1000;
        }
    }

    // Add this method to your SymbolMarketDataActivity
    // Ensure both use the same time basis (seconds vs milliseconds)
    private CandlestickData createCandlestickFromOhlcv(StreamMarketData.Ohlcv ohlcv) {
        // Convert to seconds if needed (API might use ms while WebSocket uses seconds)
        long timestampSeconds = ohlcv.getOpenTime() / 1000; // Ensure consistent format
        return new CandlestickData(
                new Time.Utc(timestampSeconds),
                (float) ohlcv.getOpen(),
                (float) ohlcv.getHigh(),
                (float) ohlcv.getLow(),
                (float) ohlcv.getClose(),
                null, null, null
        );
    }

    // Replace the loadHistoricalData method with this fixed version
    private void loadHistoricalData(String interval, Date startTime, Date endTime) {
        historicalState.isLoading = true;
        Timber.d("Loading historical data for interval: %s", interval);

        executor.execute(() -> {
            try {
                // Calculate time window based on interval
                Date[] timeWindow = calculateTimeWindow(interval, startTime, endTime);
                Date requestStart = timeWindow[0];
                Date requestEnd = timeWindow[1];

                Timber.d("Requesting data from %s to %s",
                        apiDateFormat.format(requestStart),
                        apiDateFormat.format(requestEnd));

                SymbolMarketEndpoints api = MainClient.getInstance().create(SymbolMarketEndpoints.class);
                Response<MarketDataResponse> response = api.getMarketData(
                        symbol,
                        interval,
                        apiDateFormat.format(requestStart),
                        apiDateFormat.format(requestEnd),
                        historicalState.currentPage,
                        historicalState.CHUNK_SIZE
                ).execute();

                if (response.isSuccessful() && response.body() != null) {
                    List<MarketDataEntity> newData = response.body().getData();

                    Timber.d("Received %d market data entries", newData.size());

                    if (newData.isEmpty()) {
                        mainHandler.post(() -> {
                            historicalState.isLoading = false;
                            Timber.d("No data received from API");
                        });
                        return;
                    }

                    // Convert data to chronological order (oldest to newest)
                    Collections.reverse(newData);

                    List<CandlestickData> candles = convertToCandles(newData);

                    mainHandler.post(() -> {
                        if (candlestickSeries != null) {
                            Timber.d("Adding %d candles to chart", candles.size());

                            // Ensure the combined data will be in ascending order
                            if (allCandles.isEmpty()) {
                                // First batch of data - just use as is
                                allCandles.addAll(candles);
                                binding.marketChartLayout.progressBar.setVisibility(View.GONE);
                            } else {
                                // Check if we're adding older or newer data and add appropriately
                                Time.Utc firstNewTime = (Time.Utc) candles.get(0).getTime();
                                Time.Utc firstExistingTime = (Time.Utc) allCandles.get(0).getTime();

                                if (firstNewTime.getTimestamp() < firstExistingTime.getTimestamp()) {
                                    // Adding older data - add at beginning
                                    List<CandlestickData> combined = new ArrayList<>(candles);
                                    combined.addAll(allCandles);
                                    allCandles.clear();
                                    allCandles.addAll(combined);
                                    binding.marketChartLayout.progressBar.setVisibility(View.GONE);
                                } else {
                                    // Adding newer data - add at end
                                    allCandles.addAll(candles);
                                    binding.marketChartLayout.progressBar.setVisibility(View.GONE);
                                }
                            }

                            // Sort data and remove duplicates with the same timestamp
                            deduplicateAndSortCandles();

                            try {
                                // Set all data to the series
                                candlestickSeries.setData(allCandles);

                                // Update time range state
                                updateTimeRangeState(newData);

                                // Ensure focus on recent data
                                chartsView.getApi().getTimeScale().fitContent();
                            } catch (Exception e) {
                                Timber.e(e, "Error setting data to chart: %s", e.getMessage());
                                Toast.makeText(ArchivedSymbolMarketDataActivity.this,
                                        "Error displaying chart: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Timber.e("candlestickSeries is null, cannot update chart");
                        }
                        historicalState.isLoading = false;
                    });
                } else {
                    mainHandler.post(() -> {
                        String errorCode = response.isSuccessful() ? "Empty response" :
                                "Error code: " + response.code();
                        Timber.e("API call failed: %s", errorCode);
                        historicalState.isLoading = false;
                    });
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    Timber.e(e, "Error loading historical data");
                    Toast.makeText(ArchivedSymbolMarketDataActivity.this, "Error loading data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    historicalState.isLoading = false;
                });
            }
        });
    }

    // Add this new method to deduplicate candles with the same timestamp
    private void deduplicateAndSortCandles() {
        // First sort by timestamp
        allCandles.sort((a, b) -> {
            long timeA = ((Time.Utc) a.getTime()).getTimestamp();
            long timeB = ((Time.Utc) b.getTime()).getTimestamp();
            return Long.compare(timeA, timeB);
        });

        // Remove duplicates (keeping the last one)
        Map<Long, Integer> timestampPositions = new HashMap<>();
        List<Integer> duplicateIndices = new ArrayList<>();

        // Find duplicates, keeping track of the last occurrence
        for (int i = 0; i < allCandles.size(); i++) {
            long timestamp = ((Time.Utc) allCandles.get(i).getTime()).getTimestamp();

            if (timestampPositions.containsKey(timestamp)) {
                // Mark previous position as duplicate
                duplicateIndices.add(timestampPositions.get(timestamp));
            }

            // Update with current position (latest one with this timestamp)
            timestampPositions.put(timestamp, i);
        }

        // Sort indices in descending order to safely remove from list
        duplicateIndices.sort(Collections.reverseOrder());

        // Remove duplicates
        for (int index : duplicateIndices) {
            if (index < allCandles.size()) {  // Safety check
                Timber.d("Removing duplicate candle at index %d with timestamp %d",
                        index, ((Time.Utc) allCandles.get(index).getTime()).getTimestamp());
                allCandles.remove(index);
            }
        }

        Timber.d("After deduplication: %d candles remain", allCandles.size());
    }

    // Update this method to use the new deduplication logic
    private void updateChartWithLiveData(StreamMarketData data) {
        if (data == null || data.getOhlcv() == null || candlestickSeries == null) {
            Timber.e("Cannot update chart - missing required data");
            return;
        }

        try {
            StreamMarketData.Ohlcv ohlcv = data.getOhlcv();
            long openTimeMs = ohlcv.getOpenTime();
            boolean isClosed = ohlcv.isClosed();

            // Skip if this candle is already closed
            if (closedCandleTimes.contains(openTimeMs)) {
                Timber.d("Candle with openTime %d is closed, skipping update", openTimeMs);
                return;
            }

            // Mark as closed if applicable
            if (isClosed) {
                closedCandleTimes.add(openTimeMs);
            }

            // Create the new candle from OHLCV data
            CandlestickData newCandle = createCandlestickFromOhlcv(data.getOhlcv());

            // Debug the timestamp issue
            long newCandleTimestamp = ((Time.Utc) newCandle.getTime()).getTimestamp() * 1000;
            Timber.d("New candle timestamp: %s", new Date(newCandleTimestamp));

            // Check if we have any candles yet
            if (allCandles.isEmpty()) {
                allCandles.add(newCandle);
                candlestickSeries.setData(allCandles);
                Timber.d("Added first candle");
                return;
            }

            // Get the last candle for comparison
            CandlestickData lastCandle = allCandles.get(allCandles.size() - 1);
            long lastCandleTimestamp = ((Time.Utc) lastCandle.getTime()).getTimestamp() * 1000;
            Timber.d("Last candle timestamp: %s", new Date(lastCandleTimestamp));

            String interval = getCurrentIntervalFromSelectedTab();
            long intervalMillis = getIntervalMillis(interval);

            // Check if this is an update to the current candle or a new candle
            if (newCandleTimestamp / intervalMillis == lastCandleTimestamp / intervalMillis) {
                // Update existing candle
                Timber.d("Updating existing candle");
                allCandles.set(allCandles.size() - 1, newCandle);
                deduplicateAndSortCandles();
                try {
                    candlestickSeries.setData(allCandles);
                } catch (Exception e) {
                    Timber.e(e, "Error updating candle: %s", e.getMessage());
                }
            } else if (newCandleTimestamp > lastCandleTimestamp) {
                // Add new candle if it's newer
                Timber.d("Adding new candle");
                allCandles.add(newCandle);

                // Clean up data before setting
                deduplicateAndSortCandles();

                try {
                    candlestickSeries.setData(allCandles);

                    // Auto-scroll to show the latest data
                    if (allCandles.size() > 1) {
                        // Focus on the last 10-20 candles for a better view
                        int startIndex = Math.max(0, allCandles.size() - 20);
                        Time fromTime = allCandles.get(startIndex).getTime();
                        Time toTime = allCandles.get(allCandles.size() - 1).getTime();

                        chartsView.getApi().getTimeScale().setVisibleRange(
                                new TimeRange(fromTime, toTime)
                        );
                    }
                } catch (Exception e) {
                    Timber.e(e, "Error setting chart data: %s", e.getMessage());
                }
            } else {
                // This is older data - we might need to insert it in the right place
                Timber.d("Received older candle data - adding and resorting");
                allCandles.add(newCandle);
                deduplicateAndSortCandles();

                try {
                    candlestickSeries.setData(allCandles);
                } catch (Exception e) {
                    Timber.e(e, "Error setting older data: %s", e.getMessage());
                }
            }
        } catch (Exception e) {
            Timber.e(e, "Error updating chart with live data");
        }
    }

    // 2. Create a helper method to merge new candles with existing ones efficiently
    private void mergeCandles(List<CandlestickData> newCandles) {
        if (newCandles.isEmpty()) return;

        // Build lookup map of existing timestamps
        Map<Long, Integer> existingTimestamps = new HashMap<>();
        for (int i = 0; i < allCandles.size(); i++) {
            long timestamp = ((Time.Utc) allCandles.get(i).getTime()).getTimestamp();
            existingTimestamps.put(timestamp, i);
        }

        // Process each new candle
        for (CandlestickData newCandle : newCandles) {
            long timestamp = ((Time.Utc) newCandle.getTime()).getTimestamp();

            if (existingTimestamps.containsKey(timestamp)) {
                // Update existing candle (only if closed or better data)
                int index = existingTimestamps.get(timestamp);
                CandlestickData existingCandle = allCandles.get(index);

                // Only update if this is the same candle but with newer data
                allCandles.set(index, newCandle);
            } else {
                // Add new candle
                allCandles.add(newCandle);
            }
        }

        // Sort all candles by timestamp
        allCandles.sort((a, b) -> {
            long timeA = ((Time.Utc) a.getTime()).getTimestamp();
            long timeB = ((Time.Utc) b.getTime()).getTimestamp();
            return Long.compare(timeA, timeB);
        });
    }

    // 3. Create a method to properly handle interval changes
    private void changeInterval(String newInterval) {
        Timber.d("Changing chart interval to: %s", newInterval);

        historicalState.reset();

        // Now connect with new interval
        viewModel.connect(symbol, newInterval, true);

        // Update UI to indicate loading
        binding.marketChartLayout.progressBar.setVisibility(View.VISIBLE);

        loadHistoricalData(getCurrentIntervalFromSelectedTab(), new Date(), null);
        // Reinitialize the chart (this should trigger loading new data)
        initializeTradingViewChart();
    }

    // 5. Add a method to handle proper time window calculations for various intervals
    private Date[] calculateTimeWindow(String interval, Date startTime, Date endTime) {
        Calendar calendar = Calendar.getInstance();

        // Handle loading more historical data
        if (endTime != null) {
            // We're loading older data before endTime
            calendar.setTime(endTime);

            // How far back to go depends on the interval
            switch (interval) {
                case "1m":
                    calendar.add(Calendar.HOUR, -4);
                    break;
                case "5m":
                    calendar.add(Calendar.HOUR, -12);
                    break;
                case "15m":
                    calendar.add(Calendar.DAY_OF_MONTH, -1);
                    break;
                case "30m":
                    calendar.add(Calendar.DAY_OF_MONTH, -2);
                    break;
                case "1h":
                    calendar.add(Calendar.DAY_OF_MONTH, -5);
                    break;
                case "2h":
                    calendar.add(Calendar.DAY_OF_MONTH, -10);
                    break;
                case "4h":
                    calendar.add(Calendar.DAY_OF_MONTH, -20);
                    break;
                case "6h":
                    calendar.add(Calendar.DAY_OF_MONTH, -30);
                    break;
                case "1d":
                    calendar.add(Calendar.MONTH, -3);
                    break;
                case "3d":
                    calendar.add(Calendar.MONTH, -6);
                    break;
                case "1w":
                    calendar.add(Calendar.MONTH, -12);
                    break;
                case "1M":
                    calendar.add(Calendar.YEAR, -2);
                    break;
                default:
                    calendar.add(Calendar.DAY_OF_MONTH, -7);
                    break;
            }

            Date requestStart = calendar.getTime();
            return new Date[]{requestStart, endTime};
        }
        // Initial load - start from current time and go back
        else if (startTime != null) {
            calendar.setTime(startTime);

            // How far back to show initially depends on interval
            switch (interval) {
                case "1m":
                    calendar.add(Calendar.HOUR, -8);
                    break;
                case "5m":
                    calendar.add(Calendar.DAY_OF_MONTH, -1);
                    break;
                case "15m":
                    calendar.add(Calendar.DAY_OF_MONTH, -3);
                    break;
                case "30m":
                    calendar.add(Calendar.DAY_OF_MONTH, -5);
                    break;
                case "1h":
                    calendar.add(Calendar.DAY_OF_MONTH, -10);
                    break;
                case "2h":
                    calendar.add(Calendar.DAY_OF_MONTH, -15);
                    break;
                case "4h":
                    calendar.add(Calendar.MONTH, -1);
                    break;
                case "6h":
                    calendar.add(Calendar.MONTH, -2);
                    break;
                case "1d":
                    calendar.add(Calendar.MONTH, -6);
                    break;
                case "3d":
                    calendar.add(Calendar.MONTH, -9);
                    break;
                case "1w":
                    calendar.add(Calendar.YEAR, -1);
                    break;
                case "1M":
                    calendar.add(Calendar.YEAR, -3);
                    break;
                default:
                    calendar.add(Calendar.DAY_OF_MONTH, -30);
                    break;
            }

            Date requestStart = calendar.getTime();
            return new Date[]{requestStart, startTime};
        }
        // Fallback - shouldn't happen
        else {
            calendar.setTime(new Date());
            Date now = calendar.getTime();
            calendar.add(Calendar.DAY_OF_MONTH, -7);
            return new Date[]{calendar.getTime(), now};
        }
    }

}