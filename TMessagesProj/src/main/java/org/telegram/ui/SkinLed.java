package org.telegram.ui;

import android.os.SystemClock;

import org.telegram.skin.motion.SkinMotion;

/**
 * A status light's glow over time, for views that draw their own LEDs. On, it
 * strikes and blooms past full before it settles; off, most of the light goes
 * at once and an afterglow lingers. A change mid-way carries on from the glow
 * there is. Drawing code tells it each frame what the light should show and
 * redraws while {@link #isMoving()}; it allocates nothing.
 */
public final class SkinLed {

    private boolean lit;
    private long since;
    private float from;
    private boolean still = true;

    /** Sets what the light shows; with [animate] false, or motion reduced, it is there at once. */
    public void show(boolean lit, boolean animate) {
        if (lit == this.lit) {
            return;
        }
        final long now = SystemClock.uptimeMillis();
        from = glow(now);
        this.lit = lit;
        since = now;
        still = !animate || SkinMotion.reduced();
    }

    /** 0 off, 1 lit, above 1 while blooming. */
    public float glow() {
        return glow(SystemClock.uptimeMillis());
    }

    public boolean isMoving() {
        if (still) {
            return false;
        }
        final long t = SystemClock.uptimeMillis() - since;
        return lit ? t < SkinMotion.LED_ON.getSettleMs() : t < SkinMotion.LED_OFF_MS;
    }

    private float glow(long now) {
        if (still) {
            return lit ? 1f : 0f;
        }
        final float t = now - since;
        if (lit) {
            return from + (1f - from) * SkinMotion.springAt(SkinMotion.LED_ON, t);
        }
        final float f = Math.min(1f, t / SkinMotion.LED_OFF_MS);
        return from * (1f - SkinMotion.LED_OFF.getInterpolator().getInterpolation(f));
    }
}
