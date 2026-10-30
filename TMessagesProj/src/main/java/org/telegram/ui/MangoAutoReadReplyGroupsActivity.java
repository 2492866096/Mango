package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.MangoAutoReadManager;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.Set;

/** Only explicitly selected groups take part in read-and-random-reply automation. */
public class MangoAutoReadReplyGroupsActivity extends BaseFragment {
    private UniversalRecyclerView list;
    private long ownerId;
    private boolean destroyed;

    @Override
    public boolean onFragmentCreate() {
        ownerId = getUserConfig().getClientUserId();
        return super.onFragmentCreate();
    }

    private boolean hasCurrentOwner() {
        UserConfig config = getUserConfig();
        return !destroyed && config.isClientActivated() && ownerId != 0 && config.getClientUserId() == ownerId;
    }

    @Override
    public View createView(Context context) {
        actionBar.setTitle(getString(R.string.MangoAutoReadReplyGroups));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        list = new UniversalRecyclerView(this, (items, adapter) -> {
            if (!hasCurrentOwner()) return;
            items.add(UItem.asButton(1, R.drawable.msg_add, getString(R.string.MangoAutoReadReplyAddGroup)));
            items.add(UItem.asShadow(getString(R.string.MangoAutoReadReplyGroupsInfo)));
            Set<Long> groups = MangoAutoReadManager.replyGroups(currentAccount);
            if (groups.isEmpty()) items.add(UItem.asShadow(getString(R.string.MangoAutoReadReplyNoGroups)));
            int id = 2;
            for (long did : groups) {
                TLRPC.Chat chat = getMessagesController().getChat(-did);
                String title = chat != null && chat.title != null ? chat.title : Long.toString(did);
                UItem item = UItem.asSwitch(id++, title).setChecked(true);
                item.object = did;
                items.add(item);
            }
        }, (item, view, position, x, y) -> {
            if (!hasCurrentOwner()) return;
            if (item.id == 1) {
                ExtensionAutomationTargetPicker.openGroups(this, currentAccount, (did, title) -> {
                    if (!hasCurrentOwner()) return;
                    MangoAutoReadManager.setReplyGroup(currentAccount, did, true);
                    if (list != null) list.adapter.update(true);
                });
            } else if (item.object instanceof Long) {
                MangoAutoReadManager.setReplyGroup(currentAccount, (Long) item.object, false);
                list.adapter.update(true);
            }
        }, null);
        frame.addView(list, LayoutHelper.createFrame(-1, -1));
        actionBar.setAdaptiveBackground(list);
        return fragmentView = frame;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!hasCurrentOwner()) finishFragment();
        else if (list != null) list.adapter.update(false);
    }

    @Override
    public void onFragmentDestroy() {
        destroyed = true;
        list = null;
        super.onFragmentDestroy();
    }
}
