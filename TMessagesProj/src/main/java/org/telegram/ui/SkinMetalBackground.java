package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;

import androidx.annotation.NonNull;

import org.telegram.skin.theme.CanvasMaterials;
import org.telegram.skin.theme.CanvasPalette;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSource;

/**
 * A machined-aluminium slab in place of Telegram's glass: the input console,
 * the floating buttons and panels of the chat screen. It keeps the glass
 * drawable's shape (bounds, padding, corner radii) so every caller lays out
 * exactly as before, and draws the skin's metal there: the floating shadow,
 * a vertical sheen, a lit top edge and a cut along the bottom.
 */
public class SkinMetalBackground extends BlurredBackgroundDrawable {

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private CanvasMaterials sheenMaterials;
    private int sheenTop;
    private int sheenBottom;

    public SkinMetalBackground() {
        setStrokeWidth(dpf2(1), dpf2(1));
    }

    @Override
    public BlurredBackgroundDrawable setColorProvider(BlurredBackgroundColorProvider colorProvider) {
        super.setColorProvider(colorProvider);
        // The provider sets the glass's edge widths; metal has its own one-pixel edge and cut.
        setStrokeWidth(dpf2(1), dpf2(1));
        return this;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final Rect bounds = boundProps.boundsWithPadding;
        if (bounds.isEmpty() || alpha == 0) {
            return;
        }
        // Without a provider there is no panel colour to read, so follow the app theme.
        final CanvasMaterials m = colorProvider != null
                ? SkinMetal.materials(backgroundColor)
                : CanvasPalette.of(Theme.isCurrentThemeDark());

        if (alpha == 255 && shadowAlpha > 0) {
            shadowPaint.setColor(m.sheenMid);
            for (int i = m.floatingBlur.length - 1; i >= 0; i--) {
                shadowPaint.setShadowLayer(dpf2(m.floatingBlur[i]), 0, dpf2(m.floatingDrop[i]),
                        Theme.multAlpha(m.floatingColor[i], shadowAlpha));
                boundProps.drawShadows(canvas, shadowPaint, inAppKeyboardOptimization);
            }
        }

        final boolean faded = alpha != 255;
        if (faded) {
            canvas.saveLayerAlpha(bounds.left, bounds.top, bounds.right, bounds.bottom, alpha);
        }
        if (sheenMaterials != m || sheenTop != bounds.top || sheenBottom != bounds.bottom) {
            sheenMaterials = m;
            sheenTop = bounds.top;
            sheenBottom = bounds.bottom;
            fillPaint.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
                    new int[]{m.sheenTop, m.sheenMid, m.sheenBottom}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        }
        boundProps.draw(canvas, fillPaint);
        edgePaint.setColor(m.highlight);
        canvas.drawPath(boundProps.strokePathTop, edgePaint);
        edgePaint.setColor(m.grooveCut);
        canvas.drawPath(boundProps.strokePathBottom, edgePaint);
        if (faded) {
            canvas.restore();
        }
    }

    @Override
    public BlurredBackgroundSource getSource() {
        // Metal shows nothing of what lies behind it.
        return null;
    }
}
