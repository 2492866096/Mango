package org.telegram.messenger;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

/** Loads both normal and archived memberships. Never leaves until start/retry is invoked. */
public final class ChannelBatchLeaveController {
    private static final ChannelBatchLeaveController[] instances = new ChannelBatchLeaveController[UserConfig.MAX_ACCOUNT_COUNT];
    public static ChannelBatchLeaveController getInstance(int account) {
        if (instances[account] == null || instances[account].ownerUserId != UserConfig.getInstance(account).getClientUserId()) {
            if (instances[account] != null) instances[account].queue.stop();
            instances[account] = new ChannelBatchLeaveController(account);
        }
        return instances[account];
    }
    private final int account;
    private final long ownerUserId;
    private final LinkedHashMap<Long, TLRPC.Chat> chats = new LinkedHashMap<>();
    private final ArrayList<Runnable> listeners = new ArrayList<>();
    private final BatchLeaveQueue queue;
    private boolean loading;
    private String loadError;
    private int generation;

    private ChannelBatchLeaveController(int account) {
        this.account = account;
        ownerUserId = UserConfig.getInstance(account).getClientUserId();
        queue = new BatchLeaveQueue(this::leave, this::notifyChanged);
    }
    private MessagesController messages() { return MessagesController.getInstance(account); }
    private ConnectionsManager connections() { return ConnectionsManager.getInstance(account); }
    private boolean sameAccount() { return UserConfig.getInstance(account).isClientActivated() && UserConfig.getInstance(account).getClientUserId() == ownerUserId; }
    public void addListener(Runnable listener) { if (!listeners.contains(listener)) listeners.add(listener); }
    public void removeListener(Runnable listener) { listeners.remove(listener); }
    private void notifyChanged() { for (Runnable listener : new ArrayList<>(listeners)) listener.run(); }
    public boolean isLoading() { return loading; }
    public String getLoadError() { return loadError; }
    public BatchLeaveQueue queue() { return queue; }
    public TLRPC.Chat getChat(long id) { return chats.get(id); }
    public ArrayList<TLRPC.Chat> getChats() {
        ArrayList<TLRPC.Chat> result = new ArrayList<>(chats.values());
        Collections.sort(result, (a, b) -> String.valueOf(a.title).compareToIgnoreCase(String.valueOf(b.title)));
        return result;
    }
    public static boolean canLeave(TLRPC.Chat chat) {
        return chat != null && !chat.creator && !chat.left && !chat.kicked && !chat.deactivated
                && !(chat instanceof TLRPC.TL_chatForbidden) && !(chat instanceof TLRPC.TL_channelForbidden)
                && chat.migrated_to == null && !chat.monoforum;
    }
    private static boolean isMembership(TLRPC.Chat chat) {
        return chat != null && !chat.left && !chat.kicked && !chat.deactivated && chat.migrated_to == null && !chat.monoforum
                && !(chat instanceof TLRPC.TL_chatForbidden) && !(chat instanceof TLRPC.TL_channelForbidden);
    }
    public void refresh() {
        if (loading || queue.isRunning()) return;
        if (!sameAccount()) {
            chats.clear();
            loadError = "AUTH_KEY_UNREGISTERED";
            notifyChanged();
            return;
        }
        LinkedHashMap<Long, TLRPC.Chat> previous = new LinkedHashMap<>(chats);
        chats.clear();
        for (BatchLeaveQueue.Result result : queue.results()) {
            if (previous.containsKey(result.id)) chats.put(result.id, previous.get(result.id));
        }
        for (TLRPC.Dialog dialog : messages().getAllDialogs()) {
            if (dialog.id < 0 && !(dialog instanceof TLRPC.TL_dialogFolder)) {
                TLRPC.Chat chat = messages().getChat(-dialog.id);
                if (isMembership(chat)) chats.put(chat.id, chat);
            }
        }
        loading = true;
        loadError = null;
        int token = ++generation;
        notifyChanged();
        loadPage(token, 0, false, 0, 0, new TLRPC.TL_inputPeerEmpty(), new HashSet<>());
    }
    private void loadPage(int token, int folder, boolean excludePinned, int offsetId, int offsetDate, TLRPC.InputPeer offsetPeer, HashSet<String> cursors) {
        if (token != generation || !sameAccount()) { loading = false; notifyChanged(); return; }
        TLRPC.TL_messages_getDialogs req = new TLRPC.TL_messages_getDialogs();
        req.flags = 2;
        req.folder_id = folder;
        req.exclude_pinned = excludePinned;
        req.limit = 100;
        req.offset_id = offsetId;
        req.offset_date = offsetDate;
        req.offset_peer = offsetPeer;
        connections().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (token != generation || !sameAccount()) { loading = false; notifyChanged(); return; }
            if (!(response instanceof TLRPC.messages_Dialogs)) {
                loading = false;
                loadError = error == null ? "RESPONSE_INVALID" : error.text;
                notifyChanged();
                return;
            }
            TLRPC.messages_Dialogs page = (TLRPC.messages_Dialogs) response;
            messages().putUsers(page.users, false);
            messages().putChats(page.chats, false);
            MessagesStorage.getInstance(account).putUsersAndChats(page.users, page.chats, true, true);
            TLRPC.Dialog last = null;
            for (TLRPC.Dialog dialog : page.dialogs) {
                DialogObject.initDialog(dialog);
                if (dialog instanceof TLRPC.TL_dialogFolder) continue;
                if (dialog.id < 0) {
                    TLRPC.Chat chat = messages().getChat(-dialog.id);
                    if (isMembership(chat)) chats.put(chat.id, chat);
                }
                if (!dialog.pinned) last = dialog;
            }
            notifyChanged();
            if (last == null && !excludePinned && page instanceof TLRPC.TL_messages_dialogsSlice && !page.dialogs.isEmpty()) {
                // A full first page may consist entirely of pinned dialogs.
                loadPage(token, folder, true, 0, 0, new TLRPC.TL_inputPeerEmpty(), cursors);
                return;
            }
            if (page instanceof TLRPC.TL_messages_dialogs || page.dialogs.isEmpty() || last == null) {
                finishFolder(token, folder);
                return;
            }
            int date = 0;
            for (TLRPC.Message message : page.messages) {
                if (message.id == last.top_message && MessageObject.getDialogId(message) == last.id) { date = message.date; break; }
            }
            String cursor = last.id + ":" + last.top_message + ":" + date;
            if (!cursors.add(cursor) || last.top_message == 0) {
                loading = false;
                loadError = "PAGINATION_STALLED";
                notifyChanged();
                return;
            }
            loadPage(token, folder, true, last.top_message, date, messages().getInputPeer(last.id), cursors);
        }));
    }
    private void finishFolder(int token, int folder) {
        if (folder == 0) loadPage(token, 1, false, 0, 0, new TLRPC.TL_inputPeerEmpty(), new HashSet<>());
        else { loading = false; notifyChanged(); }
    }
    public void start(List<Long> ids) {
        if (loading || !sameAccount()) return;
        ArrayList<Long> valid = new ArrayList<>();
        for (long id : ids) if (canLeave(chats.get(id))) valid.add(id);
        queue.start(valid);
    }
    private void leave(long id, BatchLeaveQueue.Completion completion) {
        TLRPC.Chat chat = chats.get(id);
        if (!sameAccount() || !canLeave(chat)) {
            AndroidUtilities.runOnUIThread(() -> completion.complete("MEMBERSHIP_CHANGED", !sameAccount()));
            return;
        }
        TLObject request;
        if (ChatObject.isChannel(chat)) {
            TLRPC.TL_channels_leaveChannel req = new TLRPC.TL_channels_leaveChannel();
            req.channel = MessagesController.getInputChannel(chat);
            request = req;
        } else {
            TLRPC.TL_messages_deleteChatUser req = new TLRPC.TL_messages_deleteChatUser();
            req.chat_id = chat.id;
            req.user_id = new TLRPC.TL_inputUserSelf();
            req.revoke_history = false;
            request = req;
        }
        connections().sendRequest(request, (response, error) -> {
            if (sameAccount() && response instanceof TLRPC.Updates) messages().processUpdates((TLRPC.Updates) response, false);
            AndroidUtilities.runOnUIThread(() -> {
                if (!sameAccount()) { completion.complete("AUTH_KEY_UNREGISTERED", true); return; }
                String failure = error == null ? response instanceof TLRPC.Updates ? null : "RESPONSE_INVALID" : error.text;
                if ("USER_NOT_PARTICIPANT".equals(failure)) failure = null;
                if (failure == null) {
                    chat.left = true;
                    messages().putChat(chat, false);
                    ArrayList<TLRPC.Chat> changed = new ArrayList<>();
                    changed.add(chat);
                    MessagesStorage.getInstance(account).putUsersAndChats(null, changed, true, true);
                    // Remove local dialog only after the server confirms membership exit.
                    messages().deleteDialog(-id, 3);
                }
                final String result = failure;
                AndroidUtilities.runOnUIThread(() -> completion.complete(result,
                        result != null && (result.startsWith("FLOOD_WAIT") || result.startsWith("AUTH_KEY"))), 700);
            });
        }, ConnectionsManager.RequestFlagInvokeAfter);
    }
}
