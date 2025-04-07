package utils;

import androidx.recyclerview.widget.DiffUtil;

import java.util.List;

import models.Symbol;

public class CryptoDiffUtil extends DiffUtil.Callback {
    private final List<Symbol> oldList;
    private final List<Symbol> newList;

    public CryptoDiffUtil(List<Symbol> oldList, List<Symbol> newList) {
        this.oldList = oldList;
        this.newList = newList;
    }

    @Override
    public int getOldListSize() { return oldList.size(); }

    @Override
    public int getNewListSize() { return newList.size(); }

    @Override
    public boolean areItemsTheSame(int oldPos, int newPos) {
        return oldList.get(oldPos).getSymbol().equals(newList.get(newPos).getSymbol());
    }

    @Override
    public boolean areContentsTheSame(int oldPos, int newPos) {
        return oldList.get(oldPos).equals(newList.get(newPos));
    }
}
