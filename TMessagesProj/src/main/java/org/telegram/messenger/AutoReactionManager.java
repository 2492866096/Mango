package org.telegram.messenger;

import android.text.TextUtils;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public class AutoReactionManager {

    private static final int MAX_PROCESSED_MESSAGES = 600;
    private static final int MAX_REACTION_MESSAGE_AGE_SECONDS = 10 * 60;

    private static final AutoReactionManager[] instances = new AutoReactionManager[UserConfig.MAX_ACCOUNT_COUNT];

    public static AutoReactionManager getInstance(int account) {
        synchronized (instances) {
            AutoReactionManager instance = instances[account];
            if (instance == null) {
                instance = new AutoReactionManager(account);
                instances[account] = instance;
            }
            return instance;
        }
    }

    private final int currentAccount;
    private final ArrayList<String> processedMessageKeys = new ArrayList<>();
    private long processedOwner;

    private AutoReactionManager(int account) {
        currentAccount = account;
    }

    public void processNewMessages(ArrayList<MessageObject> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (int i = 0; i < messages.size(); i++) {
            processMessage(messages.get(i));
        }
    }

    public void processSentMessage(MessageObject messageObject) {
        processMessage(messageObject);
    }

    private void processMessage(MessageObject messageObject) {
        if (!shouldReact(messageObject)) {
            return;
        }
        long dialogId = messageObject.getDialogId();
        int messageId = messageObject.getId();
        boolean outgoing = messageObject.isOutOwner();
        long owner = UserConfig.getInstance(currentAccount).getClientUserId();
        if (!markMessageProcessed(owner, dialogId, messageId, outgoing)) {
            return;
        }
        String emoji = SharedConfig.getAutoReactionEmoji();
        if (TextUtils.isEmpty(emoji)) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> sendReaction(messageObject, emoji, owner), 350);
    }

    private boolean shouldReact(MessageObject messageObject) {
        if (!SharedConfig.isAutoReactionEnabled() || messageObject == null || messageObject.messageOwner == null
                || messageObject.currentAccount != currentAccount
                || !UserConfig.getInstance(currentAccount).isClientActivated()
                || UserConfig.getInstance(currentAccount).getClientUserId() == 0) {
            return false;
        }
        if (messageObject.getId() <= 0 || messageObject.isSending() || messageObject.isSponsored()) {
            return false;
        }
        if (messageObject.messageOwner.action != null) {
            return false;
        }
        long dialogId = messageObject.getDialogId();
        if (!isDialogScopeEnabled(dialogId)) {
            return false;
        }
        // Thread messages can react to the original channel post. Both the displayed
        // conversation and the destination must be within the user's selected scope.
        if (messageObject.messageOwner.isThreadMessage && messageObject.messageOwner.fwd_from != null
                && !isDialogScopeEnabled(messageObject.getFromChatId())) {
            return false;
        }
        int messageDate = messageObject.messageOwner.date;
        int currentTime = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        if (messageDate > 0 && currentTime - messageDate > MAX_REACTION_MESSAGE_AGE_SECONDS) {
            return false;
        }
        boolean outgoing = messageObject.isOutOwner();
        int target = SharedConfig.getAutoReactionTarget();
        boolean matchesDirection = target == SharedConfig.AUTO_REACTION_TARGET_BOTH
                || target == SharedConfig.AUTO_REACTION_TARGET_OUTGOING && outgoing
                || target == SharedConfig.AUTO_REACTION_TARGET_INCOMING && !outgoing;
        return matchesDirection && SharedConfig.isAutoReactionUserAllowed(currentAccount, messageObject.getSenderId());
    }

    private boolean isDialogScopeEnabled(long dialogId) {
        if (DialogObject.isEncryptedDialog(dialogId)) {
            return false;
        }
        if (DialogObject.isUserDialog(dialogId)) {
            return SharedConfig.isAutoReactionPrivateEnabled();
        }
        if (!DialogObject.isChatDialog(dialogId)) {
            return false;
        }
        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
        if (chat == null) {
            return false;
        }
        if (ChatObject.isMonoForum(chat)) {
            return SharedConfig.isAutoReactionPrivateEnabled();
        }
        return ChatObject.isChannelAndNotMegaGroup(chat)
                ? SharedConfig.isAutoReactionChannelsEnabled()
                : SharedConfig.isAutoReactionGroupsEnabled();
    }

    private void sendReaction(MessageObject messageObject, String emoji, long owner) {
        // A pending reaction must not survive a disabled switch, changed scope or account.
        if (owner != UserConfig.getInstance(currentAccount).getClientUserId()
                || !shouldReact(messageObject) || TextUtils.isEmpty(emoji)) {
            return;
        }
        TLRPC.InputPeer peer;
        int messageId;
        if (messageObject.messageOwner.isThreadMessage && messageObject.messageOwner.fwd_from != null) {
            peer = MessagesController.getInstance(currentAccount).getInputPeer(messageObject.getFromChatId());
            messageId = messageObject.messageOwner.fwd_from.saved_from_msg_id;
        } else {
            peer = MessagesController.getInstance(currentAccount).getInputPeer(messageObject.getDialogId());
            if (messageObject.messageOwner.peer_id instanceof TLRPC.TL_peerChannel && !(peer instanceof TLRPC.TL_inputPeerChannel)) {
                peer = MessagesController.getInstance(currentAccount).getInputPeer(messageObject.messageOwner.peer_id);
            }
            messageId = messageObject.getId();
        }
        if (peer == null || messageId <= 0) {
            return;
        }
        TLRPC.TL_messages_sendReaction req = new TLRPC.TL_messages_sendReaction();
        req.peer = peer;
        req.msg_id = messageId;
        req.flags |= 1;
        TLRPC.TL_reactionEmoji reaction = new TLRPC.TL_reactionEmoji();
        reaction.emoticon = emoji;
        req.reaction.add(reaction);
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (response instanceof TLRPC.Updates && UserConfig.getInstance(currentAccount).isClientActivated()
                    && owner == UserConfig.getInstance(currentAccount).getClientUserId()) {
                MessagesController.getInstance(currentAccount).processUpdates((TLRPC.Updates) response, false);
            }
        });
    }

    private synchronized boolean markMessageProcessed(long owner, long dialogId, int messageId, boolean outgoing) {
        if (processedOwner != owner) {
            processedOwner = owner;
            processedMessageKeys.clear();
        }
        String key = dialogId + "_" + messageId + "_" + outgoing;
        if (processedMessageKeys.contains(key)) {
            return false;
        }
        processedMessageKeys.add(0, key);
        while (processedMessageKeys.size() > MAX_PROCESSED_MESSAGES) {
            processedMessageKeys.remove(processedMessageKeys.size() - 1);
        }
        return true;
    }
}
