package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Adapter for older preferences. Never clear preference files: they also contain login and proxy data. */
public final class MangoLegacySettings {
    private interface Getter { Object get(); }
    private interface Setter { void set(Object value); }
    private static final class Setting {
        final String key;
        final int label;
        final Getter getter;
        final Setter setter;
        final Object defaultValue;
        Setting(String key, int label, Getter getter, Setter setter, Object defaultValue) {
            this.key=key; this.label=label; this.getter=getter; this.setter=setter; this.defaultValue=defaultValue;
        }
    }
    private static final class Observer {
        long owner;
        final ArrayList<SharedPreferences> stores = new ArrayList<>();
        final Map<String,Object> previous = new HashMap<>();
        SharedPreferences.OnSharedPreferenceChangeListener listener;
        Runnable capture;
    }
    private static final Map<Integer, Observer> observers = new HashMap<>();
    private static final Map<String, Integer> labels = new HashMap<>();
    private MangoLegacySettings() {}
    private static String empty(String value) { return value == null ? "" : value; }
    private static String joinInts(int[] values) { return values[0]+","+values[1]+","+values[2]+","+values[3]; }
    private static int[] parseInts(String value) { String[] p=value.split(","); return new int[]{Integer.parseInt(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2]),Integer.parseInt(p[3])}; }
    private static String sortedIds(java.util.Collection<Long> ids) {
        java.util.ArrayList<Long> sorted = new java.util.ArrayList<>(ids);
        java.util.Collections.sort(sorted);
        return android.text.TextUtils.join(",", sorted);
    }
    private static java.util.ArrayList<Long> parseIds(String value) {
        java.util.ArrayList<Long> ids = new java.util.ArrayList<>();
        for (String part : value.split(",")) if (!part.isEmpty()) ids.add(Long.parseLong(part));
        return ids;
    }
    private static String schedule(DailyMessageTasksManager.Task task) {
        return task.mode + "," + task.hour + "," + task.minute + "," + task.randomWindowStart + "," + task.randomWindowEnd;
    }
    private static void applySchedule(DailyMessageTasksManager.Task task, String encoded) {
        String[] values = encoded.split(",");
        task.mode = Integer.parseInt(values[0]); task.hour = Integer.parseInt(values[1]); task.minute = Integer.parseInt(values[2]);
        task.randomWindowStart = Integer.parseInt(values[3]); task.randomWindowEnd = Integer.parseInt(values[4]);
    }
    private static ArrayList<Setting> settings(int account) {
        MessagesController c = MessagesController.getInstance(account);
        ArrayList<Setting> items = new ArrayList<>();
        items.add(new Setting("hide_online", R.string.HideOnlineStatus, () -> c.isHideOnlineStatusEnabled(), v -> { c.setHideOnlineStatusEnabled((Boolean) v); }, false));
        items.add(new Setting("hide_typing", R.string.HideTypingStatus, () -> c.isHideTypingStatusEnabled(), v -> { c.setHideTypingStatusEnabled((Boolean) v); }, false));
        items.add(new Setting("hide_read", R.string.HideReadStatus, () -> c.isHideReadStatusEnabled(), v -> { c.setHideReadStatusEnabled((Boolean) v); }, false));
        items.add(new Setting("anti_revoke", R.string.AntiRevoke, () -> c.isAntiRevokeEnabled(), v -> { c.setAntiRevokeEnabled((Boolean) v); }, true));
        items.add(new Setting("force_forward", R.string.ForceForward, () -> c.isForceForwardEnabled(), v -> { c.setForceForwardEnabled((Boolean) v); }, true));
        items.add(new Setting("unlimited_pin", R.string.UnlimitedPin, () -> c.isUnlimitedPinEnabled(), v -> { c.setUnlimitedPinEnabled((Boolean) v); }, true));
        items.add(new Setting("unread_tab", R.string.UnreadDialogsTab, () -> c.isUnreadDialogsTabEnabled(), v -> { c.setUnreadDialogsTabEnabled((Boolean) v); }, false));
        items.add(new Setting("scroll_channel", R.string.ContinueScrollChannelJump, () -> c.isContinueScrollChannelJumpEnabled(), v -> { c.setContinueScrollChannelJumpEnabled((Boolean) v); }, true));
        items.add(new Setting("chinese_read", R.string.ChineseReadStatusText, () -> c.isChineseReadStatusTextEnabled(), v -> { c.setChineseReadStatusTextEnabled((Boolean) v); }, true));
        items.add(new Setting("hide_send_as", R.string.HideChannelSendAsTitle, () -> SharedConfig.isHideChannelSendAsEnabled(), v -> { SharedConfig.setHideChannelSendAsEnabled((Boolean) v); }, false));
        items.add(new Setting("high_quality", R.string.DefaultHighQualityMediaTitle, () -> SharedConfig.isDefaultHighQualityMediaEnabled(), v -> { SharedConfig.setDefaultHighQualityMediaEnabled((Boolean) v); }, true));
        items.add(new Setting("tablet_width", R.string.TabletFullWidthTitle, () -> SharedConfig.isTabletFullWidthEnabled(), v -> { SharedConfig.setTabletFullWidthEnabled((Boolean) v); }, true));
        items.add(new Setting("channel_feed", R.string.ChannelFeedTitle, () -> SharedConfig.isChannelFeedEnabled(), v -> { SharedConfig.setChannelFeedEnabled((Boolean) v); }, true));
        items.add(new Setting("unlimited_channels", R.string.UnlimitedChannelJoinTitle, () -> SharedConfig.isUnlimitedChannelJoinEnabled(), v -> { SharedConfig.setUnlimitedChannelJoinEnabled((Boolean) v); }, true));
        items.add(new Setting("unlimited_filters", R.string.UnlimitedDialogFiltersTitle, () -> SharedConfig.isUnlimitedDialogFiltersEnabled(), v -> { SharedConfig.setUnlimitedDialogFiltersEnabled((Boolean) v); }, true));
        items.add(new Setting("hold_verification", R.string.LocalHoldVerificationTitle, () -> SharedConfig.isLocalHoldVerificationEnabled(), v -> { SharedConfig.setLocalHoldVerificationEnabled((Boolean) v); }, true));
        items.add(new Setting("auto_reaction", R.string.AutoReactionTitle, () -> SharedConfig.isAutoReactionEnabled(), v -> { SharedConfig.setAutoReactionEnabled((Boolean) v); }, false));
        items.add(new Setting("virtual_gifts", R.string.LocalVirtualGiftBuyTitle, () -> SharedConfig.isLocalVirtualGiftBuyEnabled(), v -> { SharedConfig.setLocalVirtualGiftBuyEnabled((Boolean) v); }, true));
        items.add(new Setting("mp3_voice", R.string.SendMp3AsVoiceTitle, () -> SharedConfig.isSendMp3AsVoiceEnabled(), v -> { SharedConfig.setSendMp3AsVoiceEnabled((Boolean) v); }, true));
        items.add(new Setting("block_ads", R.string.BlockSponsoredMessagesFull, () -> SharedConfig.isBlockSponsoredMessagesFullEnabled(), v -> { SharedConfig.setBlockSponsoredMessagesFullEnabled((Boolean) v); }, false));
        items.add(new Setting("vpn_proxy_switch", R.string.VpnProxyAutoSwitch, () -> SharedConfig.isVpnProxyAutoSwitchEnabled(), v -> { SharedConfig.setVpnProxyAutoSwitchEnabled((Boolean) v); }, true));
        items.add(new Setting("download_speed", R.string.DownloadSpeedBoostTitle, () -> SharedConfig.getDownloadSpeedMultiplier(), v -> { SharedConfig.setDownloadSpeedMultiplier(((Number) v).intValue()); }, 1));
        items.add(new Setting("upload_speed", R.string.UploadSpeedBoostTitle, () -> SharedConfig.getUploadSpeedMultiplier(), v -> { SharedConfig.setUploadSpeedMultiplier(((Number) v).intValue()); }, 1));
        items.add(new Setting("sticker_size", R.string.StickerMessageSizeTitle, () -> SharedConfig.getStickerMessageSizePercent(), v -> { SharedConfig.setStickerMessageSizePercent(((Number) v).intValue()); }, 100));
        items.add(new Setting("tablet_ratio", R.string.TabletWidthRatioTitle, () -> SharedConfig.getTabletWidthRatioPercent(), v -> { SharedConfig.setTabletWidthRatioPercent(((Number) v).intValue()); }, 32));
        items.add(new Setting("row_height", R.string.DialogRowHeightTitle, () -> SharedConfig.getDialogRowHeightPercent(), v -> { SharedConfig.setDialogRowHeightPercent(((Number) v).intValue()); }, 100));
        items.add(new Setting("translation_mode", R.string.TranslationDisplayModeTitle, () -> SharedConfig.getTranslationDisplayMode(), v -> { SharedConfig.setTranslationDisplayMode(((Number) v).intValue()); }, 0));
        items.add(new Setting("reaction_target", R.string.AutoReactionTargetTitle, () -> SharedConfig.getAutoReactionTarget(), v -> { SharedConfig.setAutoReactionTarget(((Number) v).intValue()); }, 0));
        items.add(new Setting("font_size", R.string.MessageTextSizeTitle, () -> SharedConfig.fontSize, v -> { SharedConfig.setMessageTextSize(((Number) v).intValue()); }, AndroidUtilities.isTablet() ? 18 : 16));
        items.add(new Setting("reaction_emoji", R.string.AutoReactionEmojiTitle, () -> SharedConfig.getAutoReactionEmoji(), v -> { SharedConfig.setAutoReactionEmoji((String) v); }, "👍"));
        items.add(new Setting("input_text", R.string.InputBarTextTitle, () -> SharedConfig.getInputBarText(), v -> { SharedConfig.setInputBarText((String) v); }, ""));
        items.add(new Setting("dialogs_title", R.string.DialogsTitleText, () -> empty(c.getDialogsTitleText()), v -> { c.setDialogsTitleText((String) v); }, ""));
        items.add(new Setting("star_balance", R.string.CustomStarBalanceTitle, () -> SharedConfig.getCustomStarBalance(), v -> { SharedConfig.setCustomStarBalance(((Number) v).longValue()); }, -1L));
        items.add(new Setting("audio_next", R.string.ExtensionAudioAutoPlayNext, () -> ExtensionMediaSettings.isAutoPlayNextEnabled(), v -> { ExtensionMediaSettings.setAutoPlayNextEnabled((Boolean) v); }, true));
        items.add(new Setting("center_tab", R.string.MessageManagementBottomTab, () -> MessageActionSettings.isCenterEnabled(account), v -> { MessageActionSettings.setCenterEnabled(account, (Boolean) v); }, false));
        items.add(new Setting("double_tap", R.string.MessageDoubleTapAction, () -> ApplicationLoader.applicationContext.getSharedPreferences("message_actions_" + account + "_" + UserConfig.getInstance(account).getClientUserId(), Context.MODE_PRIVATE).getInt("double_tap", 0), v -> { MessageActionSettings.setDoubleTapAction(account, ((Number) v).intValue()); }, 0));
        items.add(new Setting("save_recalled", R.string.V4RecalledSave, () -> RecalledMessagesStore.isEnabled(account), v -> { RecalledMessagesStore.setEnabled(account, (Boolean) v); }, false));
        items.add(new Setting("scam_enabled", R.string.V4ScamProtectionEnabled, () -> ScamProtectionManager.isEnabled(account), v -> { ScamProtectionManager.setEnabled(account, (Boolean) v); }, true));
        items.add(new Setting("scam_keywords", R.string.V4ScamKeywords, () -> android.text.TextUtils.join("\n", ScamProtectionManager.getCustomKeywords(account)), v -> { ScamProtectionManager.setKeywords(account, (String) v); }, ""));
        items.add(new Setting("filter_enabled", R.string.KeywordFilterEnable, () -> KeywordMessageFilterManager.getInstance(account).isEnabled(), v -> { KeywordMessageFilterManager.getInstance(account).setEnabled((Boolean) v); }, true));
        items.add(new Setting("filter_updates", R.string.KeywordFilterRemoteUpdates, () -> KeywordMessageFilterManager.getInstance(account).isRemoteUpdatesEnabled(), v -> { KeywordMessageFilterManager.getInstance(account).setRemoteUpdatesEnabled((Boolean) v); }, true));
        items.add(new Setting("auto_reply", R.string.AutoReplySettings, () -> AutoReplyManager.getInstance(account).isEnabled(), v -> { AutoReplyManager.getInstance(account).setEnabled((Boolean) v); }, false));
        items.add(new Setting("auto_reply_any", R.string.AutoReplyAnyMessageTitle, () -> AutoReplyManager.getInstance(account).isAnyMessageEnabled(), v -> { AutoReplyManager.getInstance(account).setAnyMessageEnabled((Boolean) v); }, false));
        items.add(new Setting("contacts_tab", R.string.ContactsTabTitle, () -> UserConfig.getInstance(account).showContactsTab, v -> { UserConfig.getInstance(account).setShowContactsTab((Boolean) v); }, false));
        items.add(new Setting("raise_listen", R.string.RaiseToListen, () -> SharedConfig.raiseToListen, v -> { { if (SharedConfig.raiseToListen != (Boolean) v) SharedConfig.toggleRaiseToListen(); } }, true));
        items.add(new Setting("raise_speak", R.string.RaiseToSpeak, () -> SharedConfig.raiseToSpeak, v -> { { if (SharedConfig.raiseToSpeak != (Boolean) v) SharedConfig.toggleRaiseToSpeak(); } }, false));
        items.add(new Setting("direct_share", R.string.DirectShare, () -> SharedConfig.directShare, v -> { { if (SharedConfig.directShare != (Boolean) v) SharedConfig.toggleDirectShare(); } }, true));
        items.add(new Setting("inapp_camera", R.string.DebugMenuEnableCamera, () -> SharedConfig.inappCamera, v -> { { if (SharedConfig.inappCamera != (Boolean) v) SharedConfig.toggleInappCamera(); } }, true));
        items.add(new Setting("big_emoji", R.string.LargeEmoji, () -> SharedConfig.allowBigEmoji, v -> { { if (SharedConfig.allowBigEmoji != (Boolean) v) SharedConfig.toggleBigEmoji(); } }, true));
        items.add(new Setting("stream_media", R.string.EnableStreaming, () -> SharedConfig.streamMedia, v -> { { if (SharedConfig.streamMedia != (Boolean) v) SharedConfig.toggleStreamMedia(); } }, true));
        items.add(new Setting("menu_order", R.string.MessageMenuOrder, () -> joinInts(MessageActionSettings.getMenuOrder(account)), v -> { MessageActionSettings.setMenuOrder(account, parseInts((String) v)); }, "0,1,2,3"));
        items.add(new Setting("download_path", R.string.CustomDownloadPathTitle, () -> SharedConfig.getCustomDownloadPath(), v -> { SharedConfig.setCustomDownloadPath((String) v, SharedConfig.getCustomDownloadTreeUri()); }, ""));
        items.add(new Setting("download_uri", R.string.CustomDownloadPathTitle, () -> SharedConfig.getCustomDownloadTreeUri(), v -> { SharedConfig.setCustomDownloadPath(SharedConfig.getCustomDownloadPath(), (String) v); }, ""));
        items.add(new Setting("video_wallpaper", R.string.CustomVideoWallpaperTitle, () -> SharedConfig.getCustomVideoWallpaperPath(), v -> { SharedConfig.setCustomVideoWallpaper((String) v, SharedConfig.getCustomVideoWallpaperUri()); }, ""));
        items.add(new Setting("video_wallpaper_uri", R.string.CustomVideoWallpaperTitle, () -> SharedConfig.getCustomVideoWallpaperUri(), v -> { SharedConfig.setCustomVideoWallpaper(SharedConfig.getCustomVideoWallpaperPath(), (String) v); }, ""));
        items.add(new Setting("pause_record", R.string.PauseMusicOnRecord, () -> SharedConfig.pauseMusicOnRecord, v -> { if (SharedConfig.pauseMusicOnRecord != (Boolean) v) SharedConfig.togglePauseMusicOnRecord(); }, false));
        items.add(new Setting("pause_media", R.string.PauseMusicOnMedia, () -> SharedConfig.pauseMusicOnMedia, v -> { if (SharedConfig.pauseMusicOnMedia != (Boolean) v) SharedConfig.togglePauseMusicOnMedia(); }, false));
        items.add(new Setting("media_tap", R.string.NextMediaTap, () -> SharedConfig.nextMediaTap, v -> { if (SharedConfig.nextMediaTap != (Boolean) v) SharedConfig.toggleNextMediaTap(); }, true));
        items.add(new Setting("emoji_suggestions", R.string.SuggestAnimatedEmoji, () -> SharedConfig.suggestAnimatedEmoji, v -> { if (SharedConfig.suggestAnimatedEmoji != (Boolean) v) SharedConfig.toggleSuggestAnimatedEmoji(); }, true));
        items.add(new Setting("sticker_order", R.string.DynamicPackOrder, () -> SharedConfig.updateStickersOrderOnSend, v -> { if (SharedConfig.updateStickersOrderOnSend != (Boolean) v) SharedConfig.toggleUpdateStickersOrderOnSend(); }, true));
        items.add(new Setting("inapp_browser", R.string.InappBrowser, () -> SharedConfig.inappBrowser, v -> { if (SharedConfig.inappBrowser != (Boolean) v) SharedConfig.toggleInappBrowser(); }, true));
        items.add(new Setting("save_stream_media", R.string.SaveToGallery, () -> SharedConfig.saveStreamMedia, v -> { if (SharedConfig.saveStreamMedia != (Boolean) v) SharedConfig.toggleSaveStreamMedia(); }, true));
        items.add(new Setting("sort_contacts", R.string.SortedByName, () -> SharedConfig.sortContactsByName, v -> { if (SharedConfig.sortContactsByName != (Boolean) v) SharedConfig.toggleSortContactsByName(); }, false));
        items.add(new Setting("sort_files", R.string.SortedByName, () -> SharedConfig.sortFilesByName, v -> { if (SharedConfig.sortFilesByName != (Boolean) v) SharedConfig.toggleSortFilesByName(); }, false));
        items.add(new Setting("three_line_list", R.string.ChatList, () -> SharedConfig.useThreeLinesLayout, v -> SharedConfig.setUseThreeLinesLayout((Boolean) v), false));
        items.add(new Setting("repeat_mode", R.string.RepeatList, () -> SharedConfig.repeatMode, v -> SharedConfig.setRepeatMode(((Number) v).intValue()), 0));
        items.add(new Setting("playback_order", R.string.ShuffleList, () -> SharedConfig.shuffleMusic ? 2 : SharedConfig.playOrderReversed ? 1 : 0, v -> SharedConfig.setPlaybackOrderType(((Number) v).intValue()), 0));
        items.add(new Setting("autoplay_video", R.string.AutoplayVideo, () -> LiteMode.isEnabledSetting(LiteMode.FLAG_AUTOPLAY_VIDEOS), v -> LiteMode.toggleFlag(LiteMode.FLAG_AUTOPLAY_VIDEOS, (Boolean) v), true));
        items.add(new Setting("autoplay_gif", R.string.AutoplayGIF, () -> LiteMode.isEnabledSetting(LiteMode.FLAG_AUTOPLAY_GIFS), v -> LiteMode.toggleFlag(LiteMode.FLAG_AUTOPLAY_GIFS, (Boolean) v), true));
        items.add(new Setting("reaction_users", R.string.AutoReactionUsersTitle,
                () -> sortedIds(SharedConfig.getAutoReactionUserIds(account)),
                v -> SharedConfig.setAutoReactionUserIds(account, parseIds((String) v)), ""));
        items.add(new Setting("in_chat_sound", R.string.InChatSound,
                () -> MessagesController.getNotificationsSettings(account).getBoolean("EnableInChatSound", true),
                v -> { MessagesController.getNotificationsSettings(account).edit().putBoolean("EnableInChatSound", (Boolean) v).apply(); NotificationsController.getInstance(account).setInChatSoundEnabled((Boolean) v); }, true));
        for (String key : new String[]{"EnableInAppSounds", "EnableInAppVibrate", "EnableInAppPreview"}) {
            int label = key.equals("EnableInAppSounds") ? R.string.InAppSounds : key.equals("EnableInAppVibrate") ? R.string.InAppVibrate : R.string.InAppPreview;
            items.add(new Setting(key, label, () -> MessagesController.getNotificationsSettings(account).getBoolean(key, true),
                    v -> MessagesController.getNotificationsSettings(account).edit().putBoolean(key, (Boolean) v).apply(), true));
        }
        for (DailyMessageTasksManager.Task task : DailyMessageTasksManager.getInstance(account).getTasks()) {
            final String taskId = task.id;
            items.add(new Setting("daily_schedule_" + taskId, R.string.DailyMessageTasksTime,
                    () -> { for (DailyMessageTasksManager.Task current : DailyMessageTasksManager.getInstance(account).getTasks()) if (current.id.equals(taskId)) return schedule(current); return ""; },
                    v -> { for (DailyMessageTasksManager.Task current : DailyMessageTasksManager.getInstance(account).getTasks()) if (current.id.equals(taskId)) { applySchedule(current, (String) v); DailyMessageTasksManager.getInstance(account).save(current); break; } }, schedule(task)));
            items.add(new Setting("daily_" + taskId, R.string.ExtensionDailyMessages,
                    () -> { for (DailyMessageTasksManager.Task current : DailyMessageTasksManager.getInstance(account).getTasks()) if (current.id.equals(taskId)) return current.automatic; return false; },
                    v -> { for (DailyMessageTasksManager.Task current : DailyMessageTasksManager.getInstance(account).getTasks()) if (current.id.equals(taskId)) { current.automatic = (Boolean) v; DailyMessageTasksManager.getInstance(account).save(current); break; } }, false));
        }
        for (String target : MessageEchoManager.targets(account)) {
            final long dialogId = Long.parseLong(target);
            items.add(new Setting("echo_" + target, R.string.V4MessageEcho,
                    () -> MessageEchoManager.targets(account).contains(target), v -> MessageEchoManager.setTarget(account, dialogId, (Boolean) v), false));
        }
        for (Setting setting : items) labels.put(setting.key, setting.label);
        return items;
    }
    /** Exclude task/peer-specific switches: their identifiers belong to one account. */
    public static Map<String, Boolean> booleanSwitches(int account) {
        Map<String, Boolean> result = new HashMap<>();
        for (Setting setting : settings(account)) {
            if (setting.defaultValue instanceof Boolean && !setting.key.startsWith("daily_") && !setting.key.startsWith("echo_")) {
                result.put(setting.key, (Boolean) setting.getter.get());
            }
        }
        return result;
    }

    public static void applyBooleanSwitch(int account, String key, boolean value) {
        initialize(account);
        for (Setting setting : settings(account)) {
            if (!setting.key.equals(key) || !(setting.defaultValue instanceof Boolean)
                    || key.startsWith("daily_") || key.startsWith("echo_")) continue;
            Object before = setting.getter.get();
            if (!Objects.equals(before, value)) {
                setting.setter.set(value);
                MangoSettings.recordChange(account, "legacy:" + key, before, value);
                notifyChanged(account);
            }
            // Preference listeners run later: align the baseline now to avoid echoing the sync.
            Observer observer = observers.get(account);
            if (observer != null) observer.previous.put(key, value);
            break;
        }
    }

    public static String label(String key) {
        Integer label = labels.get(key);
        if (label != null) return LocaleController.getString(label);
        if (key.startsWith("daily_")) return LocaleController.getString(R.string.ExtensionDailyMessages);
        if (key.startsWith("echo_")) return LocaleController.getString(R.string.V4MessageEcho);
        return key;
    }
    private static boolean isBehaviorPreference(String key) {
        return key.equals("hide_online_status") || key.equals("hide_typing_status") || key.equals("hide_read_status")
                || key.equals("anti_revoke") || key.equals("force_forward") || key.equals("unlimited_pin")
                || key.equals("chinese_read_status_text") || key.equals("dialogs_title_text") || key.equals("unread_dialogs_tab")
                || key.equals("continue_scroll_channel_jump") || key.equals("showContactsTab") || key.equals("enabled")
                || key.equals("keywords") || key.equals("center_enabled") || key.equals("double_tap") || key.equals("menu_order")
                || key.equals("targets") || key.equals("tasks") || key.equals("fons_size") || key.equals("lite_mode")
                || key.equals("extension_audio_auto_play_next") || key.startsWith("autoReaction") || key.startsWith("auto_reply")
                || key.startsWith("keyword_filter") || key.startsWith("hideChannel") || key.startsWith("defaultHighQuality")
                || key.startsWith("tablet") || key.startsWith("dialogRow") || key.startsWith("translationDisplay")
                || key.startsWith("stickerMessage") || key.startsWith("downloadSpeed") || key.startsWith("uploadSpeed")
                || key.startsWith("customDownload") || key.startsWith("customVideoWallpaper") || key.startsWith("customStar")
                || key.startsWith("inputBarText") || key.startsWith("localVirtualGiftBuy") || key.startsWith("localHoldVerification")
                || key.startsWith("unlimited") || key.startsWith("channelFeedEnabled") || key.startsWith("sendMp3")
                || key.startsWith("block_sponsored") || key.startsWith("vpn_proxy") || key.startsWith("EnableIn")
                || key.startsWith("raiseTo") || key.startsWith("pauseMusic") || key.startsWith("sortContacts") || key.startsWith("sortFiles")
                || key.equals("nextMediaTap") || key.equals("suggestAnimatedEmoji") || key.equals("updateStickersOrderOnSend")
                || key.equals("direct_share") || key.equals("directShare") || key.equals("inappCamera") || key.equals("inappBrowser") || key.equals("inapp_browser")
                || key.equals("allowBigEmoji") || key.equals("streamMedia") || key.equals("saveStreamMedia")
                || key.equals("useThreeLinesLayout") || key.equals("repeatMode") || key.equals("shuffleMusic") || key.equals("playOrderReversed");
    }
    public static void initialize(int account) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        Observer previous = observers.get(account);
        if (previous != null && previous.owner == owner) return;
        if (previous != null) {
            for (SharedPreferences store : previous.stores) store.unregisterOnSharedPreferenceChangeListener(previous.listener);
            AndroidUtilities.cancelRunOnUIThread(previous.capture);
        }
        Observer observer = new Observer();
        observer.owner = owner;
        observers.put(account, observer);
        for (Setting setting : settings(account)) observer.previous.put(setting.key, setting.getter.get());
        observer.capture = () -> capture(account);
        observer.listener = (preferences, key) -> {
            if (key == null || isBehaviorPreference(key)) AndroidUtilities.runOnUIThread(observer.capture);
        };
        observer.stores.add(MessagesController.getMainSettings(account));
        observer.stores.add(MessagesController.getGlobalMainSettings());
        observer.stores.add(UserConfig.getInstance(account).getPreferences());
        observer.stores.add(MessagesController.getNotificationsSettings(account));
        String suffix = account + "_" + owner;
        for (String file : new String[]{"userconfing", "mainconfig", "message_actions_" + suffix,
                "recalled_settings_" + suffix, "scam_protection_" + suffix, "message_echo_" + suffix, "extension_daily_tasks_" + account}) {
            observer.stores.add(ApplicationLoader.applicationContext.getSharedPreferences(file, Context.MODE_PRIVATE));
        }
        for (SharedPreferences store : observer.stores) store.registerOnSharedPreferenceChangeListener(observer.listener);
        MangoSettingsSync.initializeAccount(account);
    }
    public static void capture(int account) {
        Observer observer = observers.get(account);
        if (observer == null || observer.owner != UserConfig.getInstance(account).getClientUserId()) { initialize(account); return; }
        for (Setting setting : settings(account)) {
            Object after = setting.getter.get();
            Object before = observer.previous.containsKey(setting.key) ? observer.previous.get(setting.key) : setting.defaultValue;
            if (!Objects.equals(before, after)) {
                MangoSettings.recordChange(account, "legacy:" + setting.key, before, after);
                MangoSettingsSync.onLegacyChanged(account, setting.key, after);
            }
            observer.previous.put(setting.key, after);
        }
        // Echo targets disappear from the live registry after being turned off.
        for (String key : new ArrayList<>(observer.previous.keySet())) {
            if (key.startsWith("echo_") && !MessageEchoManager.targets(account).contains(key.substring(5)) && Boolean.TRUE.equals(observer.previous.get(key))) {
                MangoSettings.recordChange(account, "legacy:" + key, true, false);
                observer.previous.put(key, false);
            }
        }
    }
    public static void restore(int account, String key, Object value) {
        initialize(account);
        if (key.startsWith("echo_")) MessageEchoManager.setTarget(account, Long.parseLong(key.substring(5)), (Boolean) value);
        else for (Setting setting : settings(account)) if (setting.key.equals(key)) { setting.setter.set(value); break; }
        capture(account);
        notifyChanged(account);
    }
    public static void reset(int account) {
        initialize(account);
        MangoSettings.reset(account);
        for (Setting setting : settings(account)) {
            if (!Objects.equals(setting.getter.get(), setting.defaultValue)) setting.setter.set(setting.defaultValue);
        }
        PresenceSimulationManager.getInstance(account).stopTyping();
        PresenceSimulationManager.getInstance(account).setPresence(PresenceSimulationManager.NORMAL, 0);
        capture(account);
        notifyChanged(account);
    }
    private static void notifyChanged(int account) {
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces, 0);
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload);
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.contactsTabVisibleToggled);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewWallpapper);
    }
}
