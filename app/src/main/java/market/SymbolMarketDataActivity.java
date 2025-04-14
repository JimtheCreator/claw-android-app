package market;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
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
import com.tradingview.lightweightcharts.api.options.models.CandlestickSeriesOptions;
import com.tradingview.lightweightcharts.api.options.models.LayoutOptions;
import com.tradingview.lightweightcharts.api.options.models.PriceScaleOptions;
import com.tradingview.lightweightcharts.api.options.models.TimeScaleOptions;
import com.tradingview.lightweightcharts.api.series.models.CandlestickData;
import com.tradingview.lightweightcharts.api.series.models.Time;
import com.tradingview.lightweightcharts.view.ChartsView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import backend.ApiEndpoints;
import backend.MainClient;
import backend.WebSocketService;
import data.remote.WebSocketServiceImpl;
import factory.StreamViewModelFactory;
import kotlin.Unit;
import models.MarketDataEntity;
import models.StreamMarketData;
import okhttp3.OkHttpClient;
import repositories.StreamRepository;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;
import viewmodels.StreamViewModel;

public class SymbolMarketDataActivity extends AppCompatActivity {
    ActivitySymbolMarketDataBinding binding;
    private StreamViewModel viewModel;
    double[] sparklineArray;
    String asset;
    String symbol;

    // TradingView chart components
    private ChartsView chartsView;
    private SeriesApi candleSeries;
    List<Long> timestampList = new ArrayList<>();

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

        // Initialize TradingView chart
        initializeTradingViewChart("1M");
        fetchHistoricalData("1M"); // Initial load with 1m interval
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
    }

    private void setupObservers() {
        viewModel.getMarketDataStream().observe(this, data -> {
            if (data != null) {
                updateUI(data);

                // Add null check for OHLCV data
                if (data.getOhlcv() != null && candleSeries != null) {
                    CandlestickData newCandle = new CandlestickData(
                            new Time.Utc(System.currentTimeMillis() / 1000),
                            (float) data.getOhlcv().getOpen(),
                            (float) data.getOhlcv().getHigh(),
                            (float) data.getOhlcv().getLow(),
                            (float) data.getOhlcv().getClose(),
                            null, null, null
                    );

                    candleSeries.update(newCandle);

                    chartsView.getApi().getTimeScale().scrollToPosition(
                            (System.currentTimeMillis() / 1000f),
                            false
                    );
                }
                else {
                    Timber.w("Received null OHLCV data");
                }
            }
        });

        viewModel.getErrorStream().observe(this, error -> {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
        });
    }

    // 4. Improved Historical Data Handling
    private void fetchHistoricalData(String interval) {
        binding.marketChartLayout.progressBar.setVisibility(View.VISIBLE);

        MainClient.getInstance().create(ApiEndpoints.class)
                .getMarketData(symbol, interval)
                .enqueue(new Callback<List<MarketDataEntity>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<MarketDataEntity>> call,
                                           @NonNull Response<List<MarketDataEntity>> response) {
                        binding.marketChartLayout.progressBar.setVisibility(View.GONE);

                        if (response.isSuccessful() && response.body() != null) {
                            List<CandlestickData> candles = new ArrayList<>();
                            for (MarketDataEntity entity : response.body()) {
                                candles.add(convertToCandle(entity));
                            }

                            if (candleSeries != null) {
                                // Clear previous data and set new
                                candleSeries.setData(candles);
                                chartsView.getApi().getTimeScale().fitContent();
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<MarketDataEntity>> call, @NonNull Throwable t) {
                        handleDataLoadError(t);
                    }
                });
    }

    // 3. Enhanced Data Conversion
    private CandlestickData convertToCandle(MarketDataEntity entity) {
        // Ensure timestamp is in seconds
        long timestampSeconds = entity.getTimestamp().getTime() / 1000;

        // In convertToCandle() method
        return new CandlestickData(
                new Time.Utc(timestampSeconds),
                (float) entity.getOpen(),
                (float) entity.getHigh(),
                (float) entity.getLow(),
                (float) entity.getClose(),
                null, // Optional: bodyColor
                null, // Optional: borderColor
                null  // Optional: wickColor
        );
    }

    private void initializeTradingViewChart(String interval) {
        chartsView = binding.marketChartLayout.candlesStickChart;

        chartsView.getApi().applyOptions(chartOptions -> {
            // Layout options
            LayoutOptions layout = new LayoutOptions();
            layout.setBackground(new SolidColor(Color.BLACK));
            layout.setTextColor(new IntColor(Color.WHITE));
            chartOptions.setLayout(layout);

            // Time scale options
            TimeScaleOptions timeScaleOptions = new TimeScaleOptions();
            timeScaleOptions.setBorderVisible(false);
            chartOptions.setTimeScale(timeScaleOptions);

            // Price scale options
            PriceScaleOptions priceScaleOptions = new PriceScaleOptions();
            priceScaleOptions.setBorderVisible(false);
            chartOptions.setRightPriceScale(priceScaleOptions);

            return Unit.INSTANCE; // Required for Kotlin interoperability
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

        chartsView.getApi().addCandlestickSeries(options, series -> {
            candleSeries = series;
            fetchHistoricalData(interval); // Initial interval
            return Unit.INSTANCE; // For Kotlin interop
        });

    }

    private void time_interval_tabs() {
        String[] intervals = {
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

                // Refresh chart here
                String interval = ((TextView) tab.getCustomView().findViewById(R.id.tabTitle)).getText().toString();

                // Clear existing data before loading new
                if (candleSeries != null) {
                    candleSeries.setData(new ArrayList<>());
                }

                // Reinitialize with new interval
                viewModel.connect(symbol, interval, true);
                fetchHistoricalData(interval);
                initializeTradingViewChart(interval);
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
        int lineColor = isDowntrend ? android.graphics.Color.RED : android.graphics.Color.GREEN;
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
        binding.marketChartLayout.currentPrice.setText(
                String.format(Locale.getDefault(), "US$%.2f", data.getPrice())
        );

        String changeText = String.format(Locale.getDefault(), "%.2f%%", data.getChange());
        binding.marketChartLayout.percentagePriceChange.setText(changeText);

        int colorRes = data.getChange() >= 0 ?
                R.color.green_chart_color : R.color.crimson_red;

        binding.marketChartLayout.percentagePriceChange.setTextColor(
                ContextCompat.getColor(this, colorRes)
        );
    }

    private void handleDataLoadError(Throwable t) {
        binding.marketChartLayout.progressBar.setVisibility(View.GONE);
        Toast.makeText(this, "Chart data load failed: " + t.getMessage(),
                Toast.LENGTH_SHORT).show();
        Timber.e(t, "Data load failure");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }


}