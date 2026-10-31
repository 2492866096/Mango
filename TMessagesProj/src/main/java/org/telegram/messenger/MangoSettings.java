package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.ui.Components.MangoDownloadSpeedHud;

import java.util.ArrayList;
import java.util.Objects;

/** Extension preferences and reversible change history, isolated by slot and signed-in owner. */
public final class MangoSettings {
    private static final int HISTORY_LIMIT = 200;
    private MangoSettings() {}

    private static SharedPreferences preferences(int account) {
        return ApplicationLoader.applicationContext.getSharedPreferences("mango_settings_" + account + "_"
                + UserConfig.getInstance(account).getClientUserId(), Context.MODE_PRIVATE);
    }

    public static boolean getBoolean(int account, String key) { return preferences(account).getBoolean(key, false); }
    public static boolean getBoolean(int account, String key, boolean fallback) { return preferences(account).getBoolean(key, fallback); }
    public static String getString(int account, String key, String fallback) { return preferences(account).getString(key, fallback); }
    public static int getInt(int account, String key, int fallback) { return preferences(account).getInt(key, fallback); }
    public static void setBoolean(int account, String key, boolean value) { set(account, key, storedValue(account, key), value); }
    public static void setString(int account, String key, String value) { set(account, key, storedValue(account, key), value == null ? "" : value); }
    public static void setInt(int account, String key, int value) { set(account, key, storedValue(account, key), value); }

    private static Object storedValue(int account, String key) {
        Object value = preferences(account).getAll().get(key);
        return value == null ? JSONObject.NULL : value;
    }

    private static synchronized void set(int account, String key, Object before, Object after) {
        if (Objects.equals(before, after)) return;
        SharedPreferences.Editor editor = preferences(account).edit();
        if (after == null || after == JSONObject.NULL) editor.remove(key);
        else if (after instanceof Boolean) editor.putBoolean(key, (Boolean) after);
        else if (after instanceof Integer) editor.putInt(key, (Integer) after);
        else editor.putString(key, String.valueOf(after));
        editor.apply();
        recordChange(account, key, before, after);
        notifyChanged(account, key);
        MangoSettingsSync.onMangoChanged(account, key);
    }

    private static void notifyChanged(int account, String key) {
        if (key == null || key.equals(MangoDownloadSpeedHud.SETTING_KEY)) {
            MangoDownloadSpeedHud.onPreferenceChanged(account);
        }
        if (key == null || key.equals("hide_reaction_notifications")) {
            NotificationsController.getInstance(account).updateReactionNotifications();
        }
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces, 0);
        if (key == null || key.equals("hide_stories") || key.equals("hide_my_stories")) {
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.storiesUpdated);
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.storiesEnabledUpdate);
        }
    }

    public static final class Change {
        public final String key;
        public final Object before, after;
        public final long timestamp;
        Change(JSONObject json) {
            key = json.optString("key");
            before = json.opt("before");
            after = json.opt("after");
            timestamp = json.optLong("time");
        }
    }

    public static synchronized void recordChange(int account, String key, Object before, Object after) {
        if (Objects.equals(before, after)) return;
        try {
            JSONArray old = new JSONArray(preferences(account).getString("_history", "[]"));
            JSONArray history = new JSONArray();
            history.put(new JSONObject().put("key", key).put("before", before).put("after", after).put("time", System.currentTimeMillis()));
            for (int i = 0; i < Math.min(old.length(), HISTORY_LIMIT - 1); i++) history.put(old.get(i));
            preferences(account).edit().putString("_history", history.toString()).apply();
        } catch (Exception e) { FileLog.e(e); }
    }

    public static synchronized ArrayList<Change> getHistory(int account) {
        ArrayList<Change> result = new ArrayList<>();
        try {
            JSONArray history = new JSONArray(preferences(account).getString("_history", "[]"));
            for (int i = 0; i < history.length(); i++) result.add(new Change(history.getJSONObject(i)));
        } catch (Exception e) { FileLog.e(e); }
        return result;
    }

    public static void restore(int account, Change change) {
        if (change.before == null || change.before == JSONObject.NULL) {
            set(account, change.key, storedValue(account, change.key), JSONObject.NULL);
        } else if (change.key.startsWith("legacy:")) {
            MangoLegacySettings.restore(account, change.key.substring(7), change.before);
        } else if (change.before instanceof Boolean) setBoolean(account, change.key, (Boolean) change.before);
        else if (change.before instanceof Number) setInt(account, change.key, ((Number) change.before).intValue());
        else setString(account, change.key, String.valueOf(change.before));
    }

    /** Retain the audit trail; reset only actual behavior settings. */
    public static synchronized void reset(int account) {
        SharedPreferences preferences = preferences(account);
        java.util.Map<String, ?> previous = preferences.getAll();
        SharedPreferences.Editor editor = preferences.edit();
        for (String key : previous.keySet()) if (!key.startsWith("_")) editor.remove(key);
        editor.apply();
        for (String key : previous.keySet()) {
            if (!key.startsWith("_")) recordChange(account, key, previous.get(key), null);
        }
        notifyChanged(account, null);
        MangoSettingsSync.onMangoReset(account);
    }
}
