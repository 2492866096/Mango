package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.os.Environment;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DuplicateFileScanner;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Finds duplicate application media only; never crawls arbitrary user storage. */
public class DuplicateFilesActivity extends BaseFragment {
    private UniversalRecyclerView listView;
    private DuplicateFileScanner scanner;
    private DuplicateFileScanner.Result result;
    private final Set<String> selected = new HashSet<>();
    private boolean busy, destroyed, largeFilesMode;
    private String status;

    @Override public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(getString(R.string.DuplicateFilesTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, this::onLongClick);
        frame.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        actionBar.setAdaptiveBackground(listView);
        return fragmentView = frame;
    }

    private void refresh() { if (!destroyed && listView != null) listView.adapter.update(false); }
    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asRadio(5, getString(R.string.DuplicateFilesModeDuplicates))
                .setChecked(!largeFilesMode));
        items.add(UItem.asRadio(6, getString(R.string.DuplicateFilesModeLarge))
                .setChecked(largeFilesMode));
        items.add(UItem.asShadow(getString(largeFilesMode ? R.string.DuplicateFilesLargeInfo : R.string.DuplicateFilesInfo)));
        items.add(UItem.asButton(busy ? 3 : 1, getString(busy ? R.string.Cancel : R.string.DuplicateFilesScan)).accent());
        if (status != null) items.add(UItem.asShadow(status));
        if (result == null || busy) return;
        if (largeFilesMode) {
            items.add(UItem.asShadow(LocaleController.formatString(R.string.DuplicateFilesLargeSummary,
                    result.largeFiles.size(), AndroidUtilities.formatFileSize(DuplicateFileScanner.LARGE_FILE_MIN_BYTES),
                    result.examined, result.skipped)));
            if (result.cancelled || result.limited) items.add(UItem.asShadow(getString(R.string.DuplicateFilesPartial)));
            if (!result.largeFiles.isEmpty()) {
                items.add(UItem.asButton(12, getString(R.string.DuplicateFilesLargeSelect)).accent());
                long bytes = 0;
                for (DuplicateFileScanner.Entry entry : result.largeFiles)
                    if (selected.contains(entry.file.getAbsolutePath())) bytes += entry.size;
                items.add(UItem.asButton(2, getString(R.string.DuplicateFilesClean), selected.size() + " · " + AndroidUtilities.formatFileSize(bytes)).accent());
                for (DuplicateFileScanner.Entry entry : result.largeFiles) {
                    String path = entry.file.getAbsolutePath();
                    UItem item = UItem.asButtonCheck(10, entry.file.getName() + " · " + AndroidUtilities.formatFileSize(entry.size), path)
                            .setChecked(selected.contains(path));
                    item.object = entry;
                    items.add(item);
                }
            }
            return;
        }
        items.add(UItem.asShadow(LocaleController.formatString(R.string.DuplicateFilesSummary, result.groups.size(), result.examined, result.skipped)));
        if (result.cancelled || result.limited) items.add(UItem.asShadow(getString(R.string.DuplicateFilesPartial)));
        if (!result.groups.isEmpty()) {
            items.add(UItem.asButton(4, getString(R.string.DuplicateFilesSmartSelect)).accent());
            long bytes = 0;
            for (DuplicateFileScanner.Group group : result.groups) for (DuplicateFileScanner.Entry entry : group.entries)
                if (selected.contains(entry.file.getAbsolutePath())) bytes += entry.size;
            items.add(UItem.asButton(2, getString(R.string.DuplicateFilesClean), selected.size() + " · " + AndroidUtilities.formatFileSize(bytes)).accent());
        }
        int groupIndex = 1;
        for (DuplicateFileScanner.Group group : result.groups) {
            items.add(UItem.asHeader(LocaleController.formatString(R.string.DuplicateFilesGroup, groupIndex++)));
            for (DuplicateFileScanner.Entry entry : group.entries) {
                String path = entry.file.getAbsolutePath();
                UItem item = UItem.asButtonCheck(10, entry.file.getName() + " · " + AndroidUtilities.formatFileSize(entry.size), path)
                        .setChecked(selected.contains(path));
                item.object = entry;
                items.add(item);
            }
            items.add(UItem.asShadow(null));
        }
    }

    private DuplicateFileScanner newScanner() {
        int[] accounts = UserConfig.getActivatedAccounts();
        return new DuplicateFileScanner(roots(), file -> {
            // FileLoader maps are concurrent; check every account sharing these media directories.
            for (int account : accounts) {
                FileLoader loader = FileLoader.getInstance(account);
                if (loader.isLoadingFile(file.getName()) || loader.isUploadingFile(file.getAbsolutePath())) return true;
            }
            if (org.telegram.messenger.DownloadRetentionManager.getInstance().isManaged(file)) return true;
            // Fresh files may still be written by the recorder, camera or a pending upload.
            return System.currentTimeMillis() - file.lastModified() < 60000;
        });
    }

    private void smartSelect() {
        selected.clear();
        if (largeFilesMode) return;
        if (result != null) for (DuplicateFileScanner.Group group : result.groups)
            for (int i = 1; i < group.entries.size(); i++) selected.add(group.entries.get(i).file.getAbsolutePath());
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 5 || item.id == 6) {
            largeFilesMode = item.id == 6;
            selected.clear();
            refresh();
        } else if (item.id == 3) {
            if (scanner != null) scanner.cancel();
        } else if (!busy && item.id == 1) {
            selected.clear();
            status = getString(R.string.DuplicateFilesScanning);
            busy = true;
            scanner = newScanner();
            DuplicateFileScanner current = scanner;
            refresh();
            new Thread(() -> {
                DuplicateFileScanner.Result found = current.scan((files, bytes) -> AndroidUtilities.runOnUIThread(() -> {
                    status = getString(R.string.DuplicateFilesScanning) + " · " + files;
                    refresh();
                }));
                AndroidUtilities.runOnUIThread(() -> {
                    busy = false;
                    scanner = null;
                    status = null;
                    result = found;
                    if (!largeFilesMode) smartSelect();
                    refresh();
                });
            }, "duplicate-file-scan").start();
        } else if (!busy && item.id == 4) {
            smartSelect();
            refresh();
        } else if (!busy && item.id == 12 && result != null) {
            selected.clear();
            if (largeFilesMode) for (DuplicateFileScanner.Entry entry : result.largeFiles) selected.add(entry.file.getAbsolutePath());
            refresh();
        } else if (!busy && item.id == 2 && result != null && getParentActivity() != null) {
            if (selected.isEmpty()) { toast(R.string.DuplicateFilesSelect); return; }
            long bytes = 0;
            if (largeFilesMode) {
                for (DuplicateFileScanner.Entry entry : result.largeFiles)
                    if (selected.contains(entry.file.getAbsolutePath())) bytes += entry.size;
            } else {
                for (DuplicateFileScanner.Group group : result.groups) for (DuplicateFileScanner.Entry entry : group.entries)
                    if (selected.contains(entry.file.getAbsolutePath())) bytes += entry.size;
            }
            int confirmString = largeFilesMode ? R.string.DuplicateFilesLargeConfirm : R.string.DuplicateFilesConfirm;
            showDialog(new AlertDialog.Builder(getParentActivity(), resourceProvider).setTitle(getString(R.string.DuplicateFilesClean))
                    .setMessage(LocaleController.formatString(confirmString, selected.size(), AndroidUtilities.formatFileSize(bytes)))
                    .setPositiveButton(getString(R.string.Delete), (d, w) -> cleanSelected())
                    .setNegativeButton(getString(R.string.Cancel), null).create());
        } else if (!busy && item.id == 10 && item.object instanceof DuplicateFileScanner.Entry) {
            DuplicateFileScanner.Entry entry = (DuplicateFileScanner.Entry) item.object;
            String path = entry.file.getAbsolutePath();
            if (!selected.remove(path)) {
                if (!largeFilesMode) for (DuplicateFileScanner.Group group : result.groups) if (group.entries.contains(entry)) {
                    int retained = 0;
                    for (DuplicateFileScanner.Entry e : group.entries) if (!selected.contains(e.file.getAbsolutePath())) retained++;
                    if (retained <= 1) { toast(R.string.DuplicateFilesKeepOne); return; }
                }
                selected.add(path);
            }
            refresh();
        }
    }

    private void cleanSelected() {
        if (busy || result == null) return;
        busy = true;
        status = getString(R.string.DuplicateFilesCleaning);
        scanner = newScanner();
        DuplicateFileScanner current = scanner;
        ArrayList<DuplicateFileScanner.Group> groups = new ArrayList<>(result.groups);
        boolean large = largeFilesMode;
        ArrayList<DuplicateFileScanner.Entry> largeFiles = large ? new ArrayList<>(result.largeFiles) : null;
        Set<String> selection = new HashSet<>(selected);
        refresh();
        new Thread(() -> {
            DuplicateFileScanner.CleanupResult cleaned = large ? current.cleanLargeFiles(largeFiles, selection) : current.clean(groups, selection);
            AndroidUtilities.runOnUIThread(() -> {
                busy = false;
                scanner = null;
                status = LocaleController.formatString(large ? R.string.DuplicateFilesLargeDone : R.string.DuplicateFilesDone, cleaned.deleted,
                        AndroidUtilities.formatFileSize(cleaned.bytes), cleaned.skipped);
                // Old results cannot be reused after any cleanup attempt; rescan verifies current contents.
                result = null;
                selected.clear();
                refresh();
            });
        }, "duplicate-file-clean").start();
    }

    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (busy || !(item.object instanceof DuplicateFileScanner.Entry) || getParentActivity() == null) return false;
        DuplicateFileScanner.Entry entry = (DuplicateFileScanner.Entry) item.object;
        try {
            return AndroidUtilities.openForView(entry.file, entry.file.getName(), "application/octet-stream", getParentActivity(), resourceProvider, true);
        } catch (Exception e) { toast(R.string.DuplicateFilesPreviewUnavailable); return true; }
    }

    private List<File> roots() {
        ArrayList<File> roots = new ArrayList<>();
        int[] dirs = {FileLoader.MEDIA_DIR_IMAGE, FileLoader.MEDIA_DIR_AUDIO, FileLoader.MEDIA_DIR_VIDEO,
                FileLoader.MEDIA_DIR_DOCUMENT, FileLoader.MEDIA_DIR_FILES, FileLoader.MEDIA_DIR_STORIES,
                FileLoader.MEDIA_DIR_IMAGE_PUBLIC, FileLoader.MEDIA_DIR_VIDEO_PUBLIC};
        for (int type : dirs) {
            File dir = FileLoader.checkDirectory(type);
            if (dir != null) roots.add(dir);
        }
        File downloads = ApplicationLoader.applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (downloads != null) roots.add(downloads);
        return roots;
    }
    private void toast(int string) {
        if (getParentActivity() != null) Toast.makeText(getParentActivity(), getString(string), Toast.LENGTH_SHORT).show();
    }
    @Override public void onFragmentDestroy() {
        destroyed = true;
        if (scanner != null) scanner.cancel();
        super.onFragmentDestroy();
    }
}
