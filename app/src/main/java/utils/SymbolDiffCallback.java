package utils;

import androidx.recyclerview.widget.DiffUtil;

import java.util.List;

import models.Symbol;

/**
 * Efficiently compares two lists of Symbol items for RecyclerView updates.
 */
public class SymbolDiffCallback extends DiffUtil.Callback {
    private final List<Symbol> oldList;
    private final List<Symbol> newList;

    public SymbolDiffCallback(List<Symbol> oldList, List<Symbol> newList) {
        this.oldList = oldList;
        this.newList = newList;
    }

    @Override
    public int getOldListSize() { return oldList.size(); }
    @Override
    public int getNewListSize() { return newList.size(); }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        return oldList.get(oldItemPosition).getSymbol().equals(newList.get(newItemPosition).getSymbol());
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        Symbol oldSymbol = oldList.get(oldItemPosition);
        Symbol newSymbol = newList.get(newItemPosition);
        return oldSymbol.equals(newSymbol); // Relies on Symbol.equals including isInWatchlist
    }
}