package org.telegram.messenger;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.TimeZone;

/** All Telegram database reads run on the owning MessagesStorage queue. */
public final class LocalMessageInsightsRepository {
    public static final String DIALOGS_SQL = "SELECT uid, MAX(date), COUNT(*) FROM messages_v2 WHERE date > 0 GROUP BY uid ORDER BY MAX(date) DESC";
    public static final String STATISTICS_SQL = "SELECT date, out FROM messages_v2 WHERE uid = ? AND date >= ? AND date < ? AND mid > 0 AND send_state = 0 ORDER BY date";

    public interface Callback<T> { void onResult(T value, boolean failed); }

    public static final class DialogEntry {
        public long dialogId;
        public String title;
        public int count;
        public boolean unavailable;
    }

    public static final class Statistics {
        public int sent;
        public int received;
        public int activeDays;
        public int calendarDays;
        public int firstDate;
        public int lastDate;
        public double perDay() { return calendarDays == 0 ? 0 : (sent + received) / (double) calendarDays; }
        public double perActiveDay() { return activeDays == 0 ? 0 : (sent + received) / (double) activeDays; }
    }

    public static void dialogs(int account, boolean usersOnly, Callback<ArrayList<DialogEntry>> callback) {
        final long owner = UserConfig.getInstance(account).getClientUserId();
        MessagesStorage storage = MessagesStorage.getInstance(account);
        storage.getStorageQueue().postRunnable(() -> {
            ArrayList<DialogEntry> entries = new ArrayList<>();
            SQLiteCursor cursor = null;
            boolean failed = false;
            try {
                cursor = storage.getDatabase().queryFinalized(DIALOGS_SQL);
                while (cursor.next()) {
                    long id = cursor.longValue(0);
                    if (id == 0 || id == owner || DialogObject.isEncryptedDialog(id) || usersOnly && id < 0) continue;
                    DialogEntry entry = new DialogEntry();
                    entry.dialogId = id;
                    entry.count = cursor.intValue(2);
                    if (id > 0) {
                        TLRPC.User user = storage.getUser(id);
                        if (usersOnly && user != null && user.bot) continue;
                        entry.title = user == null ? Long.toString(id) : UserObject.getUserName(user);
                        entry.unavailable = user != null && UserObject.isDeleted(user);
                    } else {
                        TLRPC.Chat chat = storage.getChat(-id);
                        entry.title = chat == null ? Long.toString(id) : chat.title;
                        entry.unavailable = chat != null && (chat.deactivated || chat.kicked || chat.left
                                || chat instanceof TLRPC.TL_channelForbidden || chat instanceof TLRPC.TL_chatForbidden);
                    }
                    entries.add(entry);
                }
            } catch (Exception e) {
                FileLog.e(e);
                failed = true;
            } finally {
                if (cursor != null) cursor.dispose();
            }
            boolean error = failed;
            AndroidUtilities.runOnUIThread(() -> {
                if (owner == UserConfig.getInstance(account).getClientUserId()) callback.onResult(entries, error);
            });
        });
    }

    public static void statistics(int account, long dialogId, long from, long until, TimeZone zone, Callback<Statistics> callback) {
        final long owner = UserConfig.getInstance(account).getClientUserId();
        MessagesStorage storage = MessagesStorage.getInstance(account);
        storage.getStorageQueue().postRunnable(() -> {
            Statistics result = new Statistics();
            HashSet<Integer> active = new HashSet<>();
            SQLiteCursor cursor = null;
            boolean failed = false;
            try {
                result.calendarDays = MessageInsightsRules.inclusiveDays(from, until - 1, zone);
                cursor = storage.getDatabase().queryFinalized(STATISTICS_SQL, dialogId, from, until);
                while (cursor.next()) {
                    int date = cursor.intValue(0);
                    if (cursor.intValue(1) != 0) result.sent++; else result.received++;
                    if (result.firstDate == 0) result.firstDate = date;
                    result.lastDate = date;
                    active.add(MessageInsightsRules.dayKey(date, zone));
                }
                result.activeDays = active.size();
            } catch (Exception e) {
                FileLog.e(e);
                failed = true;
            } finally {
                if (cursor != null) cursor.dispose();
            }
            boolean error = failed;
            AndroidUtilities.runOnUIThread(() -> {
                if (owner == UserConfig.getInstance(account).getClientUserId()) callback.onResult(result, error);
            });
        });
    }
}
