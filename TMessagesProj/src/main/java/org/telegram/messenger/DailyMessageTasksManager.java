package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.TimeZone;
import java.util.UUID;

/** Local daily tasks. All mutations and outgoing-message operations run on the UI thread. */
public final class DailyMessageTasksManager {
    public static final int RANDOM_ONLINE = 0, FIXED_TIME = 1;
    public static final int QUEUED = 0, OFFLINE = 1, INVALID_TARGET = 2, STORAGE_ERROR = 3, INVALID_TEXT = 4, PAID_TARGET = 5;
    private static final DailyMessageTasksManager[] instances = new DailyMessageTasksManager[UserConfig.MAX_ACCOUNT_COUNT];

    public static DailyMessageTasksManager getInstance(int account) {
        synchronized (instances) {
            if (instances[account] == null) {
                instances[account] = new DailyMessageTasksManager(account);
            }
            return instances[account];
        }
    }

    public static final class Task {
        public String id = UUID.randomUUID().toString();
        public long dialogId;
        public String title = "", text = "";
        public int hour = 9, minute;
        public int mode = RANDOM_ONLINE, randomWindowStart = 0, randomWindowEnd = 1439;
        public long randomDueAt;
        public boolean automatic;
        public long armedAt, lastAttemptAt;
        public String lastDay = "";
        public int lastResult = -1;

        public Task copy() {
            Task result = new Task();
            result.id = id;
            result.dialogId = dialogId;
            result.title = title;
            result.text = text;
            result.hour = hour;
            result.minute = minute;
            result.mode = mode;
            result.randomWindowStart = randomWindowStart;
            result.randomWindowEnd = randomWindowEnd;
            result.randomDueAt = randomDueAt;
            result.automatic = automatic;
            result.armedAt = armedAt;
            result.lastAttemptAt = lastAttemptAt;
            result.lastDay = lastDay;
            result.lastResult = lastResult;
            return result;
        }
    }

    private final int account;
    private final SharedPreferences preferences;
    private final ArrayList<Task> tasks = new ArrayList<>();
    private long ownerId = Long.MIN_VALUE, previousTime, previousElapsed;
    private String previousZone;

    private DailyMessageTasksManager(int account) {
        this.account = account;
        preferences = ApplicationLoader.applicationContext.getSharedPreferences("extension_daily_tasks_" + account, Context.MODE_PRIVATE);
    }

    private void ensureLoaded() {
        long userId = UserConfig.getInstance(account).getClientUserId();
        if (ownerId == userId) {
            return;
        }
        ownerId = userId;
        tasks.clear();
        resetWindow();
        if (userId == 0 || preferences.getLong("owner_id", 0) != userId) {
            return;
        }
        try {
            JSONArray array = new JSONArray(preferences.getString("tasks", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                Task task = new Task();
                task.id = object.getString("id");
                task.dialogId = object.getLong("dialog_id");
                task.title = object.optString("title");
                task.text = object.getString("text");
                task.hour = object.getInt("hour");
                task.minute = object.getInt("minute");
                task.mode = object.optInt("mode", RANDOM_ONLINE);
                task.randomWindowStart = object.optInt("random_window_start", 0);
                task.randomWindowEnd = object.optInt("random_window_end", 1439);
                task.randomDueAt = object.optLong("random_due_at");
                task.automatic = object.optBoolean("automatic");
                task.armedAt = object.optLong("armed_at");
                task.lastDay = object.optString("last_day");
                task.lastAttemptAt = object.optLong("last_attempt_at");
                task.lastResult = object.optInt("last_result", -1);
                if (valid(task)) {
                    tasks.add(task);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public ArrayList<Task> getTasks() {
        ensureLoaded();
        ArrayList<Task> result = new ArrayList<>();
        for (Task task : tasks) {
            result.add(task.copy());
        }
        return result;
    }

    public boolean save(Task draft) {
        ensureLoaded();
        if (ownerId == 0 || !valid(draft)) {
            return false;
        }
        Task task = draft.copy();
        int index = indexOf(task.id);
        Task old = index < 0 ? null : tasks.get(index);
        // Editing a task never clears today's deduplication record.
        if (old != null) {
            task.lastDay = old.lastDay;
            task.lastAttemptAt = old.lastAttemptAt;
            task.lastResult = old.lastResult;
        }
        task.armedAt = System.currentTimeMillis();
        task.randomDueAt = 0;
        if (index < 0) {
            tasks.add(task);
        } else {
            tasks.set(index, task);
        }
        if (persist()) {
            return true;
        }
        if (index < 0) {
            tasks.remove(task);
        } else {
            tasks.set(index, old);
        }
        return false;
    }

    public boolean remove(String id) {
        ensureLoaded();
        int index = indexOf(id);
        if (index < 0) {
            return false;
        }
        Task old = tasks.remove(index);
        if (persist()) {
            return true;
        }
        tasks.add(index, old);
        return false;
    }

    public int triggerNow(String id) {
        ensureLoaded();
        int index = indexOf(id);
        return index < 0 ? INVALID_TARGET : trigger(tasks.get(index));
    }

    public void cleanup() {
        ownerId = Long.MIN_VALUE;
        tasks.clear();
        resetWindow();
        preferences.edit().clear().commit();
    }

    public static boolean isValidTarget(int account, long dialogId, boolean allowUsers) {
        if (dialogId == 0 || DialogObject.isEncryptedDialog(dialogId)) {
            return false;
        }
        MessagesController controller = MessagesController.getInstance(account);
        if (dialogId > 0) {
            TLRPC.User user = controller.getUser(dialogId);
            return user != null && !UserObject.isDeleted(user) && user.id != UserConfig.getInstance(account).getClientUserId()
                    && (allowUsers || user.bot);
        }
        TLRPC.Chat chat = controller.getChat(-dialogId);
        return chat != null && !ChatObject.isNotInChat(chat) && !ChatObject.isChannelAndNotMegaGroup(chat)
                && ChatObject.canSendMessages(chat) && ChatObject.canSendPlain(chat);
    }

    public static boolean isConnected(int account) {
        int state = ConnectionsManager.getInstance(account).getConnectionState();
        return state == ConnectionsManager.ConnectionStateConnected || state == ConnectionsManager.ConnectionStateUpdating;
    }

    public void tick() {
        ensureLoaded();
        if (ownerId == 0 || ApplicationLoader.mainInterfacePaused || !ApplicationLoader.isScreenOn || !isConnected(account)) {
            resetWindow();
            return;
        }
        long now = System.currentTimeMillis();
        long elapsed = SystemClock.elapsedRealtime();
        TimeZone zone = TimeZone.getDefault();
        boolean continuous = previousTime != 0 && now > previousTime && now - previousTime <= 90000 && zone.getID().equals(previousZone)
                && Math.abs((now - previousTime) - (elapsed - previousElapsed)) < 5000;
        for (Task task : tasks) {
            if (!task.automatic || DailyScheduleWindow.dayKey(now, zone).equals(task.lastDay)) continue;
            if (task.mode == RANDOM_ONLINE) {
                if (!DailyScheduleWindow.inRandomWindow(now, task.randomWindowStart, task.randomWindowEnd, zone)) continue;
                if (task.randomDueAt == 0 || !DailyScheduleWindow.dayKey(now, zone).equals(DailyScheduleWindow.dayKey(task.randomDueAt, zone))
                        || !continuous && task.randomDueAt <= now) {
                    long oldDue = task.randomDueAt;
                    task.randomDueAt = DailyScheduleWindow.planRandomTime(now, task.randomWindowStart, task.randomWindowEnd,
                            Utilities.random.nextDouble(), zone);
                    if (!persist()) { task.randomDueAt = oldDue; continue; }
                }
                if (DailyScheduleWindow.isRandomDue(now, task.randomDueAt, task.randomWindowStart, task.randomWindowEnd, task.lastDay, zone)) trigger(task);
            } else if (continuous && DailyScheduleWindow.isDue(previousTime, now, task.hour, task.minute, task.armedAt, task.lastDay, zone)) {
                trigger(task);
            }
        }
        previousTime = now;
        previousElapsed = elapsed;
        previousZone = zone.getID();
    }

    private int trigger(Task task) {
        if (!UserConfig.getInstance(account).isClientActivated() || !isConnected(account)) {
            return OFFLINE;
        }
        MessagesController controller = MessagesController.getInstance(account);
        int result = !valid(task) ? INVALID_TEXT : !isValidTarget(account, task.dialogId, false) ? INVALID_TARGET
                : controller.getSendPaidMessagesStars(task.dialogId) > 0
                || DialogObject.getMessagesStarsPrice(controller.isUserContactBlocked(task.dialogId)) > 0 ? PAID_TARGET : QUEUED;
        String oldDay = task.lastDay;
        long oldAttempt = task.lastAttemptAt;
        int oldResult = task.lastResult;
        task.lastAttemptAt = System.currentTimeMillis();
        task.lastDay = DailyScheduleWindow.dayKey(task.lastAttemptAt, TimeZone.getDefault());
        task.lastResult = result;
        // Commit before enqueuing. A crash may skip one send, but cannot duplicate one.
        if (!persist()) {
            task.lastDay = oldDay;
            task.lastAttemptAt = oldAttempt;
            task.lastResult = oldResult;
            return STORAGE_ERROR;
        }
        if (result == QUEUED) {
            MangoSendGuard.runConfirmed(() -> SendMessagesHelper.getInstance(account).sendMessage(SendMessagesHelper.SendMessageParams.of(task.text,
                    task.dialogId, null, null, null, true, null, null, null, true, 0, 0, null, false)));
        }
        return result;
    }

    private boolean valid(Task task) {
        return task != null && !TextUtils.isEmpty(task.id) && task.dialogId != 0 && task.text != null && !TextUtils.isEmpty(task.text.trim())
                && task.text.length() <= 4096 && task.hour >= 0 && task.hour <= 23 && task.minute >= 0 && task.minute <= 59
                && (task.mode == RANDOM_ONLINE || task.mode == FIXED_TIME) && task.randomWindowStart >= 0
                && task.randomWindowEnd <= 1439 && task.randomWindowStart <= task.randomWindowEnd;
    }

    private int indexOf(String id) {
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).id.equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private void resetWindow() {
        previousTime = previousElapsed = 0;
        previousZone = null;
    }

    public void onAppPaused() {
        resetWindow();
    }

    private boolean persist() {
        try {
            JSONArray array = new JSONArray();
            for (Task task : tasks) {
                JSONObject object = new JSONObject();
                object.put("id", task.id);
                object.put("dialog_id", task.dialogId);
                object.put("title", task.title);
                object.put("text", task.text);
                object.put("hour", task.hour);
                object.put("minute", task.minute);
                object.put("mode", task.mode);
                object.put("random_window_start", task.randomWindowStart);
                object.put("random_window_end", task.randomWindowEnd);
                object.put("random_due_at", task.randomDueAt);
                object.put("automatic", task.automatic);
                object.put("armed_at", task.armedAt);
                object.put("last_day", task.lastDay);
                object.put("last_attempt_at", task.lastAttemptAt);
                object.put("last_result", task.lastResult);
                array.put(object);
            }
            return preferences.edit().putLong("owner_id", ownerId).putString("tasks", array.toString()).commit();
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }
}
