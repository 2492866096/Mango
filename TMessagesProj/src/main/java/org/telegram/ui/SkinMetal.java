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
 * sits in, the engraved lines between them and the celadon porcelain set into
 * the metal. Measured to match the Compose dock, so the chat screen's input
 * console and the dock read as one object.
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
    private static final Paint glazePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint glazeRecessPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint cracklePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path porcelainPath = new Path();
    private static final Path cracklePath = new Path();

    /** Celadon crazing, sketched on a 74 x 52 tile and stretched to fit; the same runs as the Compose porcelain. */
    private static final float[][] CRACKS = {
            {8f, 14f, 22f, 20f, 30f, 9f, 44f, 17f, 58f, 11f, 68f, 19f},
            {22f, 20f, 20f, 34f, 34f, 40f, 44f, 17f},
            {34f, 40f, 52f, 36f, 58f, 46f},
            {52f, 36f, 58f, 11f},
    };

    private static CanvasMaterials knurlMaterials;
    private static CanvasMaterials capMaterials;
    private static float capRadius;
    private static CanvasMaterials recessMaterials;
    private static CanvasMaterials capsuleMaterials;
    private static float capsuleTop;
    private static float capsuleBottom;
    private static CanvasMaterials glazeMaterials;
    private static float glazeTop;
    private static float glazeBottom;
    private static CanvasMaterials glazeRecessMaterials;
    private static float crackleWidth;
    private static float crackleHeight;

    static {
        linePaint.setStyle(Paint.Style.STROKE);
        cracklePaint.setStyle(Paint.Style.STROKE);
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
        drawKnob(canvas, cx, cy, r, lit, 0, m, alpha);
    }

    /**
     * The knob held down: as above, with a celadon rim [rim] wide lit round its
     * edge under the LED. A rim of 0 draws none.
     */
    public static void drawKnob(Canvas canvas, float cx, float cy, float r, boolean lit, float rim, CanvasMaterials m, float alpha) {
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
        if (rim > 0) {
            linePaint.setStrokeWidth(rim);
            linePaint.setColor(m.led);
            linePaint.setAlpha((int) (Color.alpha(m.led) * alpha));
            canvas.drawCircle(0, 0, r - rim / 2, linePaint);
        }
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

    /**
     * Celadon glaze sunk into the metal, where something is "the current
     * one": the glaze deepening downwards, a recess shadow under its top edge,
     * a lit lower lip and, if [crackle] is set, the faint crazing of celadon.
     */
    public static void drawPorcelain(Canvas canvas, RectF bounds, float radius, CanvasMaterials m, boolean crackle, float alpha) {
        if (bounds.isEmpty()) {
            return;
        }
        if (glazeMaterials != m || glazeTop != bounds.top || glazeBottom != bounds.bottom) {
            glazeMaterials = m;
            glazeTop = bounds.top;
            glazeBottom = bounds.bottom;
            glazePaint.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
                    m.glazeTop, m.glazeBottom, Shader.TileMode.CLAMP));
        }
        glazePaint.setAlpha((int) (255 * alpha));
        canvas.drawRoundRect(bounds, radius, radius, glazePaint);

        porcelainPath.rewind();
        porcelainPath.addRoundRect(bounds, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(porcelainPath);
        canvas.translate(bounds.left, bounds.top);
        final float w = bounds.width();
        final float h = bounds.height();

        if (glazeRecessMaterials != m) {
            glazeRecessMaterials = m;
            glazeRecessPaint.setShader(new LinearGradient(0, 0, 0, dpf2(5),
                    m.porcelainRecess, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        }
        glazeRecessPaint.setAlpha((int) (255 * alpha));
        canvas.drawRect(0, 0, w, dpf2(5), glazeRecessPaint);

        final float px = dpf2(1);
        linePaint.setStrokeWidth(px);
        linePaint.setColor(m.porcelainLip);
        linePaint.setAlpha((int) (Color.alpha(m.porcelainLip) * alpha));
        canvas.drawLine(0, h - px / 2, w, h - px / 2, linePaint);

        if (crackle) {
            if (crackleWidth != w || crackleHeight != h) {
                crackleWidth = w;
                crackleHeight = h;
                final float sx = w / 74f;
                final float sy = h / 52f;
                cracklePath.rewind();
                for (float[] run : CRACKS) {
                    cracklePath.moveTo(run[0] * sx, run[1] * sy);
                    for (int i = 2; i < run.length; i += 2) {
                        cracklePath.lineTo(run[i] * sx, run[i + 1] * sy);
                    }
                }
            }
            // The line stays a hairline however far the tile is stretched.
            cracklePaint.setStrokeWidth(dpf2(0.8f));
            cracklePaint.setColor(m.crackle);
            cracklePaint.setAlpha((int) (Color.alpha(m.crackle) * alpha));
            canvas.drawPath(cracklePath, cracklePaint);
        }
        canvas.restore();
    }

    /** The outline of a plate engraved into the metal: one dark cut line. */
    public static void drawPlate(Canvas canvas, RectF bounds, float radius, CanvasMaterials m, float alpha) {
        final float px = dpf2(1);
        linePaint.setStrokeWidth(px);
        linePaint.setColor(m.grooveCut);
        linePaint.setAlpha((int) (Color.alpha(m.grooveCut) * alpha));
        canvas.drawRoundRect(bounds.left + px / 2, bounds.top + px / 2, bounds.right - px / 2, bounds.bottom - px / 2,
                radius - px / 2, radius - px / 2, linePaint);
    }

    /** An engraved line laid across: the dark cut on top, the lit lip under it. */
    public static void drawGrooveAcross(Canvas canvas, float left, float right, float y, CanvasMaterials m, float alpha) {
        final float px = dpf2(1);
        linePaint.setStrokeWidth(px);
        linePaint.setColor(m.grooveCut);
        linePaint.setAlpha((int) (Color.alpha(m.grooveCut) * alpha));
        canvas.drawLine(left, y + px / 2, right, y + px / 2, linePaint);
        linePaint.setColor(m.grooveLip);
        linePaint.setAlpha((int) (Color.alpha(m.grooveLip) * alpha));
        canvas.drawLine(left, y + px * 1.5f, right, y + px * 1.5f, linePaint);
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
