package adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.claw.ai.R;

import java.util.List;

import models.CachedSymbol;

public class CachedSymbolSearchAdapter extends RecyclerView.Adapter<CachedSymbolSearchAdapter.CachedSymbolViewHolder> {

    private List<CachedSymbol> symbols;
    private final OnSymbolClickListener listener;

    public interface OnSymbolClickListener {
        void onSymbolClick(CachedSymbol symbol);
    }

    public CachedSymbolSearchAdapter(List<CachedSymbol> symbols, OnSymbolClickListener listener) {
        this.symbols = symbols;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CachedSymbolViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_symbol_search, parent, false);
        return new CachedSymbolViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CachedSymbolViewHolder holder, int position) {
        CachedSymbol symbol = symbols.get(position);
        holder.bind(symbol, listener);
    }

    @Override
    public int getItemCount() {
        return Math.min(symbols.size(), 3 );
    }

    public void updateSymbols(List<CachedSymbol> newSymbols) {
        this.symbols = newSymbols;
        notifyDataSetChanged();
    }

    public static class CachedSymbolViewHolder extends RecyclerView.ViewHolder {
        private final TextView symbolNameTextView;

        public CachedSymbolViewHolder(@NonNull View itemView) {
            super(itemView);
            symbolNameTextView = itemView.findViewById(R.id.symbol_name_text_view);
        }

        public void bind(CachedSymbol symbol, OnSymbolClickListener listener) {
            symbolNameTextView.setText(symbol.symbol); // Adjust based on your CachedSymbol model

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSymbolClick(symbol);
                }
            });
        }
    }
}
