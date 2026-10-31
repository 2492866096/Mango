package org.telegram.messenger;

import java.util.ArrayList;
import org.telegram.ui.Components.MangoDownloadSpeedHud;

public class MangoSettingsTest {
    private static int assertions;
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) {
        check(!MangoSettings.getBoolean(0, "confirm_send"), "new switches default off");
        check(MangoSettings.getHistory(0).isEmpty(), "reading preferences does not create history");
        MangoSettings.setBoolean(0, "confirm_send", true);
        ArrayList<MangoSettings.Change> history = MangoSettings.getHistory(0);
        check(history.size() == 1 && org.json.JSONObject.NULL.equals(history.get(0).before) && Boolean.TRUE.equals(history.get(0).after), "absent boolean and typed after values");
        check(history.get(0).timestamp > 0, "change timestamp stored");
        MangoSettings.setBoolean(0, "confirm_send", true);
        check(MangoSettings.getHistory(0).size() == 1, "no-op writes omitted");
        MangoSettings.restore(0, history.get(0));
        check(!MangoSettings.getBoolean(0, "confirm_send"), "undo takes effect immediately");
        check(MangoSettings.getHistory(0).size() == 2, "undo itself is audited");
        check(MangoSettings.getBoolean(0, "scam_users_enabled", true), "default-enabled preference starts enabled");
        MangoSettings.setBoolean(0, "scam_users_enabled", false);
        check(!MangoSettings.getBoolean(0, "scam_users_enabled", true), "first explicit false overrides true fallback");
        MangoSettings.Change disabled = MangoSettings.getHistory(0).get(0);
        check(org.json.JSONObject.NULL.equals(disabled.before) && Boolean.FALSE.equals(disabled.after), "default-enabled initial disable is audited");
        MangoSettings.restore(0, disabled);
        check(MangoSettings.getBoolean(0, "scam_users_enabled", true), "undo first disable restores true fallback");
        MangoSettings.setBoolean(0, "hide_stories", true);
        check(NotificationCenter.counts[2] == 1 && NotificationCenter.counts[3] == 1, "story visibility invalidation");
        check(!MangoSettings.getBoolean(1, "hide_stories"), "account slots isolated even for same owner");
        UserConfig.getInstance(0).owner = 900;
        check(!MangoSettings.getBoolean(0, "hide_stories") && MangoSettings.getHistory(0).isEmpty(), "reused account slot isolated by owner");
        UserConfig.getInstance(0).owner = 42;
        check(MangoSettings.getBoolean(0, "hide_stories"), "switching owner back retains old preferences");
        MangoSettings.setInt(0, "notification_rule_10_mode", 0);
        check(MangoSettings.getInt(0, "notification_rule_10_mode", -1) == 0, "explicit zero overrides absent inherited value");
        MangoSettings.restore(0, MangoSettings.getHistory(0).get(0));
        check(MangoSettings.getInt(0, "notification_rule_10_mode", -1) == -1, "undo unset override restores inheritance, not zero");
        MangoSettings.setInt(0, "threshold", 128);
        MangoSettings.setString(0, "empty_string", "");
        check(MangoSettings.getString(0, "empty_string", "fallback").equals(""), "explicit empty string overrides fallback");
        MangoSettings.restore(0, MangoSettings.getHistory(0).get(0));
        check(MangoSettings.getString(0, "empty_string", "fallback").equals("fallback"), "undo empty string restores missing key");
        MangoSettings.setString(0, "keywords", "中文\nwith newline");
        check(MangoSettings.getInt(0, "threshold", 50) == 128, "integer value roundtrip");
        check(MangoSettings.getString(0, "keywords", "").equals("中文\nwith newline"), "Unicode string roundtrip");
        MangoSettings.reset(0);
        check(MangoSettings.getInt(0, "threshold", 50) == 50, "reset removes value and respects nonzero fallback");
        check(MangoSettings.getString(0, "keywords", "fallback").equals("fallback"), "reset restores string fallback");
        check(!MangoSettings.getBoolean(0, "hide_stories"), "reset clears boolean values");
        check(!MangoSettings.getHistory(0).isEmpty(), "reset preserves history");
        for (MangoSettings.Change change : MangoSettings.getHistory(0)) {
            if (change.key.equals("threshold") && change.after == null) { MangoSettings.restore(0, change); break; }
        }
        check(MangoSettings.getInt(0, "threshold", 50) == 128, "undo reset retains integer type");
        MangoSettings.recordChange(0, "legacy:font_size", 16, 20);
        MangoSettings.restore(0, MangoSettings.getHistory(0).get(0));
        check("font_size".equals(MangoLegacySettings.lastKey) && Integer.valueOf(16).equals(MangoLegacySettings.lastValue), "legacy undo delegates to actual-setter adapter");
        for (int i = 0; i < 240; i++) MangoSettings.setInt(0, "counter", i);
        history = MangoSettings.getHistory(0);
        check(history.size() == 200, "history capped at 200");
        check(Integer.valueOf(239).equals(history.get(0).after), "newest changes first");
        int hudChanges = MangoDownloadSpeedHud.changes[0];
        MangoSettings.setBoolean(0, MangoDownloadSpeedHud.SETTING_KEY, true);
        check(MangoDownloadSpeedHud.changes[0] == hudChanges + 1 && MangoDownloadSpeedHud.enabled[0], "HUD receives persisted enabled value");
        MangoSettings.Change enableHud = MangoSettings.getHistory(0).get(0);
        MangoSettings.setBoolean(0, MangoDownloadSpeedHud.SETTING_KEY, true);
        check(MangoDownloadSpeedHud.changes[0] == hudChanges + 1, "no-op HUD edits do not refresh or recurse");
        MangoSettings.restore(0, enableHud);
        check(MangoDownloadSpeedHud.changes[0] == hudChanges + 2 && !MangoDownloadSpeedHud.enabled[0], "undo refreshes HUD with restored effective default");
        MangoSettings.setBoolean(0, MangoDownloadSpeedHud.SETTING_KEY, true);
        MangoSettings.reset(0);
        check(MangoDownloadSpeedHud.changes[0] == hudChanges + 4 && !MangoDownloadSpeedHud.enabled[0], "reset immediately notifies HUD of disabled default");
        MangoSettings.setBoolean(1, MangoDownloadSpeedHud.SETTING_KEY, true);
        check(MangoDownloadSpeedHud.changes[1] == 1 && MangoDownloadSpeedHud.enabled[1] && !MangoDownloadSpeedHud.enabled[0], "HUD notification preserves owning account");
        System.out.println("Mango settings tests passed: " + assertions + " assertions");
    }
}
