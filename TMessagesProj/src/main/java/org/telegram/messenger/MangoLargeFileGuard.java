package org.telegram.messenger;

import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** A local pre-upload choice. Compression never changes the original files. */
public final class MangoLargeFileGuard {
    public static final long THRESHOLD_BYTES = 100L * 1024 * 1024;
    public interface CompressedFile { void ready(String path); }

    private MangoLargeFileGuard() {}

    public static boolean check(ArrayList<String> paths, ArrayList<Uri> uris, Runnable original, CompressedFile compressed) {
        if (MangoSendGuard.isLargeFileConfirmed()) return false;
        long size = 0;
        if (paths != null) for (String path : paths) if (path != null) size += new File(path).length();
        if (uris != null) for (Uri uri : uris) {
            try (Cursor cursor = ApplicationLoader.applicationContext.getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) size += Math.max(0, cursor.getLong(0));
            } catch (Exception e) { FileLog.e(e); }
        }
        if (size < THRESHOLD_BYTES) return false;
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment == null || fragment.getParentActivity() == null) return true;
        ArrayList<String> files = paths == null ? new ArrayList<>() : new ArrayList<>(paths);
        ArrayList<Uri> content = uris == null ? new ArrayList<>() : new ArrayList<>(uris);
        Runnable sendOriginal = MangoSendGuard.carryConfirmation(() -> MangoSendGuard.runLargeFileConfirmed(original));
        long seconds = (size + 1024 * 1024 - 1) / (1024 * 1024);
        String duration = LocaleController.formatPluralString("Minutes", (int) Math.max(1, (seconds + 59) / 60));
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider())
                .setTitle(LocaleController.getString(R.string.MangoLargeFileTitle))
                .setMessage(LocaleController.formatString(R.string.MangoLargeFileInfo, AndroidUtilities.formatFileSize(size), duration))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .setPositiveButton(LocaleController.getString(R.string.MangoSendOriginal), (dialog, which) -> sendOriginal.run());
        Runnable compress = MangoSendGuard.carryConfirmation(() -> compress(fragment, files, content, compressed));
        builder.setNeutralButton(LocaleController.getString(R.string.MangoCompressAndSend), (dialog, which) -> compress.run());
        builder.show();
        return true;
    }

    private static void compress(BaseFragment fragment, ArrayList<String> files, ArrayList<Uri> uris, CompressedFile done) {
        AtomicBoolean cancelled = new AtomicBoolean();
        AlertDialog progress = new AlertDialog(fragment.getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setOnCancelListener(dialog -> cancelled.set(true));
        progress.show();
        Utilities.globalQueue.postRunnable(MangoSendGuard.carryConfirmation(() -> {
            File zip = null;
            boolean success = false;
            try {
                zip = File.createTempFile("mango-upload-", ".zip", FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE));
                try (ZipOutputStream stream = new ZipOutputStream(new FileOutputStream(zip))) {
                    int index = 0;
                    for (String path : files) {
                        if (path == null) continue;
                        File source = new File(path);
                        try (InputStream input = new FileInputStream(source)) {
                            append(stream, input, (++index) + "-" + source.getName(), cancelled);
                        }
                    }
                    for (Uri uri : uris) {
                        String name = "attachment";
                        try (Cursor cursor = ApplicationLoader.applicationContext.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) name = new File(cursor.getString(0)).getName();
                        }
                        try (InputStream input = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri)) {
                            if (input == null) throw new java.io.IOException("No content stream");
                            append(stream, input, (++index) + "-" + name, cancelled);
                        }
                    }
                }
                success = !cancelled.get();
            } catch (Exception e) { FileLog.e(e); }
            final File result = zip;
            final boolean ok = success;
            AndroidUtilities.runOnUIThread(MangoSendGuard.carryConfirmation(() -> {
                progress.dismiss();
                if (ok && !cancelled.get()) {
                    MangoSendGuard.runLargeFileConfirmed(() -> done.ready(result.getAbsolutePath()));
                } else {
                    if (result != null) result.delete();
                    if (!cancelled.get() && fragment.getParentActivity() != null) {
                        new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider())
                                .setMessage(LocaleController.getString(R.string.MangoCompressionFailed))
                                .setPositiveButton(LocaleController.getString(R.string.OK), null).show();
                    }
                }
            }));
        }));
    }

    private static void append(ZipOutputStream zip, InputStream source, String name, AtomicBoolean cancelled) throws java.io.IOException {
        zip.putNextEntry(new ZipEntry(name));
        byte[] buffer = new byte[64 * 1024];
        int count;
        while ((count = source.read(buffer)) >= 0) {
            if (cancelled.get()) throw new java.io.IOException("Cancelled");
            zip.write(buffer, 0, count);
        }
        zip.closeEntry();
    }
}
