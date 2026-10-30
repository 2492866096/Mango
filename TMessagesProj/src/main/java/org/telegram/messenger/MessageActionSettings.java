package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;

/** Local interaction preferences, scoped to the signed-in user rather than an account slot. */
public final class MessageActionSettings {
    public static final int DOUBLE_TAP_REACTION = 0;
    public static final int DOUBLE_TAP_REPLY = 1;
    public static final int DOUBLE_TAP_NONE = 2;
    public static final int ACTION_COPY = 0;
    public static final int ACTION_FORWARD = 1;
    public static final int ACTION_FAVORITE = 2;
    public static final int ACTION_TODO = 3;

    private MessageActionSettings() {}

    private static SharedPreferences preferences(int account) {
        return ApplicationLoader.applicationContext.getSharedPreferences(
                "message_actions_" + account + "_" + UserConfig.getInstance(account).getClientUserId(), Context.MODE_PRIVATE);
    }

    public static boolean isCenterEnabled(int account) {
        return preferences(account).getBoolean("center_enabled", false);
    }

    public static void setCenterEnabled(int account, boolean enabled) {
        preferences(account).edit().putBoolean("center_enabled", enabled).apply();
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces, 0);
    }

    public static int getDoubleTapAction(int account) {
        int value = preferences(account).getInt("double_tap", DOUBLE_TAP_REACTION);
        if (value < DOUBLE_TAP_REACTION || value > DOUBLE_TAP_NONE) value = DOUBLE_TAP_REACTION;
        return value == DOUBLE_TAP_REACTION && MangoSettings.getBoolean(account, "disable_double_tap_reactions")
                ? DOUBLE_TAP_NONE : value;
    }

    public static void setDoubleTapAction(int account, int action) {
        if (action >= DOUBLE_TAP_REACTION && action <= DOUBLE_TAP_NONE) {
            preferences(account).edit().putInt("double_tap", action).apply();
        }
    }

    public static int[] getMenuOrder(int account) {
        ArrayList<Integer> values = new ArrayList<>();
        String[] saved = preferences(account).getString("menu_order", "0,1,2,3").split(",");
        for (String value : saved) {
            try {
                int action = Integer.parseInt(value);
                if (action >= ACTION_COPY && action <= ACTION_TODO && !values.contains(action)) values.add(action);
            } catch (NumberFormatException ignored) {}
        }
        for (int action = ACTION_COPY; action <= ACTION_TODO; action++) {
            if (!values.contains(action)) values.add(action);
        }
        int[] result = new int[4];
        for (int i = 0; i < 4; i++) result[i] = values.get(i);
        return result;
    }

    public static void setMenuOrder(int account, int[] order) {
        if (order == null || order.length != 4) return;
        boolean[] seen = new boolean[4];
        StringBuilder encoded = new StringBuilder();
        for (int action : order) {
            if (action < 0 || action > 3 || seen[action]) return;
            seen[action] = true;
            if (encoded.length() > 0) encoded.append(',');
            encoded.append(action);
        }
        preferences(account).edit().putString("menu_order", encoded.toString()).apply();
    }

    /** Reorders only the requested actions; all other context-menu positions are preserved. */
    public static void reorderMenu(int account, ArrayList<Integer> icons, ArrayList<CharSequence> labels,
                                   ArrayList<Integer> options, int copy, int forward, int favorite, int todo) {
        int[] optionIds = {copy, forward, favorite, todo};
        ArrayList<Integer> positions = new ArrayList<>();
        ArrayList<Integer> sortedOptions = new ArrayList<>();
        ArrayList<Integer> sortedIcons = new ArrayList<>();
        ArrayList<CharSequence> sortedLabels = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            for (int optionId : optionIds) {
                if (options.get(i) == optionId) {
                    positions.add(i);
                    break;
                }
            }
        }
        for (int action : getMenuOrder(account)) {
            int position = options.indexOf(optionIds[action]);
            if (position >= 0) {
                sortedOptions.add(options.get(position));
                sortedIcons.add(icons.get(position));
                sortedLabels.add(labels.get(position));
            }
        }
        for (int i = 0; i < positions.size(); i++) {
            int position = positions.get(i);
            options.set(position, sortedOptions.get(i));
            icons.set(position, sortedIcons.get(i));
            labels.set(position, sortedLabels.get(i));
        }
    }
}
