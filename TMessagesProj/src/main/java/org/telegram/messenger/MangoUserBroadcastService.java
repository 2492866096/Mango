package org.telegram.messenger;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;

import androidx.core.app.NotificationCompat;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.LaunchActivity;

/** Silent foreground execution, independent of the settings page or selected account. */
public final class MangoUserBroadcastService extends Service {
    private static final int NOTIFICATION_ID = 0x4d4742;
    private static final String CHANNEL = "mango_broadcast", CANCEL = "mango.broadcast.cancel";
    private Handler handler;
    private PowerManager.WakeLock wakeLock;
    private final long[] nextNetworkWakeAt = new long[UserConfig.MAX_ACCOUNT_COUNT];
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            int total = 0, processed = 0, skipped = 0;
            final long now = SystemClock.elapsedRealtime();
            for (int account : UserConfig.getActivatedAccounts()) {
                MangoUserBroadcastManager manager = MangoUserBroadcastManager.getInstance(account);
                if (manager.isRunning()) {
                    // A CPU wake lock does not keep Telegram's native network awake.
                    // Resume partially without changing the UI/online pause counters.
                    if (now >= nextNetworkWakeAt[account]) {
                        ConnectionsManager.getInstance(account).resumeNetworkMaybe();
                        nextNetworkWakeAt[account] = now + MangoUserBroadcastManager.INTERVAL_MS;
                    }
                    manager.tick(now);
                    total += manager.total(); processed += manager.processed(); skipped += manager.skipped();
                }
            }
            if (!hasRunning()) { stopSelf(); return; }
            if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire(10 * 60 * 1000L);
            showProgress(total, processed, skipped);
            handler.postDelayed(this, 500);
        }
    };
    public static boolean start() {
        try {
            Context context = ApplicationLoader.applicationContext;
            Intent intent = new Intent(context, MangoUserBroadcastService.class);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent); else context.startService(intent);
            return true;
        } catch (Exception e) { FileLog.e(e); return false; }
    }
    private boolean hasRunning() {
        for (int account : UserConfig.getActivatedAccounts()) if (MangoUserBroadcastManager.getInstance(account).isRunning()) return true;
        return false;
    }
    @Override public void onCreate() {
        super.onCreate();
        ApplicationLoader.postInitApplication();
        handler = new Handler(getMainLooper());
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        if (power != null) wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mango:broadcast");
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, LocaleController.getString(R.string.MangoBroadcastTitle), NotificationManager.IMPORTANCE_LOW);
            channel.setSound(null, null); channel.enableVibration(false); channel.setShowBadge(false);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        // Satisfy startForegroundService even if the job was cancelled before this callback.
        showProgress(0, 0, 0);
        if (intent != null && CANCEL.equals(intent.getAction())) {
            for (int account : UserConfig.getActivatedAccounts()) MangoUserBroadcastManager.getInstance(account).cancel();
            stopSelf(); return START_NOT_STICKY;
        }
        handler.removeCallbacks(tick);
        handler.post(tick);
        return START_NOT_STICKY;
    }
    private void showProgress(int total, int processed, int skipped) {
        Intent open = new Intent(this, LaunchActivity.class).setAction("mango.broadcast");
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent content = PendingIntent.getActivity(this, NOTIFICATION_ID, open, flags);
        PendingIntent cancel = PendingIntent.getService(this, NOTIFICATION_ID,
                new Intent(this, MangoUserBroadcastService.class).setAction(CANCEL), flags);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle(LocaleController.getString(R.string.MangoBroadcastTitle))
                .setContentText(LocaleController.formatString(R.string.MangoBroadcastProgress, processed - skipped, total, skipped))
                .setProgress(total, processed, total == 0).setContentIntent(content)
                .setOngoing(true).setSilent(true).setOnlyAlertOnce(true)
                .addAction(0, LocaleController.getString(R.string.Cancel), cancel);
        startForeground(NOTIFICATION_ID, notification.build());
    }
    // Android 15's dataSync time budget: retain progress and allow explicitly confirmed resume.
    public void onTimeout(int startId, int fgsType) { stopSelf(); }
    @Override public void onDestroy() {
        if (handler != null) handler.removeCallbacks(tick);
        for (int account : UserConfig.getActivatedAccounts()) MangoUserBroadcastManager.getInstance(account).pause();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        stopForeground(true);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
