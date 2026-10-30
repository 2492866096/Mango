package org.telegram.messenger;

import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Pattern;

/** Independent global/per-conversation notification filters; chat messages are retained. */
public final class MangoNotificationRules {
    public static final int ALL = 0, MENTIONS = 1, KEYWORDS = 2, MENTIONS_OR_KEYWORDS = 3;
    private MangoNotificationRules() { }

    private static String key(long dialogId, String field) {
        return "notification_rule_" + dialogId + "_" + field;
    }
    public static int mode(int account, long dialogId) {
        int local = MangoSettings.getInt(account, key(dialogId, "mode"), dialogId == 0 ? ALL : -1);
        return local < 0 ? dialogId == 0 ? ALL : mode(account, 0) : Math.min(MENTIONS_OR_KEYWORDS, local);
    }
    public static int ownMode(int account, long dialogId) {
        return Math.max(dialogId == 0 ? ALL : -1,
                Math.min(MENTIONS_OR_KEYWORDS, MangoSettings.getInt(account, key(dialogId, "mode"), dialogId == 0 ? ALL : -1)));
    }
    public static void setMode(int account, long dialogId, int mode) {
        MangoSettings.setInt(account, key(dialogId, "mode"), Math.max(dialogId == 0 ? ALL : -1, Math.min(3, mode)));
    }
    public static String keywords(int account, long dialogId) {
        if (dialogId != 0 && ownMode(account, dialogId) < 0) return keywords(account, 0);
        return MangoSettings.getString(account, key(dialogId, "keywords"), "");
    }
    public static void setKeywords(int account, long dialogId, String words) {
        MangoSettings.setString(account, key(dialogId, "keywords"),
                android.text.TextUtils.join("\n", MessageInsightsRules.parseKeywords(words)));
    }
    public static boolean allows(int mode, boolean mentioned, String text, String words) {
        if (mode == ALL) return true;
        boolean keyword = MessageInsightsRules.matchingKeyword(text, MessageInsightsRules.parseKeywords(words)) != null;
        return mode == MENTIONS ? mentioned : mode == KEYWORDS ? keyword : mentioned || keyword;
    }
    public static boolean shouldNotify(int account, MessageObject message) {
        if (message == null || message.messageOwner == null || message.isOauthPush) return true;
        if (message.isStoryPush || message.isStoryReactionPush) {
            return !MangoSettings.getBoolean(account, "hide_stories");
        }
        long did = message.getDialogId();
        int mode = mode(account, did);
        if (mode == ALL) return true;
        String text = message.messageOwner.message;
        boolean mentioned = message.messageOwner.mentioned;
        String username = UserObject.getPublicUsername(UserConfig.getInstance(account).getCurrentUser());
        if (!mentioned && username != null && !username.isEmpty() && text != null) {
            mentioned = Pattern.compile("(?i)(?<![A-Za-z0-9_])@" + Pattern.quote(username)
                    + "(?![A-Za-z0-9_])").matcher(text).find();
        }
        return allows(mode, mentioned, text, keywords(account, did));
    }
}
