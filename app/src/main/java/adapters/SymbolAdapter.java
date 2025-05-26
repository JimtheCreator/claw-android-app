package adapters;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.claw.ai.R;
import com.claw.ai.databinding.SearchedSymbolItemBinding;
import com.claw.ai.databinding.SymbolWatchlistItemBinding;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import market.SymbolMarketDataActivity;
import model_interfaces.OnWatchlistActionListener;
import models.Symbol;
import utils.SymbolDiffCallback;

import timber.log.Timber;

/**
 * A RecyclerView adapter that displays cryptocurrency symbol data, including price information
 * and sparkline charts. Updates list items efficiently using DiffUtil.
 */


public class SymbolAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int SEARCH_VIEW_TYPE = 0;
    private static final int WATCHLIST_VIEW_TYPE = 1;

    Context context;
    private List<Symbol> symbolList;
    boolean isSearchAdapter;
    private final OnWatchlistActionListener listener;

    public SymbolAdapter(Context context, List<Symbol> symbolList, boolean isSearchAdapter, OnWatchlistActionListener listener) {
        this.context = context;
        this.symbolList = symbolList;
        this.isSearchAdapter = isSearchAdapter;
        this.listener = listener;
    }

    public void setData(List<Symbol> newList) {
        if (newList == null) newList = new ArrayList<>();
        if (this.symbolList == null) this.symbolList = newList;

        Timber.d("Updating adapter with %d symbols", newList.size());
        List<Symbol> oldList = new ArrayList<>(this.symbolList);

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new SymbolDiffCallback(oldList, newList));
        this.symbolList.clear();
        this.symbolList.addAll(newList);
        diffResult.dispatchUpdatesTo(this);
    }

    @Override
    public int getItemViewType(int position) {
        return isSearchAdapter ? SEARCH_VIEW_TYPE : WATCHLIST_VIEW_TYPE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        if (viewType == SEARCH_VIEW_TYPE) {
            SearchedSymbolItemBinding binding = SearchedSymbolItemBinding.inflate(inflater, parent, false);
            return new SearchViewHolder(binding);
        } else {
            SymbolWatchlistItemBinding binding = SymbolWatchlistItemBinding.inflate(inflater, parent, false);
            return new WatchlistViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Symbol symbol = symbolList.get(position);

        try {
            FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();

            assert firebaseUser != null;
            String userid = firebaseUser.getUid();

            if (holder instanceof SearchViewHolder) {
                SearchViewHolder vh = (SearchViewHolder) holder;
                vh.binding.textViewSymbol.setText(symbol.getSymbol());
                vh.binding.textViewName.setText(symbol.getAsset());
                vh.binding.textViewPrice.setText(String.format(Locale.US, "US$%.2f", symbol.getCurrentPrice()));

                boolean isNegative = symbol.get_24hChange() < 0;
                int textColor = isNegative ? R.color.crimson_red : R.color.green_chart_color;
                vh.binding.percentagePriceChange.setTextColor(ContextCompat.getColor(context, textColor));
                vh.binding.percentagePriceChange.setText(String.format(Locale.US, "%.2f%%", symbol.get_24hChange()));

                vh.binding.getRoot().setOnClickListener(v -> startSymbolDetail(symbol));
                vh.binding.addToWatchlist.setOnClickListener(v -> {
                    Log.d("CLICKED", "Initiated");
                    if (!symbol.isInWatchlist()) {
                        vh.binding.addToWatchlist.setVisibility(View.GONE);
                        listener.onAddToWatchlist(userid, symbol, "Binance");
                    }else {
                        vh.binding.removeFromWatchlist.setVisibility(View.VISIBLE);
                    }
                });

                vh.binding.addToWatchlist.setVisibility(symbol.isInWatchlist() ? View.GONE : View.VISIBLE);
                vh.binding.removeFromWatchlist.setVisibility(symbol.isInWatchlist() ? View.VISIBLE : View.GONE);

                vh.removeButton.setOnClickListener(v -> {
                    vh.binding.removeFromWatchlist.setVisibility(View.GONE);
                    listener.onRemoveFromWatchlist(userid, symbol.getSymbol());
                });

            } else if (holder instanceof WatchlistViewHolder) {
                WatchlistViewHolder vh = (WatchlistViewHolder) holder;
                vh.binding.textViewSymbol.setText(symbol.getSymbol());
                vh.binding.textViewName.setText(symbol.getAsset());
                vh.binding.textViewPrice.setText(String.format(Locale.US, "US$%.2f", symbol.getCurrentPrice()));

                boolean isNegative = symbol.get_24hChange() < 0;
                int boxBackground = isNegative ? R.drawable.red_box : R.drawable.green_box;
                vh.binding.changeBox.setBackgroundResource(boxBackground);
                vh.binding.textViewChange.setText(String.format(Locale.US, "%.2f%%", symbol.get_24hChange()));

                List<Entry> entries = new ArrayList<>();
                List<Double> sparkline = symbol.getSparkline();
                if (sparkline != null && !sparkline.isEmpty()) {
                    vh.binding.lineChart.setVisibility(View.VISIBLE);
                    for (int i = 0; i < sparkline.size(); i++) {
                        entries.add(new Entry(i, sparkline.get(i).floatValue()));
                    }
                    setupChart(vh.binding.lineChart, entries);
                } else {
                    vh.binding.lineChart.setVisibility(View.GONE);
                }

                vh.binding.getRoot().setOnClickListener(v -> startSymbolDetail(symbol));
            }
        } catch (Exception e) {
            Timber.e(e, "Error binding view at position %d", position);
        }
    }

    @Override
    public int getItemCount() {
        return symbolList != null ? symbolList.size() : 0;
    }

    private void startSymbolDetail(Symbol symbol) {
        Intent intent = new Intent(context, SymbolMarketDataActivity.class);
        intent.putExtra("SYMBOL", symbol.getSymbol());
        intent.putExtra("ASSET", symbol.getAsset());
        intent.putExtra("CURRENT_PRICE", symbol.getCurrentPrice());
        intent.putExtra("CHANGE_24H", symbol.get_24hChange());

        double[] sparklineArray = new double[symbol.getSparkline().size()];
        for (int i = 0; i < symbol.getSparkline().size(); i++) {
            sparklineArray[i] = symbol.getSparkline().get(i);
        }

        intent.putExtra("SPARKLINE", sparklineArray);
        context.startActivity(intent);
    }

    public static class SearchViewHolder extends RecyclerView.ViewHolder {
        SearchedSymbolItemBinding binding;
        View addButton;  // Changed to View to match XML

        View removeButton;  // Add this to your watchlist layout

        public SearchViewHolder(@NonNull SearchedSymbolItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            this.addButton = binding.addToWatchlist;  // ID from searched_symbol_item.xml
            this.removeButton = binding.removeFromWatchlist;
        }
    }

    public static class WatchlistViewHolder extends RecyclerView.ViewHolder {
        SymbolWatchlistItemBinding binding;

        public WatchlistViewHolder(@NonNull SymbolWatchlistItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private void setupChart(LineChart chart, List<Entry> dataPoints) {
        if (dataPoints == null || dataPoints.isEmpty()) return;

        boolean isDowntrend = dataPoints.get(dataPoints.size() - 1).getY() < dataPoints.get(0).getY();
        int lineColor = isDowntrend ? android.graphics.Color.RED : android.graphics.Color.GREEN;
        int shadeColor = isDowntrend ? R.drawable.chart_fill_red : R.drawable.chart_fill_green;

        LineDataSet dataSet = new LineDataSet(dataPoints, "Price Trend");
        dataSet.setColor(lineColor);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setLineWidth(2f);
        dataSet.setDrawFilled(true);
        dataSet.setFillDrawable(ContextCompat.getDrawable(chart.getContext(), shadeColor));

        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);
        chart.invalidate();

        chart.getLegend().setEnabled(false);
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(false);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawLabels(false);

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawLabels(false);
        leftAxis.setDrawGridLines(false);
        leftAxis.setEnabled(false);

        chart.getAxisRight().setEnabled(false);
    }
}