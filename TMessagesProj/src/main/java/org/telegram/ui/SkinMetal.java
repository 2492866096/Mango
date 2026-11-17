package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;

import androidx.core.graphics.ColorUtils;

import org.telegram.skin.theme.CanvasMaterials;
import org.telegram.skin.theme.CanvasPalette;

/**
 * The skin's machined aluminium drawn on a Canvas, for Telegram views that
 * keep their own drawing: the knurled knob, the sunken well a text field
 * sits in, and the engraved line between them. Measured to match the Compose
 * dock, so the chat screen's input console and the dock read as one object.
 */
public final class SkinMetal {

    private static final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint knurlPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint capPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint capsulePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint wellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint recessPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path wellPath = new Path();

    private static CanvasMaterials knurlMaterials;
    private static CanvasMaterials capMaterials;
    private static float capRadius;
    private static CanvasMaterials recessMaterials;
    private static CanvasMaterials capsuleMaterials;
    private static float capsuleTop;
    private static float capsuleBottom;

    static {
        linePaint.setStyle(Paint.Style.STROKE);
    }

    private SkinMetal() {
    }

    /** Whether a screen whose panels are [surfaceColor] is dark. Chats may carry their own theme, so this reads the colour rather than the app setting. */
    public static boolean isDark(int surfaceColor) {
        return ColorUtils.calculateLuminance(surfaceColor) < 0.4;
    }

    public static CanvasMaterials materials(int surfaceColor) {
        return CanvasPalette.of(isDark(surfaceColor));
    }

    /**
     * The knurled knob, centred on (cx, cy) with outer radius [r]: a ring of
     * 72 slivers, a domed cap lit from the upper left, and an LED on the rim
     * that glows when [lit] (a tap would send something) and is an empty ring
     * otherwise.
     */
    public static void drawKnob(Canvas canvas, float cx, float cy, float r, boolean lit, CanvasMaterials m, float alpha) {
        final int a = (int) (255 * alpha);
        canvas.save();
        canvas.translate(cx, cy);
        if (alpha >= 1f) {
            shadowPaint.setColor(m.knurlB);
            shadowPaint.setShadowLayer(dpf2(4), 0, dpf2(2), m.castShadow);
            canvas.drawCircle(0, 0, r, shadowPaint);
        }
        if (knurlMaterials != m) {
            knurlMaterials = m;
            final int[] colors = new int[144];
            for (int i = 0; i < colors.length; i++) {
                colors[i] = (i / 2) % 2 == 0 ? m.knurlA : m.knurlB;
            }
            knurlPaint.setShader(new SweepGradient(0, 0, colors, null));
        }
        knurlPaint.setAlpha(a);
        canvas.drawCircle(0, 0, r, knurlPaint);

        final float cap = r * 0.78f;
        if (capMaterials != m || capRadius != cap) {
            capMaterials = m;
            capRadius = cap;
            capPaint.setShader(new RadialGradient(-0.3f * cap, -0.4f * cap, 2 * cap,
                    new int[]{m.capLit, m.capMid, m.capShade}, null, Shader.TileMode.CLAMP));
        }
        capPaint.setAlpha(a);
        canvas.drawCircle(0, 0, cap, capPaint);
        linePaint.setStrokeWidth(dpf2(1));
        linePaint.setColor(m.highlight);
        linePaint.setAlpha((int) (Color.alpha(m.highlight) * alpha));
        canvas.drawCircle(0, 0, cap - dpf2(0.5f), linePaint);
        canvas.restore();

        final float led = dpf2(2.5f);
        SkinStyle.drawLed(canvas, cx + r - 0.24f * r - led, cy - r + dpf2(2) + led, led, lit, m.led, m.textTertiary, alpha);
    }

    /**
     * The knob stretched into a capsule, for a key that has to show a price
     * beside its glyph: the same domed cap metal, the LED at the far end.
     */
    public static void drawCapsule(Canvas canvas, RectF bounds, boolean lit, CanvasMaterials m, float alpha) {
        final float r = bounds.height() / 2f;
        if (alpha >= 1f) {
            shadowPaint.setColor(m.knurlB);
            shadowPaint.setShadowLayer(dpf2(4), 0, dpf2(2), m.castShadow);
            canvas.drawRoundRect(bounds, r, r, shadowPaint);
        }
        if (capsuleMaterials != m || capsuleTop != bounds.top || capsuleBottom != bounds.bottom) {
            capsuleMaterials = m;
            capsuleTop = bounds.top;
            capsuleBottom = bounds.bottom;
            capsulePaint.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
                    new int[]{m.capLit, m.capMid, m.capShade}, null, Shader.TileMode.CLAMP));
        }
        capsulePaint.setAlpha((int) (255 * alpha));
        canvas.drawRoundRect(bounds, r, r, capsulePaint);
        final float inset = dpf2(0.5f);
        linePaint.setStrokeWidth(dpf2(1));
        linePaint.setColor(m.highlight);
        linePaint.setAlpha((int) (Color.alpha(m.highlight) * alpha));
        canvas.drawRoundRect(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset, r - inset, r - inset, linePaint);

        final float led = dpf2(2.5f);
        SkinStyle.drawLed(canvas, bounds.right - 0.24f * r - led, bounds.top + dpf2(2) + led, led, lit, m.led, m.textTertiary, alpha);
    }

    /**
     * The sunken well a text field sits in: the field colour with a recess
     * shadow falling from its top edge, as if cut into the metal.
     */
    public static void drawWell(Canvas canvas, RectF bounds, float radius, CanvasMaterials m, float alpha) {
        if (bounds.isEmpty()) {
            return;
        }
        wellPaint.setColor(m.field);
        wellPaint.setAlpha((int) (Color.alpha(m.field) * alpha));
        canvas.drawRoundRect(bounds, radius, radius, wellPaint);

        if (recessMaterials != m) {
            recessMaterials = m;
            recessPaint.setShader(new LinearGradient(0, 0, 0, dpf2(5),
                    m.castShadow, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        }
        recessPaint.setAlpha((int) (255 * alpha));
        wellPath.rewind();
        wellPath.addRoundRect(bounds, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(wellPath);
        canvas.translate(0, bounds.top);
        canvas.drawRect(bounds.left, 0, bounds.right, dpf2(5), recessPaint);
        canvas.restore();
    }

    /** An engraved upright line: a dark cut with a lit lip beside it. */
    public static void drawGroove(Canvas canvas, float x, float top, float bottom, CanvasMaterials m, float alpha) {
        final float px = dpf2(1);
        linePaint.setStrokeWidth(px);
        linePaint.setColor(m.grooveCut);
        linePaint.setAlpha((int) (Color.alpha(m.grooveCut) * alpha));
        canvas.drawLine(x + px / 2, top, x + px / 2, bottom, linePaint);
        linePaint.setColor(m.grooveLip);
        linePaint.setAlpha((int) (Color.alpha(m.grooveLip) * alpha));
        canvas.drawLine(x + px * 1.5f, top, x + px * 1.5f, bottom, linePaint);
    }
}
