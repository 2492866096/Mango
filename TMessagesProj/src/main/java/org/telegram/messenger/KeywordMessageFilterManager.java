package org.telegram.messenger;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;

public class KeywordMessageFilterManager {

    private static final String PREF_ENABLED = "keyword_filter_enabled";
    private static final String PREF_KEYWORDS = "keyword_filter_keywords";
    private static final String PREF_CUSTOM_BLOCKED_USERS = "keyword_filter_custom_blocked_users";
    private static final String PREF_WHITELIST = "keyword_filter_whitelist";
    private static final String PREF_LOGS = "keyword_filter_logs";

    private static final long LOG_RETENTION_SECONDS = 3 * 60 * 60;
    private static final int MAX_LOGS = 100;

    private static final KeywordMessageFilterManager[] instances = new KeywordMessageFilterManager[UserConfig.MAX_ACCOUNT_COUNT];

    public static KeywordMessageFilterManager getInstance(int account) {
        synchronized (instances) {
            KeywordMessageFilterManager instance = instances[account];
            if (instance == null) {
                instance = new KeywordMessageFilterManager(account);
                instances[account] = instance;
            }
            return instance;
        }
    }

    public static class WhitelistDialog {
        public final long dialogId;
        public final String title;
        public final boolean channel;
        public final boolean user;

        public WhitelistDialog(long dialogId, String title, boolean channel) {
            this(dialogId, title, channel, dialogId > 0);
        }

        public WhitelistDialog(long dialogId, String title, boolean channel, boolean user) {
            this.dialogId = dialogId;
            this.title = title == null ? "" : title.trim();
            this.channel = channel;
            this.user = user;
        }
    }

    public static class LogEntry {
        public final long dialogId;
        public final int messageId;
        public final String dialogTitle;
        public final String keyword;
        public final String snippet;
        public final long timestamp;

        public LogEntry(long dialogId, int messageId, String dialogTitle, String keyword, String snippet, long timestamp) {
            this.dialogId = dialogId;
            this.messageId = messageId;
            this.dialogTitle = dialogTitle == null ? "" : dialogTitle;
            this.keyword = keyword == null ? "" : keyword;
            this.snippet = snippet == null ? "" : snippet;
            this.timestamp = timestamp;
        }
    }

    private final int currentAccount;
    private final SharedPreferences preferences;

    private boolean loaded;
    private boolean enabled;
    private final ArrayList<String> keywords = new ArrayList<>();
    private final ArrayList<String> customBlockedUsers = new ArrayList<>();
    private final ArrayList<WhitelistDialog> whitelistDialogs = new ArrayList<>();
    private final ArrayList<LogEntry> logs = new ArrayList<>();

    private KeywordMessageFilterManager(int account) {
        currentAccount = account;
        preferences = MessagesController.getMainSettings(account);
    }

    public synchronized boolean isEnabled() {
        ensureLoaded();
        return enabled;
    }

    public synchronized int getKeywordsCount() {
        ensureLoaded();
        return keywords.size();
    }

    public synchronized int getWhitelistCount() {
        ensureLoaded();
        return whitelistDialogs.size();
    }

    public synchronized int getCustomBlockedUsersCount() {
        ensureLoaded();
        return customBlockedUsers.size();
    }

    public synchronized ArrayList<String> getKeywords() {
        ensureLoaded();
        return new ArrayList<>(keywords);
    }

    public synchronized String getCustomBlockedUsersText() {
        ensureLoaded();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < customBlockedUsers.size(); i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(customBlockedUsers.get(i));
        }
        return builder.toString();
    }

    public synchronized ArrayList<WhitelistDialog> getWhitelistDialogs() {
        ensureLoaded();
        return new ArrayList<>(whitelistDialogs);
    }

    public synchronized boolean isDialogWhitelisted(long dialogId) {
        ensureLoaded();
        return dialogId != 0 && isWhitelistedLocked(dialogId);
    }

    public synchronized ArrayList<LogEntry> getLogs() {
        ensureLoaded();
        if (purgeExpiredLogsLocked()) {
            saveLogsLocked();
        }
        return new ArrayList<>(logs);
    }

    public synchronized void setEnabled(boolean value) {
        ensureLoaded();
        if (enabled == value) {
            return;
        }
        enabled = value;
        saveSettingsLocked(true);
    }

    public synchronized boolean addKeyword(String value) {
        ensureLoaded();
        String normalized = normalizeKeyword(value);
        if (TextUtils.isEmpty(normalized) || findKeywordIndexLocked(normalized) >= 0) {
            return false;
        }
        keywords.add(normalized);
        saveSettingsLocked(true);
        return true;
    }

    /** One save and one reload for all phrases chosen in a message. */
    public synchronized void addKeywordsAndEnable(Collection<String> values) {
        if (values == null || values.isEmpty()) return;
        ensureLoaded();
        boolean changed = false;
        boolean hasValidKeyword = false;
        for (String value : values) {
            String normalized = normalizeKeyword(value);
            if (TextUtils.isEmpty(normalized)) continue;
            hasValidKeyword = true;
            if (findKeywordIndexLocked(normalized) < 0) {
                keywords.add(normalized);
                changed = true;
            }
        }
        if (!hasValidKeyword) return;
        changed |= !enabled;
        enabled = true;
        if (changed) saveSettingsLocked(true);
    }

    public synchronized boolean updateKeyword(String oldValue, String newValue) {
        ensureLoaded();
        int oldIndex = findKeywordIndexLocked(oldValue);
        if (oldIndex < 0) {
            return false;
        }
        String normalized = normalizeKeyword(newValue);
        if (TextUtils.isEmpty(normalized)) {
            keywords.remove(oldIndex);
            saveSettingsLocked(true);
            return true;
        }
        int duplicateIndex = findKeywordIndexLocked(normalized);
        if (duplicateIndex >= 0 && duplicateIndex != oldIndex) {
            return false;
        }
        if (normalized.equals(keywords.get(oldIndex))) {
            return false;
        }
        keywords.set(oldIndex, normalized);
        saveSettingsLocked(true);
        return true;
    }

    public synchronized boolean removeKeyword(String value) {
        ensureLoaded();
        int index = findKeywordIndexLocked(value);
        if (index < 0) {
            return false;
        }
        keywords.remove(index);
        saveSettingsLocked(true);
        return true;
    }

    public synchronized boolean removeKeywords(Collection<String> values) {
        ensureLoaded();
        if (values == null || values.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (String value : values) {
            int index = findKeywordIndexLocked(value);
            if (index >= 0) {
                keywords.remove(index);
                changed = true;
            }
        }
        if (changed) {
            saveSettingsLocked(true);
        }
        return changed;
    }

    public synchronized boolean addWhitelistDialogs(ArrayList<WhitelistDialog> dialogs) {
        ensureLoaded();
        boolean changed = false;
        for (int i = 0; i < dialogs.size(); i++) {
            WhitelistDialog dialog = dialogs.get(i);
            if (dialog == null || dialog.dialogId == 0) {
                continue;
            }
            int index = findWhitelistIndexLocked(dialog.dialogId);
            if (index >= 0) {
                WhitelistDialog existing = whitelistDialogs.get(index);
                if (!TextUtils.equals(existing.title, dialog.title) || existing.channel != dialog.channel || existing.user != dialog.user) {
                    whitelistDialogs.set(index, dialog);
                    changed = true;
                }
            } else {
                whitelistDialogs.add(dialog);
                changed = true;
            }
        }
        if (changed) {
            saveSettingsLocked(true);
        }
        return changed;
    }

    public synchronized boolean removeWhitelistDialogs(Collection<Long> dialogIds) {
        ensureLoaded();
        if (dialogIds == null || dialogIds.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (int i = whitelistDialogs.size() - 1; i >= 0; i--) {
            if (dialogIds.contains(whitelistDialogs.get(i).dialogId)) {
                whitelistDialogs.remove(i);
                changed = true;
            }
        }
        if (changed) {
            saveSettingsLocked(true);
        }
        return changed;
    }

    public synchronized void setCustomBlockedUsersFromText(String text) {
        ensureLoaded();
        ArrayList<String> values = new ArrayList<>();
        if (!TextUtils.isEmpty(text)) {
            String[] lines = text.split("\\r?\\n");
            for (int i = 0; i < lines.length; i++) {
                String value = normalizeBlockedUser(lines[i]);
                if (!TextUtils.isEmpty(value) && !containsIgnoreCase(values, value)) {
                    values.add(value);
                }
            }
        }
        customBlockedUsers.clear();
        customBlockedUsers.addAll(values);
        saveSettingsLocked(true);
    }

    public synchronized void clearLogs() {
        ensureLoaded();
        if (logs.isEmpty()) {
            return;
        }
        logs.clear();
        saveLogsLocked();
    }

    public String getMatchingKeyword(MessageObject messageObject, boolean addLog) {
        if (messageObject == null || messageObject.messageOwner == null || messageObject.getId() <= 0 || messageObject.isSponsored()) {
            return null;
        }
        long dialogId = messageObject.getDialogId();
        if (dialogId == 0) {
            return null;
        }
        String sourceText = buildSourceText(messageObject);
        String matchedKeyword;
        synchronized (this) {
            ensureLoaded();
            if (isWhitelistedLocked(dialogId)) {
                return null;
            }
            matchedKeyword = findMatchingBlockedUsernameLocked(messageObject);
            if (matchedKeyword != null) {
                matchedKeyword = isLong(matchedKeyword) ? matchedKeyword : "@" + matchedKeyword;
            } else if (!enabled || keywords.isEmpty()) {
                return null;
            } else if (TextUtils.isEmpty(sourceText)) {
                return null;
            } else {
                matchedKeyword = findMatchingKeywordLocked(sourceText);
            }
        }
        if (matchedKeyword != null && addLog) {
            addLog(messageObject, matchedKeyword, sourceText);
        }
        return matchedKeyword;
    }

    public boolean shouldHideMessageByBlockedUsername(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null || messageObject.getId() <= 0 || messageObject.isSponsored()) {
            return false;
        }
        synchronized (this) {
            ensureLoaded();
            return !isWhitelistedLocked(messageObject.getDialogId())
                    && findMatchingBlockedUsernameLocked(messageObject) != null;
        }
    }

    private synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        enabled = preferences.getBoolean(PREF_ENABLED, true);
        loadKeywordsLocked();
        loadCustomBlockedUsersLocked();
        loadWhitelistLocked();
        loadLogsLocked();
        if (purgeExpiredLogsLocked()) {
            saveLogsLocked();
        }
    }

    private void loadKeywordsLocked() {
        keywords.clear();
        try {
            JSONArray array = new JSONArray(preferences.getString(PREF_KEYWORDS, "[]"));
            for (int i = 0; i < array.length(); i++) {
                String keyword = normalizeKeyword(array.optString(i, null));
                if (!TextUtils.isEmpty(keyword) && findKeywordIndexLocked(keyword) < 0) {
                    keywords.add(keyword);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void loadCustomBlockedUsersLocked() {
        customBlockedUsers.clear();
        try {
            JSONArray array = new JSONArray(preferences.getString(PREF_CUSTOM_BLOCKED_USERS, "[]"));
            for (int i = 0; i < array.length(); i++) {
                String value = normalizeBlockedUser(array.optString(i, null));
                if (!TextUtils.isEmpty(value) && !containsIgnoreCase(customBlockedUsers, value)) {
                    customBlockedUsers.add(value);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void loadWhitelistLocked() {
        whitelistDialogs.clear();
        try {
            JSONArray array = new JSONArray(preferences.getString(PREF_WHITELIST, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                long dialogId = object.optLong("id", 0);
                if (dialogId == 0 || findWhitelistIndexLocked(dialogId) >= 0) {
                    continue;
                }
                whitelistDialogs.add(new WhitelistDialog(
                        dialogId,
                        object.optString("title", ""),
                        object.optBoolean("channel", false),
                        object.optBoolean("user", dialogId > 0)
                ));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void loadLogsLocked() {
        logs.clear();
        try {
            JSONArray array = new JSONArray(preferences.getString(PREF_LOGS, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                long dialogId = object.optLong("dialog_id", 0);
                int messageId = object.optInt("message_id", 0);
                long timestamp = object.optLong("timestamp", 0);
                if (dialogId == 0 || messageId == 0 || timestamp == 0) {
                    continue;
                }
                logs.add(new LogEntry(
                        dialogId,
                        messageId,
                        object.optString("dialog_title", ""),
                        object.optString("keyword", ""),
                        object.optString("snippet", ""),
                        timestamp
                ));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void saveSettingsLocked(boolean notifySettingsChanged) {
        try {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(PREF_ENABLED, enabled);
            editor.putString(PREF_KEYWORDS, serializeKeywordsLocked().toString());
            editor.putString(PREF_CUSTOM_BLOCKED_USERS, serializeCustomBlockedUsersLocked().toString());
            editor.putString(PREF_WHITELIST, serializeWhitelistLocked().toString());
            editor.putString(PREF_LOGS, serializeLogsLocked().toString());
            editor.apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (notifySettingsChanged) {
            notifySettingsChanged();
        }
    }

    private void saveLogsLocked() {
        try {
            preferences.edit().putString(PREF_LOGS, serializeLogsLocked().toString()).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private JSONArray serializeKeywordsLocked() {
        JSONArray array = new JSONArray();
        for (int i = 0; i < keywords.size(); i++) {
            array.put(keywords.get(i));
        }
        return array;
    }

    private JSONArray serializeCustomBlockedUsersLocked() {
        JSONArray array = new JSONArray();
        for (int i = 0; i < customBlockedUsers.size(); i++) {
            array.put(customBlockedUsers.get(i));
        }
        return array;
    }

    private JSONArray serializeWhitelistLocked() {
        JSONArray array = new JSONArray();
        for (int i = 0; i < whitelistDialogs.size(); i++) {
            WhitelistDialog dialog = whitelistDialogs.get(i);
            JSONObject object = new JSONObject();
            try {
                object.put("id", dialog.dialogId);
                object.put("title", dialog.title);
                object.put("channel", dialog.channel);
                object.put("user", dialog.user);
                array.put(object);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return array;
    }

    private JSONArray serializeLogsLocked() {
        JSONArray array = new JSONArray();
        for (int i = 0; i < logs.size(); i++) {
            LogEntry entry = logs.get(i);
            JSONObject object = new JSONObject();
            try {
                object.put("dialog_id", entry.dialogId);
                object.put("message_id", entry.messageId);
                object.put("dialog_title", entry.dialogTitle);
                object.put("keyword", entry.keyword);
                object.put("snippet", entry.snippet);
                object.put("timestamp", entry.timestamp);
                array.put(object);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return array;
    }

    private boolean purgeExpiredLogsLocked() {
        long minTimestamp = System.currentTimeMillis() / 1000 - LOG_RETENTION_SECONDS;
        boolean changed = false;
        for (int i = logs.size() - 1; i >= 0; i--) {
            if (logs.get(i).timestamp < minTimestamp) {
                logs.remove(i);
                changed = true;
            }
        }
        while (logs.size() > MAX_LOGS) {
            logs.remove(logs.size() - 1);
            changed = true;
        }
        return changed;
    }

    private int findKeywordIndexLocked(String value) {
        String normalized = normalizeKeyword(value);
        if (TextUtils.isEmpty(normalized)) {
            return -1;
        }
        for (int i = 0; i < keywords.size(); i++) {
            if (keywords.get(i).equalsIgnoreCase(normalized)) {
                return i;
            }
        }
        return -1;
    }

    private int findWhitelistIndexLocked(long dialogId) {
        for (int i = 0; i < whitelistDialogs.size(); i++) {
            if (whitelistDialogs.get(i).dialogId == dialogId) {
                return i;
            }
        }
        return -1;
    }

    private boolean isWhitelistedLocked(long dialogId) {
        return findWhitelistIndexLocked(dialogId) >= 0;
    }

    private String findMatchingKeywordLocked(String sourceText) {
        String normalizedSource = sourceText.toLowerCase(Locale.ROOT);
        for (int i = 0; i < keywords.size(); i++) {
            String keyword = keywords.get(i);
            if (normalizedSource.contains(keyword.toLowerCase(Locale.ROOT))) {
                return keyword;
            }
        }
        return null;
    }

    private String findMatchingBlockedUsernameLocked(MessageObject messageObject) {
        if (customBlockedUsers.isEmpty() || messageObject == null || messageObject.messageOwner == null) {
            return null;
        }
        TLRPC.Peer fromPeer = messageObject.messageOwner.from_id;
        if (fromPeer instanceof TLRPC.TL_peerUser || fromPeer instanceof TLRPC.TL_peerUser_layer131) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(fromPeer.user_id);
            String username = findMatchingUserUsernameLocked(user);
            if (username != null) {
                return username;
            }
        }
        long dialogId = messageObject.getDialogId();
        if (dialogId > 0 && !messageObject.isOutOwner()) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            String username = findMatchingUserUsernameLocked(user);
            if (username != null) {
                return username;
            }
        }
        return null;
    }

    private String findMatchingUserUsernameLocked(TLRPC.User user) {
        if (user == null) {
            return null;
        }
        if (containsCustomBlockedUserIdLocked(user.id)) {
            return String.valueOf(user.id);
        }
        String username = normalizeUsername(user.username);
        if (!TextUtils.isEmpty(username) && containsIgnoreCase(customBlockedUsers, username)) {
            return username;
        }
        if (user.usernames != null) {
            for (int i = 0; i < user.usernames.size(); i++) {
                TLRPC.TL_username extraUsername = user.usernames.get(i);
                if (extraUsername == null || !extraUsername.active) {
                    continue;
                }
                username = normalizeUsername(extraUsername.username);
                if (!TextUtils.isEmpty(username) && containsIgnoreCase(customBlockedUsers, username)) {
                    return username;
                }
            }
        }
        return null;
    }

    private boolean containsCustomBlockedUserIdLocked(long userId) {
        if (userId == 0) {
            return false;
        }
        String value = String.valueOf(userId);
        for (int i = 0; i < customBlockedUsers.size(); i++) {
            if (TextUtils.equals(customBlockedUsers.get(i), value)) {
                return true;
            }
        }
        return false;
    }

    private void addLog(MessageObject messageObject, String keyword, String sourceText) {
        long now = System.currentTimeMillis() / 1000;
        if (messageObject.messageOwner == null || messageObject.messageOwner.date == 0) {
            return;
        }
        if (now - messageObject.messageOwner.date > LOG_RETENTION_SECONDS) {
            return;
        }
        synchronized (this) {
            ensureLoaded();
            purgeExpiredLogsLocked();
            for (int i = 0; i < logs.size(); i++) {
                LogEntry entry = logs.get(i);
                if (entry.dialogId == messageObject.getDialogId() && entry.messageId == messageObject.getId()) {
                    return;
                }
            }
            logs.add(0, new LogEntry(
                    messageObject.getDialogId(),
                    messageObject.getId(),
                    getDialogTitleLocked(messageObject.getDialogId()),
                    keyword,
                    buildSnippet(sourceText),
                    now
            ));
            purgeExpiredLogsLocked();
            saveLogsLocked();
        }
    }

    private String getDialogTitleLocked(long dialogId) {
        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
        if (chat != null && !TextUtils.isEmpty(chat.title)) {
            return chat.title;
        }
        if (dialogId > 0) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            if (user != null) {
                return UserObject.getUserName(user);
            }
        }
        int whitelistIndex = findWhitelistIndexLocked(dialogId);
        if (whitelistIndex >= 0) {
            return whitelistDialogs.get(whitelistIndex).title;
        }
        return String.valueOf(-dialogId);
    }

    private void notifySettingsChanged() {
        AndroidUtilities.runOnUIThread(() -> {
            MessagesController.getInstance(currentAccount).clearSponsoredMessagesCache();
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.keywordFilterSettingsUpdated);
        });
    }

    private static String normalizeKeyword(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.startsWith("\uFEFF")) {
            normalized = normalized.substring(1).trim();
        }
        return normalized;
    }

    private static String normalizeUsername(String value) {
        String username = normalizeKeyword(value);
        if (TextUtils.isEmpty(username)) {
            return "";
        }
        int commentIndex = username.indexOf('#');
        if (commentIndex == 0) {
            return "";
        } else if (commentIndex > 0) {
            username = username.substring(0, commentIndex).trim();
        }
        if (username.startsWith("@")) {
            username = username.substring(1).trim();
        }
        String lower = username.toLowerCase(Locale.ROOT);
        String[] prefixes = {
                "https://t.me/",
                "http://t.me/",
                "t.me/",
                "https://telegram.me/",
                "http://telegram.me/",
                "telegram.me/"
        };
        for (int i = 0; i < prefixes.length; i++) {
            String prefix = prefixes[i];
            if (lower.startsWith(prefix)) {
                username = username.substring(prefix.length()).trim();
                break;
            }
        }
        int slashIndex = username.indexOf('/');
        if (slashIndex >= 0) {
            username = username.substring(0, slashIndex).trim();
        }
        int queryIndex = username.indexOf('?');
        if (queryIndex >= 0) {
            username = username.substring(0, queryIndex).trim();
        }
        if (username.startsWith("@")) {
            username = username.substring(1).trim();
        }
        return username.toLowerCase(Locale.ROOT);
    }

    private static String normalizeBlockedUser(String value) {
        String normalized = normalizeKeyword(value);
        if (TextUtils.isEmpty(normalized)) {
            return "";
        }
        int commentIndex = normalized.indexOf('#');
        if (commentIndex == 0) {
            return "";
        } else if (commentIndex > 0) {
            normalized = normalized.substring(0, commentIndex).trim();
        }
        if (isLong(normalized)) {
            return normalized;
        }
        return normalizeUsername(normalized);
    }

    private static boolean isLong(String value) {
        if (TextUtils.isEmpty(value)) {
            return false;
        }
        try {
            Long.parseLong(value);
            return true;
        } catch (Exception ignore) {
            return false;
        }
    }

    private static boolean containsIgnoreCase(ArrayList<String> values, String value) {
        if (TextUtils.isEmpty(value)) {
            return false;
        }
        for (int i = 0; i < values.size(); i++) {
            if (value.equalsIgnoreCase(values.get(i))) {
                return true;
            }
        }
        return false;
    }

    private static String buildSourceText(MessageObject messageObject) {
        StringBuilder builder = new StringBuilder();
        TLRPC.Message owner = messageObject.messageOwner;
        appendPart(builder, owner.message);
        appendPart(builder, messageObject.caption);
        appendPart(builder, messageObject.messageText);
        appendTextWithEntities(builder, owner.originalText);
        appendTextWithEntities(builder, owner.translatedText);
        appendTextWithEntities(builder, owner.translatedVoiceTranscription);
        appendTextWithEntities(builder, owner.summaryText);
        appendTextWithEntities(builder, owner.translatedSummaryText);
        appendEntities(builder, owner.message, owner.entities);
        appendMedia(builder, messageObject.currentAccount, owner.media);
        if (owner.fwd_from != null) {
            appendPart(builder, owner.fwd_from.from_name);
            appendPart(builder, owner.fwd_from.post_author);
            appendPart(builder, owner.fwd_from.saved_from_name);
            appendPeer(builder, messageObject.currentAccount, owner.fwd_from.from_id);
            appendPeer(builder, messageObject.currentAccount, owner.fwd_from.saved_from_id);
        }
        if (owner.reply_markup != null) {
            appendReplyMarkup(builder, owner.reply_markup);
        }
        appendPart(builder, messageObject.getFileName());
        return builder.toString().trim();
    }

    private static void appendTextWithEntities(StringBuilder builder, TLRPC.TL_textWithEntities textWithEntities) {
        if (textWithEntities == null) {
            return;
        }
        appendPart(builder, textWithEntities.text);
        appendEntities(builder, textWithEntities.text, textWithEntities.entities);
    }

    private static void appendEntities(StringBuilder builder, CharSequence text, ArrayList<TLRPC.MessageEntity> entities) {
        if (entities == null) {
            return;
        }
        HashSet<String> added = null;
        for (int i = 0; i < entities.size(); i++) {
            TLRPC.MessageEntity entity = entities.get(i);
            if (entity == null) {
                continue;
            }
            appendEntityText(builder, text, entity);
            if (TextUtils.isEmpty(entity.url)) {
                continue;
            }
            if (added == null) {
                added = new HashSet<>();
            }
            if (added.add(entity.url)) {
                appendPart(builder, entity.url);
            }
        }
    }

    private static void appendEntityText(StringBuilder builder, CharSequence text, TLRPC.MessageEntity entity) {
        if (TextUtils.isEmpty(text) || entity.offset < 0 || entity.length <= 0 || entity.offset >= text.length()) {
            return;
        }
        int end = Math.min(text.length(), entity.offset + entity.length);
        if (end > entity.offset) {
            appendPart(builder, text.subSequence(entity.offset, end));
        }
    }

    private static void appendMedia(StringBuilder builder, int account, TLRPC.MessageMedia media) {
        if (media == null) {
            return;
        }
        appendPart(builder, media.title);
        appendPart(builder, media.description);
        appendPart(builder, media.captionLegacy);
        appendPart(builder, media.address);
        appendPart(builder, media.provider);
        appendPart(builder, media.venue_id);
        appendPart(builder, media.venue_type);
        appendPart(builder, media.phone_number);
        appendPart(builder, media.first_name);
        appendPart(builder, media.last_name);
        appendPart(builder, media.vcard);
        appendPart(builder, media.currency);
        appendPart(builder, media.start_param);
        if (media instanceof TLRPC.TL_messageMediaWebPage) {
            appendWebPage(builder, media.webpage);
        } else if (media instanceof TLRPC.TL_messageMediaPoll) {
            appendPoll(builder, ((TLRPC.TL_messageMediaPoll) media).poll);
        } else if (media instanceof TLRPC.TL_messageMediaGame) {
            appendGame(builder, media.game);
        } else if (media instanceof TLRPC.TL_messageMediaStory || media instanceof TLRPC.TL_messageMediaStory_layer162) {
            appendStory(builder, media.storyItem);
            appendPeer(builder, account, media.peer);
        } else if (media instanceof TLRPC.TL_messageMediaGiveawayResults) {
            appendPart(builder, ((TLRPC.TL_messageMediaGiveawayResults) media).prize_description);
        } else if (media instanceof TLRPC.TL_messageMediaGiveaway) {
            appendPart(builder, ((TLRPC.TL_messageMediaGiveaway) media).prize_description);
        } else if (media instanceof TLRPC.TL_messageMediaToDo) {
            appendTodo(builder, ((TLRPC.TL_messageMediaToDo) media).todo);
        }
        appendDocument(builder, media.document);
        if (media.alt_documents != null) {
            for (int i = 0; i < media.alt_documents.size(); i++) {
                appendDocument(builder, media.alt_documents.get(i));
            }
        }
    }

    private static void appendWebPage(StringBuilder builder, TLRPC.WebPage webpage) {
        if (webpage == null) {
            return;
        }
        appendPart(builder, webpage.url);
        appendPart(builder, webpage.display_url);
        appendPart(builder, webpage.type);
        appendPart(builder, webpage.site_name);
        appendPart(builder, webpage.title);
        appendPart(builder, webpage.description);
        appendPart(builder, webpage.embed_url);
        appendPart(builder, webpage.embed_type);
        appendPart(builder, webpage.author);
        appendPart(builder, webpage.displayedText);
        appendPart(builder, webpage.archivePath);
        appendDocument(builder, webpage.document);
    }

    private static void appendPoll(StringBuilder builder, TLRPC.Poll poll) {
        if (poll == null) {
            return;
        }
        appendTextWithEntities(builder, poll.question);
        if (poll.answers != null) {
            for (int i = 0; i < poll.answers.size(); i++) {
                TLRPC.PollAnswer answer = poll.answers.get(i);
                if (answer != null) {
                    appendTextWithEntities(builder, answer.text);
                }
            }
        }
    }

    private static void appendTodo(StringBuilder builder, TLRPC.TodoList todo) {
        if (todo == null) {
            return;
        }
        appendTextWithEntities(builder, todo.title);
        if (todo.list != null) {
            for (int i = 0; i < todo.list.size(); i++) {
                TLRPC.TodoItem item = todo.list.get(i);
                if (item != null) {
                    appendTextWithEntities(builder, item.title);
                }
            }
        }
    }

    private static void appendGame(StringBuilder builder, TLRPC.TL_game game) {
        if (game == null) {
            return;
        }
        appendPart(builder, game.short_name);
        appendPart(builder, game.title);
        appendPart(builder, game.description);
        appendDocument(builder, game.document);
    }

    private static void appendStory(StringBuilder builder, TL_stories.StoryItem storyItem) {
        if (storyItem == null) {
            return;
        }
        appendPart(builder, storyItem.caption);
        appendEntities(builder, storyItem.caption, storyItem.entities);
    }

    private static void appendDocument(StringBuilder builder, TLRPC.Document document) {
        if (document == null) {
            return;
        }
        appendPart(builder, document.file_name);
        appendPart(builder, document.file_name_fixed);
        appendPart(builder, document.mime_type);
        appendPart(builder, document.localPath);
        appendPart(builder, document.localThumbPath);
        if (document.attributes == null) {
            return;
        }
        for (int i = 0; i < document.attributes.size(); i++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(i);
            if (attribute == null) {
                continue;
            }
            appendPart(builder, attribute.file_name);
            appendPart(builder, attribute.title);
            appendPart(builder, attribute.performer);
            appendPart(builder, attribute.alt);
        }
    }

    private static void appendReplyMarkup(StringBuilder builder, TLRPC.ReplyMarkup replyMarkup) {
        if (replyMarkup.rows == null) {
            return;
        }
        for (int i = 0; i < replyMarkup.rows.size(); i++) {
            TLRPC.TL_keyboardButtonRow row = replyMarkup.rows.get(i);
            if (row == null || row.buttons == null) {
                continue;
            }
            for (int j = 0; j < row.buttons.size(); j++) {
                TLRPC.KeyboardButton button = row.buttons.get(j);
                if (button == null) {
                    continue;
                }
                appendPart(builder, button.text);
                appendPart(builder, button.url);
                appendPart(builder, button.query);
                appendPart(builder, button.fwd_text);
                if (button instanceof TLRPC.TL_keyboardButtonCopy_layer223) {
                    appendPart(builder, ((TLRPC.TL_keyboardButtonCopy_layer223) button).copy_text);
                } else if (button instanceof TLRPC.TL_keyboardButtonCopy) {
                    appendPart(builder, ((TLRPC.TL_keyboardButtonCopy) button).copy_text);
                }
            }
        }
    }

    private static void appendPeer(StringBuilder builder, int account, TLRPC.Peer peer) {
        if (peer == null) {
            return;
        }
        if (peer.user_id != 0) {
            TLRPC.User user = MessagesController.getInstance(account).getUser(peer.user_id);
            if (user != null) {
                appendPart(builder, UserObject.getUserName(user));
                appendPart(builder, UserObject.getPublicUsername(user));
            }
        } else {
            long chatId = peer.channel_id != 0 ? peer.channel_id : peer.chat_id;
            if (chatId != 0) {
                TLRPC.Chat chat = MessagesController.getInstance(account).getChat(chatId);
                if (chat != null) {
                    appendPart(builder, chat.title);
                    appendPart(builder, ChatObject.getPublicUsername(chat));
                }
            }
        }
    }

    private static void appendPart(StringBuilder builder, CharSequence value) {
        if (TextUtils.isEmpty(value)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(value);
    }

    private static String buildSnippet(String sourceText) {
        String normalized = sourceText == null ? "" : sourceText.replace('\n', ' ').trim().replaceAll("\\s+", " ");
        if (normalized.length() > 120) {
            return normalized.substring(0, 120) + "...";
        }
        return normalized;
    }
}
