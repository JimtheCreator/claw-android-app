package utils;

import androidx.recyclerview.widget.DiffUtil;

import java.util.List;

import models.PriceAlert;

public class PriceAlertsDiffCallback extends DiffUtil.Callback{
    private final List<PriceAlert> oldList;
    private final List<PriceAlert> newList;

    public PriceAlertsDiffCallback(List<PriceAlert> oldList, List<PriceAlert> newList) {
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
        PriceAlert oldSymbol = oldList.get(oldItemPosition);
        PriceAlert newSymbol = newList.get(newItemPosition);
        return oldSymbol.equals(newSymbol);
    }
}
