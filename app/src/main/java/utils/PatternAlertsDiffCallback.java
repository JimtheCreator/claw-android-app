package utils;

import androidx.recyclerview.widget.DiffUtil;

import java.util.List;

import models.PatternAlert;
import models.PriceAlert;

public class PatternAlertsDiffCallback extends DiffUtil.Callback{
    private final List<PatternAlert> oldList;
    private final List<PatternAlert> newList;

    public PatternAlertsDiffCallback(List<PatternAlert> oldList, List<PatternAlert> newList) {
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
        PatternAlert oldSymbol = oldList.get(oldItemPosition);
        PatternAlert newSymbol = newList.get(newItemPosition);
        return oldSymbol.equals(newSymbol);
    }
}
