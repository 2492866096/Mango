package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.skin.theme.CanvasMaterials;

/**
 * A Telegram menu restyled as a slab of the skin's keys: the aluminium slab
 * it sits on, and the celadon porcelain set into the key under the finger,
 * its LED lit. The menu keeps its own rows, touch and keyboard handling.
 */
public final class SkinKeys {

    /** How far the slab sits inside a Telegram popup's 8dp margin, leaving room for its shadow. */
    private static final float SLAB_INSET = 4;
    private static final float SLAB_RADIUS = 22;
    /** Concentric with the slab: its radius less the gap between the slab's edge and the rows. */
    private static final float INLAY_RADIUS = SLAB_RADIUS - (8 - SLAB_INSET);

    private SkinKeys() {
    }

    public static Drawable slab(CanvasMaterials m) {
        return new Slab(m);
    }

    /** A row's background: nothing at rest, the porcelain inlay while pressed or selected. */
    public static Drawable key(CanvasMaterials m) {
        final StateListDrawable keys = new StateListDrawable();
        final Drawable inlay = new Inlay(m);
        keys.addState(new int[]{android.R.attr.state_pressed}, inlay);
        keys.addState(new int[]{android.R.attr.state_selected}, inlay);
        keys.addState(new int[0], new ColorDrawable(Color.TRANSPARENT));
        return keys;
    }

    /** A search field's ground: a well sunk into the metal, corner radius [radiusDp]. */
    public static Drawable well(CanvasMaterials m, float radiusDp) {
        return new Well(m, radiusDp);
    }

    private abstract static class Base extends Drawable {
        final CanvasMaterials m;
        int alpha = 255;

        Base(CanvasMaterials m) {
            this.m = m;
        }

        @Override
        public void setAlpha(int alpha) {
            this.alpha = alpha;
            invalidateSelf();
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private static final class Slab extends Base {
        private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path clip = new Path();
        private float sheenTop = Float.NaN;
        private float sheenBottom = Float.NaN;

        Slab(CanvasMaterials m) {
            super(m);
            shadowPaint.setColor(m.sheenMid);
            edgePaint.setStyle(Paint.Style.STROKE);
        }

        @Override
        public boolean getPadding(@NonNull Rect padding) {
            // The popup lays its rows out with its own 8dp padding.
            padding.set(0, 0, 0, 0);
            return false;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            final Rect b = getBounds();
            if (b.isEmpty()) {
                return;
            }
            final float inset = dpf2(SLAB_INSET);
            final float radius = dpf2(SLAB_RADIUS);
            rect.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
            final float a = alpha / 255f;

            if (alpha == 255) {
                shadowPaint.setShadowLayer(dpf2(4), 0, dpf2(1.5f), m.castShadow);
                canvas.drawRoundRect(rect, radius, radius, shadowPaint);
            }
            if (sheenTop != rect.top || sheenBottom != rect.bottom) {
                sheenTop = rect.top;
                sheenBottom = rect.bottom;
                sheenPaint.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                        new int[]{m.sheenTop, m.sheenMid, m.sheenBottom}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            }
            sheenPaint.setAlpha(alpha);
            canvas.drawRoundRect(rect, radius, radius, sheenPaint);

            clip.rewind();
            clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clip);
            final float px = dpf2(1);
            edgePaint.setStrokeWidth(px);
            edgePaint.setColor(m.highlight);
            edgePaint.setAlpha((int) (Color.alpha(m.highlight) * a));
            canvas.drawLine(rect.left, rect.top + px / 2, rect.right, rect.top + px / 2, edgePaint);
            edgePaint.setColor(m.grooveCut);
            edgePaint.setAlpha((int) (Color.alpha(m.grooveCut) * a));
            canvas.drawLine(rect.left, rect.bottom - px / 2, rect.right, rect.bottom - px / 2, edgePaint);
            canvas.restore();
        }
    }

    private static final class Well extends Base {
        private final RectF rect = new RectF();
        private final float radiusDp;

        Well(CanvasMaterials m, float radiusDp) {
            super(m);
            this.radiusDp = radiusDp;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            rect.set(getBounds());
            SkinMetal.drawWell(canvas, rect, Math.min(dpf2(radiusDp), rect.height() / 2f), m, alpha / 255f);
        }
    }

    private static final class Inlay extends Base {
        private final RectF rect = new RectF();

        Inlay(CanvasMaterials m) {
            super(m);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            final Rect b = getBounds();
            if (b.isEmpty()) {
                return;
            }
            final float a = alpha / 255f;
            rect.set(b);
            SkinMetal.drawPorcelain(canvas, rect, Math.min(dpf2(INLAY_RADIUS), rect.height() / 2f), m, true, a);
            final float led = dpf2(3);
            SkinStyle.drawLed(canvas, rect.right - dpf2(12) - led, rect.centerY(), led, true, m.led, m.textTertiary, a);
        }
    }
}
