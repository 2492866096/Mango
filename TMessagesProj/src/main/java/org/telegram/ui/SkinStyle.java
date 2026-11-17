package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.skin.R;

/**
 * Drawing pieces for Telegram's own screens restyled to the skin, starting
 * with the chat list. Those screens keep their engine and draw on a Canvas,
 * so the skin's marks (the status light, the seal, the rounded-square
 * avatars, the two typefaces) are redrawn here to the same measurements as
 * the skin module's Compose versions.
 */
public final class SkinStyle {
    /** Rounded-square avatars: corner radius as a share of the side. */
    public static final float AVATAR_CORNER = 0.32f;

    private static Typeface serif;
    private static Typeface numeralsLight;
    private static Typeface numerals;

    private static final Paint ledPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final TextPaint sealTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF sealRect = new RectF();
    private static int glowColor;
    private static float glowRadius;

    static {
        ringPaint.setStyle(Paint.Style.STROKE);
    }

    private SkinStyle() {
    }

    /** The chat list draws in the skin's style. */
    public static boolean chatList() {
        return SkinSwitch.CHAT_LIST;
    }

    public static int avatarRadius(int size) {
        return Math.round(size * AVATAR_CORNER);
    }

    /** The serif of the skin's titles (Noto Serif SC Bold, shipped as a subset). */
    public static Typeface serif() {
        if (serif == null) {
            serif = load(R.font.noto_serif_sc_bold, AndroidUtilities.bold());
        }
        return serif;
    }

    /** Outfit, the skin's face for figures: light for counts, regular for times. */
    public static Typeface numerals(boolean light) {
        if (light) {
            if (numeralsLight == null) numeralsLight = load(R.font.outfit_light, Typeface.DEFAULT);
            return numeralsLight;
        }
        if (numerals == null) numerals = load(R.font.outfit_regular, Typeface.DEFAULT);
        return numerals;
    }

    /** Sets a paint up for figures that may stack in a column: Outfit with tabular digits. */
    public static void useNumerals(Paint paint, boolean light) {
        paint.setTypeface(numerals(light));
        paint.setFontFeatureSettings("tnum");
    }

    private static Typeface load(int font, Typeface fallback) {
        try {
            Typeface typeface = ResourcesCompat.getFont(ApplicationLoader.applicationContext, font);
            if (typeface != null) return typeface;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return fallback;
    }

    /**
     * The status light. Lit, it is a celadon dot with a soft glow; unlit
     * (a muted chat), it is an empty ring, so the two differ in shape and
     * not only in colour.
     */
    public static void drawLed(Canvas canvas, float cx, float cy, boolean lit, int litColor, int unlitColor, float alpha) {
        drawLed(canvas, cx, cy, dpf2(4), lit, litColor, unlitColor, alpha);
    }

    /**
     * The status light part way through a change, as {@link SkinLed} reports it:
     * [glow] 0 is off and 1 lit, above 1 the bloom of striking. The unlit ring
     * shows at [ring] while the light is off and gives way as it comes on.
     */
    public static void drawLed(Canvas canvas, float cx, float cy, float r, float glow, float ring, int litColor, int unlitColor, float alpha) {
        final float lit = Math.max(0f, Math.min(1f, glow));
        if (lit < 1f && ring > 0f) {
            drawLed(canvas, cx, cy, r, false, litColor, unlitColor, alpha * ring * (1f - lit));
        }
        if (lit > 0f) {
            final float bloom = Math.max(0f, Math.min(1f, (glow - 1f) / 0.2f));
            // The halo grows by scaling the canvas, so its gradient is not rebuilt every frame.
            final float spread = 1f + 0.35f * bloom;
            canvas.save();
            canvas.scale(spread, spread, cx, cy);
            drawHalo(canvas, cx, cy, r, litColor, Math.min(1f, alpha * lit * (1f + 0.5f * bloom)));
            canvas.restore();
            ledPaint.setColor(litColor);
            ledPaint.setAlpha((int) (255 * alpha * lit));
            canvas.drawCircle(cx, cy, r, ledPaint);
        }
    }

    /** The status light at radius [r]; the knob's rim light is smaller than the list's. */
    public static void drawLed(Canvas canvas, float cx, float cy, float r, boolean lit, int litColor, int unlitColor, float alpha) {
        if (lit) {
            drawHalo(canvas, cx, cy, r, litColor, alpha);
            ledPaint.setColor(litColor);
            ledPaint.setAlpha((int) (255 * alpha));
            canvas.drawCircle(cx, cy, r, ledPaint);
        } else {
            final float stroke = dpf2(1.5f);
            ringPaint.setStrokeWidth(stroke);
            ringPaint.setColor(unlitColor);
            ringPaint.setAlpha((int) (Color.alpha(unlitColor) * alpha));
            canvas.drawCircle(cx, cy, r - stroke / 2f, ringPaint);
        }
    }

    private static void drawHalo(Canvas canvas, float cx, float cy, float r, int litColor, float alpha) {
        final float glow = r * 3f;
        if (glowColor != litColor || glowRadius != glow) {
            glowColor = litColor;
            glowRadius = glow;
            glowPaint.setShader(new RadialGradient(0, 0, glow,
                    new int[]{ColorUtils.setAlphaComponent(litColor, 0x66), ColorUtils.setAlphaComponent(litColor, 0x22), Color.TRANSPARENT},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        }
        glowPaint.setAlpha((int) (255 * alpha));
        canvas.save();
        canvas.translate(cx, cy);
        canvas.drawCircle(0, 0, glow, glowPaint);
        canvas.restore();
    }

    /**
     * A seal: an outlined square stamped a few degrees off straight, holding
     * one serif character ("@" for a mention). Drawn centred in [bounds].
     */
    public static void drawSeal(Canvas canvas, RectF bounds, CharSequence glyph, Drawable icon, int color, float alpha) {
        final float side = dpf2(20);
        final float stroke = dpf2(1.5f);
        sealRect.set(bounds.centerX() - side / 2f, bounds.centerY() - side / 2f, bounds.centerX() + side / 2f, bounds.centerY() + side / 2f);
        sealRect.inset(stroke / 2f, stroke / 2f);
        canvas.save();
        canvas.rotate(-6, bounds.centerX(), bounds.centerY());
        ringPaint.setStrokeWidth(stroke);
        ringPaint.setColor(color);
        ringPaint.setAlpha((int) (Color.alpha(color) * alpha));
        canvas.drawRoundRect(sealRect, dpf2(4), dpf2(4), ringPaint);
        if (glyph != null) {
            sealTextPaint.setTypeface(serif());
            sealTextPaint.setTextSize(dp(12));
            sealTextPaint.setColor(color);
            sealTextPaint.setAlpha((int) (Color.alpha(color) * alpha));
            sealTextPaint.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics metrics = sealTextPaint.getFontMetrics();
            float baseline = bounds.centerY() - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(glyph, 0, glyph.length(), bounds.centerX(), baseline, sealTextPaint);
        } else if (icon != null) {
            final int size = dp(14);
            icon.setBounds((int) (bounds.centerX() - size / 2f), (int) (bounds.centerY() - size / 2f), (int) (bounds.centerX() + size / 2f), (int) (bounds.centerY() + size / 2f));
            icon.setAlpha((int) (255 * alpha));
            icon.draw(canvas);
        }
        canvas.restore();
    }

    /** A span that sets text in a given typeface, for mixing the serif and the figures in one line. */
    public static final class TypefaceSpan extends MetricAffectingSpan {
        private final Typeface typeface;
        private final String features;

        public TypefaceSpan(Typeface typeface, String features) {
            this.typeface = typeface;
            this.features = features;
        }

        @Override
        public void updateMeasureState(@NonNull TextPaint paint) {
            apply(paint);
        }

        @Override
        public void updateDrawState(TextPaint paint) {
            apply(paint);
        }

        private void apply(TextPaint paint) {
            paint.setTypeface(typeface);
            if (features != null) paint.setFontFeatureSettings(features);
        }
    }
}
