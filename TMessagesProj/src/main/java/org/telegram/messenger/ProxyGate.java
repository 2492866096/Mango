package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceArray;

// The built-in proxy is switched on for everyone. Outside mainland China it is not
// needed, so there it starts off (still listed, still usable by hand) as long as
// Telegram is reachable without it. The region is checked on every start; once the
// user changes any proxy setting the gate stays out of the way for good.
public class ProxyGate {

    private static final String PREF_HOLD = "proxy_gate_hold";
    private static final String PREF_TURNED_OFF = "proxy_gate_turned_off";
    private static final String PREF_USER_SET = "proxy_gate_user_set";
    // BuiltinProxyController's first-launch marker.
    private static final String PREF_BUILTIN_INITIALIZED = "builtin_proxy_initialized";

    private static final int DECISION_BUDGET_MS = 3000;
    private static final int PROBE_TIMEOUT_MS = 2500;
    private static final int MAX_RESPONSE_BYTES = 4096;

    // DC1 in Miami, DC2 and DC4 in Amsterdam.
    private static final String[] TELEGRAM_ADDRESSES = {"149.154.175.53", "149.154.167.51", "149.154.167.91"};
    private static final Set<String> MAINLAND_TIME_ZONES = new HashSet<>(Arrays.asList(
            "Asia/Shanghai", "Asia/Chongqing", "Asia/Chungking", "Asia/Harbin", "Asia/Urumqi", "Asia/Kashgar", "PRC"));

    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final ArrayList<Runnable> syncTasks = new ArrayList<>();
    private static boolean decided;
    private static boolean applying;

    private static final NotificationCenter.NotificationCenterDelegate observer = (id, account, args) -> {
        if (id == NotificationCenter.proxySettingsChanged && !applying) {
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            if (!preferences.getBoolean(PREF_USER_SET, false) && isUserSet(preferences)) {
                finishDecision();
            }
        }
    };

    public static void bootstrap() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        boolean firstLaunch = !preferences.getBoolean(PREF_BUILTIN_INITIALIZED, false);
        BuiltinProxyController.bootstrap();
        NotificationCenter.getGlobalInstance().addObserver(observer, NotificationCenter.proxySettingsChanged);
        if (isUserSet(preferences)) {
            finishDecision();
            return;
        }
        if (firstLaunch) {
            // Keep the default that bootstrap just applied off until the region is known.
            hold(true);
        }
        new Thread(() -> {
            boolean holdProxy = shouldHold();
            handler.post(() -> onDecision(holdProxy));
        }, "ProxyGate").start();
    }

    public static boolean isHolding() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        return preferences.getBoolean(PREF_HOLD, false) && !preferences.getBoolean(PREF_USER_SET, false);
    }

    // The periodic sync with the proxy list waits for the decision and never runs while held.
    public static void runWhenSyncAllowed(Runnable task) {
        if (decided && !isHolding()) {
            task.run();
        } else {
            syncTasks.add(task);
        }
    }

    private static boolean isUserSet(SharedPreferences preferences) {
        if (preferences.getBoolean(PREF_USER_SET, false)) {
            return true;
        }
        SharedConfig.ProxyInfo current = SharedConfig.currentProxy;
        boolean userSet = SharedConfig.isVpnProxyAutoSwitchSetByUser()
                || SharedConfig.proxyRotationEnabled
                || current != null && !current.builtin
                // Nothing else turns the proxy on while it is held.
                || preferences.getBoolean(PREF_HOLD, false) && preferences.getBoolean("proxy_enabled", false);
        if (userSet) {
            preferences.edit()
                    .putBoolean(PREF_USER_SET, true)
                    .remove(PREF_HOLD)
                    .remove(PREF_TURNED_OFF)
                    .apply();
        }
        return userSet;
    }

    private static void onDecision(boolean holdProxy) {
        if (MessagesController.getGlobalMainSettings().getBoolean(PREF_USER_SET, false)) {
            finishDecision();
            return;
        }
        if (holdProxy) {
            hold(false);
            decided = true;
        } else {
            release();
            finishDecision();
        }
    }

    private static void finishDecision() {
        decided = true;
        if (isHolding()) {
            return;
        }
        ArrayList<Runnable> tasks = new ArrayList<>(syncTasks);
        syncTasks.clear();
        for (Runnable task : tasks) {
            task.run();
        }
    }

    private static void hold(boolean early) {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        boolean enabled = preferences.getBoolean("proxy_enabled", false);
        SharedPreferences.Editor editor = preferences.edit().putBoolean(PREF_HOLD, true);
        if (enabled) {
            editor.putBoolean(PREF_TURNED_OFF, true);
        }
        editor.commit();
        if (enabled) {
            setProxyEnabled(false, early);
        }
    }

    private static void release() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        boolean turnedOff = preferences.getBoolean(PREF_TURNED_OFF, false);
        preferences.edit().remove(PREF_HOLD).remove(PREF_TURNED_OFF).commit();
        if (turnedOff) {
            setProxyEnabled(true, false);
        }
        ApplicationLoader.syncProxyWithVpnState(true);
    }

    private static void setProxyEnabled(boolean enabled, boolean early) {
        SharedConfig.ProxyInfo proxy = SharedConfig.currentProxy;
        if (enabled && proxy == null) {
            return;
        }
        SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit().putBoolean("proxy_enabled", enabled);
        if (!enabled || !TextUtils.isEmpty(proxy.secret)) {
            editor.putBoolean("proxy_enabled_calls", false);
        }
        editor.commit();

        applying = true;
        try {
            if (enabled) {
                ConnectionsManager.setProxySettings(true, proxy.address, proxy.port, proxy.username, proxy.password, proxy.secret);
                // Also reaches account 0 before anyone has logged in.
                BuiltinProxyController.refreshProxyConnection();
            } else {
                ConnectionsManager.setProxySettings(false, "", 0, "", "", "");
                // Account 0 is not activated until login, but its connection is already up.
                if (!early && !isActivated(0)) {
                    ConnectionsManager.native_setProxySettings(0, "", 1080, "", "", "");
                }
            }
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
        } finally {
            applying = false;
        }
    }

    private static boolean isActivated(int account) {
        for (int activated : UserConfig.getActivatedAccounts()) {
            if (activated == account) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldHold() {
        boolean vpn = ApplicationLoader.isVpnActive();
        AtomicReferenceArray<String> countries = new AtomicReferenceArray<>(2);
        AtomicBoolean telegramReachable = new AtomicBoolean();
        CountDownLatch done = new CountDownLatch((vpn ? 0 : 2) + TELEGRAM_ADDRESSES.length);
        // Behind a VPN the address only tells where the VPN exits.
        if (!vpn) {
            probe(done, () -> countries.set(0, cloudflareCountry()));
            probe(done, () -> countries.set(1, ipipCountry()));
        }
        for (String address : TELEGRAM_ADDRESSES) {
            probe(done, () -> {
                if (canConnect(address)) {
                    telegramReachable.set(true);
                }
            });
        }
        try {
            done.await(DECISION_BUDGET_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignore) {
        }

        Boolean mainland = vpn ? null : mainlandFromAddress(countries.get(0), countries.get(1));
        if (mainland == null) {
            mainland = mainlandFromDevice();
        }
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("proxy gate: vpn=" + vpn + " address=" + countries.get(0) + "/" + countries.get(1)
                    + " mainland=" + mainland + " telegram=" + telegramReachable.get());
        }
        // Turning the proxy off must not cut Telegram off.
        return !mainland && telegramReachable.get();
    }

    private static void probe(CountDownLatch done, Runnable task) {
        new Thread(() -> {
            try {
                task.run();
            } finally {
                done.countDown();
            }
        }, "ProxyGateProbe").start();
    }

    // Two services that disagree count as no answer.
    private static Boolean mainlandFromAddress(String first, String second) {
        if (first == null) {
            return second == null ? null : "CN".equals(second);
        }
        if (second == null || "CN".equals(first) == "CN".equals(second)) {
            return "CN".equals(first);
        }
        return null;
    }

    private static boolean mainlandFromDevice() {
        try {
            TelephonyManager telephony = (TelephonyManager) ApplicationLoader.applicationContext.getSystemService(Context.TELEPHONY_SERVICE);
            if (telephony != null && ("cn".equalsIgnoreCase(telephony.getSimCountryIso()) || "cn".equalsIgnoreCase(telephony.getNetworkCountryIso()))) {
                return true;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return MAINLAND_TIME_ZONES.contains(TimeZone.getDefault().getID());
    }

    private static String cloudflareCountry() {
        String body = fetch("https://www.cloudflare.com/cdn-cgi/trace");
        if (body == null) {
            return null;
        }
        for (String line : body.split("\n")) {
            if (line.startsWith("loc=")) {
                String code = line.substring(4).trim().toUpperCase(Locale.US);
                return code.matches("[A-Z]{2}") ? code : null;
            }
        }
        return null;
    }

    // Reachable from the mainland; regions come back in Chinese.
    private static String ipipCountry() {
        String body = fetch("https://myip.ipip.net/json");
        if (body == null) {
            return null;
        }
        try {
            JSONArray location = new JSONObject(body).getJSONObject("data").getJSONArray("location");
            String country = location.optString(0);
            if (TextUtils.isEmpty(country)) {
                return null;
            }
            if (!"中国".equals(country)) {
                // Somewhere else; which country does not matter here.
                return "ZZ";
            }
            switch (location.optString(1)) {
                case "香港":
                    return "HK";
                case "澳门":
                    return "MO";
                case "台湾":
                    return "TW";
                default:
                    return "CN";
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static String fetch(String address) {
        HttpURLConnection connection = null;
        try {
            // Straight from this device's network, past any configured proxy.
            connection = (HttpURLConnection) new URL(address).openConnection(Proxy.NO_PROXY);
            connection.setConnectTimeout(PROBE_TIMEOUT_MS);
            connection.setReadTimeout(PROBE_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            try (InputStream input = connection.getInputStream()) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int read;
                while (output.size() < MAX_RESPONSE_BYTES && (read = input.read(buffer)) > 0) {
                    output.write(buffer, 0, read);
                }
                return output.toString("UTF-8");
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static boolean canConnect(String address) {
        try (Socket socket = new Socket(Proxy.NO_PROXY)) {
            socket.connect(new InetSocketAddress(address, 443), PROBE_TIMEOUT_MS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
