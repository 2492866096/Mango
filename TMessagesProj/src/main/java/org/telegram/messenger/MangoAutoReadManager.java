package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import java.util.ArrayList;
import java.util.LinkedHashSet;

/** Foreground-only automatic reading of fresh incoming messages; never sends messages. */
public final class MangoAutoReadManager {
    private MangoAutoReadManager() {}

    public static void process(int account, ArrayList<MessageObject> incoming) {
        // Defer until MessagesController has updated dialog counters and delivered the incoming batch.
        if (incoming == null || incoming.isEmpty() || !MangoSettings.getBoolean(account, "auto_read")) return;
        long owner = UserConfig.getInstance(account).getClientUserId();
        ArrayList<MessageObject> messages = new ArrayList<>(incoming);
        AndroidUtilities.runOnUIThread(() -> {
            if (owner == UserConfig.getInstance(account).getClientUserId()) processNow(account, messages);
        }, 250);
    }

    private static void processNow(int account, ArrayList<MessageObject> messages) {
        if (!MangoSettings.getBoolean(account, "auto_read") || ApplicationLoader.mainInterfacePaused
                || !ApplicationLoader.isScreenOn || !UserConfig.getInstance(account).isClientActivated()
                || !DailyMessageTasksManager.isConnected(account)) return;
        long owner = UserConfig.getInstance(account).getClientUserId();
        SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("mango_auto_read_" + account + "_" + owner, Context.MODE_PRIVATE);
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        String saved = prefs.getString("seen", "");
        for (String key : saved.split("\n")) if (!key.isEmpty()) seen.add(key);
        int now = ConnectionsManager.getInstance(account).getCurrentTime();
        MessagesController controller = MessagesController.getInstance(account);
        for (MessageObject message : messages) {
            if (message == null || message.messageOwner == null || message.isOut() || message.isOutOwner()
                    || !message.isUnread() || message.scheduled || message.isSponsored() || message.getId() <= 0
                    || message.messageOwner.date < now - 20 || message.messageOwner.date > now + 5
                    || message.messageOwner.from_id != null && message.messageOwner.from_id.user_id == owner
                    || message.messageOwner.action != null && !(message.messageOwner.action instanceof TLRPC.TL_messageActionEmpty)) continue;
            long did = message.getDialogId();
            String key = did + ":" + message.getId();
            if (!seen.add(key)) continue;
            while (seen.size() > 512) seen.remove(seen.iterator().next());
            // Persist before marking read, so reconnection and replay cannot repeat the action.
            if (!prefs.edit().putString("seen", android.text.TextUtils.join("\n", seen)).commit()) continue;
            long topic = 0;
            if (message.messageOwner.reply_to != null && message.messageOwner.reply_to.forum_topic)
                topic = message.messageOwner.reply_to.reply_to_top_id != 0 ? message.messageOwner.reply_to.reply_to_top_id : message.messageOwner.reply_to.reply_to_msg_id;
            controller.markDialogAsRead(did, message.getId(), 0, message.messageOwner.date, false, topic, 1, true, 0);

        }
    }
}
