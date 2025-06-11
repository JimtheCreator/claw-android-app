package adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.claw.ai.R;

import java.util.ArrayList;
import java.util.List;

import models.PatternAlert;
import models.PriceAlert;
import timber.log.Timber;
import utils.PatternAlertsDiffCallback;
import utils.PriceAlertsDiffCallback;

public class PatternAlertsAdapter extends RecyclerView.Adapter<PatternAlertsAdapter.PatternAlertViewHolder> {

    private List<PatternAlert> alerts = new ArrayList<>();
    private final OnDeleteClickListener deleteClickListener;

    public interface OnDeleteClickListener {
        void onDeleteClick(PatternAlert alert);
    }

    public PatternAlertsAdapter(OnDeleteClickListener deleteClickListener) {
        this.deleteClickListener = deleteClickListener;
    }

    @NonNull
    @Override
    public PatternAlertViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pattern_alert, parent, false);
        return new PatternAlertViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PatternAlertViewHolder holder, int position) {
        PatternAlert alert = alerts.get(position);
        holder.bind(alert, deleteClickListener);
    }

    @Override
    public int getItemCount() {
        return alerts.size();
    }

    public void setAlerts(List<PatternAlert> newList) {
        if (newList == null) newList = new ArrayList<>();
        if (this.alerts == null) this.alerts = newList;

        Timber.d("Updating adapter with %d symbols", newList.size());
        List<PatternAlert> oldList = new ArrayList<>(this.alerts);

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new PatternAlertsDiffCallback(oldList, newList));
        this.alerts.clear();
        this.alerts.addAll(newList);
        diffResult.dispatchUpdatesTo(this);
    }

    public static class PatternAlertViewHolder extends RecyclerView.ViewHolder {
        private final TextView symbolTextView;
        private final TextView patternNameTextView;
        private final TextView timeIntervalTextView;
        private final TextView patternStateTextView;
        private final ImageView symbolImageView;
        private final ImageButton deleteButton;

        public PatternAlertViewHolder(@NonNull View itemView) {
            super(itemView);
            symbolTextView = itemView.findViewById(R.id.text_view_symbol);
            patternNameTextView = itemView.findViewById(R.id.text_view_pattern_name);
            timeIntervalTextView = itemView.findViewById(R.id.text_view_time_interval);
            patternStateTextView = itemView.findViewById(R.id.text_view_pattern_state);
            symbolImageView = itemView.findViewById(R.id.icon_symbol);
            deleteButton = itemView.findViewById(R.id.button_delete);
        }

        public void bind(final PatternAlert alert, final OnDeleteClickListener listener) {
            symbolTextView.setText(alert.getSymbol());
            patternNameTextView.setText(alert.getPatternName());
            timeIntervalTextView.setText("Interval: " + alert.getTimeInterval());
            patternStateTextView.setText("State: " + alert.getPatternState());

            // Example of setting image - replace with your logic
            // Glide.with(itemView.getContext()).load(getSymbolIconUrl(alert.getSymbol())).into(symbolImageView);

            deleteButton.setOnClickListener(v -> listener.onDeleteClick(alert));
        }
    }
}

