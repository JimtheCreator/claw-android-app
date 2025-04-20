package adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.claw.ai.R;
import com.claw.ai.databinding.CryptoItemBinding;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import market.SymbolMarketDataActivity;
import models.Symbol;
import utils.SymbolDiffCallback;

import timber.log.Timber;

/**
 * A RecyclerView adapter that displays cryptocurrency symbol data, including price information
 * and sparkline charts. Updates list items efficiently using DiffUtil.
 */
public class CryptosAdapter extends RecyclerView.Adapter<CryptosAdapter.ViewHolder> {

    Context context;
    private List<Symbol> symbolList;
    boolean isSearchAdapter;

    /**
     * Updates the dataset and calculates differences for efficient RecyclerView updates
     * @param newList The new list of cryptocurrency symbols to display
     */
    public void setData(List<Symbol> newList) {
        if (newList == null) {
            newList = new ArrayList<>();
        }

        if (this.symbolList == null) {
            this.symbolList = new ArrayList<>();
        }

        Timber.d("Updating adapter with %d symbols", newList.size());

        List<Symbol> oldList = new ArrayList<>(this.symbolList);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new SymbolDiffCallback(oldList, newList));

        this.symbolList.clear();
        this.symbolList.addAll(newList);
        diffResult.dispatchUpdatesTo(this);
    }


    /**
     * Constructs a CryptosAdapter with the specified context and initial symbol list
     * @param context The context used for resource access and inflation
     * @param symbolList Initial list of cryptocurrency symbols to display
     */
    public CryptosAdapter(Context context, List<Symbol> symbolList, boolean isSearchAdapter) {
        this.context = context;
        this.symbolList = symbolList;
        this.isSearchAdapter = isSearchAdapter;
    }

    /**
     * Creates new ViewHolder instances when needed
     * @param parent The ViewGroup into which the new View will be added
     * @param viewType The type of the new View
     * @return A new ViewHolder that holds the inflated View
     */
    @NonNull
    @Override
    public CryptosAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate using ViewBinding
        CryptoItemBinding binding = CryptoItemBinding.inflate(LayoutInflater.from(context), parent, false);
        return new ViewHolder(binding);
    }

    /**
     * Binds cryptocurrency data to a ViewHolder at the specified position
     * @param holder The ViewHolder to update
     * @param position The position of the item in the dataset
     */
    @Override
    public void onBindViewHolder(@NonNull CryptosAdapter.ViewHolder holder, int position) {
        Symbol symbol = symbolList.get(position);

        try {
            // Texts
            holder.binding.textViewSymbol.setText(symbol.getSymbol());
            holder.binding.textViewName.setText(symbol.getAsset());
            holder.binding.textViewPrice.setText(String.format(Locale.US, "US$%.2f", symbol.getCurrentPrice()));

            if (!isSearchAdapter){
                // Change color based on +/- change
                boolean isNegative = symbol.get_24hChange() < 0;
                int boxBackground = isNegative ? R.drawable.red_box : R.drawable.green_box;
                holder.binding.changeBox.setBackgroundResource(boxBackground);

                holder.binding.textViewChange.setText(String.format(Locale.US,"%.2f%%", symbol.get_24hChange()));

                // Sparkline chart
                List<Entry> entries = new ArrayList<>();
                List<Double> sparkline = symbol.getSparkline();
                if (sparkline != null && !sparkline.isEmpty()) {
                    holder.binding.lineChart.setVisibility(View.VISIBLE);
                    for (int i = 0; i < sparkline.size(); i++) {
                        entries.add(new Entry(i, sparkline.get(i).floatValue()));
                    }

                    setupChart(holder.binding.lineChart, entries);
                } else {
                    holder.binding.lineChart.setVisibility(View.GONE);
                }
            }

            else {
                holder.binding.lineChart.setVisibility(View.GONE);
                holder.binding.changeBox.setVisibility(View.GONE);
                holder.binding.percentagePriceChange.setVisibility(View.VISIBLE);
                // Change color based on +/- change
                boolean isNegative = symbol.get_24hChange() < 0;
                int textColor = isNegative ? R.color.crimson_red : R.color.green_chart_color;

                holder.binding.percentagePriceChange.setTextColor(ContextCompat.getColor(context, textColor));
                holder.binding.percentagePriceChange.setText(String.format(Locale.US,"%.2f%%", symbol.get_24hChange()));
            }

            holder.binding.getRoot().setOnClickListener(v -> {
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
            });
        }

        catch (Exception e) {
            Timber.e(e, "Error binding view at position %d", position);
        }
    }

    /**
     * Returns the total number of items in the dataset
     * @return The size of the symbol list
     */
    @Override
    public int getItemCount() {
        return symbolList != null ? symbolList.size() : 0;
    }

    /**
     * ViewHolder class that holds references to all views in a list item using ViewBinding
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        CryptoItemBinding binding;

        /**
         * Constructs a ViewHolder with the specified ViewBinding
         * @param binding The binding object containing the item views
         */
        public ViewHolder(@NonNull CryptoItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    /**
     * Configures a LineChart with price trend data and visual styling
     * @param chart The LineChart widget to configure
     * @param dataPoints List of price entries for the sparkline chart
     */
    private void setupChart(LineChart chart, List<Entry> dataPoints) {
        if (dataPoints == null || dataPoints.isEmpty()) return;

        // Determine if trend is up or down
        int lastIndex = dataPoints.size() - 1;
        boolean isDowntrend = dataPoints.get(lastIndex).getY() < dataPoints.get(0).getY();

        int lineColor = isDowntrend ? android.graphics.Color.RED : android.graphics.Color.GREEN;
        int shadeColor = isDowntrend ? R.drawable.chart_fill_red : R.drawable.chart_fill_green;

        // Create DataSet
        LineDataSet dataSet = new LineDataSet(dataPoints, "Price Trend");
        dataSet.setColor(lineColor);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setLineWidth(2f);

        // Set fill gradient for shaded effect
        dataSet.setDrawFilled(true);
        dataSet.setFillDrawable(ContextCompat.getDrawable(chart.getContext(), shadeColor));

        // Apply data
        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);
        chart.invalidate();

        // Disable extra features
        chart.getLegend().setEnabled(false);
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(false);

        // Customize Axes
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