package org.telegram.ui.Components;

import static org.telegram.messenger.LocaleController.getString;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadRetentionManager;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.DownloadRetentionActivity;

import java.util.List;

/** Foreground reminder only. File deletion always needs an explicit confirmation. */
public final class DownloadRetentionPrompt {
    private static long nextPromptAt;
    private static boolean showing;
    private static boolean checking;

    public static void maybeShow(BaseFragment fragment) {
        if (!canShow(fragment) || checking) return;
        checking = true;
        Utilities.globalQueue.postRunnable(() -> {
            List<DownloadRetentionManager.Entry> expired = DownloadRetentionManager.getInstance().expired();
            AndroidUtilities.runOnUIThread(() -> {
                checking = false;
                if (canShow(fragment) && !expired.isEmpty()) show(fragment, expired);
            });
        });
    }

    private static boolean canShow(BaseFragment fragment) {
        return fragment != null && fragment.getParentActivity() != null && !fragment.isPaused()
                && fragment.getVisibleDialog() == null && !showing && System.currentTimeMillis() >= nextPromptAt;
    }

    private static void show(BaseFragment fragment, List<DownloadRetentionManager.Entry> expired) {
        long bytes = 0;
        for (DownloadRetentionManager.Entry entry : expired) bytes += entry.size;
        AlertDialog dialog = new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider())
                .setTitle(getString(R.string.DownloadRetentionExpiredTitle))
                .setMessage(LocaleController.formatString(R.string.DownloadRetentionConfirm, expired.size(), AndroidUtilities.formatFileSize(bytes)))
                .setPositiveButton(getString(R.string.DownloadRetentionReview), (d, which) -> {
                    DownloadRetentionActivity activity = new DownloadRetentionActivity();
                    activity.setCurrentAccount(fragment.getCurrentAccount());
                    fragment.presentFragment(activity);
                })
                .setNegativeButton(getString(R.string.DownloadRetentionLater), null).create();
        if (fragment.showDialog(dialog, d -> showing = false) != null) {
            showing = true;
            nextPromptAt = System.currentTimeMillis() + 24L * 60L * 60L * 1000L;
        }
    }
}
