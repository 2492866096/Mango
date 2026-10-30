package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import org.telegram.tgnet.TLRPC;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Opt-in echo in selected conversations; outgoing messages never trigger another echo. */
public final class MessageEchoManager {
    private static SharedPreferences preferences(int account) {
        return ApplicationLoader.applicationContext.getSharedPreferences("message_echo_" + account + "_" + UserConfig.getInstance(account).getClientUserId(), Context.MODE_PRIVATE);
    }
    public static Set<String> targets(int account) {
        return new HashSet<>(preferences(account).getStringSet("targets", new HashSet<>()));
    }
    public static void setTarget(int account, long dialogId, boolean enabled) {
        Set<String> targets = targets(account);
        if (enabled) targets.add(Long.toString(dialogId)); else targets.remove(Long.toString(dialogId));
        preferences(account).edit().putStringSet("targets", targets).putLong("armed_" + dialogId, org.telegram.tgnet.ConnectionsManager.getInstance(account).getCurrentTime()).apply();
    }
    public static boolean canEcho(int account, MessageObject message) {
        if (message == null || message.messageOwner == null || message.getId() <= 0 || message.isOut() || message.isOutOwner()
                || message.messageOwner.from_id != null && message.messageOwner.from_id.user_id == UserConfig.getInstance(account).getClientUserId()
                || message.isSponsored() || message.scheduled || message.isSending() || message.isSendError()
                || message.messageOwner.action != null && !(message.messageOwner.action instanceof TLRPC.TL_messageActionEmpty)
                || message.messageOwner.noforwards || message.messageOwner.ttl > 0
                || message.messageOwner.media != null && (message.messageOwner.media.ttl_seconds > 0 || message.messageOwner.media instanceof TLRPC.TL_messageMediaPaidMedia)
                || !DailyMessageTasksManager.isValidTarget(account, message.getDialogId(), true)) return false;
        MessagesController controller = MessagesController.getInstance(account);
        if (message.getDialogId() > 0) {
            TLRPC.UserFull full = controller.getUserFull(message.getDialogId());
            if (full != null && full.voice_messages_forbidden && (message.isVoice() || message.isRoundVideo())) return false;
        }
        TLRPC.Chat chat = message.getDialogId() < 0 ? controller.getChat(-message.getDialogId()) : null;
        return (chat == null || !chat.noforwards && SendMessagesHelper.canSendMessageToChat(chat, message) == 0)
                && controller.getSendPaidMessagesStars(message.getDialogId()) <= 0
                && DialogObject.getMessagesStarsPrice(controller.isUserContactBlocked(message.getDialogId())) <= 0;
    }
    public static boolean echo(int account, MessageObject message) {
        if (!UserConfig.getInstance(account).isClientActivated() || !DailyMessageTasksManager.isConnected(account) || !canEcho(account, message)) return false;
        ArrayList<MessageObject> messages = new ArrayList<>();
        messages.add(message);
        MessageObject topic = null;
        if (message.messageOwner.reply_to != null && message.messageOwner.reply_to.forum_topic) {
            TLRPC.TL_message root = new TLRPC.TL_message();
            root.id = message.messageOwner.reply_to.reply_to_top_id != 0 ? message.messageOwner.reply_to.reply_to_top_id : message.messageOwner.reply_to.reply_to_msg_id;
            root.peer_id = message.messageOwner.peer_id;
            root.message = "";
            topic = new MessageObject(account, root, false, false);
            topic.isTopicMainMessage = true;
        }
        return SendMessagesHelper.getInstance(account).sendMessage(messages, message.getDialogId(), true, false, true, 0, topic, -1, 0) == 0;
    }
    public static void process(int account, ArrayList<MessageObject> messages) {
        if (ApplicationLoader.mainInterfacePaused || !ApplicationLoader.isScreenOn || !UserConfig.getInstance(account).isClientActivated()) return;
        SharedPreferences prefs = preferences(account);
        Set<String> targets = targets(account);
        long now = org.telegram.tgnet.ConnectionsManager.getInstance(account).getCurrentTime();
        for (MessageObject message : messages) {
            long did = message.getDialogId();
            if (!targets.contains(Long.toString(did)) || !canEcho(account, message) || message.messageOwner.date < now - 20
                    || message.messageOwner.date <= prefs.getLong("armed_" + did, 0)
                    || message.getId() <= prefs.getInt("last_mid_" + did, 0)
                    || now - prefs.getLong("last_time_" + did, 0) < 3) continue;
            if (!DailyMessageTasksManager.isConnected(account)) continue;
            // Persist before dispatch so reconnects, edits and duplicate updates cannot resend it.
            if (prefs.edit().putInt("last_mid_" + did, message.getId()).putLong("last_time_" + did, now).commit()) {
                MangoSendGuard.runConfirmed(() -> echo(account, message));
            }
        }
    }
}
