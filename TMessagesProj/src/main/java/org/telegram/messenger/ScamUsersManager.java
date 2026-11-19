package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import org.telegram.tgnet.TLRPC;
import java.util.ArrayList;
import java.util.HashMap;

/** Account-owned public-list cache. Requests contain the configured URL only. UI-thread API. */
public final class ScamUsersManager {
    public static final String DEFAULT_SOURCE = "https://1812652903.cdn.123clouddisk.com/1812652903/GY/%E9%AA%97%E5%AD%90%E6%A0%87%E8%AE%B0.txt";
    private static final DispatchQueue queue = new DispatchQueue("scamUsers");
    private static final HashMap<String, State> states = new HashMap<>();
    private static final ArrayList<Runnable> listeners = new ArrayList<>();
    private static final Runnable periodic = ScamUsersManager::refreshAll;
    private static class State {
        String source = "";
        RemoteScamUsers.Entries entries = new RemoteScamUsers.Entries();
        long updated, attempted;
        boolean busy, failed;
    }
    private static SharedPreferences prefs(int account) {
        return ApplicationLoader.applicationContext.getSharedPreferences("scam_users_" + account + "_"
                + UserConfig.getInstance(account).getClientUserId(), Context.MODE_PRIVATE);
    }
    public static String source(int account) { return MangoSettings.getString(account, "scam_users_url", DEFAULT_SOURCE); }
    public static boolean enabled(int account) { return MangoSettings.getBoolean(account, "scam_users_enabled", true); }
    public static void setEnabled(int account, boolean enabled) {
        MangoSettings.setBoolean(account, "scam_users_enabled", enabled);
        refresh(account, false);
        scheduleNextRefresh();
        notifyListeners();
    }
    public static void setSource(int account, String url) {
        MangoSettings.setString(account, "scam_users_url", url.trim());
        state(account);
        refresh(account, true);
        scheduleNextRefresh();
        notifyListeners();
    }
    private static State state(int account) {
        String owner = account + ":" + UserConfig.getInstance(account).getClientUserId();
        String source = source(account);
        State state = states.get(owner);
        if (state == null || !source.equals(state.source)) {
            state = new State(); state.source = source;
            SharedPreferences prefs = prefs(account);
            if (source.equals(prefs.getString("source", ""))) {
                try { state.entries = RemoteScamUsers.parse(prefs.getString("data", "")); state.updated = prefs.getLong("updated", 0); }
                catch (Exception e) { FileLog.e(e); }
            }
            states.put(owner, state);
        }
        return state;
    }
    public static int count(int account) { return state(account).entries.size; }
    public static long updated(int account) { return state(account).updated; }
    public static boolean busy(int account) { return state(account).busy; }
    public static boolean failed(int account) { return state(account).failed; }
    public static boolean isAnyRefreshing() {
        for (int account : UserConfig.getActivatedAccounts()) if (state(account).busy) return true;
        return false;
    }
    public static boolean hasRefreshFailed() {
        for (int account : UserConfig.getActivatedAccounts()) if (enabled(account) && state(account).failed) return true;
        return false;
    }
    public static void addListener(Runnable listener) { if (!listeners.contains(listener)) listeners.add(listener); }
    public static void removeListener(Runnable listener) { listeners.remove(listener); }
    private static void notifyListeners() { for (Runnable listener : new ArrayList<>(listeners)) listener.run(); }
    public static void refreshAll() {
        AndroidUtilities.runOnUIThread(() -> {
            for (int account : UserConfig.getActivatedAccounts()) if (enabled(account)) refresh(account, false);
            scheduleNextRefresh();
        });
    }
    private static void scheduleNextRefresh() {
        AndroidUtilities.cancelRunOnUIThread(periodic);
        long delay = Long.MAX_VALUE;
        for (int account : UserConfig.getActivatedAccounts()) {
            State state = state(account);
            if (!enabled(account) || !RemoteScamUsers.validUrl(state.source)) continue;
            long age = System.currentTimeMillis() - state.updated;
            long due = age >= 0 ? Math.max(1000, ScamProtectionManager.REFRESH_INTERVAL_MS - age) : 1000;
            if (state.attempted != 0) due = Math.max(due, 300000 - (SystemClock.elapsedRealtime() - state.attempted));
            if (state.busy) due = Math.max(due, 300000);
            delay = Math.min(delay, due);
        }
        if (delay != Long.MAX_VALUE) AndroidUtilities.runOnUIThread(periodic, delay);
    }
    public static void refresh(int account, boolean force) {
        State state = state(account);
        if (state.busy || !RemoteScamUsers.validUrl(state.source) || !force && !enabled(account)) return;
        long age = System.currentTimeMillis() - state.updated;
        if (!force && age >= 0 && age < ScamProtectionManager.REFRESH_INTERVAL_MS) return;
        long elapsed = SystemClock.elapsedRealtime();
        if (!force && state.attempted != 0 && elapsed - state.attempted < 300000) return;
        state.busy = true; state.attempted = elapsed;
        long owner = UserConfig.getInstance(account).getClientUserId();
        String source = state.source;
        notifyListeners();
        queue.postRunnable(() -> {
            String data = null;
            RemoteScamUsers.Entries entries = null;
            try { data = RemoteScamUsers.fetch(source); entries = RemoteScamUsers.parse(data); }
            catch (Exception e) { FileLog.e(e); }
            final String result = data;
            final RemoteScamUsers.Entries parsed = entries;
            AndroidUtilities.runOnUIThread(() -> {
                state.busy = false;
                if (owner != UserConfig.getInstance(account).getClientUserId() || state != state(account)) return;
                state.failed = parsed == null;
                if (parsed != null) {
                    state.entries = parsed; state.updated = System.currentTimeMillis();
                    prefs(account).edit().putString("source", source).putString("data", result).putLong("updated", state.updated).apply();
                    NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME);
                }
                notifyListeners();
                scheduleNextRefresh();
            });
        });
    }
    public static TLRPC.User match(int account, MessageObject message) {
        if (!enabled(account) || message == null || message.messageOwner == null || message.isOutOwner() || message.isOut()
                || message.getDialogId() == UserConfig.getInstance(account).getClientUserId()) return null;
        long id = message.messageOwner.from_id == null ? message.getDialogId() : message.messageOwner.from_id.user_id;
        return matchUser(account, id);
    }
    /** Match the peer even when a private chat has no incoming or currently visible messages. */
    public static TLRPC.User matchUser(int account, long userId) {
        if (!enabled(account) || userId <= 0 || userId == UserConfig.getInstance(account).getClientUserId()) return null;
        refresh(account, false);
        TLRPC.User user = MessagesController.getInstance(account).getUser(userId);
        State state = state(account);
        boolean matches = state.entries.contains(userId, user == null ? null : UserObject.getPublicUsername(user));
        if (!matches && user != null && user.usernames != null) {
            for (TLRPC.TL_username name : user.usernames) if (name != null && name.active && state.entries.contains(userId, name.username)) { matches = true; break; }
        }
        if (!matches) return null;
        // ID-only matches may precede Telegram's user cache. This object is for the warning
        // text only; opening a profile must still require a real cached Telegram user.
        if (user == null || user instanceof TLRPC.TL_userEmpty) { user = new TLRPC.TL_user(); user.id = userId; }
        return user;
    }

    /** Broadcast channels use their own peer namespace; a user ID never flags a channel. */
    public static TLRPC.Chat matchChannel(int account, long channelId) {
        if (!enabled(account) || channelId <= 0) return null;
        refresh(account, false);
        TLRPC.Chat chat = MessagesController.getInstance(account).getChat(channelId);
        if (chat == null || !ChatObject.isChannelAndNotMegaGroup(chat)) return null;
        RemoteScamUsers.Entries entries = state(account).entries;
        if (entries.containsChannel(channelId, ChatObject.getPublicUsername(chat))) return chat;
        if (chat.usernames != null) {
            for (TLRPC.TL_username name : chat.usernames) {
                if (name != null && name.active && entries.containsChannel(channelId, name.username)) return chat;
            }
        }
        return null;
    }

    /** Includes posts sent as a channel into another chat, but never labels the group itself. */
    public static TLRPC.Chat matchChannelSender(int account, MessageObject message) {
        if (!enabled(account) || message == null || message.messageOwner == null || message.isOutOwner() || message.isOut()) return null;
        TLRPC.Peer from = message.messageOwner.from_id;
        return from != null && from.channel_id > 0 ? matchChannel(account, from.channel_id) : null;
    }
}
