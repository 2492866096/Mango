package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.view.View;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.skin.motion.MotionSpring;
import org.telegram.skin.motion.SkinHaptics;
import org.telegram.skin.motion.SkinMotion;

import java.util.WeakHashMap;

/**
 * A list row picked up to be moved: it rises off the list toward the finger,
 * a little larger and casting a shadow, and seats back into its slot with a
 * hint of a click when let go. A drop during the lift carries on from where
 * the row is.
 */
public final class SkinLift {

    private static final float SCALE = 1.02f;
    private static final float HEIGHT_DP = 6;

    /** The rows lifted and not yet down, with their springs: scale x, scale y, height. */
    private static final WeakHashMap<View, SpringAnimation[]> lifted = new WeakHashMap<>();
    /** A row lifted with motion reduced has no springs. */
    private static final SpringAnimation[] STILL = new SpringAnimation[0];

    private SkinLift() {
    }

    public static void lift(View row) {
        SkinHaptics.confirm(row);
        move(row, SkinMotion.ENTER, SCALE, dpf2(HEIGHT_DP));
    }

    /** Lets go of [row] if it was lifted; returns whether it was. */
    public static boolean drop(View row) {
        if (!lifted.containsKey(row)) {
            return false;
        }
        SkinHaptics.tap(row);
        move(row, SkinMotion.SEAT, 1f, 0f);
        return true;
    }

    private static void move(View row, MotionSpring spring, float scale, float height) {
        SpringAnimation[] springs = lifted.get(row);
        if (SkinMotion.reduced()) {
            if (springs != null) {
                for (SpringAnimation s : springs) {
                    s.cancel();
                }
            }
            row.setScaleX(scale);
            row.setScaleY(scale);
            row.setTranslationZ(height);
            if (height == 0f) {
                lifted.remove(row);
            } else {
                lifted.put(row, STILL);
            }
            return;
        }
        if (springs == null || springs.length == 0) {
            springs = new SpringAnimation[]{
                    springOf(row, DynamicAnimation.SCALE_X, DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE),
                    springOf(row, DynamicAnimation.SCALE_Y, DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE),
                    springOf(row, DynamicAnimation.TRANSLATION_Z, DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS),
            };
            lifted.put(row, springs);
            // Once a dropped row is down, it no longer needs its springs.
            springs[2].addEndListener((animation, canceled, value, velocity) -> {
                if (!canceled && value == 0f) {
                    lifted.remove(row);
                }
            });
        }
        springs[0].getSpring().setStiffness(spring.getStiffness()).setDampingRatio(spring.getDamping());
        springs[1].getSpring().setStiffness(spring.getStiffness()).setDampingRatio(spring.getDamping());
        springs[2].getSpring().setStiffness(spring.getStiffness()).setDampingRatio(spring.getDamping());
        springs[0].animateToFinalPosition(scale);
        springs[1].animateToFinalPosition(scale);
        springs[2].animateToFinalPosition(height);
    }

    private static SpringAnimation springOf(View row, DynamicAnimation.ViewProperty property, float visible) {
        final SpringAnimation animation = new SpringAnimation(row, property);
        animation.setSpring(new SpringForce());
        animation.setMinimumVisibleChange(visible);
        return animation;
    }
}
