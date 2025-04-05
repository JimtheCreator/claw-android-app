package adapters;

import android.content.Context;
import android.view.LayoutInflater;
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

import models.Symbol;
import utils.SymbolDiffCallback;

/**
 * A RecyclerView adapter that displays cryptocurrency symbol data, including price information
 * and sparkline charts. Updates list items efficiently using DiffUtil.
 */
public class CryptosAdapter extends RecyclerView.Adapter<CryptosAdapter.ViewHolder> {

    Context context;

    private List<Symbol> symbolList;

    /**
     * Updates the dataset and calculates differences for efficient RecyclerView updates
     * @param newList The new list of cryptocurrency symbols to display
     */
    public void setData(List<Symbol> newList) {
        this.symbolList.clear();
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new SymbolDiffCallback(this.symbolList, newList));
        this.symbolList = newList;
        diffResult.dispatchUpdatesTo(this);
    }

    /**
     * Constructs a CryptosAdapter with the specified context and initial symbol list
     * @param context The context used for resource access and inflation
     * @param symbolList Initial list of cryptocurrency symbols to display
     */
    public CryptosAdapter(Context context, List<Symbol> symbolList) {
        this.context = context;
        this.symbolList = symbolList;
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

        // Texts
        holder.binding.getRoot().setBackgroundColor(context.getColor(R.color.black2_0));
        holder.binding.textViewSymbol.setText(symbol.getSymbol());
        holder.binding.textViewName.setText(symbol.getName());
        holder.binding.textViewPrice.setText(String.format(Locale.US, "US$%.2f", symbol.getCurrentPrice()));
        holder.binding.textViewChange.setText(String.format(Locale.US,"%.2f%%", symbol.get_24hChange()));

        // Change color based on +/- change
        boolean isNegative = symbol.get_24hChange() < 0;
        int boxBackground = isNegative ? R.drawable.red_box : R.drawable.green_box;
        holder.binding.changeBox.setBackgroundResource(boxBackground);

        // Sparkline chart
        List<Entry> entries = new ArrayList<>();
        List<Double> sparkline = symbol.getSparkline();
        for (int i = 0; i < sparkline.size(); i++) {
            entries.add(new Entry(i, sparkline.get(i).floatValue()));
        }

        setupChart(holder.binding.lineChart, entries);
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
        if (dataPoints.isEmpty()) return;

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
        dataSet.setFillDrawable(ContextCompat.getDrawable(chart.getContext(), shadeColor)); // previous code was: dataSet.setFillDrawable(chart.getContext().getDrawable(shadeColor)

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