package org.telegram.messenger;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Debug;
import android.os.SystemClock;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Lightweight, on-demand device/app metrics for the Mango performance page. */
public final class MangoPerformanceMonitor {
    private static final Object LOCK = new Object();
    private static long cacheBaseline = -1;
    private static long cachedCacheBytes = -1;
    private static long lastCacheScanAt;
    private static boolean cacheScanInProgress;
    private static boolean foreground;

    private MangoPerformanceMonitor() {
    }

    public static void setForeground(boolean value) {
        synchronized (LOCK) {
            foreground = value;
        }
    }

    public static boolean isForeground() {
        synchronized (LOCK) {
            return foreground;
        }
    }

    public static Snapshot sample(Context context) {
        return sample(context, UserConfig.selectedAccount);
    }

    public static Snapshot sample(Context context, int account) {
        if (context == null) return Snapshot.empty();
        Context app = context.getApplicationContext();
        Debug.MemoryInfo memoryInfo = new Debug.MemoryInfo();
        Debug.getMemoryInfo(memoryInfo);
        long memoryBytes = memoryInfo.getTotalPss() * 1024L;

        requestCacheScan(app);
        long cacheBytes;
        synchronized (LOCK) {
            cacheBytes = cachedCacheBytes;
            if (cacheBaseline < 0 && cacheBytes >= 0) cacheBaseline = cacheBytes;
        }

        Intent battery = app.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = -1, scale = -1, temperature = Integer.MIN_VALUE, current = Integer.MIN_VALUE, status = -1;
        if (battery != null) {
            level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            temperature = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        }
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            try {
                BatteryManager manager = (BatteryManager) app.getSystemService(Context.BATTERY_SERVICE);
                if (manager != null) current = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            } catch (Throwable ignore) { }
        }
        ActivityManager activityManager = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
        int backgroundProcesses = 0;
        if (activityManager != null) {
            try {
                List<android.app.ActivityManager.RunningAppProcessInfo> processes = activityManager.getRunningAppProcesses();
                if (processes != null) {
                    for (android.app.ActivityManager.RunningAppProcessInfo process : processes) {
                        if (process.uid == android.os.Process.myUid() && process.importance != android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) backgroundProcesses++;
                    }
                }
            } catch (Throwable ignore) {
            }
        }
        long baseline;
        boolean appForeground;
        synchronized (LOCK) {
            baseline = cacheBaseline;
            appForeground = foreground;
        }
        int pendingSends = 0;
        for (int activeAccount : UserConfig.getActivatedAccounts()) {
            pendingSends += SendMessagesHelper.getInstance(activeAccount).getActiveSendTasksCount();
        }
        return new Snapshot(memoryBytes, cacheBytes, cacheBytes < 0 || baseline < 0 ? Long.MIN_VALUE : cacheBytes - baseline, level, scale,
                current, temperature, status, MangoDownloadSpeedMonitor.activeCount(account),
                backgroundProcesses, pendingSends, appForeground, SystemClock.elapsedRealtime());
    }

    public static void resetCacheBaseline() {
        synchronized (LOCK) {
            cacheBaseline = cachedCacheBytes;
        }
    }

    private static void requestCacheScan(Context app) {
        long now = System.currentTimeMillis();
        synchronized (LOCK) {
            if (cacheScanInProgress || now - lastCacheScanAt < 20000) return;
            cacheScanInProgress = true;
            lastCacheScanAt = now;
        }
        Utilities.globalQueue.postRunnable(() -> {
            try {
                long size = 0;
                Set<String> seen = new HashSet<>();
                File[] roots = new File[10];
                roots[0] = app.getCacheDir();
                for (int i = 0; i <= FileLoader.MEDIA_DIR_STORIES; i++) {
                    try { roots[i + 1] = FileLoader.getDirectory(i); } catch (Throwable ignore) { }
                }
                try { roots[8] = FileLoader.getDirectory(FileLoader.MEDIA_DIR_IMAGE_PUBLIC); } catch (Throwable ignore) { }
                try { roots[9] = FileLoader.getDirectory(FileLoader.MEDIA_DIR_VIDEO_PUBLIC); } catch (Throwable ignore) { }
                for (File root : roots) size = saturatedAdd(size, directorySize(root, seen, 0));
                synchronized (LOCK) {
                    cachedCacheBytes = size;
                    if (cacheBaseline < 0) cacheBaseline = size;
                    cacheScanInProgress = false;
                }
            } catch (Throwable ignore) {
                synchronized (LOCK) { cacheScanInProgress = false; }
            }
        });
    }

    /** Start cache-growth measurement from the current cache size when known. */
    public static void beginSession() {
        synchronized (LOCK) {
            cacheBaseline = cachedCacheBytes;
        }
    }

    private static long directorySize(File file, Set<String> visited, int depth) {
        if (file == null || depth > 64 || !file.exists()) return 0;
        String canonical;
        try {
            canonical = file.getCanonicalPath();
            if (!canonical.equals(file.getAbsolutePath())) return 0;
        } catch (Exception e) { return 0; }
        if (file.isFile()) return Math.max(0, file.length());
        if (!visited.add(canonical)) return 0;
        long total = 0;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) total = saturatedAdd(total, directorySize(child, visited, depth + 1));
        }
        return total;
    }

    private static long saturatedAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }

    public static final class Snapshot {
        public final long memoryBytes, cacheBytes, cacheGrowthBytes;
        public final int batteryLevel, batteryScale, batteryCurrentMicroAmps, batteryTemperatureTenthsC, batteryStatus;
        public final int activeDownloads, backgroundProcesses, pendingSendTasks;
        public final boolean foreground;
        public final long elapsedRealtime;

        Snapshot(long memoryBytes, long cacheBytes, long cacheGrowthBytes, int batteryLevel, int batteryScale,
                 int batteryCurrentMicroAmps, int batteryTemperatureTenthsC, int batteryStatus,
                 int activeDownloads, int backgroundProcesses, int pendingSendTasks, boolean foreground, long elapsedRealtime) {
            this.memoryBytes = memoryBytes;
            this.cacheBytes = cacheBytes;
            this.cacheGrowthBytes = cacheGrowthBytes;
            this.batteryLevel = batteryLevel;
            this.batteryScale = batteryScale;
            this.batteryCurrentMicroAmps = batteryCurrentMicroAmps;
            this.batteryTemperatureTenthsC = batteryTemperatureTenthsC;
            this.batteryStatus = batteryStatus;
            this.activeDownloads = activeDownloads;
            this.backgroundProcesses = backgroundProcesses;
            this.pendingSendTasks = pendingSendTasks;
            this.foreground = foreground;
            this.elapsedRealtime = elapsedRealtime;
        }

        static Snapshot empty() {
            return new Snapshot(0, -1, Long.MIN_VALUE, -1, -1, Integer.MIN_VALUE, Integer.MIN_VALUE,
                    -1, 0, 0, 0, false, 0);
        }
    }
}
