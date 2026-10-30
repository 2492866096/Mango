package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/** Foreground-only automation for fresh incoming messages, with durable duplicate suppression. */
public final class MangoAutoReadManager {
    private static final String CHARACTERS = "天地山水风云日月星光春夏秋冬花草树木清远平安快乐明亮温暖自在悠然心语梦海天空雨雪晨夜";
    private static final String REPLY_GROUPS_KEY = "auto_read_random_reply_groups";
    private MangoAutoReadManager() {}

    /** Empty on upgrade too: an existing enabled switch never opts any group in. */
    public static Set<Long> replyGroups(int account) {
        Set<Long> groups = new LinkedHashSet<>();
        for (String value : MangoSettings.getString(account, REPLY_GROUPS_KEY, "").split("\n")) {
            try {
                long did = Long.parseLong(value);
                if (DialogObject.isChatDialog(did)) groups.add(did);
            } catch (NumberFormatException ignored) { }
        }
        return groups;
    }

    public static boolean setReplyGroup(int account, long dialogId, boolean enabled) {
        if (enabled && (!DialogObject.isChatDialog(dialogId) || !DailyMessageTasksManager.isValidTarget(account, dialogId, false))) return false;
        Set<Long> groups = replyGroups(account);
        if (enabled) groups.add(dialogId); else groups.remove(dialogId);
        MangoSettings.setString(account, REPLY_GROUPS_KEY, android.text.TextUtils.join("\n", groups));
        return true;
    }

    public static String randomReply() {
        StringBuilder text = new StringBuilder(8);
        for (int i = 0; i < 8; i++) text.append(CHARACTERS.charAt(Utilities.random.nextInt(CHARACTERS.length())));
        return text.toString();
    }

    public static void process(int account, ArrayList<MessageObject> incoming) {
        // Defer until MessagesController has updated dialog counters and delivered the incoming batch.
        if (!MangoSettings.getBoolean(account, "auto_read") && !MangoSettings.getBoolean(account, "auto_read_random_reply")) return;
        long owner = UserConfig.getInstance(account).getClientUserId();
        ArrayList<MessageObject> messages = new ArrayList<>(incoming);
        AndroidUtilities.runOnUIThread(() -> {
            if (owner == UserConfig.getInstance(account).getClientUserId()) processNow(account, messages);
        }, 250);
    }

    private static void processNow(int account, ArrayList<MessageObject> messages) {
        boolean reply = MangoSettings.getBoolean(account, "auto_read_random_reply");
        boolean readAll = MangoSettings.getBoolean(account, "auto_read");
        if ((!reply && !readAll) || ApplicationLoader.mainInterfacePaused
                || !ApplicationLoader.isScreenOn || !UserConfig.getInstance(account).isClientActivated()
                || !DailyMessageTasksManager.isConnected(account)) return;
        long owner = UserConfig.getInstance(account).getClientUserId();
        SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("mango_auto_read_" + account + "_" + owner, Context.MODE_PRIVATE);
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        String saved = prefs.getString("seen", "");
        for (String key : saved.split("\n")) if (!key.isEmpty()) seen.add(key);
        int now = ConnectionsManager.getInstance(account).getCurrentTime();
        MessagesController controller = MessagesController.getInstance(account);
        Set<Long> replyGroups = replyGroups(account);
        for (MessageObject message : messages) {
            if (message == null || message.messageOwner == null || message.isOut() || message.isOutOwner()
                    || !message.isUnread() || message.scheduled || message.isSponsored() || message.getId() <= 0
                    || message.messageOwner.date < now - 20 || message.messageOwner.date > now + 5
                    || message.messageOwner.from_id != null && message.messageOwner.from_id.user_id == owner
                    || message.messageOwner.action != null && !(message.messageOwner.action instanceof TLRPC.TL_messageActionEmpty)) continue;
            long did = message.getDialogId();
            boolean replyInDialog = reply && (DialogObject.isUserDialog(did) || replyGroups.contains(did))
                    && DailyMessageTasksManager.isValidTarget(account, did, true);
            // Random reply includes marking as read, so scope both actions together.
            // The independent read-only switch keeps its existing scope.
            if (!readAll && !replyInDialog) continue;
            String key = did + ":" + message.getId();
            if (!seen.add(key)) continue;
            while (seen.size() > 512) seen.remove(seen.iterator().next());
            // Persist before read/send, so reconnection and replay cannot repeat an action.
            if (!prefs.edit().putString("seen", android.text.TextUtils.join("\n", seen)).commit()) continue;
            long topic = 0;
            if (message.messageOwner.reply_to != null && message.messageOwner.reply_to.forum_topic)
                topic = message.messageOwner.reply_to.reply_to_top_id != 0 ? message.messageOwner.reply_to.reply_to_top_id : message.messageOwner.reply_to.reply_to_msg_id;
            controller.markDialogAsRead(did, message.getId(), 0, message.messageOwner.date, false, topic, 1, true, 0);
            if (!replyInDialog
                    || controller.getSendPaidMessagesStars(did) > 0
                    || DialogObject.getMessagesStarsPrice(controller.isUserContactBlocked(did)) > 0) continue;
            long last = prefs.getLong("reply_" + did, 0);
            if (now >= last && now - last < 3) continue;
            if (!prefs.edit().putLong("reply_" + did, now).commit()) continue;
            MangoSendGuard.runConfirmed(() -> SendMessagesHelper.getInstance(account).sendMessage(
                    SendMessagesHelper.SendMessageParams.of(randomReply(), did, message, null, null, true,
                            null, null, null, !MangoSettings.getBoolean(account, "silent_send_default"), 0, 0, null, false)));
        }
    }
}
