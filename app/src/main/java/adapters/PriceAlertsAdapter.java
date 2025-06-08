package adapters;

import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.claw.ai.R;

import models.PriceAlert;
import models.Symbol;
import timber.log.Timber;
import utils.PriceAlertsDiffCallback;
import utils.SymbolDiffCallback;

import java.util.ArrayList;
import java.util.List;

public class PriceAlertsAdapter extends RecyclerView.Adapter<PriceAlertsAdapter.ViewHolder> {
    private List<PriceAlert> alerts = new ArrayList<>();
    private final OnCancelClickListener cancelClickListener;

    public interface OnCancelClickListener {
        void onCancelClick(PriceAlert alert);
    }

    public PriceAlertsAdapter(OnCancelClickListener listener) {
        this.cancelClickListener = listener;
    }

    public void setAlerts(List<PriceAlert> newList) {
        if (newList == null) newList = new ArrayList<>();
        if (this.alerts == null) this.alerts = newList;

        Timber.d("Updating adapter with %d symbols", newList.size());
        List<PriceAlert> oldList = new ArrayList<>(this.alerts);

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new PriceAlertsDiffCallback(oldList, newList));
        this.alerts.clear();
        this.alerts.addAll(newList);
        diffResult.dispatchUpdatesTo(this);
    }


    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_price_alert, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PriceAlert alert = alerts.get(position);
        holder.symbolTextView.setText(alert.getSymbol());
        holder.conditionTextView.setText(
                alert.getConditionType() + " " + alert.getConditionValue()
        );
        holder.cancelButton.setOnClickListener(v -> {
            if (cancelClickListener != null) {
                cancelClickListener.onCancelClick(alert);
            }
        });
    }

    @Override
    public int getItemCount() {
        return alerts.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView symbolTextView;
        TextView conditionTextView;
        Button cancelButton;
        FrameLayout glow_container;

        ViewHolder(View itemView) {
            super(itemView);
            symbolTextView = itemView.findViewById(R.id.alert_symbol);
            conditionTextView = itemView.findViewById(R.id.alert_condition);
            cancelButton = itemView.findViewById(R.id.cancel_button);
            glow_container = itemView.findViewById(R.id.glow_container);
        }
    }
}
