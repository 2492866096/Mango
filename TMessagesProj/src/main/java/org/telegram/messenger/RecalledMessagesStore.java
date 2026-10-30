package org.telegram.messenger;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

/** Durable, identity-scoped copies of messages already present on this device. */
public final class RecalledMessagesStore {
    private static SQLiteOpenHelper helper;

    private static synchronized SQLiteDatabase database() {
        if (helper == null) {
            helper = new SQLiteOpenHelper(ApplicationLoader.applicationContext, "recalled_messages.db", null, 1) {
                @Override public void onCreate(SQLiteDatabase db) {
                    db.execSQL("CREATE TABLE recalled (account INTEGER, owner INTEGER, did INTEGER, mid INTEGER, date INTEGER, data BLOB, PRIMARY KEY(account,owner,did,mid))");
                }
                @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }
            };
        }
        return helper.getWritableDatabase();
    }

    public static boolean isEnabled(int account) {
        return ApplicationLoader.applicationContext.getSharedPreferences("recalled_settings_" + account + "_" + UserConfig.getInstance(account).getClientUserId(), Context.MODE_PRIVATE)
                .getBoolean("enabled", false);
    }

    public static void setEnabled(int account, boolean enabled) {
        ApplicationLoader.applicationContext.getSharedPreferences("recalled_settings_" + account + "_" + UserConfig.getInstance(account).getClientUserId(), Context.MODE_PRIVATE)
                .edit().putBoolean("enabled", enabled).apply();
    }

    public static void save(int account, TLRPC.Message message) {
        save(account, UserConfig.getInstance(account).getClientUserId(), message);
    }

    public static void saveAsync(int account, TLRPC.Message message) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        Utilities.globalQueue.postRunnable(() -> save(account, owner, message));
    }

    private static void save(int account, long owner, TLRPC.Message message) {
        if (owner == 0 || owner != UserConfig.getInstance(account).getClientUserId() || !isEnabled(account) || message == null || message.id <= 0 || message.ttl > 0
                || (message.media != null && message.media.ttl_seconds > 0) || DialogObject.isEncryptedDialog(MessageObject.getDialogId(message))) return;
        try {
            SerializedData stream = new SerializedData();
            message.serializeToStream(stream);
            ContentValues values = new ContentValues();
            values.put("account", account);
            values.put("owner", owner);
            values.put("did", MessageObject.getDialogId(message));
            values.put("mid", message.id);
            values.put("date", message.date);
            values.put("data", stream.toByteArray());
            stream.cleanup();
            if (owner == UserConfig.getInstance(account).getClientUserId()) {
                database().insertWithOnConflict("recalled", null, values, SQLiteDatabase.CONFLICT_REPLACE);
            }
        } catch (Exception e) { FileLog.e(e); }
    }

    /** Called on Telegram's storage queue before any deletion or UI notification. */
    public static void captureDeleted(int account, long dialogId, ArrayList<Integer> ids) {
        captureDeleted(account, UserConfig.getInstance(account).getClientUserId(), dialogId, ids);
    }

    public static void captureDeleted(int account, long owner, long dialogId, ArrayList<Integer> ids) {
        if (owner == 0 || owner != UserConfig.getInstance(account).getClientUserId()) return;
        if (!isEnabled(account) || ids == null || ids.isEmpty()) return;
        for (int offset = 0; offset < ids.size(); offset += 300) {
            String joined = android.text.TextUtils.join(",", ids.subList(offset, Math.min(ids.size(), offset + 300)));
            String where = dialogId == 0 ? "1=1" : "uid = " + dialogId;
            SQLiteCursor cursor = null;
            try {
                cursor = MessagesStorage.getInstance(account).getDatabase().queryFinalized("SELECT uid,mid,data FROM messages_v2 WHERE " + where + " AND mid IN(" + joined + ")");
                while (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(2);
                    if (data == null) continue;
                    try {
                        TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                        if (message == null) continue;
                        message.dialog_id = cursor.longValue(0);
                        message.id = cursor.intValue(1);
                        if (dialogId == 0 && message.peer_id != null && message.peer_id.channel_id != 0) continue;
                        message.readAttachPath(data, UserConfig.getInstance(account).getClientUserId());
                        save(account, owner, message);
                    } finally { data.reuse(); }
                }
            } catch (Exception e) { FileLog.e(e); }
            finally { if (cursor != null) cursor.dispose(); }
        }
    }

    private static boolean sameOwner(int account, long owner) {
        return owner != 0 && owner == UserConfig.getInstance(account).getClientUserId();
    }

    private static TLRPC.Message read(Cursor cursor, long owner) {
        SerializedData stream = new SerializedData(cursor.getBlob(2));
        try {
            TLRPC.Message message = TLRPC.Message.TLdeserialize(stream, stream.readInt32(false), false);
            if (message == null) return null;
            message.dialog_id = cursor.getLong(0);
            message.id = cursor.getInt(1);
            message.readAttachPath(stream, owner);
            message.send_state = MessageObject.MESSAGE_SEND_STATE_DELETED;
            message.unread = message.media_unread = false;
            return message;
        } finally { stream.cleanup(); }
    }

    public static ArrayList<TLRPC.Message> list(int account, long dialogId) {
        ArrayList<TLRPC.Message> result = new ArrayList<>();
        long owner = UserConfig.getInstance(account).getClientUserId();
        if (owner == 0) return result;
        String selection = "account=? AND owner=?" + (dialogId == 0 ? "" : " AND did=?");
        String[] args = dialogId == 0 ? new String[]{"" + account, "" + owner} : new String[]{"" + account, "" + owner, "" + dialogId};
        try (Cursor cursor = database().query("recalled", new String[]{"did", "mid", "data"}, selection, args, null, null, "date DESC,mid DESC", "2000")) {
            while (cursor.moveToNext()) {
                TLRPC.Message message = read(cursor, owner);
                if (message != null) result.add(message);
            }
        } catch (Exception e) { FileLog.e(e); }
        if (!sameOwner(account, owner)) result.clear();
        return result;
    }

    /** Compatibility overload: a nonzero anchor means loading older messages. */
    public static void merge(int account, long dialogId, ArrayList<TLRPC.Message> messages, int maxId, int count, long topicId, boolean isEnd) {
        merge(account, dialogId, messages, maxId, count, topicId, isEnd, MessagesController.LOAD_BACKWARD, 0);
    }

    /** The load type and date are the same values passed to MessagesStorage.getMessages. */
    public static void merge(int account, long dialogId, ArrayList<TLRPC.Message> messages, int maxId, int count,
                             long topicId, boolean isEnd, int loadType, int offsetDate) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        if (!sameOwner(account, owner) || !isEnabled(account) || !MessagesController.getInstance(account).isAntiRevokeEnabled()) return;
        // Work on a copy so a logout/account change while reading cannot publish another owner's data.
        ArrayList<TLRPC.Message> merged = new ArrayList<>(messages);
        HashMap<Integer, Integer> existing = new HashMap<>();
        int min = Integer.MAX_VALUE, max = 0;
        for (int i = 0; i < merged.size(); i++) {
            TLRPC.Message message = merged.get(i);
            existing.put(message.id, i);
            if (message.id > 0) { min = Math.min(min, message.id); max = Math.max(max, message.id); }
        }
        replaceExisting(account, owner, dialogId, merged, existing, topicId);
        int limit = Math.max(1, count);
        boolean empty = max == 0;
        if (empty && (loadType == MessagesController.LOAD_AROUND_MESSAGE || loadType == MessagesController.LOAD_FROM_UNREAD) && maxId > 0) {
            mergeRange(account, owner, dialogId, merged, existing, "mid <= " + maxId, "mid DESC", (limit + 1) / 2, topicId);
            mergeRange(account, owner, dialogId, merged, existing, "mid > " + maxId, "mid ASC", limit / 2, topicId);
        } else if (empty && loadType == MessagesController.LOAD_AROUND_DATE && offsetDate > 0) {
            mergeRange(account, owner, dialogId, merged, existing, "date <= " + offsetDate, "date DESC,mid DESC", (limit + 1) / 2, topicId);
            mergeRange(account, owner, dialogId, merged, existing, "date > " + offsetDate, "date ASC,mid ASC", limit / 2, topicId);
        } else {
            String range;
            String order = "mid DESC";
            if (loadType == MessagesController.LOAD_FORWARD) {
                range = "mid > " + Math.max(0, maxId);
                if (!empty) range += " AND mid <= " + max;
                order = "mid ASC";
            } else if (loadType == MessagesController.LOAD_BACKWARD && maxId > 0) {
                range = "mid < " + maxId;
                if (!empty && !isEnd) range += " AND mid >= " + min;
            } else if (!empty && (loadType == MessagesController.LOAD_AROUND_MESSAGE || loadType == MessagesController.LOAD_AROUND_DATE
                    || loadType == MessagesController.LOAD_FROM_UNREAD && maxId > 0)) {
                range = "mid >= " + min + " AND mid <= " + max;
            } else {
                range = !empty && !isEnd ? "mid >= " + min : "mid > 0";
            }
            mergeRange(account, owner, dialogId, merged, existing, range, order, limit, topicId);
        }
        if (!sameOwner(account, owner) || !isEnabled(account)) return;
        merged.sort((a, b) -> Integer.compare(b.id, a.id));
        messages.clear();
        messages.addAll(merged);
    }

    private static void replaceExisting(int account, long owner, long dialogId, ArrayList<TLRPC.Message> messages,
                                         HashMap<Integer, Integer> existing, long topicId) {
        ArrayList<Integer> ids = new ArrayList<>(existing.keySet());
        for (int start = 0; start < ids.size(); start += 300) {
            String joined = android.text.TextUtils.join(",", ids.subList(start, Math.min(ids.size(), start + 300)));
            String selection = "account=? AND owner=? AND did=? AND mid IN(" + joined + ")";
            try (Cursor cursor = database().query("recalled", new String[]{"did", "mid", "data"}, selection,
                    new String[]{"" + account, "" + owner, "" + dialogId}, null, null, null)) {
                while (cursor.moveToNext()) {
                    if (!sameOwner(account, owner)) return;
                    TLRPC.Message message = read(cursor, owner);
                    if (message != null && (topicId == 0 || message.reply_to != null
                            && (message.reply_to.reply_to_top_id == topicId || message.reply_to.reply_to_msg_id == topicId)))
                        messages.set(existing.get(message.id), message);
                }
            } catch (Exception e) { FileLog.e(e); }
        }
    }

    /** Reads only this peer's requested interval; there is no global 2,000-row cutoff. */
    private static void mergeRange(int account, long owner, long dialogId, ArrayList<TLRPC.Message> merged,
                                   HashMap<Integer, Integer> existing, String range, String order, int limit, long topicId) {
        if (limit <= 0) return;
        String selection = "account=? AND owner=? AND did=? AND (" + range + ")";
        String[] args = {"" + account, "" + owner, "" + dialogId};
        int added = 0;
        try (Cursor cursor = database().query("recalled", new String[]{"did", "mid", "data"}, selection, args, null, null, order)) {
            while (cursor.moveToNext()) {
                if (!sameOwner(account, owner)) return;
                int id = cursor.getInt(1);
                Integer position = existing.get(id);
                if (position != null) continue;
                if (added >= limit) break;
                TLRPC.Message recalled = read(cursor, owner);
                if (recalled == null) continue;
                if (topicId != 0 && (recalled.reply_to == null || (recalled.reply_to.reply_to_top_id != topicId && recalled.reply_to.reply_to_msg_id != topicId))) continue;
                existing.put(id, merged.size()); merged.add(recalled); added++;
            }
        } catch (Exception e) { FileLog.e(e); }
    }

    public static void remove(int account, long dialogId, ArrayList<Integer> ids) {
        try {
            database().delete("recalled", String.format(Locale.US, "account=%d AND owner=%d AND did=%d AND mid IN(%s)", account,
                    UserConfig.getInstance(account).getClientUserId(), dialogId, android.text.TextUtils.join(",", ids)), null);
        } catch (Exception e) { FileLog.e(e); }
    }

    public static void clear(int account) {
        try { database().delete("recalled", "account=? AND owner=?", new String[]{"" + account, "" + UserConfig.getInstance(account).getClientUserId()}); }
        catch (Exception e) { FileLog.e(e); }
    }
}
