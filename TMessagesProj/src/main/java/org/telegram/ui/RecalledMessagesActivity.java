package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.RecalledMessagesStore;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalRecyclerView;
import java.util.ArrayList;

public class RecalledMessagesActivity extends BaseFragment {
    private UniversalRecyclerView list;
    private ArrayList<TLRPC.Message> messages = new ArrayList<>();
    @Override public View createView(Context context) {
        actionBar.setTitle(LocaleController.getString(R.string.V4RecalledArchive));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        list = new UniversalRecyclerView(this, (items, adapter) -> {
            items.add(UItem.asShadow(LocaleController.getString(R.string.V4RecalledInfo)));
            if (!messages.isEmpty()) items.add(UItem.asButton(1, R.drawable.msg_delete, LocaleController.getString(R.string.DeleteAll)));
            for (TLRPC.Message message : messages) {
                String preview = message.message;
                if (preview == null || preview.isEmpty()) preview = new MessageObject(currentAccount, message, false, false).messageText.toString();
                UItem item = UItem.asButton(2, preview, LocaleController.formatDateChat(message.date));
                item.object = message;
                items.add(item);
            }
            if (messages.isEmpty()) items.add(UItem.asShadow(LocaleController.getString(R.string.NoMessages)));
        }, (item, view, position, x, y) -> {
            if (item.id == 1) showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(LocaleController.getString(R.string.V4RecalledArchive))
                    .setMessage(LocaleController.getString(R.string.V4RecalledClearConfirm))
                    .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                    .setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> Utilities.globalQueue.postRunnable(() -> {
                        RecalledMessagesStore.clear(currentAccount);
                        reload();
                    })).create());
            else if (item.id == 2) {
                TLRPC.Message message = (TLRPC.Message) item.object;
                MessageObject object = new MessageObject(currentAccount, message, false, false);
                showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(LocaleController.getString(R.string.V4RecalledArchive))
                        .setMessage(object.messageText).setPositiveButton(LocaleController.getString(R.string.OK), null).create());
            }
        }, null);
        frame.addView(list, LayoutHelper.createFrame(-1, -1));
        reload();
        return fragmentView = frame;
    }
    private void reload() {
        final long owner = getUserConfig().getClientUserId();
        Utilities.globalQueue.postRunnable(() -> {
            ArrayList<TLRPC.Message> loaded = RecalledMessagesStore.list(currentAccount, 0);
            AndroidUtilities.runOnUIThread(() -> {
                if (isFinished || owner != getUserConfig().getClientUserId()) return;
                messages = loaded;
                if (list != null) list.adapter.update(true);
            });
        });
    }
}
