package org.telegram.messenger;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;

/** Read-only pagination of both ordinary and archived conversations, before confirmation. */
public final class MangoBroadcastTargets {
    public interface Callback { void complete(ArrayList<Long> users, String error); }
    private final int account;
    private final long owner;
    private final Callback callback;
    private final LinkedHashSet<Long> targets = new LinkedHashSet<>();
    private final HashSet<Long> blocked = new HashSet<>();
    private boolean cancelled, started;
    private int requestId;

    public MangoBroadcastTargets(int account, Callback callback) {
        this.account = account;
        this.owner = UserConfig.getInstance(account).getClientUserId();
        this.callback = callback;
    }

    public void load() { start(false); }
    /** Refresh exclusions before explicitly resuming a saved, confirmed snapshot. */
    public void loadBlockedOnly() { start(true); }
    private void start(boolean blockedOnly) {
        if (started || !current()) return;
        started = true;
        blockedPage(0, blockedOnly);
    }
    public void cancel() {
        cancelled = true;
        if (requestId != 0) ConnectionsManager.getInstance(account).cancelRequest(requestId, true);
    }
    private boolean current() { return !cancelled && owner != 0 && UserConfig.getInstance(account).isClientActivated()
            && UserConfig.getInstance(account).getClientUserId() == owner; }
    private void finish(String error) {
        if (!current()) return;
        cancelled = true;
        if (error != null) targets.clear();
        callback.complete(new ArrayList<>(targets), error);
    }
    private void blockedPage(int offset, boolean blockedOnly) {
        if (!current()) return;
        TLRPC.TL_contacts_getBlocked req = new TLRPC.TL_contacts_getBlocked();
        req.offset = offset;
        req.limit = 100;
        MessagesController messages = MessagesController.getInstance(account);
        requestId = ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (!current()) return;
            requestId = 0;
            if (!(response instanceof TLRPC.contacts_Blocked)) { finish(error == null ? "RESPONSE_INVALID" : error.text); return; }
            TLRPC.contacts_Blocked page = (TLRPC.contacts_Blocked) response;
            messages.putUsers(page.users, false);
            messages.putChats(page.chats, false);
            MessagesStorage.getInstance(account).putUsersAndChats(page.users, page.chats, true, true);
            int previousSize = blocked.size();
            for (TLRPC.TL_peerBlocked peer : page.blocked) blocked.add(MessageObject.getPeerId(peer.peer_id));
            int next = offset + page.blocked.size();
            if (page instanceof TLRPC.TL_contacts_blockedSlice && next < page.count) {
                if (next <= offset || blocked.size() == previousSize) { finish("BLOCKED_PAGINATION_STALLED"); return; }
                blockedPage(next, blockedOnly);
                return;
            }
            // Merge only after the complete read. Never clear a concurrent local block action.
            for (long did : blocked) messages.blockePeers.put(did, 1);
            if (blockedOnly) finish(null);
            else page(0, false, 0, 0, new TLRPC.TL_inputPeerEmpty(), new HashSet<>());
        }));
    }
    private void nextFolder(int folder) {
        if (folder == 0) page(1, false, 0, 0, new TLRPC.TL_inputPeerEmpty(), new HashSet<>());
        else finish(null);
    }
    private void page(int folder, boolean excludePinned, int offsetId, int offsetDate, TLRPC.InputPeer offsetPeer, HashSet<String> cursors) {
        if (!current()) return;
        TLRPC.TL_messages_getDialogs req = new TLRPC.TL_messages_getDialogs();
        req.flags = 2;
        req.folder_id = folder;
        req.exclude_pinned = excludePinned;
        req.limit = 100;
        req.offset_id = offsetId;
        req.offset_date = offsetDate;
        req.offset_peer = offsetPeer;
        MessagesController messages = MessagesController.getInstance(account);
        requestId = ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (!current()) return;
            requestId = 0;
            if (!(response instanceof TLRPC.messages_Dialogs)) { finish(error == null ? "RESPONSE_INVALID" : error.text); return; }
            TLRPC.messages_Dialogs page = (TLRPC.messages_Dialogs) response;
            messages.putUsers(page.users, false);
            messages.putChats(page.chats, false);
            MessagesStorage.getInstance(account).putUsersAndChats(page.users, page.chats, true, true);
            TLRPC.Dialog last = null;
            for (TLRPC.Dialog dialog : page.dialogs) {
                DialogObject.initDialog(dialog);
                if (dialog instanceof TLRPC.TL_dialogFolder) continue;
                if (dialog.top_message != 0 && !blocked.contains(dialog.id) && MangoUserBroadcastManager.canSend(account, dialog.id)) targets.add(dialog.id);
                if (!dialog.pinned) last = dialog;
            }
            if (last == null && !excludePinned && page instanceof TLRPC.TL_messages_dialogsSlice && !page.dialogs.isEmpty()) {
                page(folder, true, 0, 0, new TLRPC.TL_inputPeerEmpty(), cursors);
                return;
            }
            if (page instanceof TLRPC.TL_messages_dialogs || page.dialogs.isEmpty() || last == null) { nextFolder(folder); return; }
            int date = 0;
            for (TLRPC.Message message : page.messages) {
                if (message.id == last.top_message && MessageObject.getDialogId(message) == last.id) { date = message.date; break; }
            }
            String cursor = last.id + ":" + last.top_message + ":" + date;
            if (last.top_message == 0 || date == 0 || !cursors.add(cursor)) { finish("PAGINATION_STALLED"); return; }
            page(folder, true, last.top_message, date, messages.getInputPeer(last.id), cursors);
        }));
    }
}
