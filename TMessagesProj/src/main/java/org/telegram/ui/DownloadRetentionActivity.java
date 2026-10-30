package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadRetentionManager;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.DownloadQuickActions;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Settings and explicit-download inventory for local retention. */
public class DownloadRetentionActivity extends BaseFragment {
    private static final int ITEM_DEFAULT = 1;
    private static final int ITEM_CLEAN = 2;
    private static final int ITEM_FILE = 3;
    private UniversalRecyclerView listView;
    private DownloadQuickActions quickActions;
    private List<DownloadRetentionManager.Entry> entries = new ArrayList<>();
    private int defaultDays;
    private boolean destroyed;
    private boolean cleaning;
    private int refreshGeneration;

    public DownloadRetentionActivity() {}

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(getString(R.string.DownloadRetentionTitle));
        quickActions = new DownloadQuickActions(this, this::refresh);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        frame.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        actionBar.setAdaptiveBackground(listView);
        importDownloadHistory();
        return fragmentView = frame;
    }

    private void importDownloadHistory() {
        ArrayList<org.telegram.messenger.MessageObject> completed = new ArrayList<>();
        int[] accounts = UserConfig.getActivatedAccounts();
        for (int account : accounts) {
            ArrayList<org.telegram.messenger.MessageObject> items = new ArrayList<>();
            FileLoader.getInstance(account).getRecentLoadingFiles(items);
            completed.addAll(items);
        }
        Utilities.globalQueue.postRunnable(() -> {
            for (org.telegram.messenger.MessageObject item : completed) {
                File file = FileLoader.getInstance(item.currentAccount).getPathToMessage(item.messageOwner);
                DownloadRetentionManager.getInstance().recordExistingDownload(file);
            }
            AndroidUtilities.runOnUIThread(this::refresh);
        });
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asShadow(getString(R.string.DownloadRetentionInfo)));
        items.add(UItem.asButton(ITEM_DEFAULT, R.drawable.menu_storage_path,
                getString(R.string.DownloadRetentionDefault), policyLabel(defaultDays)));
        List<DownloadRetentionManager.Entry> expired = expiredEntries();
        String cleanSummary = expired.isEmpty() ? getString(R.string.DownloadRetentionNothingExpired)
                : LocaleController.formatString(R.string.DownloadRetentionExpiredCount, expired.size());
        items.add(UItem.asButton(ITEM_CLEAN, R.drawable.msg_delete,
                getString(cleaning ? R.string.DuplicateFilesCleaning : R.string.DownloadRetentionClean), cleanSummary));
        items.add(UItem.asShadow(getString(R.string.DownloadRetentionListInfo)));
        for (DownloadRetentionManager.Entry entry : entries) {
            String detail;
            if (entry.expiresAt == 0) {
                detail = getString(R.string.DownloadRetentionForever);
            } else if (entry.isExpired(System.currentTimeMillis())) {
                detail = getString(R.string.DownloadRetentionExpired);
            } else {
                detail = LocaleController.formatString(R.string.DownloadRetentionExpires,
                        LocaleController.formatDateTime(entry.expiresAt / 1000L, true));
            }
            UItem row = UItem.asButton(ITEM_FILE, R.drawable.msg_download,
                    entry.file().getName(), AndroidUtilities.formatFileSize(entry.size) + " · " + detail);
            row.object = entry;
            items.add(row);
        }
        if (entries.isEmpty()) items.add(UItem.asShadow(getString(R.string.DownloadRetentionEmpty)));
    }

    private String policyLabel(int days) {
        if (days == DownloadRetentionManager.ONE_DAY) return getString(R.string.DownloadRetentionOneDay);
        if (days == DownloadRetentionManager.ONE_WEEK) return getString(R.string.DownloadRetentionOneWeek);
        return getString(R.string.DownloadRetentionForever);
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (cleaning) return;
        if (item.id == ITEM_DEFAULT) {
            showPolicyPicker(null);
        } else if (item.id == ITEM_CLEAN) {
            confirmClean();
        } else if (item.id == ITEM_FILE && item.object instanceof DownloadRetentionManager.Entry) {
            DownloadRetentionManager.Entry entry = (DownloadRetentionManager.Entry) item.object;
            quickActions.show(entry.file(), entry.file().getName(), null);
        }
    }

    private void showPolicyPicker(File file) {
        CharSequence[] labels = {
                getString(R.string.DownloadRetentionOneDay),
                getString(R.string.DownloadRetentionOneWeek),
                getString(R.string.DownloadRetentionForever)
        };
        new AlertDialog.Builder(getParentActivity(), resourceProvider)
                .setTitle(file == null ? getString(R.string.DownloadRetentionDefault) : file.getName())
                .setItems(labels, (dialog, which) -> {
                    int days = which == 0 ? DownloadRetentionManager.ONE_DAY
                            : which == 1 ? DownloadRetentionManager.ONE_WEEK : DownloadRetentionManager.FOREVER;
                    Utilities.globalQueue.postRunnable(() -> {
                        if (file == null) DownloadRetentionManager.getInstance().setAllDays(days);
                        else DownloadRetentionManager.getInstance().setFileDays(file, days);
                        AndroidUtilities.runOnUIThread(this::refresh);
                    });
                }).setNegativeButton(getString(R.string.Cancel), null).show();
    }

    private void confirmClean() {
        List<DownloadRetentionManager.Entry> expired = expiredEntries();
        if (expired.isEmpty()) {
            Toast.makeText(getParentActivity(), getString(R.string.DownloadRetentionNothingExpired), Toast.LENGTH_SHORT).show();
            return;
        }
        long bytes = 0;
        for (DownloadRetentionManager.Entry entry : expired) bytes += entry.size;
        new AlertDialog.Builder(getParentActivity(), resourceProvider)
                .setTitle(getString(R.string.DownloadRetentionClean))
                .setMessage(LocaleController.formatString(R.string.DownloadRetentionConfirm, expired.size(), AndroidUtilities.formatFileSize(bytes)))
                .setPositiveButton(getString(R.string.Delete), (dialog, which) -> {
                    cleaning = true;
                    if (listView != null) listView.adapter.update(false);
                    Utilities.globalQueue.postRunnable(() -> {
                        DownloadRetentionManager.CleanupResult result = DownloadRetentionManager.getInstance().cleanExpired(expired);
                        AndroidUtilities.runOnUIThread(() -> {
                            cleaning = false;
                            for (int account : UserConfig.getActivatedAccounts()) {
                                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.onDownloadingFilesChanged);
                                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.mangoDownloadCompleted);
                            }
                            if (destroyed || getParentActivity() == null) return;
                            Toast.makeText(getParentActivity(), LocaleController.formatString(R.string.DownloadRetentionDone,
                                    result.deleted, AndroidUtilities.formatFileSize(result.bytes), result.skipped), Toast.LENGTH_LONG).show();
                            refresh();
                        });
                    });
                })
                .setNegativeButton(getString(R.string.Cancel), null).show();
    }

    private void refresh() {
        if (destroyed) return;
        final int generation = ++refreshGeneration;
        Utilities.globalQueue.postRunnable(() -> {
            List<DownloadRetentionManager.Entry> snapshot = DownloadRetentionManager.getInstance().snapshot();
            int days = DownloadRetentionManager.getInstance().getDefaultDays();
            AndroidUtilities.runOnUIThread(() -> {
                if (destroyed || generation != refreshGeneration) return;
                entries = snapshot;
                defaultDays = days;
                if (listView != null && listView.adapter != null) listView.adapter.update(false);
            });
        });
    }

    private List<DownloadRetentionManager.Entry> expiredEntries() {
        ArrayList<DownloadRetentionManager.Entry> expired = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (DownloadRetentionManager.Entry entry : entries) if (entry.isExpired(now)) expired.add(entry);
        return expired;
    }

    @Override public void onResume() {
        super.onResume();
        refresh();
    }

    @Override public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (quickActions != null && quickActions.onActivityResult(requestCode, resultCode, data)) return;
        super.onActivityResultFragment(requestCode, resultCode, data);
    }

    @Override public void onFragmentDestroy() {
        destroyed = true;
        super.onFragmentDestroy();
    }
}
