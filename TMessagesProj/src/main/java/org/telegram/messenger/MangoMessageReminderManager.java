package org.telegram.messenger;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONObject;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;

import java.util.Calendar;
import java.util.Map;

/** One-shot, on-device reminders that open the original chat message. */
public final class MangoMessageReminderManager {
    public static final int IN_ONE_HOUR = 0;
    public static final int TOMORROW = 1;
    static final String ACTION_FIRE = "org.telegram.messenger.MANGO_MESSAGE_REMINDER";
    private static final String CHANNEL_ID = "mango_message_reminders";
    private static final String KEY_PREFIX = "reminder_";
    private static long lastTickAt;

    private MangoMessageReminderManager() { }

    private static SharedPreferences preferences(int account, long ownerId) {
        return ApplicationLoader.applicationContext.getSharedPreferences(
                "mango_message_reminders_" + account + "_" + ownerId, Context.MODE_PRIVATE);
    }

    private static String key(long dialogId, long topicId, int messageId) {
        return KEY_PREFIX + dialogId + "_" + topicId + "_" + messageId;
    }

    public static long dueAt(int choice, long now) {
        if (choice == IN_ONE_HOUR) return now + 60L * 60L * 1000L;
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now);
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 9);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    /** Converts picker fields without silently shifting invalid dates or nonexistent DST times. */
    public static long localTime(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.setLenient(false);
        calendar.set(year, month, day, hour, minute, 0);
        try {
            return calendar.getTimeInMillis();
        } catch (IllegalArgumentException ignored) {
            return 0;
        }
    }

    public static boolean hasReminder(int account, MessageObject message) {
        return getReminderTime(account, message) > 0;
    }

    /** Returns the saved time so editing starts at the existing reminder, including an overdue one. */
    public static long getReminderTime(int account, MessageObject message) {
        if (!isValidMessage(account, message)) return 0;
        long ownerId = UserConfig.getInstance(account).getClientUserId();
        if (ownerId == 0) return 0;
        JSONObject value = read(preferences(account, ownerId).getString(
                key(message.getDialogId(), message.getTopicId(), message.getId()), null));
        return value != null && value.optLong("owner") == ownerId && value.optInt("account", -1) == account
                ? value.optLong("due") : 0;
    }

    public static boolean schedule(int account, MessageObject message, int choice) {
        if (!isValidMessage(account, message) ||
                choice != IN_ONE_HOUR && choice != TOMORROW) return false;
        return scheduleAt(account, UserConfig.getInstance(account).getClientUserId(), message,
                dueAt(choice, System.currentTimeMillis()));
    }

    /** expectedOwner is captured when the editor opens, preventing a reused account slot from saving it. */
    public static boolean scheduleAt(int account, long expectedOwner, MessageObject message, long when) {
        if (!isValidMessage(account, message) || expectedOwner == 0 ||
                UserConfig.getInstance(account).getClientUserId() != expectedOwner ||
                when <= System.currentTimeMillis()) return false;
        long ownerId = expectedOwner;
        long dialogId = message.getDialogId();
        long topicId = message.getTopicId();
        String reminderKey = key(dialogId, topicId, message.getId());
        try {
            JSONObject value = new JSONObject();
            value.put("owner", ownerId);
            value.put("account", account);
            value.put("dialog", dialogId);
            value.put("topic", topicId);
            value.put("message", message.getId());
            value.put("due", when);
            MessageTagsController.Entry entry = MessageTagsController.fromMessage(account, message);
            value.put("title", TextUtils.isEmpty(entry.title) ? "" : entry.title);
            if (!preferences(account, ownerId).edit().putString(reminderKey, value.toString()).commit()) return false;
            arm(ApplicationLoader.applicationContext, reminderKey, value);
            return true;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    public static void cancel(int account, MessageObject message) {
        if (!isValidMessage(account, message)) return;
        long ownerId = UserConfig.getInstance(account).getClientUserId();
        if (ownerId == 0) return;
        String reminderKey = key(message.getDialogId(), message.getTopicId(), message.getId());
        JSONObject value = read(preferences(account, ownerId).getString(reminderKey, null));
        if (value != null) cancelAlarm(ApplicationLoader.applicationContext, reminderKey, value);
        preferences(account, ownerId).edit().remove(reminderKey).apply();
    }

    private static boolean isValidMessage(int account, MessageObject message) {
        return UserConfig.isValidAccount(account) && message != null && message.currentAccount == account &&
                message.getId() != 0 && message.getDialogId() != 0 && !message.deleted && !message.deletedByThanos;
    }

    /** Called once after account initialization and again by the boot receiver. */
    public static void rescheduleAll() {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) return;
        long now = System.currentTimeMillis();
        for (int account : UserConfig.getActivatedAccounts()) {
            long ownerId = UserConfig.getInstance(account).getClientUserId();
            if (ownerId == 0) continue;
            for (Map.Entry<String, ?> record : preferences(account, ownerId).getAll().entrySet()) {
                if (!record.getKey().startsWith(KEY_PREFIX) || !(record.getValue() instanceof String)) continue;
                JSONObject value = read((String) record.getValue());
                if (value == null || value.optLong("owner") != ownerId || value.optInt("account", -1) != account) continue;
                if (value.optLong("due") <= now) fire(context, account, ownerId, record.getKey());
                else arm(context, record.getKey(), value);
            }
        }
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        if (now >= lastTickAt && now - lastTickAt < 30000L) return;
        lastTickAt = now;
        for (int account : UserConfig.getActivatedAccounts()) {
            long ownerId = UserConfig.getInstance(account).getClientUserId();
            if (ownerId == 0) continue;
            for (Map.Entry<String, ?> record : preferences(account, ownerId).getAll().entrySet()) {
                if (!record.getKey().startsWith(KEY_PREFIX) || !(record.getValue() instanceof String)) continue;
                JSONObject value = read((String) record.getValue());
                if (value != null && value.optLong("due") <= now) fire(ApplicationLoader.applicationContext, account, ownerId, record.getKey());
            }
        }
    }

    static void fire(Context context, int account, long ownerId, String reminderKey) {
        if (!UserConfig.isValidAccount(account) || !reminderKey.startsWith(KEY_PREFIX) ||
                UserConfig.getInstance(account).getClientUserId() != ownerId) return;
        SharedPreferences prefs = preferences(account, ownerId);
        JSONObject value = read(prefs.getString(reminderKey, null));
        if (value == null || value.optLong("owner") != ownerId || value.optInt("account", -1) != account ||
                value.optLong("due") > System.currentTimeMillis() + 1000L) return;
        if (showNotification(context, reminderKey, value)) prefs.edit().remove(reminderKey).commit();
    }

    private static JSONObject read(String encoded) {
        if (encoded == null) return null;
        try { return new JSONObject(encoded); } catch (Exception e) { FileLog.e(e); return null; }
    }

    private static PendingIntent alarmIntent(Context context, String reminderKey, JSONObject value, int flags) {
        int account = value.optInt("account", -1);
        long owner = value.optLong("owner");
        Intent intent = new Intent(context, MangoMessageReminderReceiver.class);
        intent.setAction(ACTION_FIRE);
        intent.setData(Uri.parse("mango-reminder://" + account + "/" + owner + "/" + Uri.encode(reminderKey)));
        intent.putExtra("account", account);
        intent.putExtra("owner", owner);
        intent.putExtra("key", reminderKey);
        return PendingIntent.getBroadcast(context, 0, intent, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void cancelAlarm(Context context, String reminderKey, JSONObject value) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm != null) alarm.cancel(alarmIntent(context, reminderKey, value, PendingIntent.FLAG_UPDATE_CURRENT));
    }

    private static void arm(Context context, String reminderKey, JSONObject value) {
        try {
            AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarm == null) return;
            long due = value.optLong("due");
            if (due <= System.currentTimeMillis()) return;
            PendingIntent intent = alarmIntent(context, reminderKey, value, PendingIntent.FLAG_UPDATE_CURRENT);
            if (Build.VERSION.SDK_INT >= 31 && !alarm.canScheduleExactAlarms()) {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, intent);
            } else if (Build.VERSION.SDK_INT >= 23) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, intent);
            } else {
                alarm.setExact(AlarmManager.RTC_WAKEUP, due, intent);
            }
        } catch (Exception e) {
            // The durable record is retained and the foreground ticker retries delivery at its due time.
            FileLog.e(e);
        }
    }

    private static boolean showNotification(Context context, String reminderKey, JSONObject value) {
        NotificationManager notifications = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notifications == null) return false;
        if (Build.VERSION.SDK_INT >= 26) {
            notifications.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                    LocaleController.getString(R.string.MangoMessageReminderTitle), NotificationManager.IMPORTANCE_DEFAULT));
        }
        long dialogId = value.optLong("dialog");
        Intent open = new Intent(context, MangoMessageReminderOpenActivity.class);
        open.setAction("com.tmessages.openchat.reminder." + reminderKey);
        open.setData(Uri.parse("mango-reminder-open://" + value.optInt("account") + "/" +
                value.optLong("owner") + "/" + Uri.encode(reminderKey)));
        open.putExtra("currentAccount", value.optInt("account"));
        open.putExtra("reminderOwner", value.optLong("owner"));
        open.putExtra("message_id", value.optInt("message"));
        open.putExtra("topicId", value.optLong("topic"));
        if (DialogObject.isEncryptedDialog(dialogId)) open.putExtra("encId", DialogObject.getEncryptedChatId(dialogId));
        else if (dialogId < 0) open.putExtra("chatId", -dialogId);
        else open.putExtra("userId", dialogId);
        PendingIntent click = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = value.optString("title");
        String notificationTitle = LocaleController.getString(R.string.MangoMessageReminderTitle);
        if (!TextUtils.isEmpty(title) && TextUtils.isEmpty(SharedConfig.passcodeHash)) notificationTitle += " · " + title;
        boolean canNotify = NotificationManagerCompat.from(context).areNotificationsEnabled();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = notifications.getNotificationChannel(CHANNEL_ID);
            canNotify &= channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
        }
        if (!canNotify) {
            BaseFragment fragment = LaunchActivity.getLastFragment();
            if (!ApplicationLoader.mainInterfacePaused && !SharedConfig.appLocked && fragment != null && fragment.getParentActivity() != null) {
                open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.ic_save_to_gallery,
                        notificationTitle, LocaleController.getString(R.string.Open), () -> context.startActivity(open)).show();
                return true;
            }
            return false;
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.notification)
                .setContentTitle(notificationTitle)
                .setContentText(LocaleController.getString(R.string.MangoMessageReminderOpenMessage))
                .setContentIntent(click)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER);
        try {
            notifications.notify(value.optInt("account") + ":" + value.optLong("owner") + ":" + reminderKey, 0, builder.build());
            return true;
        } catch (Exception e) { FileLog.e(e); return false; }
    }
}
