package space;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class SpacebetweenItems extends RecyclerView.ItemDecoration {
    private final int halfSpace;
    private final float dividerHeight;
    private final Paint dividerPaint;

    /**
     * @param verticalSpacing total gap (in px) between item contents
     * @param dividerHeight   thickness of the divider line (can be fractional, e.g. 1.5f)
     * @param dividerColor    color of the divider
     */
    public SpacebetweenItems(int verticalSpacing, double dividerHeight, int dividerColor) {
        // split total gap in two
        this.halfSpace = Math.round(verticalSpacing / 2f);
        this.dividerHeight = (float) dividerHeight;

        dividerPaint = new Paint();
        dividerPaint.setColor(dividerColor);
        dividerPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect,
                               @NonNull View view,
                               @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        // equal padding above and below each item
        outRect.top = halfSpace;
        outRect.bottom = halfSpace;
    }

    @Override
    public void onDrawOver(@NonNull Canvas c,
                           @NonNull RecyclerView parent,
                           @NonNull RecyclerView.State state) {
        int childCount = parent.getChildCount();
        for (int i = 0; i < childCount - 1; i++) {
            View child = parent.getChildAt(i);

            float left = child.getLeft();
            float right = child.getRight();
            // start drawing the divider in the middle of the gap
            float top = child.getBottom() + halfSpace - (dividerHeight / 2f);
            float bottom = top + dividerHeight;

            c.drawRect(left, top, right, bottom, dividerPaint);
        }
    }
}