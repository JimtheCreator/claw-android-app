package adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.claw.ai.R;
import com.claw.ai.databinding.CryptoItemBinding;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.List;

public class CryptosAdapter extends RecyclerView.Adapter<CryptosAdapter.ViewHolder> {

    Context context;

    public CryptosAdapter(Context context) {
        this.context = context;
    }

    @NonNull
    @Override
    public CryptosAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate using ViewBinding
        CryptoItemBinding binding = CryptoItemBinding.inflate(LayoutInflater.from(context), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull CryptosAdapter.ViewHolder holder, int position) {

    }

    @Override
    public int getItemCount() {
        return 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        CryptoItemBinding binding;

        public ViewHolder(@NonNull CryptoItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

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