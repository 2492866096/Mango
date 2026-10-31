package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Synchronizes behavior switches only. Account ids, per-chat targets and histories stay account-owned. */
public final class MangoSettingsSync {
    private static final String STORE = "mango_extension_switch_sync";
    private static final Map<Integer, Long> initializedOwners = new HashMap<>();
    private static boolean applying;
    private MangoSettingsSync() {}

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled() { return preferences().getBoolean("enabled", false); }

    private static Map<String, Boolean> defaults() {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (MangoFeatureRegistry.Feature feature : MangoFeatureRegistry.FEATURES) result.put(feature.key, false);
        result.put("cross_account_inbox", true);
        result.put("scam_users_enabled", true);
        result.put("mango_quick_forward_account_selection", false);
        result.put("mango_download_speed_hud", false);
        return result;
    }

    public static void setEnabled(int source, boolean enabled) {
        if (!enabled) {
            preferences().edit().putBoolean("enabled", false).apply();
            initializedOwners.clear();
            return;
        }
        if (!UserConfig.getInstance(source).isClientActivated()) return;
        applying = true;
        try {
            // Read the effective values, including defaults never explicitly saved by the user.
            Map<String, Boolean> switches = new LinkedHashMap<>();
            for (Map.Entry<String, Boolean> entry : defaults().entrySet()) {
                switches.put("mango:" + entry.getKey(), MangoSettings.getBoolean(source, entry.getKey(), entry.getValue()));
            }
            for (Map.Entry<String, Boolean> entry : MangoLegacySettings.booleanSwitches(source).entrySet()) {
                switches.put("legacy:" + entry.getKey(), entry.getValue());
            }
            SharedPreferences.Editor editor = preferences().edit().putBoolean("enabled", true);
            for (String key : preferences().getAll().keySet()) if (key.startsWith("mango:") || key.startsWith("legacy:")) editor.remove(key);
            for (Map.Entry<String, Boolean> entry : switches.entrySet()) editor.putBoolean(entry.getKey(), entry.getValue());
            editor.apply();
            initializedOwners.clear();
            for (int account : UserConfig.getActivatedAccounts()) {
                if (account != source) applySnapshot(account);
                initializedOwners.put(account, UserConfig.getInstance(account).getClientUserId());
            }
            MangoLegacySettings.initialize(source);
        } finally { applying = false; }
    }

    public static void onMangoChanged(int source, String key) {
        Boolean fallback = defaults().get(key);
        if (applying || !isEnabled() || fallback == null) return;
        propagate(source, "mango:" + key, MangoSettings.getBoolean(source, key, fallback));
    }

    public static void onMangoReset(int source) {
        for (String key : defaults().keySet()) onMangoChanged(source, key);
    }

    public static void onLegacyChanged(int source, String key, Object value) {
        if (applying || !isEnabled() || !(value instanceof Boolean) || key.startsWith("daily_") || key.startsWith("echo_")) return;
        propagate(source, "legacy:" + key, (Boolean) value);
    }

    private static void propagate(int source, String key, boolean value) {
        if (!UserConfig.getInstance(source).isClientActivated()) return;
        applying = true;
        try {
            preferences().edit().putBoolean(key, value).apply();
            for (int target : UserConfig.getActivatedAccounts()) {
                if (target == source) continue;
                if (key.startsWith("mango:")) MangoSettings.setBoolean(target, key.substring(6), value);
                else MangoLegacySettings.applyBooleanSwitch(target, key.substring(7), value);
            }
        } finally { applying = false; }
    }

    /** Called when an activated slot is first initialized, including a new owner in a reused slot. */
    public static void initializeAccount(int account) {
        if (applying || !isEnabled() || !UserConfig.getInstance(account).isClientActivated()) return;
        long owner = UserConfig.getInstance(account).getClientUserId();
        if (Long.valueOf(owner).equals(initializedOwners.get(account))) return;
        applying = true;
        try {
            applySnapshot(account);
            initializedOwners.put(account, owner);
        } finally { applying = false; }
    }

    private static void applySnapshot(int account) {
        if (!UserConfig.getInstance(account).isClientActivated()) return;
        MangoLegacySettings.initialize(account);
        Map<String, Boolean> supported = defaults();
        for (Map.Entry<String, ?> entry : preferences().getAll().entrySet()) {
            if (!(entry.getValue() instanceof Boolean)) continue;
            String key = entry.getKey();
            boolean value = (Boolean) entry.getValue();
            if (key.startsWith("mango:") && supported.containsKey(key.substring(6))) {
                MangoSettings.setBoolean(account, key.substring(6), value);
            } else if (key.startsWith("legacy:")) {
                MangoLegacySettings.applyBooleanSwitch(account, key.substring(7), value);
            }
        }
    }
}
