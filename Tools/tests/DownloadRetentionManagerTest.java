package org.telegram.messenger;

import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.util.List;

public class DownloadRetentionManagerTest {
    private static int assertions;
    private static void check(boolean value, String label) {
        assertions++;
        if (!value) throw new AssertionError(label);
    }
    private static File oldFile(File folder, String name, int ageDays) throws Exception {
        File file = new File(folder, name);
        Files.write(file.toPath(), new byte[] {1, 2, 3, 4});
        file.setLastModified(System.currentTimeMillis() - ageDays * 86_400_000L);
        return file;
    }
    public static void main(String[] args) throws Exception {
        File root = new File(args[0], "managed");
        root.mkdirs();
        FileLoader.root = root;
        DownloadRetentionManager manager = DownloadRetentionManager.getInstance();
        check(manager.getDefaultDays() == DownloadRetentionManager.FOREVER, "default forever");
        File first = oldFile(root, "first.txt", 2);
        manager.recordExistingDownload(first);
        check(manager.snapshot().size() == 1, "old download imported");
        check(manager.get(first).expiresAt == 0, "forever has no expiry");
        manager.setAllDays(DownloadRetentionManager.ONE_DAY);
        check(manager.expired().size() == 1, "old copy expires from download time");
        manager.setFileDays(first, DownloadRetentionManager.ONE_WEEK);
        check(manager.expired().isEmpty(), "per-file renewal starts now");
        long expiry = manager.get(first).expiresAt;
        manager.recordExistingDownload(first);
        check(manager.get(first).expiresAt == expiry, "history reimport retains single override");
        manager.setAllDays(DownloadRetentionManager.ONE_DAY);
        List<DownloadRetentionManager.Entry> confirmed = manager.expired();
        File later = oldFile(root, "later.txt", 2);
        manager.recordExistingDownload(later);
        DownloadRetentionManager.CleanupResult result = manager.cleanExpired(confirmed);
        check(result.deleted == 1 && result.bytes == 4, "confirmed expiry deletes local copy");
        check(!first.exists() && later.exists(), "newly expired file excluded from prior confirmation");
        manager.setAllDays(DownloadRetentionManager.ONE_DAY);
        confirmed = manager.expired();
        manager.setFileDays(later, DownloadRetentionManager.FOREVER);
        result = manager.cleanExpired(confirmed);
        check(result.deleted == 0 && later.exists(), "policy change invalidates confirmation");
        manager.setAllDays(DownloadRetentionManager.ONE_DAY);
        FileLoader.active = true;
        result = manager.cleanExpired(manager.expired());
        check(result.skipped == 1 && later.exists(), "active download protected");
        FileLoader.active = false;
        AutoDeleteMediaTask.usingFilePaths.add(later.getAbsolutePath());
        result = manager.cleanExpired(manager.expired());
        check(result.skipped == 1 && later.exists(), "playing file protected");
        AutoDeleteMediaTask.usingFilePaths.clear();
        confirmed = manager.expired();
        Files.write(later.toPath(), new byte[] {4, 5, 6});
        result = manager.cleanExpired(confirmed);
        check(result.skipped == 1 && later.exists(), "changed file not deleted");
        File outside = oldFile(new File(args[0]), "outside.txt", 2);
        manager.recordExistingDownload(outside);
        check(manager.get(outside) == null, "unmanaged path rejected");
        File symlink = new File(root, "symlink.txt");
        Files.createSymbolicLink(symlink.toPath(), outside.toPath());
        manager.recordExistingDownload(symlink);
        check(manager.get(symlink) == null && outside.exists(), "symlink escape rejected");
        manager.recordDownload(later);
        check(!manager.get(later).isExpired(System.currentTimeMillis()), "redownload resets expiry");
        check(manager.get(later).expiresAt - manager.get(later).downloadedAt == 86_400_000L, "new download inherits one-day policy");
        check(manager.isManaged(later), "record marks file managed");
        manager.setAllDays(1000);
        check(manager.getDefaultDays() == DownloadRetentionManager.FOREVER, "invalid periods sanitized");
        Constructor<DownloadRetentionManager> constructor = DownloadRetentionManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        DownloadRetentionManager restored = constructor.newInstance();
        check(restored.get(later).expiresAt == 0, "policy and metadata persist");
        check(restored.get(later).size == later.length(), "file size persists");
        later.delete();
        check(restored.snapshot().isEmpty(), "externally deleted records pruned");
        System.out.println("DownloadRetentionManagerTest: " + assertions + " assertions passed");
    }
}
