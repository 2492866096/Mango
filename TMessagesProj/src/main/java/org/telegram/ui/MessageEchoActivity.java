package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageEchoManager;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalRecyclerView;

public class MessageEchoActivity extends BaseFragment {
    private UniversalRecyclerView list;
    @Override public View createView(Context context) {
        actionBar.setTitle(LocaleController.getString(R.string.V4MessageEcho));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        list = new UniversalRecyclerView(this, (items, adapter) -> {
            items.add(UItem.asButton(1, R.drawable.msg_add, LocaleController.getString(R.string.V4EchoAdd)));
            items.add(UItem.asShadow(LocaleController.getString(R.string.V4EchoInfo)));
            for (String target : MessageEchoManager.targets(currentAccount)) {
                try {
                    long did = Long.parseLong(target);
                    TLRPC.Chat chat = did < 0 ? getMessagesController().getChat(-did) : null;
                    TLRPC.User user = did > 0 ? getMessagesController().getUser(did) : null;
                    String title = chat != null ? chat.title : user != null ? UserObject.getUserName(user) : target;
                    UItem item = UItem.asSwitch(2, title).setChecked(true);
                    item.object = did;
                    items.add(item);
                } catch (NumberFormatException ignored) { }
            }
        }, (item, view, position, x, y) -> {
            if (item.id == 1) ExtensionAutomationTargetPicker.open(this, currentAccount, true, (did, title) -> {
                MessageEchoManager.setTarget(currentAccount, did, true);
                list.adapter.update(true);
            });
            else if (item.id == 2) {
                MessageEchoManager.setTarget(currentAccount, (Long) item.object, false);
                list.adapter.update(true);
            }
        }, null);
        frame.addView(list, LayoutHelper.createFrame(-1, -1));
        return fragmentView = frame;
    }
}
