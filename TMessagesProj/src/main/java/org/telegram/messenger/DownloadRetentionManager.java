package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Retention applies only to explicit local downloads, never to user exports or chat history. */
public final class DownloadRetentionManager {
    public static final int FOREVER = 0;
    public static final int ONE_DAY = 1;
    public static final int ONE_WEEK = 7;

    private static final String PREFS = "mango_download_retention";
    private static final String KEY_DEFAULT = "default_days";
    private static final String KEY_RECORDS = "records";
    private static final DownloadRetentionManager INSTANCE = new DownloadRetentionManager();

    public static DownloadRetentionManager getInstance() {
        return INSTANCE;
    }

    public static final class Entry {
        public final String path;
        public final long downloadedAt;
        public final long expiresAt;
        public final long size;
        public final long modifiedAt;

        Entry(String path, long downloadedAt, long expiresAt, long size, long modifiedAt) {
            this.path = path;
            this.downloadedAt = downloadedAt;
            this.expiresAt = expiresAt;
            this.size = size;
            this.modifiedAt = modifiedAt;
        }

        public File file() { return new File(path); }
        public boolean isExpired(long now) { return expiresAt > 0 && now >= expiresAt; }
    }

    public static final class CleanupResult {
        public final int deleted;
        public final int skipped;
        public final long bytes;

        CleanupResult(int deleted, int skipped, long bytes) {
            this.deleted = deleted;
            this.skipped = skipped;
            this.bytes = bytes;
        }
    }

    private final Map<String, Entry> records = new HashMap<>();
    private boolean loaded;
    private int defaultDays;

    private DownloadRetentionManager() {}

    private SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private synchronized void load() {
        if (loaded || ApplicationLoader.applicationContext == null) return;
        SharedPreferences prefs = preferences();
        defaultDays = sanitizeDays(prefs.getInt(KEY_DEFAULT, FOREVER));
        try {
            JSONArray data = new JSONArray(prefs.getString(KEY_RECORDS, "[]"));
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null) continue;
                String path = item.optString("path", "");
                if (path.isEmpty()) continue;
                records.put(path, new Entry(path, item.optLong("downloaded"), item.optLong("expires"),
                        item.optLong("size"), item.optLong("modified")));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        loaded = true;
    }

    private synchronized void save() {
        if (ApplicationLoader.applicationContext == null) return;
        JSONArray array = new JSONArray();
        for (Entry entry : records.values()) {
            JSONObject item = new JSONObject();
            try {
                item.put("path", entry.path);
                item.put("downloaded", entry.downloadedAt);
                item.put("expires", entry.expiresAt);
                item.put("size", entry.size);
                item.put("modified", entry.modifiedAt);
                array.put(item);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        preferences().edit().putInt(KEY_DEFAULT, defaultDays).putString(KEY_RECORDS, array.toString()).apply();
    }

    public static int sanitizeDays(int days) {
        return days == ONE_DAY || days == ONE_WEEK ? days : FOREVER;
    }

    private static long expiry(long from, int days) {
        return days == FOREVER ? 0 : from + days * 24L * 60L * 60L * 1000L;
    }

    public synchronized int getDefaultDays() {
        load();
        return defaultDays;
    }

    /** Sets the policy for current records as well as future explicit downloads. */
    public synchronized void setAllDays(int days) {
        load();
        defaultDays = sanitizeDays(days);
        for (Entry entry : new ArrayList<>(records.values())) {
            records.put(entry.path, new Entry(entry.path, entry.downloadedAt,
                    expiry(entry.downloadedAt, defaultDays), entry.size, entry.modifiedAt));
        }
        save();
    }

    /** Called only after an explicitly requested document download has completed. */
    public synchronized void recordDownload(File file) {
        load();
        String path = allowedPath(file);
        if (path == null || !file.isFile()) return;
        long now = System.currentTimeMillis();
        records.put(path, new Entry(path, now, expiry(now, defaultDays), file.length(), file.lastModified()));
        save();
    }

    /** Imports old entries from Telegram's explicit download history without scanning arbitrary storage. */
    public synchronized void recordExistingDownload(File file) {
        load();
        String path = allowedPath(file);
        if (path == null || records.containsKey(path) || !file.isFile()) return;
        long downloaded = Math.min(System.currentTimeMillis(), Math.max(0, file.lastModified()));
        records.put(path, new Entry(path, downloaded, expiry(downloaded, defaultDays), file.length(), file.lastModified()));
        save();
    }

    public synchronized void setFileDays(File file, int days) {
        load();
        String path = allowedPath(file);
        if (path == null || !file.isFile()) return;
        Entry old = records.get(path);
        long downloaded = old == null ? System.currentTimeMillis() : old.downloadedAt;
        records.put(path, new Entry(path, downloaded, expiry(System.currentTimeMillis(), sanitizeDays(days)),
                file.length(), file.lastModified()));
        save();
    }

    public synchronized Entry get(File file) {
        load();
        String path = allowedPath(file);
        return path == null ? null : records.get(path);
    }

    public synchronized boolean isManaged(File file) {
        load();
        String path = allowedPath(file);
        return path != null && records.containsKey(path);
    }

    public synchronized List<Entry> snapshot() {
        load();
        boolean changed = false;
        for (Entry entry : new ArrayList<>(records.values())) {
            if (!entry.file().isFile()) {
                records.remove(entry.path);
                changed = true;
            }
        }
        if (changed) save();
        ArrayList<Entry> items = new ArrayList<>(records.values());
        Collections.sort(items, (a, b) -> Long.compare(b.downloadedAt, a.downloadedAt));
        return items;
    }

    public synchronized List<Entry> expired() {
        long now = System.currentTimeMillis();
        ArrayList<Entry> items = new ArrayList<>();
        for (Entry entry : snapshot()) if (entry.isExpired(now)) items.add(entry);
        return items;
    }

    /** Called only after a foreground confirmation dialog. Never deletes unmanaged paths. */
    public synchronized CleanupResult cleanExpired(List<Entry> confirmedEntries) {
        load();
        int deleted = 0, skipped = 0;
        long bytes = 0;
        for (Entry entry : confirmedEntries) {
            Entry current = records.get(entry.path);
            if (current == null || current.expiresAt != entry.expiresAt || current.downloadedAt != entry.downloadedAt
                    || !current.isExpired(System.currentTimeMillis())) {
                skipped++;
                continue;
            }
            File file = entry.file();
            if (allowedPath(file) == null || file.length() != entry.size || file.lastModified() != entry.modifiedAt
                    || isActiveDownload(file)) {
                skipped++;
                continue;
            }
            long size = file.length();
            if (file.delete()) {
                records.remove(entry.path);
                deleted++;
                bytes += size;
            } else {
                skipped++;
            }
        }
        save();
        return new CleanupResult(deleted, skipped, bytes);
    }

    private static boolean isActiveDownload(File file) {
        if (AutoDeleteMediaTask.usingFilePaths.contains(file.getAbsolutePath())) return true;
        for (String usedPath : AutoDeleteMediaTask.usingFilePaths) {
            try {
                if (new File(usedPath).getCanonicalPath().equals(file.getCanonicalPath())) return true;
            } catch (IOException ignored) {
                // If a tracked path disappeared, other activity and metadata checks still apply.
            }
        }
        for (int account : UserConfig.getActivatedAccounts()) {
            FileLoader loader = FileLoader.getInstance(account);
            if (loader.isLoadingFile(file.getName()) || loader.isUploadingFile(file.getAbsolutePath())) return true;
        }
        return System.currentTimeMillis() - file.lastModified() < 60_000L;
    }

    private static String allowedPath(File file) {
        if (file == null || ApplicationLoader.applicationContext == null) return null;
        try {
            String path = file.getCanonicalPath();
            int[] dirs = {FileLoader.MEDIA_DIR_DOCUMENT, FileLoader.MEDIA_DIR_FILES,
                    FileLoader.MEDIA_DIR_VIDEO, FileLoader.MEDIA_DIR_AUDIO,
                    FileLoader.MEDIA_DIR_IMAGE, FileLoader.MEDIA_DIR_IMAGE_PUBLIC,
                    FileLoader.MEDIA_DIR_VIDEO_PUBLIC};
            for (int dir : dirs) {
                File root = FileLoader.checkDirectory(dir);
                if (root != null && path.startsWith(root.getCanonicalPath() + File.separator)) return path;
            }
        } catch (IOException e) {
            FileLog.e(e);
        }
        return null;
    }
}
