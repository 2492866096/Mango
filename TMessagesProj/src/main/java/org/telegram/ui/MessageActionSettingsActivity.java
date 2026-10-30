package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.MessageActionSettings;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class MessageActionSettingsActivity extends BaseFragment {
    private static final int ITEM_DOUBLE_TAP = 1;
    private static final int ITEM_RESET = 2;
    private static final int ITEM_ACTION = 10;
    private UniversalRecyclerView listView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(getString(R.string.MessageActionSettings));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });
        FrameLayout content = new FrameLayout(context);
        content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        listView.setSections();
        content.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        actionBar.setAdaptiveBackground(listView);
        return fragmentView = content;
    }

    public static String doubleTapLabel(int account) {
        switch (MessageActionSettings.getDoubleTapAction(account)) {
            case MessageActionSettings.DOUBLE_TAP_REPLY: return getString(R.string.Reply);
            case MessageActionSettings.DOUBLE_TAP_NONE: return getString(R.string.MessageDoubleTapNone);
            default: return getString(R.string.MessageDoubleTapReaction);
        }
    }

    private String actionLabel(int action) {
        switch (action) {
            case MessageActionSettings.ACTION_COPY: return getString(R.string.Copy);
            case MessageActionSettings.ACTION_FORWARD: return getString(R.string.Forward);
            case MessageActionSettings.ACTION_TODO: return getString(R.string.MessageTagTodo);
            default: return getString(R.string.MessageTagFavorite);
        }
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asSettingsCell(ITEM_DOUBLE_TAP, getString(R.string.MessageDoubleTapAction), doubleTapLabel(currentAccount)));
        items.add(UItem.asShadow(getString(R.string.MessageDoubleTapInfo)));
        items.add(UItem.asHeader(getString(R.string.MessageMenuOrder)));
        int[] order = MessageActionSettings.getMenuOrder(currentAccount);
        for (int i = 0; i < order.length; i++) {
            UItem item = UItem.asSettingsCell(ITEM_ACTION, (i + 1) + ". " + actionLabel(order[i]), getString(R.string.MessageMenuMove));
            item.intValue = i;
            items.add(item);
        }
        items.add(UItem.asButton(ITEM_RESET, getString(R.string.MessageMenuReset)));
        items.add(UItem.asShadow(getString(R.string.MessageMenuOrderInfo)));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ITEM_DOUBLE_TAP) {
            showDialog(new AlertDialog.Builder(getParentActivity(), resourceProvider)
                    .setTitle(getString(R.string.MessageDoubleTapAction))
                    .setItems(new CharSequence[]{getString(R.string.MessageDoubleTapReaction), getString(R.string.Reply), getString(R.string.MessageDoubleTapNone)}, (dialog, which) -> {
                        MessageActionSettings.setDoubleTapAction(currentAccount, which);
                        listView.adapter.update(true);
                    }).create());
        } else if (item.id == ITEM_RESET) {
            MessageActionSettings.setMenuOrder(currentAccount, new int[]{0, 1, 2, 3});
            listView.adapter.update(true);
        } else if (item.id == ITEM_ACTION) {
            int from = item.intValue;
            CharSequence[] destinations = new CharSequence[]{"1", "2", "3", "4"};
            showDialog(new AlertDialog.Builder(getParentActivity(), resourceProvider)
                    .setTitle(getString(R.string.MessageMenuMove))
                    .setItems(destinations, (dialog, which) -> {
                        int[] order = MessageActionSettings.getMenuOrder(currentAccount);
                        int action = order[from];
                        if (from < which) System.arraycopy(order, from + 1, order, from, which - from);
                        else if (from > which) System.arraycopy(order, which, order, which + 1, from - which);
                        order[which] = action;
                        MessageActionSettings.setMenuOrder(currentAccount, order);
                        listView.adapter.update(true);
                    }).create());
        }
    }
}
