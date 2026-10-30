package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

/** Private local bookmarks. A tag never modifies or forwards the original message. */
public final class MessageTagsController {
    public static final int IMPORTANT = 1;
    public static final int TODO = 2;
    public static final int REPLY = 4;
    public static final int FAVORITE = 8;
    public static final int[] TAGS = {IMPORTANT, TODO, REPLY, FAVORITE};

    public static final class Entry {
        public long dialogId;
        public long topicId;
        public int messageId;
        public int tags;
        public int date;
        public long updatedAt;
        public String title;
        public String preview;

        public String key() {
            return "message_" + dialogId + "_" + topicId + "_" + messageId;
        }
    }

    private MessageTagsController() {}

    private static SharedPreferences preferences(int account) {
        return ApplicationLoader.applicationContext.getSharedPreferences(
                "message_tags_" + account + "_" + UserConfig.getInstance(account).getClientUserId(), Context.MODE_PRIVATE);
    }

    public static int tagName(int tag) {
        switch (tag) {
            case IMPORTANT: return R.string.MessageTagImportant;
            case TODO: return R.string.MessageTagTodo;
            case REPLY: return R.string.MessageTagReply;
            default: return R.string.MessageTagFavorite;
        }
    }

    public static String tagsText(int tags) {
        StringBuilder result = new StringBuilder();
        for (int tag : TAGS) {
            if ((tags & tag) == 0) continue;
            if (result.length() > 0) result.append(" · ");
            result.append(LocaleController.getString(tagName(tag)));
        }
        return result.toString();
    }

    public static Entry fromMessage(int account, MessageObject message) {
        Entry entry = new Entry();
        entry.dialogId = message.getDialogId();
        entry.topicId = message.getTopicId();
        entry.messageId = message.getId();
        entry.date = message.messageOwner.date;
        MessagesController controller = MessagesController.getInstance(account);
        if (DialogObject.isEncryptedDialog(entry.dialogId)) {
            entry.title = LocaleController.getString(R.string.SecretChat);
        } else if (entry.dialogId < 0) {
            TLRPC.Chat chat = controller.getChat(-entry.dialogId);
            entry.title = chat == null ? Long.toString(entry.dialogId) : chat.title;
        } else {
            TLRPC.User user = controller.getUser(entry.dialogId);
            entry.title = user == null ? Long.toString(entry.dialogId) : UserObject.getUserName(user);
        }
        CharSequence preview = !TextUtils.isEmpty(message.caption) ? message.caption : message.messageText;
        // Do not create a second plaintext copy of secret-chat or disappearing-message content.
        entry.preview = DialogObject.isEncryptedDialog(entry.dialogId) || message.isSecretMedia() ?
                LocaleController.getString(R.string.MessageTagPrivateMessage) :
                preview == null ? "" : preview.subSequence(0, Math.min(preview.length(), 320)).toString();
        entry.tags = getTags(account, entry);
        return entry;
    }

    public static int getTags(int account, Entry entry) {
        String encoded = preferences(account).getString(entry.key(), null);
        if (encoded == null) return 0;
        try {
            return new JSONObject(encoded).optInt("tags", 0) & 15;
        } catch (Exception e) {
            FileLog.e(e);
            return 0;
        }
    }

    public static void setTags(int account, Entry entry, int tags) {
        entry.tags = tags & 15;
        if (entry.tags == 0) {
            preferences(account).edit().remove(entry.key()).apply();
            return;
        }
        if (entry.dialogId == 0 || entry.messageId == 0) return;
        entry.updatedAt = System.currentTimeMillis();
        try {
            JSONObject value = new JSONObject();
            value.put("dialog", entry.dialogId);
            value.put("topic", entry.topicId);
            value.put("message", entry.messageId);
            value.put("tags", entry.tags);
            value.put("date", entry.date);
            value.put("updated", entry.updatedAt);
            value.put("title", entry.title);
            value.put("preview", entry.preview);
            preferences(account).edit().putString(entry.key(), value.toString()).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static ArrayList<Entry> getEntries(int account, int filter) {
        ArrayList<Entry> result = new ArrayList<>();
        for (Map.Entry<String, ?> item : preferences(account).getAll().entrySet()) {
            if (!item.getKey().startsWith("message_") || !(item.getValue() instanceof String)) continue;
            try {
                JSONObject value = new JSONObject((String) item.getValue());
                Entry entry = new Entry();
                entry.tags = value.optInt("tags", 0) & 15;
                if (entry.tags == 0 || filter != 0 && (entry.tags & filter) == 0) continue;
                entry.dialogId = value.getLong("dialog");
                entry.topicId = value.optLong("topic", 0);
                entry.messageId = value.getInt("message");
                entry.date = value.optInt("date", 0);
                entry.updatedAt = value.optLong("updated", 0);
                entry.title = value.optString("title", "");
                entry.preview = value.optString("preview", "");
                result.add(entry);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        Collections.sort(result, (a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        return result;
    }
}
