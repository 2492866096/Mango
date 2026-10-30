package org.telegram.messenger;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

import java.io.File;
import java.io.FileOutputStream;

public class DialogsBackgroundHelper {

    private static final String BACKGROUND_FILE_NAME = "dialogs_background.jpg";
    private static final String OPACITY_KEY = "dialogsBackgroundOpacity";

    private static Bitmap cachedBitmap;
    private static long cachedLastModified;
    private static String cachedPath;

    private DialogsBackgroundHelper() {
    }

    public static File getBackgroundFile() {
        return new File(ApplicationLoader.getFilesDirFixed(), BACKGROUND_FILE_NAME);
    }

    public static boolean hasBackground() {
        File file = getBackgroundFile();
        return file.exists() && file.length() > 0;
    }

    public static Bitmap getBitmap(int width, int height) {
        File file = getBackgroundFile();
        if (!file.exists() || file.length() == 0) {
            clearCache();
            return null;
        }
        String path = file.getAbsolutePath();
        long lastModified = file.lastModified();
        if (cachedBitmap != null && !cachedBitmap.isRecycled() && lastModified == cachedLastModified && path.equals(cachedPath)) {
            return cachedBitmap;
        }
        clearCache();
        cachedBitmap = ImageLoader.loadBitmap(path, null, Math.max(1, width), Math.max(1, height), true);
        cachedLastModified = lastModified;
        cachedPath = path;
        return cachedBitmap;
    }

    public static boolean saveBackground(File sourceFile, Bitmap bitmap) {
        File destFile = getBackgroundFile();
        try {
            if (!destFile.getParentFile().exists()) {
                destFile.getParentFile().mkdirs();
            }
            boolean saved = false;
            if (sourceFile != null && sourceFile.exists()) {
                saved = AndroidUtilities.copyFile(sourceFile, destFile);
            }
            if (!saved && bitmap != null) {
                FileOutputStream stream = new FileOutputStream(destFile);
                saved = bitmap.compress(Bitmap.CompressFormat.JPEG, 88, stream);
                stream.close();
            }
            clearCache();
            return saved;
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    public static void clearBackground() {
        File file = getBackgroundFile();
        if (file.exists()) {
            file.delete();
        }
        clearCache();
    }

    public static int getOpacity() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        return Math.max(0, Math.min(100, preferences.getInt(OPACITY_KEY, 100)));
    }

    public static void setOpacity(int opacity) {
        opacity = Math.max(0, Math.min(100, opacity));
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        preferences.edit().putInt(OPACITY_KEY, opacity).commit();
    }

    private static void clearCache() {
        cachedBitmap = null;
        cachedLastModified = 0;
        cachedPath = null;
    }
}
