package adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.claw.ai.R;

import java.util.ArrayList;
import java.util.List;

import models.Pattern;

public class PatternSelectionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_PATTERN = 0;
    private static final int VIEW_TYPE_LOADING = 1;

    private final List<Pattern> patterns;
    private final OnPatternClickListener listener;
    private final OnLoadMoreListener loadMoreListener;
    private boolean isLoading = false;
    private boolean hasMoreData = true;

    public interface OnPatternClickListener {
        void onPatternClick(Pattern pattern);
    }

    public interface OnLoadMoreListener {
        void onLoadMore();
    }

    public PatternSelectionAdapter(List<Pattern> patterns, OnPatternClickListener listener, OnLoadMoreListener loadMoreListener) {
        this.patterns = patterns != null ? patterns : new ArrayList<>();
        this.listener = listener;
        this.loadMoreListener = loadMoreListener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_LOADING) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_loading, parent, false);
            return new LoadingViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_pattern_selection, parent, false);
            return new PatternViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof PatternViewHolder) {
            Pattern pattern = patterns.get(position);
            ((PatternViewHolder) holder).bind(pattern, listener);
        }
        // LoadingViewHolder doesn't need binding - scroll listener handles load more
    }

    @Override
    public int getItemCount() {
        return patterns.size() + (isLoading && hasMoreData ? 1 : 0);
    }

    @Override
    public int getItemViewType(int position) {
        if (position >= patterns.size() && isLoading) {
            return VIEW_TYPE_LOADING;
        }
        return VIEW_TYPE_PATTERN;
    }

    public void addPatterns(List<Pattern> newPatterns) {
        int startPosition = patterns.size();
        this.patterns.addAll(newPatterns);
        notifyItemRangeInserted(startPosition, newPatterns.size());
        isLoading = false;

        // Update hasMoreData based on whether we received a full page
        hasMoreData = newPatterns.size() == 20; // Assuming page size is 20
    }

    public void updatePatterns(List<Pattern> newPatterns) {
        this.patterns.clear();
        this.patterns.addAll(newPatterns != null ? newPatterns : new ArrayList<>());
        notifyDataSetChanged();
        isLoading = false;
        hasMoreData = true; // Reset for new search
    }

    public void setLoading(boolean loading) {
        boolean wasLoading = isLoading;
        isLoading = loading;

        if (wasLoading && !loading) {
            // Remove loading item
            notifyItemRemoved(patterns.size());
        } else if (!wasLoading && loading && hasMoreData) {
            // Add loading item
            notifyItemInserted(patterns.size());
        }
    }

    public void setHasMoreData(boolean hasMoreData) {
        this.hasMoreData = hasMoreData;
        if (!hasMoreData && isLoading) {
            isLoading = false;
            notifyItemRemoved(patterns.size());
        }
    }

    public static class PatternViewHolder extends RecyclerView.ViewHolder {
        private final TextView patternNameTextView;

        public PatternViewHolder(@NonNull View itemView) {
            super(itemView);
            patternNameTextView = itemView.findViewById(R.id.pattern_name_text_view);
        }

        public void bind(Pattern pattern, OnPatternClickListener listener) {
            patternNameTextView.setText(pattern.displayName);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPatternClick(pattern);
                }
            });
        }
    }

    public static class LoadingViewHolder extends RecyclerView.ViewHolder {
        public LoadingViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}