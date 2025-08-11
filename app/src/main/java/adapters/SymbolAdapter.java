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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import interfaces.OnSymbolClickListener;
import market.symbol.SymbolMarketDataActivity;
import model_interfaces.OnWatchlistActionListener;
import models.Symbol;
import timber.log.Timber;
import utils.SymbolDiffCallback;

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
    private final String userId;
    private final OnWatchlistActionListener listener;
    private final OnSymbolClickListener symbolClickListener;

    public SymbolAdapter(Context context, List<Symbol> symbolList, boolean isSearchAdapter,
                         String userId, OnWatchlistActionListener listener,
                         OnSymbolClickListener symbolClickListener) {
        this.context = context;
        this.symbolList = symbolList;
        this.isSearchAdapter = isSearchAdapter;
        this.userId = userId;
        this.listener = listener;
        this.symbolClickListener = symbolClickListener;
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
            if (holder instanceof SearchViewHolder) {
                SearchViewHolder vh = (SearchViewHolder) holder;
                vh.binding.textViewSymbol.setText(symbol.getSymbol());
                vh.binding.textViewName.setText(symbol.getAsset()); // Or getBaseCurrency(), depends on desired display
                vh.binding.textViewPrice.setText(String.format(Locale.US, "US$%.2f", symbol.getCurrentPrice()));

                boolean isNegative = symbol.get_24hChange() < 0;
                int textColor = ContextCompat.getColor(context, isNegative ? R.color.crimson_red : R.color.green_chart_color);
                vh.binding.percentagePriceChange.setTextColor(textColor);
                vh.binding.percentagePriceChange.setText(String.format(Locale.US, "%.2f%%", symbol.get_24hChange()));

                vh.binding.addToWatchlist.setVisibility(symbol.isInWatchlist() ? View.GONE : View.VISIBLE);
                vh.binding.removeFromWatchlist.setVisibility(symbol.isInWatchlist() ? View.VISIBLE : View.GONE);

                vh.binding.getRoot().setOnClickListener(v -> {
                    if (symbolClickListener != null) {
                        symbolClickListener.onSymbolClicked(symbol);
                    }
                    startSymbolDetail(symbol);
                });

                vh.binding.addToWatchlist.setOnClickListener(v -> {
                    // --- Optimistic UI Update ---
                    symbol.setInWatchlist(true);
                    // Use the non-deprecated method
                    notifyItemChanged(holder.getBindingAdapterPosition());

                    if (listener != null) {
                        listener.onAddToWatchlist(userId, symbol, "Binance");
                    }
                });

                vh.removeButton.setOnClickListener(v -> {
                    // --- Optimistic UI Update ---
                    symbol.setInWatchlist(false);
                    // Use the non-deprecated method
                    notifyItemChanged(holder.getBindingAdapterPosition());

                    if (listener != null) {
                        listener.onRemoveFromWatchlist(userId, symbol.getSymbol());
                    }
                });

            } else if (holder instanceof WatchlistViewHolder) {
                WatchlistViewHolder vh = (WatchlistViewHolder) holder;
                vh.binding.textViewSymbol.setText(symbol.getSymbol());
                vh.binding.textViewName.setText(symbol.getAsset()); // Or getBaseCurrency()
                vh.binding.textViewPrice.setText(String.format(Locale.US, "US$%.2f", symbol.getPrice()));

                boolean isNegative = symbol.getChange() < 0;
                int boxBackground = isNegative ? R.drawable.red_box : R.drawable.green_box;
                vh.binding.changeBox.setBackgroundResource(boxBackground);
                vh.binding.textViewChange.setText(String.format(Locale.US, "%.2f%%", symbol.getChange()));

//                List<Double> sparklineData = symbol.getSparkline();
//                if (sparklineData != null && !sparklineData.isEmpty()) {
//                    vh.binding.lineChart.setVisibility(View.VISIBLE);
//                    List<Entry> entries = new ArrayList<>();
//                    for (int i = 0; i < sparklineData.size(); i++) {
//                        if(sparklineData.get(i) != null) { // Check for null points in sparkline
//                            entries.add(new Entry(i, sparklineData.get(i).floatValue()));
//                        }
//                    }
//                    if (!entries.isEmpty()) { // Only setup chart if there are valid entries
//                        setupChart(vh.binding.lineChart, entries);
//                    } else {
//                        vh.binding.lineChart.setVisibility(View.GONE);
//                    }
//                } else {
//                    vh.binding.lineChart.setVisibility(View.GONE);
//                }

                // MODIFIED: Simplified click listener
                vh.binding.getRoot().setOnClickListener(v -> {
                    if (symbolClickListener != null) {
                        symbolClickListener.onSymbolClicked(symbol);
                    }
                    startSymbolDetail(symbol);
                });

                // If watchlist items need a remove button, add it to XML and handle here:
                // vh.binding.idOfRemoveButtonInWatchlistItem.setOnClickListener(v -> listener.onRemoveFromWatchlist(null, symbol.getSymbol()));
            }
        } catch (Exception e) {
            Timber.e(e, "Error binding view at position %d for symbol %s", position, symbol.getSymbol());
        }
    }

    @Override
    public int getItemCount() {
        return symbolList != null ? symbolList.size() : 0;
    }

    // MODIFIED: Simplified this method to only pass the symbol's ticker.
    private void startSymbolDetail(Symbol symbol) {
        if (context == null || symbol == null || symbol.getSymbol() == null) return;

        Intent intent = new Intent(context, SymbolMarketDataActivity.class);
        intent.putExtra("SYMBOL", symbol.getSymbol());
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