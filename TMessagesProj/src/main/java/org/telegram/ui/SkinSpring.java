package org.telegram.ui;

import android.view.View;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.skin.motion.MotionSpring;
import org.telegram.skin.motion.SkinMotion;

/**
 * A value a skin view draws from, moved by one of the skin's springs. A new
 * target picks up from where the value is and how fast it is moving, so a key
 * let go halfway down or a panel sent back mid-flight never jumps. When motion
 * is reduced it goes straight to the target. Main thread only.
 */
public final class SkinSpring {

    /** For values drawn as fractions (alpha, progress, depth): a change too small to see. */
    public static final float FRACTION = DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE;

    private final View view;
    private final FloatValueHolder holder;
    private final SpringAnimation animation;
    private final SpringForce force;

    /**
     * @param minimumVisibleChange the smallest change worth a frame, in the value's
     *                             own units: {@link #FRACTION} for 0..1, about one
     *                             pixel for positions.
     */
    public SkinSpring(View view, MotionSpring spring, float value, float minimumVisibleChange) {
        this.view = view;
        holder = new FloatValueHolder(value);
        force = new SpringForce(value);
        use(spring);
        animation = new SpringAnimation(holder).setSpring(force);
        animation.setMinimumVisibleChange(minimumVisibleChange);
        animation.addUpdateListener((a, v, velocity) -> view.invalidate());
    }

    public float get() {
        return holder.getValue();
    }

    public float getTarget() {
        return force.getFinalPosition();
    }

    /** Swaps the spring, e.g. a key's press for its release; a running motion carries on with the new one. */
    public SkinSpring use(MotionSpring spring) {
        force.setStiffness(spring.getStiffness());
        force.setDampingRatio(spring.getDamping());
        return this;
    }

    public void animateTo(float target) {
        if (SkinMotion.reduced()) {
            jumpTo(target);
        } else if (animation.isRunning() || holder.getValue() != target) {
            animation.animateToFinalPosition(target);
        }
    }

    public void jumpTo(float target) {
        animation.cancel();
        force.setFinalPosition(target);
        holder.setValue(target);
        view.invalidate();
    }

    public boolean isRunning() {
        return animation.isRunning();
    }
}
