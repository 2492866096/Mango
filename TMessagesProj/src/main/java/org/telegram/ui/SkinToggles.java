package org.telegram.ui;

import android.app.Activity;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.ProxyRotationController;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;

/**
 * The four switches on the 我 page's control deck. Each one does exactly what
 * the matching switch in Telegram's own settings does, so the two never
 * disagree; a long press opens that settings page.
 */
final class SkinToggles {
    static final int NIGHT = 0;
    static final int PROXY = 1;
    static final int DATA_SAVER = 2;
    static final int QUIET = 3;

    private static final int[] QUIET_TYPES = {
            NotificationsController.TYPE_PRIVATE,
            NotificationsController.TYPE_GROUP,
            NotificationsController.TYPE_CHANNEL,
    };
    private static final String QUIET_SAVED = "skin_quiet_saved";

    private SkinToggles() {
    }

    static boolean isOn(int id, int account) {
        switch (id) {
            case NIGHT:
                return Theme.isCurrentThemeDark();
            case PROXY:
                return SharedConfig.isProxyEnabled();
            case DATA_SAVER:
                return !DownloadController.getInstance(account).mobilePreset.enabled;
            case QUIET:
                NotificationsController notifications = NotificationsController.getInstance(account);
                for (int type : QUIET_TYPES) {
                    if (notifications.isGlobalNotificationsEnabled(type)) return false;
                }
                return true;
        }
        return false;
    }

    /** [origin] is where the tap was, in window coordinates; the theme change spreads from there. */
    static void toggle(BaseFragment fragment, int id, int[] origin) {
        final int account = fragment.getCurrentAccount();
        switch (id) {
            case NIGHT:
                switchTheme(fragment, origin);
                break;
            case PROXY:
                toggleProxy(fragment);
                break;
            case DATA_SAVER:
                toggleDataSaver(account);
                break;
            case QUIET:
                toggleQuiet(account);
                break;
        }
    }

    static void openDetails(BaseFragment fragment, int id) {
        switch (id) {
            case NIGHT:
                fragment.presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_NIGHT));
                break;
            case PROXY:
                fragment.presentFragment(new ProxyListActivity());
                break;
            case DATA_SAVER:
                fragment.presentFragment(new DataSettingsActivity());
                break;
            case QUIET:
                fragment.presentFragment(new NotificationsSettingsActivity());
                break;
        }
    }

    // As the chat list's own day/night item does, falling back to the skin's
    // themes when no other day or night theme has been picked.
    private static void switchTheme(BaseFragment fragment, int[] origin) {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE);
        String dayThemeName = preferences.getString("lastDayTheme", "Frost");
        if (Theme.getTheme(dayThemeName) == null || Theme.getTheme(dayThemeName).isDark()) {
            dayThemeName = "Frost";
        }
        String nightThemeName = preferences.getString("lastDarkTheme", "Frost Night");
        if (Theme.getTheme(nightThemeName) == null || !Theme.getTheme(nightThemeName).isDark()) {
            nightThemeName = "Frost Night";
        }
        boolean toDark = !Theme.isCurrentThemeDark();
        Theme.ThemeInfo themeInfo = Theme.getTheme(toDark ? nightThemeName : dayThemeName);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, themeInfo, false, origin, -1, toDark, null, null, null, true);
        Theme.turnOffAutoNight(BulletinFactory.of(fragment), () -> fragment.presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_NIGHT)));
    }

    // As the "Use proxy" switch on the proxy list does.
    private static void toggleProxy(BaseFragment fragment) {
        SharedConfig.loadProxyList();
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        boolean enabled = preferences.getBoolean("proxy_enabled", false) && !SharedConfig.proxyList.isEmpty();
        if (SharedConfig.currentProxy == null) {
            if (SharedConfig.proxyList.isEmpty()) {
                // Nothing to switch on yet: go and add one.
                fragment.presentFragment(new ProxySettingsActivity());
                return;
            }
            SharedConfig.currentProxy = SharedConfig.proxyList.get(0);
            if (!enabled) {
                preferences.edit()
                        .putString("proxy_ip", SharedConfig.currentProxy.address)
                        .putString("proxy_pass", SharedConfig.currentProxy.password)
                        .putString("proxy_user", SharedConfig.currentProxy.username)
                        .putInt("proxy_port", SharedConfig.currentProxy.port)
                        .putString("proxy_secret", SharedConfig.currentProxy.secret)
                        .commit();
            }
        }
        enabled = !enabled;
        SharedPreferences.Editor editor = preferences.edit().putBoolean("proxy_enabled", enabled);
        if (!enabled) {
            editor.putBoolean("proxy_enabled_calls", false);
        }
        editor.commit();
        SharedConfig.ProxyInfo proxy = SharedConfig.currentProxy;
        ConnectionsManager.setProxySettings(enabled, proxy.address, proxy.port, proxy.username, proxy.password, proxy.secret);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
        ProxyRotationController.refreshSchedule(enabled && SharedConfig.proxyRotationEnabled);
    }

    // Data saver means no automatic downloads over mobile data, the same
    // switch as "When using mobile data" in data settings.
    private static void toggleDataSaver(int account) {
        DownloadController downloads = DownloadController.getInstance(account);
        DownloadController.Preset preset = downloads.mobilePreset;
        preset.enabled = !preset.enabled;
        MessagesController.getMainSettings(account).edit()
                .putString("mobilePreset", preset.toString())
                .putInt("currentMobilePreset", 3)
                .commit();
        downloads.checkAutodownloadSettings();
        downloads.savePresetToServer(0);
    }

    // Do not disturb mutes private chats, groups and channels together, and
    // remembers how each was set so turning it off puts them back.
    private static void toggleQuiet(int account) {
        NotificationsController notifications = NotificationsController.getInstance(account);
        SharedPreferences settings = MessagesController.getNotificationsSettings(account);
        if (!isOn(QUIET, account)) {
            StringBuilder saved = new StringBuilder();
            for (int type : QUIET_TYPES) {
                if (saved.length() > 0) saved.append(',');
                saved.append(settings.getInt(NotificationsController.getGlobalNotificationsKey(type), 0));
            }
            settings.edit().putString(QUIET_SAVED, saved.toString()).commit();
            for (int type : QUIET_TYPES) {
                notifications.setGlobalNotificationsEnabled(type, Integer.MAX_VALUE);
            }
        } else {
            String[] saved = settings.getString(QUIET_SAVED, "").split(",");
            int now = ConnectionsManager.getInstance(account).getCurrentTime();
            for (int i = 0; i < QUIET_TYPES.length; i++) {
                int until = 0;
                if (i < saved.length) {
                    try {
                        until = Integer.parseInt(saved[i]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                // A type that was already muted before, and still would be,
                // stays muted; the rest come back on.
                notifications.setGlobalNotificationsEnabled(QUIET_TYPES[i], until > now ? until : 0);
            }
            settings.edit().remove(QUIET_SAVED).commit();
        }
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.notificationsSettingsUpdated);
    }
}
