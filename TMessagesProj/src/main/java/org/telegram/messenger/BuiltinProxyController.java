package org.telegram.messenger;

import android.app.Activity;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.tgnet.ConnectionsManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class BuiltinProxyController {

    private static final String DEFAULT_PROXY_LINK = "https://t.me/proxy?server=154.37.214.251&port=443&secret=ddefd4fd1582a2f0007eaff82d4338a265";
    private static final String OLD_DEFAULT_REMOTE_SOURCE_URL = "https://1812652903.v.123pan.cn/1812652903/24089242";
    private static final String PREVIOUS_DEFAULT_REMOTE_SOURCE_URL = "https://1812652903.v.123pan.cn/1812652903/25892092";
    private static final String DEFAULT_REMOTE_SOURCE_URL = "https://1812652903.v.123pan.cn/1812652903/JY/DL.txt";

    private static final String PREF_INITIALIZED = "builtin_proxy_initialized";
    private static final String PREF_MANAGED_SIGNATURE = "builtin_proxy_managed_signature";
    private static final String PREF_MANAGED_SIGNATURES = "builtin_proxy_managed_signatures";
    private static final String PREF_REMOTE_SOURCE_URL = "builtin_proxy_remote_source_url";
    private static final String PREF_LAST_SYNC_TIME = "builtin_proxy_last_sync_time";
    private static final String PREF_CACHED_PROXIES = "builtin_proxy_cached_proxies";
    private static final String PREF_CACHED_SOURCE = "builtin_proxy_cached_source";

    private static final long SYNC_INTERVAL_MS = 30L * 60L * 1000L;
    private static final long APPLY_WAIT_TIMEOUT_MS = 4000L;
    private static final long APPLY_WAIT_INTERVAL_MS = 150L;
    private static final long STARTUP_REAPPLY_DELAY_MS = 1200L;
    private static final int NETWORK_TIMEOUT_MS = 8000;

    private static final BuiltinProxyController INSTANCE = new BuiltinProxyController();

    private boolean initialized;
    private boolean syncInProgress;
    private boolean syncActivateRequested;
    private boolean lastSyncRestoredFromCache;
    private SyncResult recoveryResult;
    private SyncResult pendingSyncResult;
    private ArrayList<SharedConfig.ProxyInfo> fetchedSyncProxies;
    private final ArrayList<Utilities.Callback<Boolean>> syncCallbacks = new ArrayList<>();
    private boolean startupProxyEnsured;
    private final Runnable syncRunnable = () -> performRemoteSync(false, false, null);

    public static void bootstrap() {
        INSTANCE.bootstrapInternal();
    }

    public static void init() {
        INSTANCE.initInternal();
    }

    public static void ensureStartupProxyApplied() {
        INSTANCE.ensureStartupProxyAppliedInternal();
    }

    public static void refreshProxyConnection() {
        INSTANCE.reapplyCurrentProxySettings();
    }

    public static void syncNow(boolean force) {
        INSTANCE.performRemoteSync(force, false, null);
    }

    public static void syncNow(boolean force, Utilities.Callback<Boolean> onDone) {
        INSTANCE.performRemoteSync(force, false, onDone);
    }

    public static void syncNow(boolean force, boolean activateProxy, Utilities.Callback<Boolean> onDone) {
        INSTANCE.performRemoteSync(force, activateProxy, onDone);
    }

    public static boolean isSyncInProgress() {
        return INSTANCE.syncInProgress;
    }

    public static boolean wasLastSyncRestoredFromCache() {
        return INSTANCE.lastSyncRestoredFromCache;
    }

    public static boolean isCurrentSubscriptionHealthy() {
        return INSTANCE.isCurrentSubscriptionHealthyInternal();
    }

    public static String getRemoteSourceUrl() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        String url = preferences.getString(PREF_REMOTE_SOURCE_URL, DEFAULT_REMOTE_SOURCE_URL);
        if (TextUtils.equals(url, OLD_DEFAULT_REMOTE_SOURCE_URL) || TextUtils.equals(url, PREVIOUS_DEFAULT_REMOTE_SOURCE_URL)) {
            url = DEFAULT_REMOTE_SOURCE_URL;
            preferences.edit().putString(PREF_REMOTE_SOURCE_URL, url).apply();
        }
        return url == null ? "" : url.trim();
    }

    public static void setRemoteSourceUrl(String url) {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        preferences.edit()
                .putString(PREF_REMOTE_SOURCE_URL, url == null ? "" : url.trim())
                .apply();
        syncNow(true);
    }

    private void bootstrapInternal() {
        SharedConfig.loadProxyList();
        SharedConfig.ProxyInfo defaultProxy = AndroidUtilities.parseProxyInfo(DEFAULT_PROXY_LINK);
        if (defaultProxy == null) {
            return;
        }
        defaultProxy.builtin = true;
        defaultProxy = SharedConfig.addProxy(defaultProxy);
        String defaultSignature = buildSignature(defaultProxy);

        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        if (preferences.getBoolean(PREF_INITIALIZED, false)) {
            SharedPreferences.Editor editor = null;
            if (TextUtils.isEmpty(preferences.getString(PREF_MANAGED_SIGNATURE, ""))) {
                editor = preferences.edit().putString(PREF_MANAGED_SIGNATURE, defaultSignature);
            }
            if (TextUtils.isEmpty(preferences.getString(PREF_MANAGED_SIGNATURES, ""))) {
                if (editor == null) {
                    editor = preferences.edit();
                }
                editor.putString(PREF_MANAGED_SIGNATURES, defaultSignature);
            }
            if (editor != null) {
                editor.apply();
            }
            return;
        }

        applyProxyInternal(defaultProxy, true, true);
    }

    private void initInternal() {
        if (initialized) {
            return;
        }
        initialized = true;
        scheduleNextSync(false);
    }

    private void ensureStartupProxyAppliedInternal() {
        if (startupProxyEnsured) {
            return;
        }
        startupProxyEnsured = true;
        reapplyCurrentProxySettings();
        AndroidUtilities.runOnUIThread(this::reapplyCurrentProxySettings, STARTUP_REAPPLY_DELAY_MS);
    }

    private void scheduleNextSync(boolean immediate) {
        AndroidUtilities.cancelRunOnUIThread(syncRunnable);
        String remoteUrl = getRemoteSourceUrl();
        if (TextUtils.isEmpty(remoteUrl)) {
            return;
        }
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        long lastSync = preferences.getLong(PREF_LAST_SYNC_TIME, 0L);
        long delay = immediate ? 0L : Math.max(0L, SYNC_INTERVAL_MS - (System.currentTimeMillis() - lastSync));
        AndroidUtilities.runOnUIThread(syncRunnable, delay);
    }

    private void performRemoteSync(boolean force, boolean activateProxy, Utilities.Callback<Boolean> onDone) {
        String remoteUrl = getRemoteSourceUrl();
        if (TextUtils.isEmpty(remoteUrl)) {
            if (onDone != null) {
                onDone.run(false);
            }
            return;
        }
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        long lastSync = preferences.getLong(PREF_LAST_SYNC_TIME, 0L);
        if (!force && System.currentTimeMillis() - lastSync < SYNC_INTERVAL_MS) {
            scheduleNextSync(false);
            if (onDone != null) {
                onDone.run(false);
            }
            return;
        }
        if (!syncInProgress) {
            recoveryResult = null;
            pendingSyncResult = null;
            fetchedSyncProxies = null;
            lastSyncRestoredFromCache = false;
            syncActivateRequested = false;
        }
        if (onDone != null) {
            syncCallbacks.add(onDone);
        }
        syncActivateRequested |= activateProxy;
        if (activateProxy && !hasSavedManagedProxy()) {
            // Removing the active proxy can make the subscription unreachable. Restore
            // the last subscription (or the bundled default on first use) before fetching.
            ArrayList<SharedConfig.ProxyInfo> cached = loadCachedProxies(remoteUrl);
            if (!cached.isEmpty()) {
                recoveryResult = handleRemoteProxies(cached, true);
            }
        }
        if (syncInProgress) {
            if (activateProxy && fetchedSyncProxies != null) {
                // A manual refresh can join after the fetch, while application is
                // still being checked. Upgrade that result too, not just the flag.
                pendingSyncResult = handleRemoteProxies(fetchedSyncProxies, true);
            }
            return;
        }
        syncInProgress = true;
        Utilities.globalQueue.postRunnable(() -> {
            ArrayList<SharedConfig.ProxyInfo> remoteProxies = fetchRemoteProxies(remoteUrl);
            AndroidUtilities.runOnUIThread(() -> {
                preferences.edit().putLong(PREF_LAST_SYNC_TIME, System.currentTimeMillis()).apply();
                if (!remoteProxies.isEmpty()) {
                    saveCachedProxies(remoteUrl, remoteProxies);
                    fetchedSyncProxies = remoteProxies;
                    pendingSyncResult = handleRemoteProxies(remoteProxies, syncActivateRequested);
                    waitForProxyApplied(this::finishRemoteSync);
                } else {
                    lastSyncRestoredFromCache = recoveryResult != null && isSyncResultApplied(recoveryResult);
                    finishRemoteSync(lastSyncRestoredFromCache);
                }
            });
        });
    }

    private void finishRemoteSync(boolean success) {
        syncInProgress = false;
        ArrayList<Utilities.Callback<Boolean>> callbacks = new ArrayList<>(syncCallbacks);
        syncCallbacks.clear();
        recoveryResult = null;
        pendingSyncResult = null;
        fetchedSyncProxies = null;
        syncActivateRequested = false;
        scheduleNextSync(false);
        for (Utilities.Callback<Boolean> callback : callbacks) {
            callback.run(success);
        }
    }

    private boolean hasSavedManagedProxy() {
        SharedConfig.loadProxyList();
        Set<String> signatures = getManagedSignatures(MessagesController.getGlobalMainSettings());
        for (SharedConfig.ProxyInfo proxy : SharedConfig.proxyList) {
            if (signatures.contains(buildSignature(proxy))) {
                return true;
            }
        }
        return false;
    }

    private ArrayList<SharedConfig.ProxyInfo> loadCachedProxies(String source) {
        ArrayList<SharedConfig.ProxyInfo> proxies = new ArrayList<>();
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        if (TextUtils.equals(source, preferences.getString(PREF_CACHED_SOURCE, ""))) {
            try {
                JSONArray entries = new JSONArray(preferences.getString(PREF_CACHED_PROXIES, "[]"));
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject entry = entries.getJSONObject(i);
                    String address = entry.getString("address");
                    int port = entry.getInt("port");
                    if (!TextUtils.isEmpty(address) && port > 0 && port <= 65535) {
                        proxies.add(new SharedConfig.ProxyInfo(address, port, entry.optString("username"), entry.optString("password"), entry.optString("secret"), entry.optString("name")));
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        if (proxies.isEmpty() && TextUtils.equals(source, DEFAULT_REMOTE_SOURCE_URL)) {
            SharedConfig.ProxyInfo proxy = AndroidUtilities.parseProxyInfo(DEFAULT_PROXY_LINK);
            if (proxy != null) {
                proxies.add(proxy);
            }
        }
        return proxies;
    }

    private void saveCachedProxies(String source, ArrayList<SharedConfig.ProxyInfo> proxies) {
        try {
            JSONArray entries = new JSONArray();
            for (SharedConfig.ProxyInfo proxy : proxies) {
                JSONObject entry = new JSONObject();
                entry.put("address", proxy.address);
                entry.put("port", proxy.port);
                entry.put("username", proxy.username);
                entry.put("password", proxy.password);
                entry.put("secret", proxy.secret);
                entry.put("name", proxy.displayName);
                entries.put(entry);
            }
            MessagesController.getGlobalMainSettings().edit()
                    .putString(PREF_CACHED_SOURCE, source)
                    .putString(PREF_CACHED_PROXIES, entries.toString()).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private boolean isCurrentSubscriptionHealthyInternal() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        if (!preferences.getBoolean("proxy_enabled", false)) {
            return false;
        }
        String currentSignature = getCurrentProxySignature(preferences);
        if (TextUtils.isEmpty(currentSignature) || !getManagedSignatures(preferences).contains(currentSignature)) {
            return false;
        }
        int state = ConnectionsManager.getInstance(UserConfig.selectedAccount).getConnectionState();
        return state == ConnectionsManager.ConnectionStateConnected || state == ConnectionsManager.ConnectionStateUpdating;
    }

    private SyncResult handleRemoteProxies(ArrayList<SharedConfig.ProxyInfo> remoteProxies, boolean activateProxy) {
        SharedConfig.loadProxyList();

        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        Set<String> previousManagedSignatures = getManagedSignatures(preferences);
        ArrayList<String> targetSignatures = new ArrayList<>();
        HashSet<String> targetSignatureSet = new HashSet<>();
        for (int i = 0; i < remoteProxies.size(); i++) {
            String signature = buildSignature(remoteProxies.get(i));
            if (targetSignatureSet.add(signature)) {
                targetSignatures.add(signature);
            }
        }
        if (targetSignatures.isEmpty()) {
            return null;
        }

        HashSet<String> addedSignatures = new HashSet<>();
        for (int i = remoteProxies.size() - 1; i >= 0; i--) {
            SharedConfig.ProxyInfo remoteProxy = remoteProxies.get(i);
            remoteProxy.builtin = true;
            String signature = buildSignature(remoteProxy);
            if (!addedSignatures.add(signature)) {
                continue;
            }
            SharedConfig.ProxyInfo savedProxy = SharedConfig.addProxy(remoteProxy);
            if (savedProxy.builtin) {
                if (!TextUtils.isEmpty(remoteProxy.displayName) && !TextUtils.equals(remoteProxy.displayName, savedProxy.displayName)) {
                    savedProxy.displayName = remoteProxy.displayName;
                }
                SharedConfig.proxyList.remove(savedProxy);
                SharedConfig.proxyList.add(0, savedProxy);
            }
        }
        SharedConfig.saveProxyList();

        String currentSignature = getCurrentProxySignature(preferences);
        boolean shouldAutoApply = activateProxy || (preferences.getBoolean("proxy_enabled", false)
                && !TextUtils.isEmpty(currentSignature)
                && previousManagedSignatures.contains(currentSignature));
        SharedConfig.ProxyInfo targetProxy = null;
        if (shouldAutoApply) {
            if (!activateProxy && targetSignatureSet.contains(currentSignature)) {
                targetProxy = findProxyBySignature(currentSignature);
            }
            if (targetProxy == null) {
                targetProxy = findProxyBySignature(targetSignatures.get(0));
            }
        }

        preferences.edit()
                .putString(PREF_MANAGED_SIGNATURE, targetSignatures.get(0))
                .putString(PREF_MANAGED_SIGNATURES, encodeSignatures(targetSignatures))
                .apply();

        if (targetProxy != null) {
            applyProxyInternal(targetProxy, true, false);
            if (removeStaleManagedProxies(previousManagedSignatures, targetSignatureSet)) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
            }
        } else {
            removeStaleManagedProxies(previousManagedSignatures, targetSignatureSet);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
        }
        return new SyncResult(targetProxy != null ? buildSignature(targetProxy) : targetSignatures.get(0), targetProxy != null);
    }

    private void waitForProxyApplied(Utilities.Callback<Boolean> onDone) {
        if (pendingSyncResult == null) {
            if (onDone != null) {
                onDone.run(false);
            }
            return;
        }
        long deadline = System.currentTimeMillis() + APPLY_WAIT_TIMEOUT_MS;
        Runnable[] poll = new Runnable[1];
        poll[0] = () -> {
            if (pendingSyncResult != null && isSyncResultApplied(pendingSyncResult)) {
                if (onDone != null) {
                    onDone.run(true);
                }
            } else if (System.currentTimeMillis() >= deadline) {
                if (onDone != null) {
                    onDone.run(false);
                }
            } else {
                AndroidUtilities.runOnUIThread(poll[0], APPLY_WAIT_INTERVAL_MS);
            }
        };
        poll[0].run();
    }

    private boolean isSyncResultApplied(SyncResult syncResult) {
        SharedConfig.loadProxyList();
        boolean exists = false;
        for (int i = 0; i < SharedConfig.proxyList.size(); i++) {
            if (TextUtils.equals(buildSignature(SharedConfig.proxyList.get(i)), syncResult.signature)) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            return false;
        }
        if (!syncResult.applied) {
            return true;
        }
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        return preferences.getBoolean("proxy_enabled", false)
                && TextUtils.equals(syncResult.signature, getCurrentProxySignature(preferences));
    }

    private ArrayList<SharedConfig.ProxyInfo> fetchRemoteProxies(String remoteUrl) {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        ByteArrayOutputStream outputStream = null;
        try {
            connection = (HttpURLConnection) new URL(remoteUrl).openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(NETWORK_TIMEOUT_MS);
            connection.setReadTimeout(NETWORK_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setRequestProperty("User-Agent", "TelegramBuiltInProxy/1.0");
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_ACCEPTED) {
                return new ArrayList<>();
            }

            inputStream = connection.getInputStream();
            outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            String text = outputStream.toString("UTF-8");
            if (text != null && text.startsWith("\uFEFF")) {
                text = text.substring(1);
            }
            return parseRemoteProxyList(text);
        } catch (Throwable e) {
            FileLog.e(e);
            return new ArrayList<>();
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception ignore) {
            }
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (Exception ignore) {
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private ArrayList<SharedConfig.ProxyInfo> parseRemoteProxyList(String text) {
        ArrayList<SharedConfig.ProxyInfo> proxies = new ArrayList<>();
        HashSet<String> signatures = new HashSet<>();
        if (TextUtils.isEmpty(text)) {
            return proxies;
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n");
        for (int i = 0; i < lines.length; i++) {
            addRemoteProxyLine(proxies, signatures, lines[i]);
        }
        if (proxies.isEmpty()) {
            addRemoteProxyLine(proxies, signatures, text);
        }
        return proxies;
    }

    private void addRemoteProxyLine(ArrayList<SharedConfig.ProxyInfo> proxies, HashSet<String> signatures, String line) {
        if (line == null) {
            return;
        }
        line = line.trim();
        if (TextUtils.isEmpty(line)) {
            return;
        }
        SharedConfig.ProxyInfo proxyInfo = AndroidUtilities.parseProxyInfo(line);
        if (proxyInfo == null) {
            return;
        }
        String displayName = extractRemoteProxyDisplayName(line);
        if (!TextUtils.isEmpty(displayName)) {
            proxyInfo.displayName = displayName;
        }
        String signature = buildSignature(proxyInfo);
        if (signatures.add(signature)) {
            proxies.add(proxyInfo);
        }
    }

    private String extractRemoteProxyDisplayName(String line) {
        int markerIndex = findProxyMarkerIndex(line);
        if (markerIndex <= 0) {
            return "";
        }
        String displayName = line.substring(0, markerIndex).trim();
        while (!TextUtils.isEmpty(displayName)) {
            char last = displayName.charAt(displayName.length() - 1);
            if (last == '-' || last == ':' || last == '|' || last == ',' || last == ';'
                    || last == '\u2013' || last == '\u2014' || last == '\uFF0D' || last == '\uFF1A') {
                displayName = displayName.substring(0, displayName.length() - 1).trim();
            } else {
                break;
            }
        }
        return displayName;
    }

    private int findProxyMarkerIndex(String text) {
        String lower = text.toLowerCase(Locale.US);
        String[] markers = new String[]{
                "https://t.me/proxy?", "http://t.me/proxy?", "https://telegram.me/proxy?", "http://telegram.me/proxy?", "https://telegram.dog/proxy?", "http://telegram.dog/proxy?",
                "https://t.me/socks?", "http://t.me/socks?", "https://telegram.me/socks?", "http://telegram.me/socks?", "https://telegram.dog/socks?", "http://telegram.dog/socks?",
                "t.me/proxy?", "telegram.me/proxy?", "telegram.dog/proxy?",
                "t.me/socks?", "telegram.me/socks?", "telegram.dog/socks?",
                "tg://proxy?", "tg:proxy?", "tg://socks?", "tg:socks?",
                "socks5://", "socks://", "mtproto://"
        };
        int markerIndex = Integer.MAX_VALUE;
        for (String marker : markers) {
            int found = lower.indexOf(marker);
            if (found >= 0 && found < markerIndex) {
                markerIndex = found;
            }
        }
        return markerIndex == Integer.MAX_VALUE ? -1 : markerIndex;
    }

    private void applyProxyInternal(SharedConfig.ProxyInfo proxyInfo, boolean enabled, boolean markInitialized) {
        SharedConfig.loadProxyList();
        proxyInfo = SharedConfig.addProxy(proxyInfo);
        SharedConfig.currentProxy = proxyInfo;

        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("proxy_ip", proxyInfo.address);
        editor.putString("proxy_pass", proxyInfo.password);
        editor.putString("proxy_user", proxyInfo.username);
        editor.putInt("proxy_port", proxyInfo.port);
        editor.putString("proxy_secret", proxyInfo.secret);
        editor.putBoolean("proxy_enabled", enabled);
        if (!proxyInfo.secret.isEmpty()) {
            editor.putBoolean("proxy_enabled_calls", false);
        }
        editor.putString(PREF_MANAGED_SIGNATURE, buildSignature(proxyInfo));
        if (markInitialized) {
            editor.putBoolean(PREF_INITIALIZED, true);
            editor.putString(PREF_MANAGED_SIGNATURES, buildSignature(proxyInfo));
        }
        editor.commit();

        ConnectionsManager.setProxySettings(enabled, proxyInfo.address, proxyInfo.port, proxyInfo.username, proxyInfo.password, proxyInfo.secret);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
    }

    private void reapplyCurrentProxySettings() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        boolean enabled = preferences.getBoolean("proxy_enabled", false);
        String address = preferences.getString("proxy_ip", "");
        int port = preferences.getInt("proxy_port", 0);
        if (!enabled || TextUtils.isEmpty(address) || port <= 0) {
            return;
        }

        SharedConfig.loadProxyList();
        SharedConfig.ProxyInfo proxyInfo = SharedConfig.addProxy(new SharedConfig.ProxyInfo(
                address,
                port,
                preferences.getString("proxy_user", ""),
                preferences.getString("proxy_pass", ""),
                preferences.getString("proxy_secret", "")
        ));
        SharedConfig.currentProxy = proxyInfo;

        // Always set proxy for account 0 directly, since getActivatedAccounts()
        // may return empty on first install before any user has logged in.
        ConnectionsManager.native_setProxySettings(0, proxyInfo.address, proxyInfo.port, proxyInfo.username, proxyInfo.password, proxyInfo.secret);
        int[] activatedAccounts = UserConfig.getActivatedAccounts();
        for (int i = 0; i < activatedAccounts.length; i++) {
            int account = activatedAccounts[i];
            if (account != 0) {
                ConnectionsManager.native_setProxySettings(account, proxyInfo.address, proxyInfo.port, proxyInfo.username, proxyInfo.password, proxyInfo.secret);
            }
        }

        ConnectionsManager.getInstance(0).checkConnection();
        for (int i = 0; i < activatedAccounts.length; i++) {
            ConnectionsManager.getInstance(activatedAccounts[i]).checkConnection();
        }
    }

    private Set<String> getManagedSignatures(SharedPreferences preferences) {
        HashSet<String> signatures = new HashSet<>();
        String primarySignature = preferences.getString(PREF_MANAGED_SIGNATURE, "");
        if (!TextUtils.isEmpty(primarySignature)) {
            signatures.add(primarySignature);
        }
        String encodedSignatures = preferences.getString(PREF_MANAGED_SIGNATURES, "");
        if (!TextUtils.isEmpty(encodedSignatures)) {
            String[] parts = encodedSignatures.split("\n");
            for (int i = 0; i < parts.length; i++) {
                String signature = parts[i].trim();
                if (!TextUtils.isEmpty(signature)) {
                    signatures.add(signature);
                }
            }
        }
        return signatures;
    }

    private String encodeSignatures(List<String> signatures) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < signatures.size(); i++) {
            if (TextUtils.isEmpty(signatures.get(i))) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(signatures.get(i));
        }
        return builder.toString();
    }

    private SharedConfig.ProxyInfo findProxyBySignature(String signature) {
        if (TextUtils.isEmpty(signature)) {
            return null;
        }
        for (int i = 0; i < SharedConfig.proxyList.size(); i++) {
            SharedConfig.ProxyInfo proxyInfo = SharedConfig.proxyList.get(i);
            if (TextUtils.equals(signature, buildSignature(proxyInfo))) {
                return proxyInfo;
            }
        }
        return null;
    }

    private boolean removeStaleManagedProxies(Set<String> previousManagedSignatures, Set<String> currentManagedSignatures) {
        if (previousManagedSignatures.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (int i = SharedConfig.proxyList.size() - 1; i >= 0; i--) {
            SharedConfig.ProxyInfo proxyInfo = SharedConfig.proxyList.get(i);
            String signature = buildSignature(proxyInfo);
            if (proxyInfo.builtin && previousManagedSignatures.contains(signature) && !currentManagedSignatures.contains(signature) && proxyInfo != SharedConfig.currentProxy) {
                SharedConfig.proxyList.remove(i);
                changed = true;
            }
        }
        if (changed) {
            SharedConfig.saveProxyList();
        }
        return changed;
    }

    private static String getCurrentProxySignature(SharedPreferences preferences) {
        String address = preferences.getString("proxy_ip", "");
        int port = preferences.getInt("proxy_port", 0);
        if (TextUtils.isEmpty(address) || port <= 0) {
            return "";
        }
        return buildSignature(
                address,
                port,
                preferences.getString("proxy_user", ""),
                preferences.getString("proxy_pass", ""),
                preferences.getString("proxy_secret", "")
        );
    }

    private static String buildSignature(SharedConfig.ProxyInfo proxyInfo) {
        return buildSignature(proxyInfo.address, proxyInfo.port, proxyInfo.username, proxyInfo.password, proxyInfo.secret);
    }

    private static String buildSignature(String address, int port, String username, String password, String secret) {
        return (address == null ? "" : address)
                + ":" + port
                + "|" + (username == null ? "" : username)
                + "|" + (password == null ? "" : password)
                + "|" + (secret == null ? "" : secret);
    }

    private static class SyncResult {
        private final String signature;
        private final boolean applied;

        private SyncResult(String signature, boolean applied) {
            this.signature = signature;
            this.applied = applied;
        }
    }
}
