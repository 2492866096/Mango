package org.telegram.messenger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Platform-independent engine. Only caller-supplied application media roots can be examined. */
public final class DuplicateFileScanner {
    public interface Guard { boolean isBusy(File file); }
    public interface Progress { void update(int examined, long bytes); }
    public static final class Entry {
        public final File file;
        public final long size, modified;
        Entry(File file) { this.file = file; size = file.length(); modified = file.lastModified(); }
    }
    public static final class Group {
        public final String sha256;
        public final ArrayList<Entry> entries = new ArrayList<>();
        Group(String hash) { sha256 = hash; }
    }
    public static final class Result {
        public final ArrayList<Group> groups = new ArrayList<>();
        /** Large, standalone cleanup candidates sorted largest-first. */
        public final ArrayList<Entry> largeFiles = new ArrayList<>();
        public int examined, skipped;
        public long bytes;
        public boolean cancelled, limited;
    }
    public static final class CleanupResult {
        public int deleted, skipped;
        public long bytes;
    }
    private final ArrayList<File> roots = new ArrayList<>();
    private final Guard guard;
    private volatile boolean cancelled;
    private static final int MAX_FILES = 100000;
    private static final int MAX_LARGE_RESULTS = 2000;
    public static final long LARGE_FILE_MIN_BYTES = 100L * 1024L * 1024L;

    public DuplicateFileScanner(List<File> requestedRoots, Guard guard) {
        this.guard = guard;
        for (File root : requestedRoots) {
            try {
                File canonical = root.getCanonicalFile();
                if (canonical.isDirectory() && !roots.contains(canonical)) roots.add(canonical);
            } catch (IOException ignored) { }
        }
    }
    public void cancel() { cancelled = true; }

    public Result scan(Progress progress) {
        Result result = new Result();
        Map<Long, ArrayList<Entry>> sizes = new HashMap<>();
        Set<String> seen = new HashSet<>();
        for (File root : roots) collect(root, sizes, seen, result, progress, 0);
        for (ArrayList<Entry> sameSize : sizes.values()) {
            if (cancelled) break;
            if (sameSize.size() < 2) continue;
            Map<String, Group> hashes = new HashMap<>();
            for (Entry entry : sameSize) {
                if (cancelled) break;
                try {
                    String hash = digest(entry);
                    if (hash == null) { result.skipped++; continue; }
                    Group group = hashes.get(hash);
                    if (group == null) { group = new Group(hash); hashes.put(hash, group); }
                    group.entries.add(entry);
                } catch (Exception ignored) { result.skipped++; }
            }
            for (Group group : hashes.values()) {
                if (group.entries.size() > 1) {
                    // Keep the oldest copy by default; users may choose a different retained copy.
                    Collections.sort(group.entries, Comparator.comparingLong((Entry e) -> e.modified).thenComparing(e -> e.file.getAbsolutePath()));
                    result.groups.add(group);
                }
            }
        }
        Collections.sort(result.largeFiles, Comparator.comparingLong((Entry e) -> e.size).reversed()
                .thenComparing(e -> e.file.getAbsolutePath()));
        if (result.largeFiles.size() > MAX_LARGE_RESULTS) {
            result.largeFiles.subList(MAX_LARGE_RESULTS, result.largeFiles.size()).clear();
            result.limited = true;
        }
        Collections.sort(result.groups, (a, b) -> Long.compare(b.entries.get(0).size * (b.entries.size() - 1), a.entries.get(0).size * (a.entries.size() - 1)));
        result.cancelled = cancelled;
        return result;
    }

    private void collect(File path, Map<Long, ArrayList<Entry>> sizes, Set<String> seen, Result result, Progress progress, int depth) {
        if (cancelled || depth > 24 || result.examined >= MAX_FILES) { result.limited |= result.examined >= MAX_FILES; return; }
        try {
            if (!path.getAbsolutePath().equals(path.getCanonicalPath()) || !seen.add(path.getCanonicalPath())) return;
            if (path.isDirectory()) {
                File[] children = path.listFiles();
                if (children == null) { result.skipped++; return; }
                for (File child : children) {
                    if (cancelled || result.examined >= MAX_FILES) { result.limited |= result.examined >= MAX_FILES; break; }
                    collect(child, sizes, seen, result, progress, depth + 1);
                }
            } else {
                result.examined++;
                if (!eligible(path)) { result.skipped++; return; }
                Entry entry = new Entry(path);
                if (entry.size >= LARGE_FILE_MIN_BYTES) result.largeFiles.add(entry);
                ArrayList<Entry> entries = sizes.get(entry.size);
                if (entries == null) { entries = new ArrayList<>(); sizes.put(entry.size, entries); }
                entries.add(entry);
                result.bytes += entry.size;
                if (progress != null && result.examined % 100 == 0) progress.update(result.examined, result.bytes);
                if (result.largeFiles.size() > MAX_LARGE_RESULTS * 2) {
                    Collections.sort(result.largeFiles, Comparator.comparingLong((Entry e) -> e.size).reversed());
                    result.largeFiles.subList(MAX_LARGE_RESULTS, result.largeFiles.size()).clear();
                    result.limited = true;
                }
            }
        } catch (Exception ignored) { result.skipped++; }
    }

    /** Delete individually selected large files after a fresh safety/unchanged check. */
    public CleanupResult cleanLargeFiles(List<Entry> files, Set<String> selected) {
        CleanupResult result = new CleanupResult();
        Set<String> handled = new HashSet<>();
        if (files == null || selected == null) return result;
        for (Entry entry : files) {
            if (cancelled) break;
            String path = entry.file.getAbsolutePath();
            if (!selected.contains(path) || !handled.add(path)) continue;
            try {
                if (entry.size < LARGE_FILE_MIN_BYTES || !unchanged(entry) || !entry.file.delete()) {
                    result.skipped++;
                } else {
                    result.deleted++;
                    result.bytes += entry.size;
                }
            } catch (Exception ignored) {
                result.skipped++;
            }
        }
        return result;
    }

    private boolean eligible(File file) throws IOException {
        String path = file.getAbsolutePath();
        if (!file.isFile() || file.length() == 0 || !path.equals(file.getCanonicalPath())) return false;
        boolean within = false;
        for (File root : roots) if (path.startsWith(root.getPath() + File.separator)) { within = true; break; }
        if (!within || guard != null && guard.isBusy(file)) return false;
        String name = file.getName().toLowerCase(Locale.US);
        return !name.startsWith(".") && !name.endsWith(".temp") && !name.endsWith(".tmp") && !name.endsWith(".part")
                && !name.endsWith(".enc") && !name.endsWith(".key") && !name.endsWith(".db") && !name.endsWith("-wal")
                && !name.endsWith("-shm") && !name.endsWith(".lock") && !name.endsWith("-journal");
    }

    private boolean unchanged(Entry entry) throws IOException {
        return !cancelled && eligible(entry.file) && entry.size == entry.file.length() && entry.modified == entry.file.lastModified();
    }
    private String digest(Entry entry) throws Exception {
        if (!unchanged(entry)) return null;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[65536];
        try (FileInputStream input = new FileInputStream(entry.file)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (cancelled) return null;
                digest.update(buffer, 0, read);
            }
        }
        if (!unchanged(entry)) return null;
        StringBuilder hash = new StringBuilder(64);
        for (byte value : digest.digest()) hash.append(String.format(Locale.US, "%02x", value & 255));
        return hash.toString();
    }

    public CleanupResult clean(List<Group> groups, Set<String> selected) {
        CleanupResult result = new CleanupResult();
        for (Group group : groups) {
            if (cancelled) break;
            Entry keeper = null;
            // A retained, unchanged, byte-identical copy must exist before every deletion.
            for (Entry entry : group.entries) {
                if (!selected.contains(entry.file.getAbsolutePath())) {
                    try { if (group.sha256.equals(digest(entry))) { keeper = entry; break; } } catch (Exception ignored) { }
                }
            }
            for (Entry entry : group.entries) {
                if (cancelled) break;
                if (!selected.contains(entry.file.getAbsolutePath())) continue;
                try {
                    if (keeper == null || !group.sha256.equals(digest(entry)) || !group.sha256.equals(digest(keeper))
                            || !unchanged(entry) || !unchanged(keeper) || !entry.file.delete()) {
                        result.skipped++;
                    } else {
                        result.deleted++;
                        result.bytes += entry.size;
                    }
                } catch (Exception ignored) { result.skipped++; }
            }
        }
        return result;
    }
}
