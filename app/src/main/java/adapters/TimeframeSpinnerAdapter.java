package adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.claw.ai.R;

import java.util.List;

/**
 * Custom adapter for the timeframe spinner with a modern UI look
 */
public class TimeframeSpinnerAdapter extends ArrayAdapter<String> {
    private final Context context;
    private final List<String> items;

    public TimeframeSpinnerAdapter(Context context, List<String> items) {
        super(context, R.layout.dark_dropdown_item, items);
        this.context = context;
        this.items = items;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return createItemView(position, convertView, parent);
    }

    @Override
    public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return createItemView(position, convertView, parent);
    }

    private View createItemView(int position, View convertView, ViewGroup parent) {
        View view = LayoutInflater.from(context).inflate(R.layout.dark_dropdown_item, parent, false);
        TextView textView = view.findViewById(android.R.id.text1);
        textView.setText(items.get(position));
        textView.setTextColor(ContextCompat.getColor(context, R.color.off_white));
        return view;
    }
}
