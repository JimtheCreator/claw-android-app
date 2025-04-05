package animations;

import android.content.Context;
import android.graphics.Canvas;
import android.widget.EdgeEffect;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * A custom {@link RecyclerView.EdgeEffectFactory} that modifies the overscroll behavior
 * of a {@link RecyclerView} to create a more pronounced, bouncy iOS-like effect.
 * <p>
 * It overrides the default edge effects to:
 * <ul>
 *     <li>Amplify the pull distance for more bounce</li>
 *     <li>Slow down the absorb animation on fling</li>
 *     <li>Force redraws on pull and release to improve animation smoothness</li>
 * </ul>
 */
public class BounceEdgeEffectFactory extends RecyclerView.EdgeEffectFactory {
    private final Context context;

    /**
     * Constructs a new instance of {@code BounceEdgeEffectFactory}.
     *
     * @param context the context used to initialize EdgeEffect
     */
    public BounceEdgeEffectFactory(Context context) {
        this.context = context;
    }

    /**
     * Creates and returns a custom {@link EdgeEffect} with enhanced bounce behavior.
     *
     * @param view      the RecyclerView this edge effect is attached to
     * @param direction the scroll direction (e.g., {@link RecyclerView.EdgeEffectFactory#DIRECTION_TOP})
     * @return a custom {@link EdgeEffect} instance with bounce modifications
     */
    @NonNull
    @Override
    protected EdgeEffect createEdgeEffect(@NonNull RecyclerView view, int direction) {
        return new EdgeEffect(context) {
            @Override
            public void onPull(float deltaDistance, float displacement) {
                // Amplify the pull distance to increase bounce
                super.onPull(deltaDistance * 3, displacement);
                view.invalidate(); // Request a redraw
            }

            @Override
            public void onRelease() {
                super.onRelease();
                view.invalidate(); // Redraw when the finger is released
            }

            @Override
            public void onAbsorb(int velocity) {
                // Reduce absorb velocity for a softer effect on fling
                super.onAbsorb(velocity / 2);
            }

            @Override
            public boolean draw(Canvas canvas) {
                return super.draw(canvas); // Default drawing behavior
            }
        };
    }
}
