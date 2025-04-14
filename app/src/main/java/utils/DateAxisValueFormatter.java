package utils;

import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DateAxisValueFormatter extends ValueFormatter {
    private final List<Long> timestamps;
    private final SimpleDateFormat hourFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat dayFormat = new SimpleDateFormat("dd MMM", Locale.getDefault());
    private final SimpleDateFormat monthFormat = new SimpleDateFormat("MMM yy", Locale.getDefault());

    private final String interval;

    public DateAxisValueFormatter(List<Long> timestamps, String interval) {
        this.timestamps = timestamps;
        this.interval = interval;
    }

    @Override
    public String getFormattedValue(float value) {
        int index = (int) value;
        if (index >= 0 && index < timestamps.size()) {
            // Select format based on interval
            if (interval.equals("1h") || interval.equals("4h")) {
                return hourFormat.format(new Date(timestamps.get(index)));
            } else if (interval.equals("1d") || interval.equals("3d")) {
                return dayFormat.format(new Date(timestamps.get(index)));
            } else {
                // For weekly, monthly intervals
                return monthFormat.format(new Date(timestamps.get(index)));
            }
        } else {
            return "";
        }
    }
}

