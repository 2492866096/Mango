package org.telegram.ui.Components.quickforward;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.Bulletin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class QuickShareSelectorOverlayLayout extends View {
    private final HashMap<String, QuickShareSelectorDrawable> drawableHashMap = new HashMap<>();
    private final ArrayList<String> drawablesForRemove = new ArrayList<>();
    private final int sourceAccount;

    public QuickShareSelectorOverlayLayout(Context context) {
        this(context, UserConfig.selectedAccount);
    }

    public QuickShareSelectorOverlayLayout(Context context, int sourceAccount) {
        super(context);
        this.sourceAccount = sourceAccount;
    }

    public int getSourceAccount() {
        return sourceAccount;
    }

    public void open(ChatMessageCell cell) {
        open(cell, false);
    }

    public void open(ChatMessageCell cell, boolean chooseSendingAccount) {
        fetchDialogs();

        final String key = key(cell);
        if (key == null) {
            return;
        }

        // When cross-account quick forwarding is enabled the first gesture
        // step is deliberately limited to choosing the sending account. The
        // recipient is selected in that account's own dialog picker after the
        // gesture is released. Legacy mode keeps the current account's quick
        // targets in the bubble.
        final ArrayList<QuickShareDestination> destinations = chooseSendingAccount
                ? buildAccountChoices()
                : buildDestinationsForAccount(sourceAccount);
        if (destinations.isEmpty()) {
            return;
        }

        final QuickShareSelectorDrawable drawable = new QuickShareSelectorDrawable(this, cell, destinations, key, () -> {
            drawablesForRemove.add(key);
            invalidate();
        });
        drawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
        drawable.setCallback(this);

        if (!drawableHashMap.containsKey(key)) {
            drawableHashMap.put(key, drawable);
        }
    }

    public boolean isActive() {
        for (Map.Entry<String, QuickShareSelectorDrawable> e : drawableHashMap.entrySet()) {
            if (e.getValue().isActive()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void invalidateDrawable(@NonNull Drawable drawable) {
        super.invalidateDrawable(drawable);
        if (drawable instanceof QuickShareSelectorDrawable) {
            invalidate();
        }
    }

    public void onTouchMoveEvent (ChatMessageCell cell, float x, float y) {
        final QuickShareSelectorDrawable drawable = drawableHashMap.get(key(cell));
        if (drawable != null) {
            drawable.onTouchMoveEvent(x, y);
        }
    }

    public long getSelectedDialogId(ChatMessageCell cell) {
        final QuickShareSelectorDrawable drawable = drawableHashMap.get(key(cell));
        if (drawable != null) {
            return drawable.getSelectedDialogId();
        }

        return 0;
    }

    public QuickShareDestination getSelectedDestination(ChatMessageCell cell) {
        final QuickShareSelectorDrawable drawable = drawableHashMap.get(key(cell));
        if (drawable != null) {
            return drawable.getSelectedDestination();
        }
        return null;
    }

    public MessageObject getSelectedMessageObject(ChatMessageCell cell) {
        final QuickShareSelectorDrawable drawable = drawableHashMap.get(key(cell));
        if (drawable != null) {
            return drawable.messageObject;
        }

        return null;
    }

    public void close(ChatMessageCell cell, Bulletin bulletin) {
        final QuickShareSelectorDrawable drawable = drawableHashMap.get(key(cell));
        if (drawable != null) {
            drawable.close(bulletin);
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        for (Map.Entry<String, QuickShareSelectorDrawable> e : drawableHashMap.entrySet()) {
            e.getValue().destroy();
        }

        drawableHashMap.clear();
        drawablesForRemove.clear();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        for (Map.Entry<String, QuickShareSelectorDrawable> e : drawableHashMap.entrySet()) {
            e.getValue().setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        for (Map.Entry<String, QuickShareSelectorDrawable> e : drawableHashMap.entrySet()) {
            e.getValue().draw(canvas);
        }

        if (!drawablesForRemove.isEmpty()) {
            for (String key : drawablesForRemove) {
                final QuickShareSelectorDrawable d = drawableHashMap.remove(key);
                if (d != null) {
                    d.destroy();
                }
            }
            drawablesForRemove.clear();
        }
    }

    private final LinkedHashMap<Integer, ArrayList<Long>> dialogsByAccount = new LinkedHashMap<>();

    private void fetchDialogs() {
        dialogsByAccount.clear();

        final int selected = sourceAccount;
        final int[] activatedAccounts = UserConfig.getActivatedAccounts();
        // Preserve the existing current-account-first behavior, followed by
        // other signed-in accounts in stable account-index order.
        if (isActivated(selected, activatedAccounts)) {
            addDialogsForAccount(selected);
        }
        for (int account : activatedAccounts) {
            if (account != selected) {
                addDialogsForAccount(account);
            }
        }
    }

    private ArrayList<QuickShareDestination> buildAccountChoices() {
        ArrayList<QuickShareDestination> destinations = new ArrayList<>();
        for (Integer account : dialogsByAccount.keySet()) {
            long selfUserId = UserConfig.getInstance(account).getClientUserId();
            if (selfUserId != 0) {
                // Saved Messages is only a stable id carrier here.  The cell
                // renders the account identity, not a Saved Messages target.
                destinations.add(new QuickShareDestination(account, selfUserId, true));
            }
        }
        return destinations;
    }

    private ArrayList<QuickShareDestination> buildDestinationsForAccount(int account) {
        ArrayList<QuickShareDestination> destinations = new ArrayList<>();
        ArrayList<Long> accountDialogs = dialogsByAccount.get(account);
        if (accountDialogs != null) {
            for (Long dialogId : accountDialogs) {
                destinations.add(new QuickShareDestination(account, dialogId));
            }
        }
        return destinations;
    }

    private boolean isActivated(int account, int[] activatedAccounts) {
        for (int activatedAccount : activatedAccounts) {
            if (activatedAccount == account && UserConfig.getInstance(account).isClientActivated()) {
                return true;
            }
        }
        return false;
    }

    private void addDialogsForAccount(int account) {
        final UserConfig config = UserConfig.getInstance(account);
        if (!config.isClientActivated()) {
            return;
        }
        final long selfUserId = config.getClientUserId();
        if (selfUserId == 0) {
            return;
        }
        final ArrayList<Long> accountDialogs = new ArrayList<>();
        accountDialogs.add(selfUserId);

        if (config.suggestContacts) {
            final ArrayList<TLRPC.TL_topPeer> hints = MediaDataController.getInstance(account).hints;
            for (TLRPC.TL_topPeer peer : hints) {
                if (peer.peer.user_id == 0) {
                    continue;
                }
                TLRPC.User user = MessagesController.getInstance(account).getUser(peer.peer.user_id);
                if (user != null) {
                    accountDialogs.add((long) peer.peer.user_id);
                }
            }
        }

        ArrayList<Long> archivedDialogs = new ArrayList<>();
        ArrayList<TLRPC.Dialog> allDialogs = MessagesController.getInstance(account).getAllDialogs();
        for (int a = 0; a < allDialogs.size(); a++) {
            TLRPC.Dialog dialog = allDialogs.get(a);
            if (!(dialog instanceof TLRPC.TL_dialog) || dialog.id == selfUserId || DialogObject.isEncryptedDialog(dialog.id)) {
                continue;
            }
            if (DialogObject.isUserDialog(dialog.id)) {
                TLRPC.User user = MessagesController.getInstance(account).getUser(dialog.id);
                if (user != null && !UserObject.isBot(user) && !UserObject.isDeleted(user) && !UserObject.isService(user.id)) {
                    (dialog.folder_id == 1 ? archivedDialogs : accountDialogs).add(dialog.id);
                }
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialog.id);
                if (!(chat == null || chat.forum || ChatObject.isNotInChat(chat) || chat.gigagroup && !ChatObject.hasAdminRights(chat) || ChatObject.isChannel(chat) && !chat.creator && (chat.admin_rights == null || !chat.admin_rights.post_messages) && !chat.megagroup)) {
                    (dialog.folder_id == 1 ? archivedDialogs : accountDialogs).add(dialog.id);
                }
            }
        }
        accountDialogs.addAll(archivedDialogs);
        dialogsByAccount.put(account, removeDuplicates(accountDialogs));
    }

    private static String key (ChatMessageCell cell) {
        MessageObject object = cell.getMessageObject();
        if (object == null) {
            return null;
        }

        return object.getChatId() + "_" + object.getId();
    }

    private static ArrayList<Long> removeDuplicates(ArrayList<Long> list) {
        HashSet<Long> seen = new HashSet<>();
        ArrayList<Long> result = new ArrayList<>();
        for (Long item : list) {
            if (seen.add(item)) {
                result.add(item);
            }
        }
        return result;
    }
}
