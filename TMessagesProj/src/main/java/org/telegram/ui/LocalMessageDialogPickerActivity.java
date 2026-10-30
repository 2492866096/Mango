package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.LocalMessageInsightsRepository;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextDetailCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

/** Unlike the forwarding picker, this also includes inaccessible peers with cached history. */
public class LocalMessageDialogPickerActivity extends BaseFragment {
    public interface Callback { void onSelected(long dialogId, String title); }
    private final boolean usersOnly;
    private final Callback callback;
    private ArrayList<LocalMessageInsightsRepository.DialogEntry> entries = new ArrayList<>();
    private UniversalRecyclerView listView;
    private boolean loading = true, failed;

    public LocalMessageDialogPickerActivity(boolean usersOnly, Callback callback) {
        this.usersOnly = usersOnly;
        this.callback = callback;
    }

    @Override public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(getString(R.string.V4SelectLocalChat));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        listView = new UniversalRecyclerView(this, this::fillItems, (item, view, position, x, y) -> {
            if (item.object instanceof LocalMessageInsightsRepository.DialogEntry) {
                LocalMessageInsightsRepository.DialogEntry entry = (LocalMessageInsightsRepository.DialogEntry) item.object;
                callback.onSelected(entry.dialogId, entry.title);
                finishFragment();
            }
        }, null);
        frame.addView(listView, LayoutHelper.createFrame(-1, -1));
        LocalMessageInsightsRepository.dialogs(currentAccount, usersOnly, (result, error) -> {
            if (isFinished) return;
            loading = false;
            failed = error;
            entries = result;
            if (!usersOnly) entries.sort((a, b) -> Boolean.compare(b.unavailable, a.unavailable));
            listView.adapter.update(true);
        });
        actionBar.setAdaptiveBackground(listView);
        return fragmentView = frame;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asShadow(getString(usersOnly ? R.string.V4StatisticsLocalInfo : R.string.V4MemorialPickerInfo)));
        if (loading || failed || entries.isEmpty()) {
            items.add(UItem.asShadow(getString(loading ? R.string.Loading : failed ? R.string.V4LocalReadError : R.string.V4NoLocalMessages)));
        }
        for (LocalMessageInsightsRepository.DialogEntry entry : entries) {
            UItem item = TextDetailCell.Factory.of(1, entry.title,
                    (entry.unavailable ? getString(R.string.V4UnavailablePeer) + " · " : "") + entry.count + " · " + entry.dialogId);
            item.object = entry;
            items.add(item);
        }
    }
}
