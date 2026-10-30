package org.telegram.messenger;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.telegram.messenger.AndroidUtilities;

/**
 * Counts bytes delivered by FileLoadOperation.  It intentionally measures the
 * bytes received from the network, rather than the number of completed files,
 * so a speed readout remains useful for a large file and for parallel chunks.
 */
public final class MangoDownloadSpeedMonitor {
    private static final Object LOCK = new Object();
    private static final Map<FileLoadOperation, Integer> ACTIVE =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private static final HashMap<Integer, Counters> COUNTERS = new HashMap<>();
    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private MangoDownloadSpeedMonitor() {
    }

    public static void started(FileLoadOperation operation, int account) {
        if (operation == null) return;
        synchronized (LOCK) {
            ACTIVE.put(operation, account);
            counters(account);
        }
        notifyListeners();
    }

    public static void received(FileLoadOperation operation, long count) {
        if (operation == null || count <= 0) return;
        synchronized (LOCK) {
            // An operation can deliver a final callback after it has been
            // cancelled. Do not let that callback resurrect a task.
            Integer account = ACTIVE.get(operation);
            if (account != null) counters(account).bytes += count;
        }
    }

    public static void finished(FileLoadOperation operation) {
        if (operation == null) return;
        synchronized (LOCK) {
            ACTIVE.remove(operation);
        }
        notifyListeners();
    }

    public static void paused(FileLoadOperation operation) {
        finished(operation);
    }

    public interface Listener { void onDownloadTasksChanged(); }
    public static void addListener(Listener listener) { if (listener != null && !LISTENERS.contains(listener)) LISTENERS.add(listener); }
    public static void removeListener(Listener listener) { LISTENERS.remove(listener); }
    private static void notifyListeners() {
        if (LISTENERS.isEmpty()) return;
        AndroidUtilities.runOnUIThread(() -> {
            for (Listener listener : LISTENERS) listener.onDownloadTasksChanged();
        });
    }

    public static Snapshot sample() {
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            long delta = 0;
            int active = 0;
            long total = 0;
            for (Map.Entry<Integer, Counters> entry : COUNTERS.entrySet()) {
                Counters c = entry.getValue();
                delta += Math.max(0, c.bytes - c.lastGlobalSampleBytes);
                c.lastGlobalSampleBytes = c.bytes;
                total += c.bytes;
            }
            for (Integer account : ACTIVE.values()) active++;
            long elapsed = lastGlobalSampleTime == 0 ? 0 : now - lastGlobalSampleTime;
            lastGlobalSampleTime = now;
            return new Snapshot(elapsed > 0 ? Math.max(0, delta * 1000L / elapsed) : 0, active, total);
        }
    }

    private static long lastGlobalSampleTime;

    public static Snapshot sample(int account) {
        synchronized (LOCK) {
            Counters c = counters(account);
            long now = System.currentTimeMillis();
            long elapsed = c.lastSampleTime == 0 ? 0 : now - c.lastSampleTime;
            long delta = Math.max(0, c.bytes - c.lastAccountSampleBytes);
            c.lastAccountSampleBytes = c.bytes;
            c.lastSampleTime = now;
            int active = 0;
            for (Integer owner : ACTIVE.values()) if (owner == account) active++;
            return new Snapshot(elapsed > 0 ? Math.max(0, delta * 1000L / elapsed) : 0, active, c.bytes);
        }
    }

    private static Counters counters(int account) {
        Counters c = COUNTERS.get(account);
        if (c == null) {
            c = new Counters();
            COUNTERS.put(account, c);
        }
        return c;
    }

    public static int activeCount() {
        synchronized (LOCK) {
            return ACTIVE.size();
        }
    }

    public static int activeCount(int account) {
        synchronized (LOCK) {
            int count = 0;
            for (Integer owner : ACTIVE.values()) if (owner == account) count++;
            return count;
        }
    }

    /** Test hook and a safe reset when an app process is recreated. */
    public static void resetForTests() {
        synchronized (LOCK) {
            ACTIVE.clear();
            COUNTERS.clear();
            lastGlobalSampleTime = 0;
        }
    }

    private static final class Counters {
        long bytes, lastGlobalSampleBytes, lastAccountSampleBytes, lastSampleTime;
    }

    public static final class Snapshot {
        public final long bytesPerSecond;
        public final int activeTasks;
        public final long bytesTotal;

        Snapshot(long bytesPerSecond, int activeTasks, long bytesTotal) {
            this.bytesPerSecond = bytesPerSecond;
            this.activeTasks = activeTasks;
            this.bytesTotal = bytesTotal;
        }
    }
}
