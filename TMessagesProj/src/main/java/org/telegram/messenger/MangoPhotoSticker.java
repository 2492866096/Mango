package org.telegram.messenger;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Produces a real static sticker: transparent 512px WebP, limited to 512 KiB. */
public final class MangoPhotoSticker {
    private MangoPhotoSticker() {}

    public static String convert(String path) throws IOException {
        Bitmap image = ImageLoader.loadBitmap(path, null, 512, 512, true);
        if (image == null) throw new IOException("Cannot decode photo");
        Bitmap sticker = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
        float scale = Math.min(512f / image.getWidth(), 512f / image.getHeight());
        float width = image.getWidth() * scale, height = image.getHeight() * scale;
        new Canvas(sticker).drawBitmap(image, null, new RectF((512 - width) / 2, (512 - height) / 2,
                (512 + width) / 2, (512 + height) / 2), new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        image.recycle();
        File file = File.createTempFile("mango-sticker-", ".webp", FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE));
        boolean complete = false;
        try {
            for (int quality = 95; quality >= 35; quality -= 15) {
                try (FileOutputStream output = new FileOutputStream(file)) {
                    if (!sticker.compress(Bitmap.CompressFormat.WEBP, quality, output)) throw new IOException("WebP encoding failed");
                }
                if (file.length() <= 512 * 1024) {
                    complete = true;
                    return file.getAbsolutePath();
                }
            }
            throw new IOException("Sticker exceeds maximum size");
        } finally {
            sticker.recycle();
            if (!complete) file.delete();
        }
    }
}
