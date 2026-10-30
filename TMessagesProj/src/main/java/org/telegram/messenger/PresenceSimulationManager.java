package org.telegram.messenger;

import android.os.SystemClock;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

/** Explicit, short-lived presence actions. Nothing is restored after process death. */
public final class PresenceSimulationManager {
    public static final int NORMAL = 0, ONLINE = 1, OFFLINE = 2;
    private static final PresenceSimulationManager[] instances = new PresenceSimulationManager[UserConfig.MAX_ACCOUNT_COUNT];

    public static PresenceSimulationManager getInstance(int account) {
        synchronized (instances) {
            if (instances[account] == null) {
                instances[account] = new PresenceSimulationManager(account);
            }
            return instances[account];
        }
    }

    private final int account;
    private volatile int presenceMode;
    private volatile long presenceUntil, ownerId;
    private long typingUntil, typingDialog, lastTypingAt;
    private int typingAction;
    private boolean typingRequestPending;
    private String typingTitle = "";
    private int generation;

    private PresenceSimulationManager(int account) {
        this.account = account;
    }

    public int getPresenceMode() {
        return ownerId == UserConfig.getInstance(account).getClientUserId() && SystemClock.elapsedRealtime() < presenceUntil
                ? presenceMode : NORMAL;
    }

    public long getPresenceRemainingMinutes() {
        return getPresenceMode() == NORMAL ? 0 : Math.max(1, (presenceUntil - SystemClock.elapsedRealtime() + 59999) / 60000);
    }

    public void setPresence(int mode, int minutes) {
        ownerId = UserConfig.getInstance(account).getClientUserId();
        presenceMode = mode >= NORMAL && mode <= OFFLINE ? mode : NORMAL;
        presenceUntil = presenceMode == NORMAL ? 0 : SystemClock.elapsedRealtime() + Math.max(1, Math.min(60, minutes)) * 60000L;
        MessagesController.getInstance(account).refreshExtensionPresence();
    }

    public boolean startTyping(long dialogId, String title, int action, int minutes) {
        if (!DailyMessageTasksManager.isValidTarget(account, dialogId, true)
                || MessagesController.getInstance(account).isHideTypingStatusEnabled()
                || !DailyMessageTasksManager.isConnected(account)) {
            return false;
        }
        stopTyping();
        ownerId = UserConfig.getInstance(account).getClientUserId();
        typingDialog = dialogId;
        typingTitle = title;
        typingAction = Math.max(0, Math.min(2, action));
        typingUntil = SystemClock.elapsedRealtime() + Math.max(1, Math.min(60, minutes)) * 60000L;
        lastTypingAt = 0;
        tick();
        return true;
    }

    public String getTypingTitle() {
        return typingDialog != 0 && SystemClock.elapsedRealtime() < typingUntil ? typingTitle : "";
    }

    public void stopTyping() {
        long dialogId = typingDialog;
        typingDialog = typingUntil = lastTypingAt = 0;
        typingTitle = "";
        typingRequestPending = false;
        generation++;
        if (dialogId != 0 && ownerId == UserConfig.getInstance(account).getClientUserId() && DailyMessageTasksManager.isConnected(account)) {
            TLRPC.TL_messages_setTyping request = new TLRPC.TL_messages_setTyping();
            request.peer = MessagesController.getInstance(account).getInputPeer(dialogId);
            request.action = new TLRPC.TL_sendMessageCancelAction();
            if (request.peer != null && !(request.peer instanceof TLRPC.TL_inputPeerEmpty)) {
                ConnectionsManager.getInstance(account).sendRequest(request, (response, error) -> { });
            }
        }
    }

    public void cleanup() {
        ownerId = 0;
        presenceMode = NORMAL;
        presenceUntil = 0;
        stopTyping();
    }

    public void tick() {
        if (presenceMode != NORMAL && getPresenceMode() == NORMAL) {
            presenceMode = NORMAL;
            presenceUntil = 0;
            MessagesController.getInstance(account).refreshExtensionPresence();
        }
        if (typingDialog == 0) {
            return;
        }
        if (ownerId != UserConfig.getInstance(account).getClientUserId() || SystemClock.elapsedRealtime() >= typingUntil
                || ApplicationLoader.mainInterfacePaused || !ApplicationLoader.isScreenOn
                || MessagesController.getInstance(account).isHideTypingStatusEnabled()
                || !DailyMessageTasksManager.isValidTarget(account, typingDialog, true)) {
            stopTyping();
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (typingRequestPending || now - lastTypingAt < 5000 || !DailyMessageTasksManager.isConnected(account)) {
            return;
        }
        TLRPC.TL_messages_setTyping request = new TLRPC.TL_messages_setTyping();
        request.peer = MessagesController.getInstance(account).getInputPeer(typingDialog);
        if (request.peer == null || request.peer instanceof TLRPC.TL_inputPeerEmpty) {
            stopTyping();
            return;
        }
        request.action = typingAction == 1 ? new TLRPC.TL_sendMessageRecordAudioAction()
                : typingAction == 2 ? new TLRPC.TL_sendMessageChooseStickerAction() : new TLRPC.TL_sendMessageTypingAction();
        lastTypingAt = now;
        typingRequestPending = true;
        final int requestGeneration = generation;
        ConnectionsManager.getInstance(account).sendRequest(request, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (generation != requestGeneration) {
                return;
            }
            typingRequestPending = false;
            if (error != null) {
                stopTyping();
            }
        }), ConnectionsManager.RequestFlagFailOnServerErrors);
    }
}
