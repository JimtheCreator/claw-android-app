package market;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
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
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.tradingview.lightweightcharts.api.chart.models.color.IntColor;
import com.tradingview.lightweightcharts.api.chart.models.color.surface.SolidColor;
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi;
import com.tradingview.lightweightcharts.api.options.models.CandlestickSeriesOptions;
import com.tradingview.lightweightcharts.api.options.models.GridLineOptions;
import com.tradingview.lightweightcharts.api.options.models.GridOptions;
import com.tradingview.lightweightcharts.api.options.models.HistogramSeriesOptions;
import com.tradingview.lightweightcharts.api.options.models.LayoutOptions;
import com.tradingview.lightweightcharts.api.options.models.PriceScaleMargins;
import com.tradingview.lightweightcharts.api.options.models.PriceScaleOptions;
import com.tradingview.lightweightcharts.api.options.models.TimeScaleOptions;
import com.tradingview.lightweightcharts.api.series.enums.LineWidth;
import com.tradingview.lightweightcharts.api.series.models.CandlestickData;
import com.tradingview.lightweightcharts.api.series.models.HistogramData;
import com.tradingview.lightweightcharts.api.series.models.PriceScaleId;
import com.tradingview.lightweightcharts.api.series.models.Time;
import com.tradingview.lightweightcharts.view.ChartsView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import adapters.TimeframeSpinnerAdapter;
import backend.ApiService;
import backend.MainClient;
import backend.WebSocketService;
import bottomsheets.PriceAlertsBottomSheetFragment;
import data.remote.WebSocketServiceImpl;
import factory.HomeViewModelFactory;
import factory.StreamViewModelFactory;
import kotlin.Unit;
import model_interfaces.OnWatchlistActionListener;
import models.HistoricalState;
import models.MarketDataEntity;
import backend.results.MarketDataResponse;
import models.StreamMarketData;
import models.Symbol;
import okhttp3.OkHttpClient;
import repositories.StreamRepository;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;
import viewmodels.HomeViewModel;
import viewmodels.StreamViewModel;

public class SymbolMarketDataActivity extends AppCompatActivity {
    ActivitySymbolMarketDataBinding binding;
    private StreamViewModel viewModel;
    double[] sparklineArray;
    private HomeViewModel homeViewModel;
    OnWatchlistActionListener commonWatchlistListener;
    HomeViewModelFactory factory;
    FirebaseUser firebaseUser;
    String asset;
    private final SimpleDateFormat apiDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
    String symbol;
    private final HistoricalState historicalState = new HistoricalState();
    // TradingView chart components
    private ChartsView chartsView;
    private SeriesApi candleSeries;
    private SeriesApi volumeSeries;
    List<Long> timestampList = new ArrayList<>();

    // For handling request timeouts and retries
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean isRequestInProgress = new AtomicBoolean(false);
    private final int MAX_RETRIES = 3;
    private String currentInterval = "1m";

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
        apiDateFormat.setTimeZone(TimeZone.getTimeZone("UTC")); // Use UTC, not default timezone

        firebaseUser = FirebaseAuth.getInstance().getCurrentUser();

        initViewModel();
        globalInit();
        onClicksAndDrags();
        setupObservers();
        time_interval_tabs();
        setupTimeframeSpinner();
    }

    private String getCurrentUserId() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return (user != null) ? user.getUid() : null;
    }

    private void initViewModel() {
        factory = new HomeViewModelFactory(getApplication());
        homeViewModel = new ViewModelProvider(this, factory).get(HomeViewModel.class);

        WebSocketService wsService = new WebSocketServiceImpl(new OkHttpClient(), new OkHttpClient());
        StreamRepository repository = new StreamRepository(wsService, getApplicationContext());
        viewModel = new ViewModelProvider(this, new StreamViewModelFactory(repository))
                .get(StreamViewModel.class);

        binding.marketChartLayout.progressBar.setVisibility(View.VISIBLE);
        // Initial load with 1m interval using current time

        // Initialize TradingView chart
        initializeTradingViewChart();
        fetchHistoricalData("1m", new Date(), null);
    }

    private void globalInit() {
        addButtonSwitch();
        symbol = getIntent().getStringExtra("SYMBOL");
        asset = getIntent().getStringExtra("ASSET");
        sparklineArray = getIntent().getDoubleArrayExtra("SPARKLINE");
        double initialPrice = getIntent().getDoubleExtra("CURRENT_PRICE", 0.0);
        double initialChange = getIntent().getDoubleExtra("CHANGE_24H", 0.0);

        if (symbol != null) {
            creatingViewModelConnection(currentInterval);
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
            Toast.makeText(this, "Error: Invalid symbol", Toast.LENGTH_SHORT).show();
            finish(); // Close activity if symbol is null - critical error
        }
    }

    private void addButtonSwitch() {
        // Retrieve necessary data from intent extras
        String symbol = getIntent().getStringExtra("SYMBOL");
        String asset = getIntent().getStringExtra("ASSET");
        String base_currency = getIntent().getStringExtra("BASE_CURRENCY");
        double currentPrice = getIntent().getDoubleExtra("CURRENT_PRICE", 0.0);
        double change24h = getIntent().getDoubleExtra("CHANGE_24H", 0.0);
        double[] sparklineArray = getIntent().getDoubleArrayExtra("SPARKLINE");

        // Convert sparkline array to List<Double>
        List<Double> sparkline = new ArrayList<>();
        if (sparklineArray != null) {
            for (double value : sparklineArray) {
                sparkline.add(value);
            }
        }

        // Load watchlist if user is logged in
        String userId = getCurrentUserId();
        if (userId != null) {
            homeViewModel.loadWatchlist(userId);
        } else {
            binding.marketChartLayout.addToWatchlist.setVisibility(View.GONE);
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

        binding.marketChartLayout.aiButton.setOnClickListener(v -> {
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
        });

        binding.closePage.setOnClickListener(v -> finish());

        binding.marketChartLayout.addToWatchlist.setOnClickListener(v -> {
            String userId = getCurrentUserId();
            if (userId == null) {
                Toast.makeText(this, "Please log in to add to watchlist", Toast.LENGTH_SHORT).show();
                return;
            }

            // Retrieve necessary data from intent extras
            String symbol = getIntent().getStringExtra("SYMBOL");
            String asset = getIntent().getStringExtra("ASSET");
            String base_currency = getIntent().getStringExtra("BASE_CURRENCY");
            double currentPrice = getIntent().getDoubleExtra("CURRENT_PRICE", 0.0);
            double change24h = getIntent().getDoubleExtra("CHANGE_24H", 0.0);
            double[] sparklineArray = getIntent().getDoubleArrayExtra("SPARKLINE");

            // Convert sparkline array to List<Double>
            List<Double> sparkline = new ArrayList<>();
            if (sparklineArray != null) {
                for (double value : sparklineArray) {
                    sparkline.add(value);
                }
            }

            // Create Symbol object with available data (default values for missing fields)
            Symbol symbolObj = new Symbol(
                    symbol,
                    asset,
                    "", // pair (not available, set empty)
                    base_currency,
                    currentPrice,
                    change24h,
                    currentPrice, // price (same as currentPrice)
                    change24h, // change (same as 24h change)
                    0.0, // 24h volume (default 0)
                    sparkline,
                    false // isInWatchlist (default false)
            );

            // Add to watchlist
            homeViewModel.addToWatchlist(userId, symbolObj, "Binance");
        });

        binding.marketChartLayout.createPriceAlert.setOnClickListener(v -> {
            String symbol = getIntent().getStringExtra("SYMBOL");
            String stringCurrentPrice = binding.marketChartLayout.currentPrice.getText().toString();
            // Remove all non-numeric characters except dot and minus
            String numeric = stringCurrentPrice.replaceAll("[^\\d.-]", "");
            double price = Double.parseDouble(numeric);
            PriceAlertsBottomSheetFragment priceAlertFragment = PriceAlertsBottomSheetFragment.newInstance(firebaseUser.getUid(), symbol, price);
            priceAlertFragment.show(getSupportFragmentManager(), priceAlertFragment.getTag());
        });
    }

    private void setupObservers() {
        // Observe watchlist to update button visibility
        homeViewModel.getWatchlist().observe(this, symbols -> {
            if (symbols != null) {
                boolean isInWatchlist = false;
                for (Symbol s : symbols) {
                    if (s.getSymbol().equals(symbol)) {
                        isInWatchlist = true;
                        break;
                    }
                }
                binding.marketChartLayout.addToWatchlist.setVisibility(
                        isInWatchlist ? View.GONE : View.VISIBLE
                );
            }
        });

        // Observe errors
        homeViewModel.getErrorMessage().observe(this, error -> {
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getMarketDataStream().observe(this, data -> {
            if (data != null) {
                updateUI(data);
            }
        });

        viewModel.getErrorStream().observe(this, error -> {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
        });
    }


    /**
     * Enhanced historical data fetching with proper handling of timeframes
     *
     * @param interval  The chart interval (e.g., "1m", "5m", "1h")
     * @param startTime The end of the time window (typically current time for initial load)
     * @param endTime   The start of the time window (null for initial load, otherwise used for pagination)
     */
    private void fetchHistoricalData(String interval, Date startTime, Date endTime) {
        // Safeguard against concurrent requests
        if (isRequestInProgress.getAndSet(true)) {
            Timber.d("Request already in progress, skipping");
            return;
        }

        // Reset state when interval changes
        if (!interval.equals(currentInterval)) {
            historicalState.reset();
            currentInterval = interval;
        }

        try {
            // Calculate time window based on interval
            Date[] timeWindow = calculateTimeWindow(interval, startTime, endTime);
            Date requestStart = timeWindow[0];
            Date requestEnd = timeWindow[1];

            // Update historical state
            if (historicalState.oldestLoadedTimestamp == null ||
                    (endTime != null && endTime.before(historicalState.oldestLoadedTimestamp))) {
                historicalState.oldestLoadedTimestamp = endTime != null ? endTime : requestStart;
            }

            // Format dates for API call
            String formattedStart = apiDateFormat.format(requestStart);
            String formattedEnd = apiDateFormat.format(requestEnd);

            Timber.d("Fetching market data: %s to %s (interval: %s, page: %d)",
                    formattedStart, formattedEnd, interval, historicalState.currentPage);

            // Make API call with retry mechanism
            fetchDataWithRetry(interval, formattedStart, formattedEnd, 0);

        } catch (Exception e) {
            Timber.e(e, "Failed to prepare historical data request");
            handleDataLoadError(e);
            isRequestInProgress.set(false);
        }
    }

    /**
     * Schedules a retry of the API call with exponential backoff
     */
    private void scheduleRetry(String interval, String start, String end, int retryCount, long delayMs) {
        Timber.d("Scheduling retry %d in %d ms", retryCount + 1, delayMs);
        executorService.schedule(() -> {
            runOnUiThread(() -> fetchDataWithRetry(interval, start, end, retryCount + 1));
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Calculates the appropriate time window based on interval and pagination
     */
    private Date[] calculateTimeWindow(String interval, Date startTime, Date endTime) {
        Calendar calendar = Calendar.getInstance();

        // Handle loading more historical data
        if (endTime != null) {
            // We're loading older data before endTime
            calendar.setTime(endTime);

            // How far back to go depends on the interval
            switch (interval) {
                case "1m":
                    calendar.add(Calendar.HOUR, -24);
                    break;
                case "5m":
                    calendar.add(Calendar.DAY_OF_WEEK, -1);
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

    /**
     * Converts a MarketDataEntity to TradingView CandlestickData
     */
    private CandlestickData convertToCandle(MarketDataEntity entity) {
        if (entity == null) {
            Timber.w("Received null MarketDataEntity");
            return null;
        }

        try {
            // Get UTC timestamp from backend
            long localAdjustedSeconds = getLocalAdjustedSeconds(entity);

            return new CandlestickData(
                    new Time.Utc(localAdjustedSeconds), // Treat adjusted timestamp as UTC
                    (float) entity.getOpen(),
                    (float) entity.getHigh(),
                    (float) entity.getLow(),
                    (float) entity.getClose(),
                    null, null, null
            );
        } catch (Exception e) {
            Timber.e(e, "Error converting entity to candle");
            return null;
        }
    }

    private static long getLocalAdjustedSeconds(MarketDataEntity entity) {
        long utcTimestampMs = entity.getTimestamp().getTime();

        // Calculate device's timezone offset for this timestamp
        TimeZone localTimeZone = TimeZone.getDefault();
        int offsetMs = localTimeZone.getOffset(utcTimestampMs); // Offset in milliseconds

        // Convert UTC timestamp to local timezone equivalent (for display purposes)
        long localAdjustedTimestampMs = utcTimestampMs + offsetMs;
        return localAdjustedTimestampMs / 1000;
    }

    /**
     * Initializes the TradingView candlestick chart with appropriate options
     */
    private void initializeTradingViewChart() {
        chartsView = binding.marketChartLayout.candlesStickChart;

        chartsView.getApi().applyOptions(chartOptions -> {
            // Layout options
            LayoutOptions layout = new LayoutOptions();
            layout.setBackground(new SolidColor(ContextCompat.getColor(getApplicationContext(), R.color.darkTheme)));
            layout.setTextColor(new IntColor(Color.WHITE));
            chartOptions.setLayout(layout);

            // Time scale options
            TimeScaleOptions timeScaleOptions = new TimeScaleOptions();
            timeScaleOptions.setBorderVisible(false);
            // Enable handling of missing data points

            timeScaleOptions.setTimeVisible(true);
            timeScaleOptions.setFixLeftEdge(true);
            timeScaleOptions.setRightBarStaysOnScroll(true);
            chartOptions.setTimeScale(timeScaleOptions);

            // Price scale options
            PriceScaleOptions priceScaleOptions = new PriceScaleOptions();
            priceScaleOptions.setBorderVisible(false);
            chartOptions.setRightPriceScale(priceScaleOptions);

            // Add left price scale for volume histogram
            // Use LEFT price scale for volume, but make it invisible or minimal
            PriceScaleOptions leftPriceScale = new PriceScaleOptions();
            leftPriceScale.setScaleMargins(new PriceScaleMargins(0.85F, 0.02F));
            leftPriceScale.setVisible(false); // hide the price labels
            chartOptions.setLeftPriceScale(leftPriceScale);

            // Grid options
            GridLineOptions vertGrid = new GridLineOptions();
            vertGrid.setColor(new IntColor(Color.parseColor("#1C1C1C")));
            GridLineOptions horzGrid = new GridLineOptions();
            horzGrid.setColor(new IntColor(Color.parseColor("#1C1C1C")));
            GridOptions grid = new GridOptions();
            grid.setVertLines(vertGrid);
            grid.setHorzLines(horzGrid);
            chartOptions.setGrid(grid);

            return Unit.INSTANCE; // Required for Kotlin interoperability
        });

        // Candlestick series options with trader-friendly colors
        CandlestickSeriesOptions options = new CandlestickSeriesOptions();
        options.setUpColor(new IntColor(Color.parseColor("#26a69a")));     // Green for up candles
        options.setDownColor(new IntColor(Color.parseColor("#ef5350"))); // Red for down candles
        options.setBorderUpColor(new IntColor(Color.parseColor("#26a69a")));
        options.setBorderDownColor(new IntColor(Color.parseColor("#ef5350")));
        options.setWickUpColor(new IntColor(Color.parseColor("#26a69a")));
        options.setWickDownColor(new IntColor(Color.parseColor("#ef5350")));
        options.setBorderVisible(true);
        options.setWickVisible(true);

        // Create the series in the chart
        chartsView.getApi().addCandlestickSeries(options, series -> {
            candleSeries = series;

            // Register for chart events
            registerChartScrollEvents();

            return Unit.INSTANCE; // For Kotlin interop
        });

        addingHistogram();
    }

    private void addingHistogram() {
        HistogramSeriesOptions volumeOptions = new HistogramSeriesOptions();
        volumeOptions.setColor(new IntColor(Color.parseColor("#26a69a")));  // Green
        volumeOptions.setBase(0.0F);  // Start from zero
        volumeOptions.setBaseLineWidth(LineWidth.TWO);  // Replace with actual enum values if defined
        volumeOptions.setPriceScaleId(new PriceScaleId("volume")); // if allowed
        volumeOptions.setPriceScaleId(PriceScaleId.Companion.getLEFT()); // Use LEFT scale

        chartsView.getApi().addHistogramSeries(volumeOptions, series -> {
            volumeSeries = series;

            return Unit.INSTANCE;
        });
    }

    /**
     * Create a new method to convert MarketDataEntity to histogram data for volume
     */
    private HistogramData convertToVolumeBar(MarketDataEntity entity) {
        if (entity == null) {
            return null;
        }

        try {
            // Get UTC timestamp from backend
            long localAdjustedSeconds = getLocalAdjustedSeconds(entity);

            // Determine color based on whether the candle is bullish (green) or bearish (red)
            IntColor color = entity.getClose() >= entity.getOpen()
                    ? new IntColor(Color.parseColor("#26a69a"))  // Green for bullish
                    : new IntColor(Color.parseColor("#ef5350"));  // Red for bearish

            return new com.tradingview.lightweightcharts.api.series.models.HistogramData(
                    new Time.Utc(localAdjustedSeconds),
                    (float) entity.getVolume(),
                    color
            );
        } catch (Exception e) {
            Timber.e(e, "Error converting entity to volume bar");
            return null;
        }
    }

    /**
     * Updates the chart with new candle data
     */
    private void updateCandlestickChart(List<CandlestickData> candles, List<HistogramData> volumeBars) {
        if (candleSeries != null && !candles.isEmpty()) {
            // Add new data to chart
            candleSeries.setData(candles);

            // Add volume data if available
            if (volumeSeries != null && !volumeBars.isEmpty()) {
                volumeSeries.setData(volumeBars);
            }

            // Fit content to view
            chartsView.getApi().getTimeScale().fitContent();

            Timber.d("Updated chart with %d candles", candles.size());
        }
    }

    /**
     * Handles the API call with automatic retry logic
     */
    private void fetchDataWithRetry(String interval, String start, String end, int retryCount) {
        MainClient.getInstance().create(ApiService.class)
                .getMarketData(symbol, interval, start, end, historicalState.currentPage,
                        historicalState.CHUNK_SIZE).enqueue(new Callback<MarketDataResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<MarketDataResponse> call,
                                           @NonNull Response<MarketDataResponse> response) {
                        try {
                            if (response.isSuccessful() && response.body() != null) {
                                // Hide loading indicator
                                binding.marketChartLayout.progressBar.setVisibility(View.GONE);

                                List<CandlestickData> candles = new ArrayList<>();
                                List<HistogramData> volumeBars = new ArrayList<>();
                                // Process response data
                                MarketDataResponse marketResponse = response.body(); // Get the single response object

                                // Process response data
                                if (marketResponse != null && marketResponse.getData() != null) {
                                    for (MarketDataEntity entity : marketResponse.getData()) {
                                        candles.add(convertToCandle(entity));
                                        volumeBars.add(convertToVolumeBar(entity));
                                    }
                                }

                                // Update UI with data
                                if (!candles.isEmpty()) {
                                    updateCandlestickChart(candles, volumeBars);
                                    // Update page counter for pagination
                                    historicalState.currentPage++;
                                    historicalState.backfilledChunks++;
                                } else {
                                    // No data returned
                                    Timber.d("No data returned for the specified range");
                                    historicalState.noMoreData = true;
                                }
                            } else {
                                // Handle API errors
                                String errorMsg = "Error code: " + response.code();
                                if (response.errorBody() != null) {
                                    errorMsg += " - " + response.errorBody().string();
                                }
                                Timber.e("API error: %s", errorMsg);

                                if (retryCount < MAX_RETRIES) {
                                    // Schedule retry with exponential backoff
                                    long delayMs = (long) Math.pow(2, retryCount) * 1000;
                                    scheduleRetry(interval, start, end, retryCount, delayMs);
                                } else {
                                    handleDataLoadError(new Exception("Failed after " + MAX_RETRIES + " retries"));
                                }
                            }
                        } catch (Exception e) {
                            Timber.e(e, "Error processing response");
                            handleDataLoadError(e);
                        } finally {
                            isRequestInProgress.set(false);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<MarketDataResponse> call, @NonNull Throwable t) {
                        Timber.e(t, "API call failed");

                        if (retryCount < MAX_RETRIES) {
                            // Schedule retry with exponential backoff
                            long delayMs = (long) Math.pow(2, retryCount) * 1000;
                            scheduleRetry(interval, start, end, retryCount, delayMs);
                        } else {
                            handleDataLoadError(t);
                            isRequestInProgress.set(false);
                        }
                    }
                });

    }

    /**
     * Registers event handlers for chart navigation to load more data when needed
     */
    private void registerChartScrollEvents() {
        // Handle chart scroll events to load more historical data when scrolling left
        chartsView.getApi().getTimeScale().subscribeVisibleTimeRangeChange(range -> {
            if (range != null && !historicalState.isLoading && !historicalState.noMoreData &&
                    historicalState.backfilledChunks < historicalState.maxBackfillChunks) {

                // Check if we're close to the left edge of the chart
                // This logic may need refinement based on your specific UI requirements
                if (historicalState.oldestLoadedTimestamp != null) {
                    long fromTime = range.getFrom().getDate().getTime();
                    long oldestTime = historicalState.oldestLoadedTimestamp.getTime() / 1000;

                    // If within 10% of oldest loaded data, load more
                    long rangeSize = range.getTo().getDate().getTime() - fromTime;
                    if (Math.abs(fromTime - oldestTime) < (rangeSize * 0.1)) {
                        Timber.d("Near left edge, loading more historical data");
                        loadMoreHistoricalData();
                    }
                }
            }
            return Unit.INSTANCE;
        });
    }

    /**
     * Loads more historical data when scrolling back in time
     */
    private void loadMoreHistoricalData() {
        if (historicalState.isLoading || historicalState.noMoreData) {
            return;
        }

        historicalState.isLoading = true;

        binding.marketChartLayout.progressBar.setVisibility(View.VISIBLE);
        // Use the oldest timestamp we have as the end time for the next request
        fetchHistoricalData(currentInterval, null, historicalState.oldestLoadedTimestamp);
    }

    /**
     * Sets up the time interval tabs (1m, 5m, 15m, etc.)
     */
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

                // Reset historical state and update the current interval
                String interval = ((TextView) tab.getCustomView().findViewById(R.id.tabTitle)).getText().toString();
                currentInterval = interval;
                historicalState.reset();

                // Clear existing data before loading new
                if (candleSeries != null) {
                    candleSeries.setData(new ArrayList<>());
                }

                // Reinitialize with new interval
                creatingViewModelConnection(interval);

                binding.marketChartLayout.progressBar.setVisibility(View.VISIBLE);

                // Load initial data for the new interval
                fetchHistoricalData(interval, new Date(), null);
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
            // Select tab without triggering listener
            binding.marketChartLayout.timeIntervalTabLayout.selectTab(firstTab, false);

            // Then manually update the visual state
            TextView text = Objects.requireNonNull(firstTab.getCustomView()).findViewById(R.id.tabTitle);
            LinearLayout tabHolder = Objects.requireNonNull(firstTab.getCustomView()).findViewById(R.id.tab_holder);
            tabHolder.setBackgroundResource(R.drawable.bg_selected);
            text.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.white));

            // And handle the initial connection in globalInit instead
            binding.marketChartLayout.progressBar.setVisibility(View.VISIBLE);
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

    private void creatingViewModelConnection(String interval) {
        viewModel.connect(symbol, interval, false);
    }


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
    protected void onPause() {
        super.onPause();
        // Disconnect from WebSocket to avoid background data usage
        if (viewModel != null && symbol != null) {
            viewModel.disconnect();
        }

        // Cancel any pending tasks by resetting the request flag
        // but DON'T shut down the executor service since it's final
        isRequestInProgress.set(false);

        Timber.d("Activity paused, WebSocket disconnected");
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reconnect to WebSocket when activity becomes visible again
        if (viewModel != null && symbol != null) {
            creatingViewModelConnection(currentInterval);
            Timber.d("Activity resumed, reconnected to WebSocket with interval: %s", currentInterval);
        }

        // Refresh chart data if needed
        if (historicalState != null) {
            historicalState.reset();
            fetchHistoricalData(currentInterval, new Date(), null);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Ensure all resources are released
        if (viewModel != null && symbol != null) {
            viewModel.disconnect();
        }

        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }

    private void setupTimeframeSpinner() {
        MaterialAutoCompleteTextView autoComplete = findViewById(R.id.auto_complete_timeframe);
        List<String> timeframeOptions = Arrays.asList(
                "Last 30 minutes", "1 hour", "7 days", "Last 30 days", "Custom..."
        );

        // For timeframe spinner
        TimeframeSpinnerAdapter adapter = new TimeframeSpinnerAdapter(
                this,
                timeframeOptions
        );

        autoComplete.setAdapter(adapter);

        autoComplete.setOnItemClickListener((parent, view, position, id) -> {
            String selectedTimeframe = timeframeOptions.get(position);
            if (selectedTimeframe.equals("Custom...")) {
                showCustomTimeframeDialog();
            }
        });
    }

    private void showCustomTimeframeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
        builder.setTitle("Enter Custom Timeframe");

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_custom_timeframe, null);
        builder.setView(dialogView);

        final EditText valueInput = dialogView.findViewById(R.id.edittext_value);
        final AutoCompleteTextView unitAutoComplete = dialogView.findViewById(R.id.spinner_time_unit); // Changed to AutoCompleteTextView

        // Set up adapter for time units
        // In showCustomTimeframeDialog() for time units
        ArrayAdapter<CharSequence> unitAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.time_units,
                R.layout.dark_dropdown_item  // Use the same dark layout
        );

        unitAutoComplete.setAdapter(unitAdapter);

        builder.setPositiveButton("Apply", (dialog, which) -> {
            String value = valueInput.getText().toString();
            String unit = unitAutoComplete.getText().toString();

            if (!value.isEmpty()) {
                String customTimeframe = value + " " + unit;
                // Handle custom timeframe
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void handlePatternSelection(List<String> patternName, boolean isSelected) {
        if (isSelected) {
            Log.d("PatternSelected", patternName + " selected");
            // Handle pattern selection

        } else {
            Log.d("PatternSelected", patternName + " deselected");
            // Handle pattern deselection
        }
    }

    private void handleGoalSelection(String goalName, boolean isSelected) {
        if (isSelected) {
            Log.d("GoalSelected", goalName + " selected");
            // Handle goal selection
        } else {
            Log.d("GoalSelected", goalName + " deselected");
            // Handle goal deselection
        }
    }

}