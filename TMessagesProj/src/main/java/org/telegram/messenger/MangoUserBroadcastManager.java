package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import org.json.JSONArray;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** A confirmed, owner-bound snapshot. UI thread only; no automatic retry of submitted messages. */
public final class MangoUserBroadcastManager {
    public static final int IDLE = 0, RUNNING = 1, PAUSED = 2, DONE = 3, CANCELLED = 4;
    public static final long INTERVAL_MS = 3000;
    private static final MangoUserBroadcastManager[] instances = new MangoUserBroadcastManager[UserConfig.MAX_ACCOUNT_COUNT];
    public static MangoUserBroadcastManager getInstance(int account) {
        if (instances[account] == null) instances[account] = new MangoUserBroadcastManager(account);
        instances[account].ensureLoaded();
        return instances[account];
    }
    private final int account;
    private final SharedPreferences preferences;
    private long owner = Long.MIN_VALUE, dueAt, missingUser;
    private boolean loadingUser;
    private int generation;
    private String text = "";
    private final ArrayList<Long> targets = new ArrayList<>();
    private int cursor, skipped, state;

    private MangoUserBroadcastManager(int account) {
        this.account = account;
        preferences = ApplicationLoader.applicationContext.getSharedPreferences("mango_user_broadcast_" + account, Context.MODE_PRIVATE);
    }
    private void ensureLoaded() {
        long current = UserConfig.getInstance(account).getClientUserId();
        if (owner == current) return;
        owner = current;
        generation++; loadingUser = false; missingUser = 0;
        targets.clear(); cursor = skipped = 0; state = IDLE; text = "";
        if (owner == 0 || preferences.getLong("owner", 0) != owner) {
            preferences.edit().clear().commit();
            return;
        }
        try {
            JSONArray array = new JSONArray(preferences.getString("targets", "[]"));
            for (int i = 0; i < array.length(); i++) targets.add(array.getLong(i));
            text = preferences.getString("text", "");
            cursor = Math.max(0, Math.min(targets.size(), preferences.getInt("cursor", 0)));
            skipped = Math.max(0, Math.min(cursor, preferences.getInt("skipped", 0)));
            state = preferences.getInt("state", IDLE);
            // Process death never silently restarts a broadcast. Resume needs another tap and confirmation.
            if (state == RUNNING) { state = PAUSED; persist(); }
        } catch (Exception e) { FileLog.e(e); state = CANCELLED; text = ""; targets.clear(); }
    }
    public int state() { ensureLoaded(); return state; }
    public int total() { ensureLoaded(); return targets.size(); }
    public int processed() { ensureLoaded(); return cursor; }
    public int submitted() { ensureLoaded(); return cursor - skipped; }
    public int skipped() { ensureLoaded(); return skipped; }
    public String message() { ensureLoaded(); return text; }
    public boolean isRunning() { return state() == RUNNING; }
    public boolean hasPending() { return (state() == RUNNING || state == PAUSED) && cursor < targets.size(); }
    public static boolean canSend(int account, long did) {
        if (!DialogObject.isUserDialog(did) || did == UserConfig.getInstance(account).getClientUserId()) return false;
        MessagesController controller = MessagesController.getInstance(account);
        TLRPC.User user = controller.getUser(did);
        return user != null && !user.bot && user.send_paid_messages_stars <= 0 && !UserObject.isDeleted(user) && !UserObject.isService(did) && !UserObject.isReplyUser(did)
                && (!user.contact_require_premium || UserConfig.getInstance(account).isPremium())
                && !DialogObject.isPremiumBlocked(controller.isUserContactBlocked(did))
                && controller.blockePeers.indexOfKey(did) < 0 && controller.getSendPaidMessagesStars(did) <= 0
                && DialogObject.getMessagesStarsPrice(controller.isUserContactBlocked(did)) <= 0;
    }
    public boolean start(String message, List<Long> confirmedTargets) {
        ensureLoaded();
        if (owner == 0 || hasPending() || confirmedTargets == null || message == null || message.trim().isEmpty()
                || message.length() > Math.min(4096, MessagesController.getInstance(account).maxMessageLength)) return false;
        targets.clear();
        for (Long id : new LinkedHashSet<>(confirmedTargets)) if (id != null && canSend(account, id)) targets.add(id);
        if (targets.isEmpty()) return false;
        text = message.trim(); cursor = skipped = 0; state = RUNNING; dueAt = SystemClock.elapsedRealtime();
        generation++; loadingUser = false; missingUser = 0;
        if (persist()) return true;
        state = IDLE; targets.clear(); text = ""; return false;
    }
    public boolean resume() {
        ensureLoaded();
        if (state != PAUSED || owner == 0 || cursor >= targets.size()) return false;
        state = RUNNING; dueAt = SystemClock.elapsedRealtime() + INTERVAL_MS;
        if (persist()) return true;
        state = PAUSED; return false;
    }
    public void pause() {
        ensureLoaded();
        if (state == RUNNING) { state = PAUSED; persist(); }
    }
    public void cancel() {
        ensureLoaded();
        if (state == RUNNING || state == PAUSED) { state = CANCELLED; text = ""; generation++; loadingUser = false; persist(); }
    }
    /** Called synchronously on logout, including a later login by the same owner. */
    public static void clearForLogout(int account) {
        MangoUserBroadcastManager manager = instances[account];
        if (manager != null) {
            manager.generation++;
            manager.loadingUser = false;
            manager.missingUser = 0;
            manager.owner = Long.MIN_VALUE;
            manager.targets.clear();
            manager.cursor = manager.skipped = 0;
            manager.text = "";
            manager.state = IDLE;
        }
        ApplicationLoader.applicationContext.getSharedPreferences("mango_user_broadcast_" + account, Context.MODE_PRIVATE)
                .edit().clear().commit();
    }
    public void tick(long now) {
        ensureLoaded();
        if (state != RUNNING || owner == 0 || !UserConfig.getInstance(account).isClientActivated() || now < dueAt) return;
        if (!DailyMessageTasksManager.isConnected(account)) return;
        if (cursor >= targets.size()) { state = DONE; text = ""; persist(); return; }
        long did = targets.get(cursor);
        // A resumed job may reference users outside the first in-memory dialog page.
        // Read their persisted identity off the UI thread before eligibility is rechecked.
        if (MessagesController.getInstance(account).getUser(did) == null && missingUser != did) {
            if (!loadingUser) {
                loadingUser = true;
                final long expectedOwner = owner;
                final int token = generation;
                MessagesStorage storage = MessagesStorage.getInstance(account);
                storage.getStorageQueue().postRunnable(() -> {
                    TLRPC.User user = storage.getUser(did);
                    AndroidUtilities.runOnUIThread(() -> {
                        if (token != generation || expectedOwner != UserConfig.getInstance(account).getClientUserId()) return;
                        loadingUser = false;
                        if (user != null) MessagesController.getInstance(account).putUser(user, true);
                        else missingUser = did;
                    });
                });
            }
            return;
        }
        boolean valid = canSend(account, did);
        // Durable claim precedes enqueue: process death can skip one claim, never duplicate it.
        cursor++;
        if (!valid) skipped++;
        if (!persist()) { cursor--; if (!valid) skipped--; state = PAUSED; return; }
        dueAt = now + INTERVAL_MS;
        if (valid) {
            try {
                MangoSendGuard.runConfirmed(() -> SendMessagesHelper.getInstance(account).sendMessage(
                        SendMessagesHelper.SendMessageParams.of(text, did, null, null, null, false,
                                null, null, null, false, 0, 0, null, false)));
            } catch (Exception e) { FileLog.e(e); skipped++; persist(); }
        }
        if (cursor == targets.size()) { state = DONE; text = ""; persist(); }
    }
    private boolean persist() {
        JSONArray array = new JSONArray();
        for (long id : targets) array.put(id);
        return preferences.edit().putLong("owner", owner).putString("targets", array.toString()).putString("text", text)
                .putInt("cursor", cursor).putInt("skipped", skipped).putInt("state", state).commit();
    }
}
