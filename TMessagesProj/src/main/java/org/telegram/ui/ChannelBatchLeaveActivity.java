package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BatchLeaveQueue;
import org.telegram.messenger.ChannelBatchLeaveController;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

public class ChannelBatchLeaveActivity extends BaseFragment {
    private static final int TYPE = 1, SELECT_ALL = 2, LEAVE = 3, RETRY = 4, REFRESH = 5, STOP = 6, CHAT = 7;
    private final HashSet<Long> selected = new HashSet<>();
    private UniversalRecyclerView listView;
    private ChannelBatchLeaveController controller;
    private String query = "";
    private int filter; // 0 channels, 1 groups, 2 both
    private final Runnable changed = () -> {
        if (controller != null) selected.removeIf(id -> !ChannelBatchLeaveController.canLeave(controller.getChat(id)));
        if (listView != null) listView.adapter.update(false);
    };

    @Override
    public boolean onFragmentCreate() {
        if (!super.onFragmentCreate()) return false;
        controller = ChannelBatchLeaveController.getInstance(currentAccount);
        controller.addListener(changed);
        if (!controller.queue().isRunning()) controller.refresh();
        return true;
    }
    @Override
    public void onFragmentDestroy() {
        if (controller != null) controller.removeListener(changed);
        super.onFragmentDestroy();
    }
    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(getString(R.string.ChannelBatchLeaveTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        actionBar.createMenu().addItem(0, R.drawable.outline_header_search).setIsSearchField(true)
                .setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                    @Override public void onSearchCollapse() { query = ""; changed.run(); }
                    @Override public void onTextChanged(EditText editText) {
                        query = editText.getText().toString().trim().toLowerCase(Locale.ROOT);
                        changed.run();
                    }
                }).setSearchFieldHint(getString(R.string.Search));
        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        listView.setPadding(0, 0, 0, AndroidUtilities.navigationBarHeight);
        listView.setClipToPadding(false);
        frame.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        actionBar.setAdaptiveBackground(listView);
        return fragmentView = frame;
    }
    private boolean matches(TLRPC.Chat chat) {
        boolean channel = ChatObject.isChannelAndNotMegaGroup(chat);
        if (filter == 0 && !channel || filter == 1 && channel) return false;
        return query.isEmpty() || String.valueOf(chat.title).toLowerCase(Locale.ROOT).contains(query)
                || chat.username != null && chat.username.toLowerCase(Locale.ROOT).contains(query);
    }
    private ArrayList<TLRPC.Chat> visibleChats() {
        ArrayList<TLRPC.Chat> result = new ArrayList<>();
        for (TLRPC.Chat chat : controller.getChats()) if (matches(chat)) result.add(chat);
        return result;
    }
    private ArrayList<Long> visibleSelectable() {
        ArrayList<Long> result = new ArrayList<>();
        for (TLRPC.Chat chat : visibleChats()) if (ChannelBatchLeaveController.canLeave(chat)) result.add(chat.id);
        return result;
    }
    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (controller == null) return;
        boolean busy = controller.isLoading() || controller.queue().isRunning();
        items.add(UItem.asShadow(getString(R.string.ChannelBatchLeaveInfo)));
        items.add(UItem.asSettingsCell(TYPE, getString(R.string.ChannelBatchLeaveScope),
                getString(filter == 0 ? R.string.ChannelBatchLeaveChannels : filter == 1 ? R.string.ChannelBatchLeaveGroups : R.string.ChannelBatchLeaveAll)));
        items.add(UItem.asButton(REFRESH, getString(controller.isLoading() ? R.string.Loading : R.string.ChannelBatchLeaveRefresh)).setEnabled(!busy));
        if (controller.getLoadError() != null) {
            items.add(UItem.asShadow(LocaleController.formatString(R.string.ChannelBatchLeaveLoadFailed, controller.getLoadError())));
        }
        ArrayList<Long> selectable = visibleSelectable();
        boolean all = !selectable.isEmpty() && selected.containsAll(selectable);
        items.add(UItem.asCheck(SELECT_ALL, getString(all ? R.string.ChannelBatchLeaveDeselectVisible : R.string.ChannelBatchLeaveSelectVisible))
                .setChecked(all).setEnabled(!busy && !selectable.isEmpty()));
        items.add(UItem.asButton(LEAVE, LocaleController.formatString(R.string.ChannelBatchLeaveSelected, selected.size()))
                .setEnabled(!busy && !selected.isEmpty()).red());
        if (controller.queue().isRunning()) {
            items.add(UItem.asButton(STOP, getString(R.string.ChannelBatchLeaveStop)).red());
            items.add(UItem.asShadow(getString(R.string.ChannelBatchLeaveRunningInfo)));
        }
        int left = 0, failed = 0, cancelled = 0;
        for (BatchLeaveQueue.Result result : controller.queue().results()) {
            if (result.state == BatchLeaveQueue.State.LEFT) left++;
            else if (result.state == BatchLeaveQueue.State.FAILED) failed++;
            else if (result.state == BatchLeaveQueue.State.CANCELLED) cancelled++;
        }
        if (!controller.queue().results().isEmpty()) {
            items.add(UItem.asShadow(LocaleController.formatString(R.string.ChannelBatchLeaveSummary, left, failed, cancelled)));
        }
        if (failed > 0) items.add(UItem.asButton(RETRY, LocaleController.formatString(R.string.ChannelBatchLeaveRetry, failed)).setEnabled(!busy));
        ArrayList<TLRPC.Chat> visible = visibleChats();
        if (visible.isEmpty()) items.add(UItem.asShadow(getString(controller.isLoading() ? R.string.Loading : R.string.ChannelBatchLeaveEmpty)));
        for (TLRPC.Chat chat : visible) {
            BatchLeaveQueue.Result result = controller.queue().result(chat.id);
            String status;
            if (result != null) {
                switch (result.state) {
                    case LEFT: status = getString(R.string.ChannelBatchLeaveDone); break;
                    case FAILED: status = LocaleController.formatString(R.string.ChannelBatchLeaveFailed, result.error); break;
                    case RUNNING: status = getString(R.string.ChannelBatchLeaveWorking); break;
                    case CANCELLED: status = getString(R.string.ChannelBatchLeaveCancelled); break;
                    default: status = getString(R.string.ChannelBatchLeaveQueued);
                }
            } else {
                status = chat.creator ? getString(R.string.ChannelBatchLeaveOwner) : chat.username != null ? "@" + chat.username
                        : getString(ChatObject.isChannelAndNotMegaGroup(chat) ? R.string.ChannelBatchLeaveChannels : R.string.ChannelBatchLeaveGroups);
            }
            UItem item = UItem.asButtonCheck(CHAT, chat.title, status).setChecked(selected.contains(chat.id))
                    .setEnabled(!busy && ChannelBatchLeaveController.canLeave(chat));
            item.object = chat.id;
            items.add(item);
        }
    }
    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == TYPE) {
            showDialog(new AlertDialog.Builder(getParentActivity(), resourceProvider)
                    .setTitle(getString(R.string.ChannelBatchLeaveScope))
                    .setItems(new CharSequence[]{getString(R.string.ChannelBatchLeaveChannels), getString(R.string.ChannelBatchLeaveGroups), getString(R.string.ChannelBatchLeaveAll)},
                            (dialog, which) -> { filter = which; changed.run(); }).create());
        } else if (item.id == STOP) {
            controller.queue().stop();
        } else if (!controller.isLoading() && !controller.queue().isRunning()) {
            if (item.id == REFRESH) controller.refresh();
            else if (item.id == CHAT && item.object instanceof Long) {
                long id = (Long) item.object;
                if (!ChannelBatchLeaveController.canLeave(controller.getChat(id))) return;
                if (!selected.remove(id)) selected.add(id);
                changed.run();
            } else if (item.id == SELECT_ALL) {
                ArrayList<Long> ids = visibleSelectable();
                if (selected.containsAll(ids)) selected.removeAll(ids); else selected.addAll(ids);
                changed.run();
            } else if (item.id == LEAVE) confirm(false);
            else if (item.id == RETRY) confirm(true);
        }
    }
    private void confirm(boolean retry) {
        ArrayList<Long> ids = new ArrayList<>();
        if (retry) {
            for (BatchLeaveQueue.Result result : controller.queue().results()) if (result.state == BatchLeaveQueue.State.FAILED) ids.add(result.id);
        } else ids.addAll(selected);
        if (ids.isEmpty()) return;
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < Math.min(ids.size(), 20); i++) {
            TLRPC.Chat chat = controller.getChat(ids.get(i));
            names.append('\n').append(chat == null ? ids.get(i) : chat.title);
        }
        if (ids.size() > 20) names.append('\n').append(LocaleController.formatString(R.string.ChannelBatchLeaveMore, ids.size() - 20));
        showDialog(new AlertDialog.Builder(getParentActivity(), resourceProvider)
                .setTitle(getString(R.string.ChannelBatchLeaveTitle))
                .setMessage(LocaleController.formatString(R.string.ChannelBatchLeaveConfirm, ids.size()) + names)
                .setNegativeButton(getString(R.string.Cancel), null)
                .setPositiveButton(getString(R.string.ChannelBatchLeaveConfirmButton), (dialog, which) -> {
                    if (retry) controller.queue().retryFailures(); else controller.start(ids);
                    selected.clear();
                    changed.run();
                }).create());
    }
}
