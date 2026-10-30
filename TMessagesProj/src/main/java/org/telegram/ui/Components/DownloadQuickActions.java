package org.telegram.ui.Components;

import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.AutoDeleteMediaTask;
import org.telegram.messenger.DownloadRetentionManager;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

/** Foreground actions offered from the completed-download bar and retention inventory. */
public final class DownloadQuickActions {
    public static final int REQUEST_SAVE_COPY = 845;
    private final BaseFragment fragment;
    private final Runnable onRetentionChanged;
    private File pendingSave;

    public DownloadQuickActions(BaseFragment fragment) {
        this(fragment, null);
    }

    public DownloadQuickActions(BaseFragment fragment, Runnable onRetentionChanged) {
        this.fragment = fragment;
        this.onRetentionChanged = onRetentionChanged;
    }

    public void show(MessageObject message) {
        if (message == null) return;
        File file = FileLoader.getInstance(message.currentAccount).getPathToMessage(message.messageOwner);
        show(file, message.getFileName(), message.getDocument() == null ? null : message.getDocument().mime_type);
    }

    public void show(File file, String name, String mime) {
        if (fragment.getParentActivity() == null) return;
        if (file == null || !file.isFile()) {
            toast(R.string.DownloadQuickActionsMissing);
            return;
        }
        String mimeType = AndroidUtilities.getMimeTypeForFile(file, name, mime == null ? "application/octet-stream" : mime);
        fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider())
                .setTitle(name)
                .setItems(new CharSequence[]{getString(R.string.DownloadQuickOpen), getString(R.string.DownloadQuickShare),
                        getString(R.string.DownloadQuickSave), getString(R.string.DownloadRetentionTitle)},
                        (dialog, which) -> {
                            if (which == 0) {
                                AndroidUtilities.openForView(file, name, mimeType, fragment.getParentActivity(), fragment.getResourceProvider(), false);
                            } else if (which == 1) {
                                share(file, mimeType);
                            } else if (which == 2) {
                                chooseSaveLocation(file, name, mimeType);
                            } else {
                                showRetention(file);
                            }
                        }).setNegativeButton(getString(R.string.Cancel), null).create());
    }

    private void share(File file, String mimeType) {
        try {
            Activity activity = fragment.getParentActivity();
            if (activity == null) return;
            Uri uri = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    ? FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", file)
                    : Uri.fromFile(file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(mimeType);
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.setClipData(ClipData.newRawUri("download", uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent, getString(R.string.DownloadQuickShare)));
        } catch (Exception e) {
            FileLog.e(e);
            toast(R.string.DownloadQuickActionsFailed);
        }
    }

    private void chooseSaveLocation(File file, String name, String mimeType) {
        try {
            pendingSave = file;
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(mimeType);
            intent.putExtra(Intent.EXTRA_TITLE, name);
            fragment.startActivityForResult(intent, REQUEST_SAVE_COPY);
        } catch (Exception e) {
            pendingSave = null;
            FileLog.e(e);
            toast(R.string.DownloadQuickActionsFailed);
        }
    }

    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_SAVE_COPY) return false;
        File source = pendingSave;
        pendingSave = null;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null || source == null) return true;
        Uri target = data.getData();
        Utilities.globalQueue.postRunnable(() -> {
            boolean success = false;
            boolean markedInUse = AutoDeleteMediaTask.usingFilePaths.add(source.getAbsolutePath());
            long expectedLength = source.length();
            long expectedModified = source.lastModified();
            try (FileInputStream input = new FileInputStream(source);
                 OutputStream output = ApplicationLoader.applicationContext.getContentResolver().openOutputStream(target, "w")) {
                if (output == null) throw new java.io.IOException("Unable to open destination");
                byte[] buffer = new byte[64 * 1024];
                int read;
                long copied = 0;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    copied += read;
                }
                if (copied != expectedLength || source.length() != expectedLength || source.lastModified() != expectedModified) {
                    throw new java.io.IOException("Downloaded file changed while copying");
                }
                success = true;
            } catch (Exception e) {
                success = false;
                FileLog.e(e);
                try {
                    DocumentsContract.deleteDocument(ApplicationLoader.applicationContext.getContentResolver(), target);
                } catch (Exception deleteError) {
                    FileLog.e(deleteError);
                }
            } finally {
                if (markedInUse) AutoDeleteMediaTask.usingFilePaths.remove(source.getAbsolutePath());
            }
            final boolean copied = success;
            AndroidUtilities.runOnUIThread(() -> toast(copied ? R.string.DownloadQuickSaved : R.string.DownloadQuickActionsFailed));
        });
        return true;
    }

    private void showRetention(File file) {
        if (fragment.getParentActivity() == null) return;
        fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider())
                .setTitle(getString(R.string.DownloadRetentionTitle))
                .setItems(new CharSequence[]{getString(R.string.DownloadRetentionOneDay), getString(R.string.DownloadRetentionOneWeek),
                        getString(R.string.DownloadRetentionForever)}, (dialog, which) -> {
                    Utilities.globalQueue.postRunnable(() -> {
                        DownloadRetentionManager.getInstance().setFileDays(file, which == 0 ? DownloadRetentionManager.ONE_DAY
                                : which == 1 ? DownloadRetentionManager.ONE_WEEK : DownloadRetentionManager.FOREVER);
                        AndroidUtilities.runOnUIThread(() -> {
                            toast(R.string.DownloadRetentionUpdated);
                            if (onRetentionChanged != null) onRetentionChanged.run();
                        });
                    });
                }).create());
    }

    private void toast(int text) {
        if (fragment.getParentActivity() != null) Toast.makeText(fragment.getParentActivity(), getString(text), Toast.LENGTH_SHORT).show();
    }
}
