package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;

import androidx.annotation.NonNull;

import org.telegram.skin.theme.CanvasMaterials;

/**
 * The skin's chat ground: the page colour with the faint crazing of celadon
 * glaze over it. It stays a ColorDrawable, so Telegram still reads it as a
 * plain colour wallpaper (service bubbles, previews), and only the theme's
 * own wallpaper becomes it: a chat or theme with a picture keeps that.
 */
public class SkinCrackleWallpaper extends ColorDrawable {

    /** The crazing, drawn on a 390 x 720 sheet and scaled to cover the screen like a sliced background. */
    private static final float SHEET_WIDTH = 390;
    private static final float SHEET_HEIGHT = 720;
    private static final float[][] CRACKS = {
            {0, 60, 70, 48, 120, 90, 190, 70, 260, 110, 330, 92, 390, 120},
            {120, 90, 110, 170, 60, 210, 0, 200},
            {190, 70, 210, 0},
            {260, 110, 250, 190, 300, 240, 390, 230},
            {110, 170, 180, 200, 250, 190},
            {180, 200, 170, 290, 100, 320, 40, 300, 0, 330},
            {170, 290, 240, 320, 300, 240},
            {240, 320, 230, 410, 300, 450, 390, 430},
            {100, 320, 90, 410, 30, 450, 0, 440},
            {90, 410, 160, 440, 230, 410},
            {160, 440, 150, 530, 80, 560, 0, 550},
            {150, 530, 220, 560, 300, 520, 300, 450},
            {300, 520, 390, 540},
            {220, 560, 210, 650, 140, 690, 60, 670, 0, 700},
            {210, 650, 290, 680, 350, 640, 390, 650},
            {80, 560, 70, 650, 60, 670},
            {350, 640, 340, 560, 300, 520},
            {60, 210, 40, 300},
    };

    private final Paint crackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path sheet = new Path();
    private final Path scaled = new Path();
    private final Matrix matrix = new Matrix();
    private final Rect laidOut = new Rect();
    private final int crackAlpha;

    public SkinCrackleWallpaper(int color) {
        super(color);
        for (float[] run : CRACKS) {
            sheet.moveTo(run[0], run[1]);
            for (int i = 2; i < run.length; i += 2) {
                sheet.lineTo(run[i], run[i + 1]);
            }
        }
        final CanvasMaterials m = SkinMetal.materials(color);
        crackPaint.setStyle(Paint.Style.STROKE);
        crackPaint.setStrokeJoin(Paint.Join.ROUND);
        crackPaint.setStrokeWidth(dpf2(1));
        crackPaint.setColor(m.ink);
        crackAlpha = Math.round(255 * (m.dark ? 0.07f : 0.075f));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        super.draw(canvas);
        final Rect b = getBounds();
        if (b.isEmpty()) {
            return;
        }
        if (!laidOut.equals(b)) {
            laidOut.set(b);
            // Cover the bounds, centred, cropping whichever side overflows.
            final float scale = Math.max(b.width() / SHEET_WIDTH, b.height() / SHEET_HEIGHT);
            matrix.setScale(scale, scale);
            matrix.postTranslate(b.left + (b.width() - SHEET_WIDTH * scale) / 2f, b.top + (b.height() - SHEET_HEIGHT * scale) / 2f);
            sheet.transform(matrix, scaled);
        }
        // The crazing fades with the ground when Telegram fades the wallpaper.
        crackPaint.setAlpha(crackAlpha * getAlpha() / 255);
        canvas.save();
        canvas.clipRect(b);
        canvas.drawPath(scaled, crackPaint);
        canvas.restore();
    }
}
