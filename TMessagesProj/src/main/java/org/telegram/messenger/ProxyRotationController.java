package org.telegram.messenger;

import android.content.SharedPreferences;
import android.os.SystemClock;

import org.telegram.tgnet.ConnectionsManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProxyRotationController implements NotificationCenter.NotificationCenterDelegate {
    private final static ProxyRotationController INSTANCE = new ProxyRotationController();
    private final static long RECENT_CHECK_REUSE_MS = 2L * 60L * 1000L;

    public final static int DEFAULT_TIMEOUT_INDEX = 3;
    public final static List<Integer> ROTATION_TIMEOUTS = Arrays.asList(
            2, 5, 10, 15
    );

    private boolean isCurrentlyChecking;
    private int currentCheckToken;
    private final ArrayList<SharedConfig.ProxyInfo> pendingCheckProxies = new ArrayList<>();
    private final Runnable checkProxyAndSwitchRunnable = this::runProxyCheckAndSwitch;

    private void runProxyCheckAndSwitch() {
        SharedConfig.loadProxyList();
        if (!shouldRotate()) {
            resetCurrentBatch();
            return;
        }

        AndroidUtilities.cancelRunOnUIThread(checkProxyAndSwitchRunnable);
        pendingCheckProxies.clear();
        isCurrentlyChecking = true;
        currentCheckToken++;

        int currentAccount = UserConfig.selectedAccount;
        long now = SystemClock.elapsedRealtime();
        for (int i = 0; i < SharedConfig.proxyList.size(); i++) {
            SharedConfig.ProxyInfo proxyInfo = SharedConfig.proxyList.get(i);
            if (proxyInfo.checking) {
                pendingCheckProxies.add(proxyInfo);
                continue;
            }
            if (hasRecentCheck(proxyInfo, now)) {
                continue;
            }
            pendingCheckProxies.add(proxyInfo);
            proxyInfo.checking = true;
            proxyInfo.proxyCheckPingId = ConnectionsManager.getInstance(currentAccount).checkProxy(proxyInfo.address, proxyInfo.port, proxyInfo.username, proxyInfo.password, proxyInfo.secret, time -> AndroidUtilities.runOnUIThread(() -> {
                proxyInfo.availableCheckTime = SystemClock.elapsedRealtime();
                proxyInfo.checking = false;
                if (time == -1) {
                    proxyInfo.available = false;
                    proxyInfo.ping = 0;
                } else {
                    proxyInfo.ping = time;
                    proxyInfo.available = true;
                }
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyCheckDone, proxyInfo);
            }));
        }

        if (pendingCheckProxies.isEmpty()) {
            finishCheckBatch(currentCheckToken);
        }
    }

    public static void init() {
        INSTANCE.initInternal();
    }

    public static void refreshSchedule(boolean immediate) {
        INSTANCE.refreshScheduleInternal(immediate);
    }

    private boolean shouldRotate() {
        return SharedConfig.isProxyEnabled() && SharedConfig.proxyRotationEnabled && SharedConfig.proxyList.size() > 1;
    }

    private boolean hasRecentCheck(SharedConfig.ProxyInfo proxyInfo, long now) {
        return proxyInfo.availableCheckTime > 0
                && proxyInfo.availableCheckTime <= now
                && now - proxyInfo.availableCheckTime < RECENT_CHECK_REUSE_MS;
    }

    private void resetCurrentBatch() {
        currentCheckToken++;
        pendingCheckProxies.clear();
        isCurrentlyChecking = false;
    }

    private void refreshScheduleInternal(boolean immediate) {
        SharedConfig.loadProxyList();
        AndroidUtilities.cancelRunOnUIThread(checkProxyAndSwitchRunnable);
        resetCurrentBatch();
        if (!shouldRotate()) {
            return;
        }
        if (immediate) {
            checkProxyAndSwitchRunnable.run();
        } else {
            AndroidUtilities.runOnUIThread(checkProxyAndSwitchRunnable, getRotationIntervalMs());
        }
    }

    private void finishCheckBatch(int token) {
        if (token != currentCheckToken) {
            return;
        }
        isCurrentlyChecking = false;
        pendingCheckProxies.clear();
        switchToLowestPing();
        if (token != currentCheckToken) {
            return;
        }
        if (shouldRotate()) {
            AndroidUtilities.runOnUIThread(checkProxyAndSwitchRunnable, getRotationIntervalMs());
        }
    }

    private long getRotationIntervalMs() {
        int index = Math.max(0, Math.min(SharedConfig.proxyRotationTimeout, ROTATION_TIMEOUTS.size() - 1));
        return ROTATION_TIMEOUTS.get(index) * 60L * 1000L;
    }

    private void switchToLowestPing() {
        SharedConfig.ProxyInfo bestProxy = null;
        for (int i = 0; i < SharedConfig.proxyList.size(); i++) {
            SharedConfig.ProxyInfo info = SharedConfig.proxyList.get(i);
            if (info.checking || !info.available) {
                continue;
            }
            if (bestProxy == null || info.ping < bestProxy.ping || info.ping == bestProxy.ping && info == SharedConfig.currentProxy) {
                bestProxy = info;
            }
        }
        if (bestProxy == null || bestProxy == SharedConfig.currentProxy) {
            return;
        }

        SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
        editor.putString("proxy_ip", bestProxy.address);
        editor.putString("proxy_pass", bestProxy.password);
        editor.putString("proxy_user", bestProxy.username);
        editor.putInt("proxy_port", bestProxy.port);
        editor.putString("proxy_secret", bestProxy.secret);
        editor.putBoolean("proxy_enabled", true);

        if (!bestProxy.secret.isEmpty()) {
            editor.putBoolean("proxy_enabled_calls", false);
        }
        editor.apply();

        SharedConfig.currentProxy = bestProxy;
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyChangedByRotation);
        ConnectionsManager.setProxySettings(true, SharedConfig.currentProxy.address, SharedConfig.currentProxy.port, SharedConfig.currentProxy.username, SharedConfig.currentProxy.password, SharedConfig.currentProxy.secret);
    }

    private void initInternal() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxyCheckDone);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxySettingsChanged);
        refreshScheduleInternal(true);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.proxyCheckDone) {
            if (!shouldRotate() || !isCurrentlyChecking) {
                return;
            }
            SharedConfig.ProxyInfo proxyInfo = (SharedConfig.ProxyInfo) args[0];
            if (!pendingCheckProxies.remove(proxyInfo)) {
                return;
            }
            if (pendingCheckProxies.isEmpty()) {
                finishCheckBatch(currentCheckToken);
            }
        } else if (id == NotificationCenter.proxySettingsChanged) {
            refreshScheduleInternal(false);
        }
    }
}
