package animations;

import android.view.View;

import androidx.core.widget.NestedScrollView;

public class MotionAnimation {

    static boolean isSearchExpanded = false;

    public static void animateSearchState(boolean expand, View one, View two) {
        float headerAlpha = expand ? 0f : 1f;
        float translation = expand ? -one.getHeight() : 0f;

        one.animate()
                .alpha(headerAlpha)
                .translationY(translation)
                .setDuration(300)
                .start();

        two.animate()
                .translationY(translation)
                .setDuration(300)
                .start();

        isSearchExpanded = expand;
    }

    public static void animateSmoothScrollToTop(NestedScrollView scrollView){
        scrollView.smoothScrollTo(0, 0);
    }

    public static void animateSmoothScrollToBottom(NestedScrollView scrollView){
        scrollView.smoothScrollTo(0, scrollView.getChildAt(0).getHeight());
    }
}
