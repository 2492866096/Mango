package org.telegram.messenger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Single catalog for extension switches, categories, labels and search descriptions. */
public final class MangoFeatureRegistry {
    public static final int ALL = 0, CHAT = 1, PRIVACY = 2, MEDIA = 3, AUTOMATION = 4, APPEARANCE = 5, TOOLS = 6;
    public static final int[] CATEGORY_LABELS = {R.string.MangoCategoryAll, R.string.MangoCategoryChat, R.string.MangoCategoryPrivacy,
            R.string.MangoCategoryMedia, R.string.MangoCategoryAutomation, R.string.MangoCategoryAppearance, R.string.MangoCategoryTools};
    public static final class Feature {
        public final String key;
        public final int title, description, category, id;
        Feature(int id, String key, int title, int description, int category) {
            this.id = id; this.key = key; this.title = title; this.description = description; this.category = category;
        }
    }
    public static final List<Feature> FEATURES;
    static {
        ArrayList<Feature> features = new ArrayList<>();
        features.add(new Feature(1000, "disable_double_tap_reactions", R.string.MangoDisableDoubleTap, R.string.MangoDisableDoubleTapInfo, 1));
        features.add(new Feature(1001, "expand_quotes", R.string.MangoExpandQuotes, R.string.MangoExpandQuotesInfo, 1));
        features.add(new Feature(1002, "reveal_spoilers", R.string.MangoRevealSpoilers, R.string.MangoRevealSpoilersInfo, 1));
        features.add(new Feature(1003, "hide_stories", R.string.MangoHideStories, R.string.MangoHideStoriesInfo, 2));
        features.add(new Feature(1004, "hide_my_stories", R.string.MangoHideMyStories, R.string.MangoHideMyStoriesInfo, 2));
        features.add(new Feature(1005, "keep_video_muted", R.string.MangoKeepVideoMuted, R.string.MangoKeepVideoMutedInfo, 3));
        features.add(new Feature(1006, "disable_sent_sound", R.string.MangoDisableSentSound, R.string.MangoDisableSentSoundInfo, 1));
        features.add(new Feature(1007, "disable_received_sound", R.string.MangoDisableReceivedSound, R.string.MangoDisableReceivedSoundInfo, 1));
        features.add(new Feature(1008, "auto_read", R.string.MangoAutoRead, R.string.MangoAutoReadInfo, 4));
        features.add(new Feature(1009, "auto_read_random_reply", R.string.MangoAutoReadRandomReply, R.string.MangoAutoReadRandomReplyInfo, 4));
        features.add(new Feature(1010, "confirm_send", R.string.MangoConfirmSend, R.string.MangoConfirmSendInfo, 1));
        features.add(new Feature(1011, "confirm_voice", R.string.MangoConfirmVoice, R.string.MangoConfirmVoiceInfo, 1));
        features.add(new Feature(1012, "confirm_bot_command", R.string.MangoConfirmBotCommand, R.string.MangoConfirmBotCommandInfo, 1));
        features.add(new Feature(1013, "silent_send_default", R.string.MangoSilentSend, R.string.MangoSilentSendInfo, 1));
        features.add(new Feature(1014, "hide_reaction_list", R.string.MangoHideReactionList, R.string.MangoHideReactionListInfo, CHAT));
        features.add(new Feature(1015, "hide_reaction_notifications", R.string.MangoHideReactionNotifications, R.string.MangoHideReactionNotificationsInfo, CHAT));
        features.add(new Feature(1016, "collapse_keyboard_after_send", R.string.MangoCollapseKeyboardAfterSend, R.string.MangoCollapseKeyboardAfterSendInfo, CHAT));
        features.add(new Feature(1017, "hide_sticker_time", R.string.MangoHideStickerTime, R.string.MangoHideStickerTimeInfo, MEDIA));
        FEATURES = Collections.unmodifiableList(features);
    }
    public static Feature find(int id) {
        for (Feature feature : FEATURES) if (feature.id == id) return feature;
        return null;
    }
    public static String label(String key) {
        if ("cross_account_inbox".equals(key)) return LocaleController.getString(R.string.MangoUnifiedBottomTab);
        if ("auto_read_random_reply_groups".equals(key)) return LocaleController.getString(R.string.MangoAutoReadReplyGroups);
        for (Feature feature : FEATURES) if (feature.key.equals(key)) return LocaleController.getString(feature.title);
        if (key.startsWith("legacy:")) return MangoLegacySettings.label(key.substring(7));
        if (key.startsWith("notification_")) return LocaleController.getString(R.string.MangoNotificationRules);
        if (key.startsWith("scam_")) return LocaleController.getString(R.string.MangoScamUsers);
        return key;
    }
    private MangoFeatureRegistry() {}
}
