package utils;

import androidx.recyclerview.widget.DiffUtil;

import java.util.List;

import models.Symbol;

import androidx.recyclerview.widget.DiffUtil;
import java.util.List;
import java.util.Objects;

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
    public int getOldListSize() {
        return oldList != null ? oldList.size() : 0;
    }

    @Override
    public int getNewListSize() {
        return newList != null ? newList.size() : 0;
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        Symbol oldItem = oldList.get(oldItemPosition);
        Symbol newItem = newList.get(newItemPosition);
        return Objects.equals(oldItem.getSymbol(), newItem.getSymbol());
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        Symbol oldItem = oldList.get(oldItemPosition);
        Symbol newItem = newList.get(newItemPosition);

        return Objects.equals(oldItem.getAsset(), newItem.getAsset()) &&
                Objects.equals(oldItem.getSymbol(), newItem.getSymbol()) &&
                Objects.equals(oldItem.getBaseCurrency(), newItem.getBaseCurrency()) &&
                Double.compare(oldItem.getCurrentPrice(), newItem.getCurrentPrice()) == 0 &&
                Double.compare(oldItem.get_24hChange(), newItem.get_24hChange()) == 0 &&
                Double.compare(oldItem.get_24hVolume(), newItem.get_24hVolume()) == 0 &&
                Objects.equals(oldItem.getSparkline(), newItem.getSparkline());
    }
}


