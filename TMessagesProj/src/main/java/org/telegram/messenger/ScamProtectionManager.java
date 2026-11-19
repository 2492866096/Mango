package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.LinkedHashSet;

/** Local keyword hints only; it never sends message contents to another service. */
public final class ScamProtectionManager {
    public static final String DEFAULT_KEYWORDS = "刷单返利\n先交保证金\n解冻费\n安全账户\n助记词\n私钥\n保证收益\n稳赚不赔\n充值解锁提现\nseed phrase\nverify your wallet\nguaranteed profit";
    private static final LinkedHashSet<String> shown = new LinkedHashSet<>();
    private static final DispatchQueue downloadQueue = new DispatchQueue("scamKeywords");
    private static final ArrayList<Runnable> listeners = new ArrayList<>();
    public static final long REFRESH_INTERVAL_MS = 2 * 60 * 60 * 1000L;
    private static final long RETRY_INTERVAL_MS = 5 * 60 * 1000L;
    private static ArrayList<String> remoteKeywords = new ArrayList<>();
    private static boolean remoteLoaded, refreshing, refreshFailed;
    private static long lastUpdated, lastAttemptElapsed = -1;
    private static boolean automaticRefreshStarted; // Main thread only.
    private static final Runnable periodicRefresh = new Runnable() {
        @Override public void run() {
            refreshForEnabledAccounts();
            scheduleNextRefresh();
        }
    };

    /** A real timer while alive, plus a network-constrained system job after process death. */
    public static void startAutomaticRefresh() {
        AndroidUtilities.runOnUIThread(() -> {
            if (!automaticRefreshStarted) {
                automaticRefreshStarted = true;
                ScamKeywordsUpdateService.schedule();
            }
            // Also called on foreground entry so elapsed time during device sleep is caught up.
            periodicRefresh.run();
        });
    }

    private static boolean hasEnabledAccount() {
        for (int account : UserConfig.getActivatedAccounts()) {
            if (UserConfig.getInstance(account).isClientActivated() && isEnabled(account)) return true;
        }
        return false;
    }

    public static void refreshForEnabledAccounts() {
        ScamUsersManager.refreshAll();
        if (hasEnabledAccount()) refresh(false);
    }

    private static void scheduleNextRefresh() {
        AndroidUtilities.runOnUIThread(() -> {
            if (!automaticRefreshStarted) return;
            AndroidUtilities.cancelRunOnUIThread(periodicRefresh);
            long delay = hasEnabledAccount() ? getRefreshDelay() : REFRESH_INTERVAL_MS;
            AndroidUtilities.runOnUIThread(periodicRefresh, Math.max(1000L, delay));
        });
    }

    private static synchronized long getRefreshDelay() {
        ensureRemoteLoaded();
        long age = System.currentTimeMillis() - lastUpdated;
        long delay = !remoteKeywords.isEmpty() && age >= 0 && age < REFRESH_INTERVAL_MS
                ? REFRESH_INTERVAL_MS - age : 0;
        if (lastAttemptElapsed >= 0) {
            long sinceAttempt = SystemClock.elapsedRealtime() - lastAttemptElapsed;
            if (sinceAttempt >= 0 && sinceAttempt < RETRY_INTERVAL_MS) {
                delay = Math.max(delay, RETRY_INTERVAL_MS - sinceAttempt);
            }
        }
        return refreshing ? Math.max(delay, RETRY_INTERVAL_MS) : delay;
    }

    private static SharedPreferences remotePreferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("remote_scam_keywords", Context.MODE_PRIVATE);
    }

    private static synchronized void ensureRemoteLoaded() {
        if (remoteLoaded) return;
        remoteLoaded = true;
        SharedPreferences prefs = remotePreferences();
        if (!RemoteScamKeywords.SOURCE_URL.equals(prefs.getString("source", ""))) return;
        try {
            remoteKeywords = RemoteScamKeywords.parse(prefs.getString("keywords", ""));
            lastUpdated = prefs.getLong("updated_at", 0);
        } catch (Exception ignored) {
            // Invalid or incomplete cache never disables the bundled fallback.
        }
    }

    public static void addListener(Runnable listener) { synchronized (listeners) { if (!listeners.contains(listener)) listeners.add(listener); } }
    public static void removeListener(Runnable listener) { synchronized (listeners) { listeners.remove(listener); } }
    private static void notifyChanged() {
        AndroidUtilities.runOnUIThread(() -> {
            ArrayList<Runnable> snapshot;
            synchronized (listeners) { snapshot = new ArrayList<>(listeners); }
            for (Runnable listener : snapshot) listener.run();
        });
    }

    public static synchronized boolean isRefreshing() { return refreshing; }
    public static synchronized boolean hasRefreshFailed() { return refreshFailed; }
    public static synchronized long getLastUpdated() { ensureRemoteLoaded(); return lastUpdated; }
    public static synchronized int getRemoteKeywordCount() { ensureRemoteLoaded(); return remoteKeywords.size(); }

    /** Matching never waits for HTTP; retries and concurrent requests are coalesced. */
    public static void refreshIfNeeded(int account) {
        if (isEnabled(account)) refresh(false);
    }

    public static void refresh(boolean force) {
        synchronized (ScamProtectionManager.class) {
            ensureRemoteLoaded();
            if (refreshing) return;
            if (!force && getRefreshDelay() > 0) return;
            refreshing = true;
            refreshFailed = false;
            lastAttemptElapsed = SystemClock.elapsedRealtime();
        }
        notifyChanged();
        downloadQueue.postRunnable(() -> {
            try {
                ArrayList<String> downloaded = RemoteScamKeywords.fetch();
                long now = System.currentTimeMillis();
                if (!remotePreferences().edit().putString("source", RemoteScamKeywords.SOURCE_URL)
                        .putString("keywords", android.text.TextUtils.join("\n", downloaded))
                        .putLong("updated_at", now).commit()) throw new java.io.IOException("Cannot save keyword cache");
                synchronized (ScamProtectionManager.class) {
                    remoteKeywords = downloaded;
                    lastUpdated = now;
                    refreshing = false;
                    refreshFailed = false;
                }
            } catch (Exception error) {
                synchronized (ScamProtectionManager.class) { refreshing = false; refreshFailed = true; }
                FileLog.e(error);
            }
            notifyChanged();
            scheduleNextRefresh();
        });
    }

    private static SharedPreferences preferences(int account) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        return ApplicationLoader.applicationContext.getSharedPreferences("scam_protection_" + account + "_" + owner, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(int account) {
        return preferences(account).getBoolean("enabled", true);
    }

    public static void setEnabled(int account, boolean value) {
        preferences(account).edit().putBoolean("enabled", value).apply();
        if (value) refreshIfNeeded(account);
        startAutomaticRefresh();
    }

    public static ArrayList<String> getKeywords(int account) {
        LinkedHashSet<String> combined;
        synchronized (ScamProtectionManager.class) {
            ensureRemoteLoaded();
            combined = new LinkedHashSet<>(remoteKeywords.isEmpty() ? MessageInsightsRules.parseKeywords(DEFAULT_KEYWORDS) : remoteKeywords);
        }
        combined.addAll(getCustomKeywords(account));
        return new ArrayList<>(combined);
    }

    public static ArrayList<String> getCustomKeywords(int account) {
        String saved = preferences(account).getString("keywords", "");
        // Earlier releases persisted the bundled list when restoring defaults.
        ArrayList<String> words = MessageInsightsRules.parseKeywords(saved);
        return words.equals(MessageInsightsRules.parseKeywords(DEFAULT_KEYWORDS)) ? new ArrayList<>() : words;
    }

    public static void setKeywords(int account, String value) {
        preferences(account).edit().putString("keywords", android.text.TextUtils.join("\n", MessageInsightsRules.parseKeywords(value))).apply();
        synchronized (shown) { shown.clear(); }
    }

    public static String match(int account, MessageObject message) {
        if (!isEnabled(account) || message == null || message.messageOwner == null || message.isOut()
                || message.messageOwner.action != null && !(message.messageOwner.action instanceof org.telegram.tgnet.TLRPC.TL_messageActionEmpty)) return null;
        refreshIfNeeded(account);
        return MessageInsightsRules.matchingKeyword(message.messageOwner.message, getKeywords(account));
    }

    /** Once per content revision per account, bounded to avoid keeping old chat contents. */
    public static boolean markShown(int account, MessageObject message, String keyword) {
        String key = account + ":" + UserConfig.getInstance(account).getClientUserId() + ":" + message.getDialogId() + ":" + message.getId()
                + ":" + message.messageOwner.edit_date + ":" + keyword;
        synchronized (shown) {
            if (!shown.add(key)) return false;
            while (shown.size() > 2000) shown.remove(shown.iterator().next());
            return true;
        }
    }
}
