package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageMemorialStore;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextDetailCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

/** Deliberately no chat input, forwarding, editing, or Telegram API calls. */
public class MessageMemorialViewerActivity extends BaseFragment {
    private final MessageMemorialStore.Memorial memorial;
    private final ArrayList<MessageMemorialStore.Entry> entries = new ArrayList<>();
    private UniversalRecyclerView listView;
    private boolean loading, failed;
    private int offset;

    public MessageMemorialViewerActivity(MessageMemorialStore.Memorial memorial) { this.memorial = memorial; }

    @Override public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(memorial.title);
        actionBar.setSubtitle(getString(R.string.V4MemorialReadOnly));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        frame.addView(listView, LayoutHelper.createFrame(-1, -1));
        actionBar.setAdaptiveBackground(listView);
        loadPage(0);
        return fragmentView = frame;
    }

    private void loadPage(int requestedOffset) {
        if (loading) return;
        loading = true; failed = false;
        listView.adapter.update(true);
        MessageMemorialStore.read(currentAccount, memorial.id, requestedOffset, 100, (result, error) -> {
            if (isFinished) return;
            loading = false; failed = error;
            if (!error) { offset = requestedOffset; entries.clear(); entries.addAll(result); }
            listView.adapter.update(true);
            if (!error) listView.scrollToPosition(0);
        });
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asShadow(getString(R.string.V4MemorialReadOnlyInfo)));
        items.add(UItem.asShadow((offset + (entries.isEmpty() ? 0 : 1)) + "–" + (offset + entries.size()) + " / " + memorial.count));
        if (loading || failed) items.add(UItem.asShadow(getString(loading ? R.string.Loading : R.string.V4LocalReadError)));
        if (failed) items.add(UItem.asButton(4, getString(R.string.V4StatisticsRefresh)));
        if (offset > 0) items.add(UItem.asButton(2, getString(R.string.V4MemorialPrevious)));
        for (MessageMemorialStore.Entry entry : entries) {
            String preview = entry.text;
            if (preview.length() > 600) preview = preview.substring(0, 600) + "…";
            if (preview.isEmpty()) preview = getString(R.string.V4MemorialMediaMessage) + " " + entry.mediaType;
            String detail = entry.sender + " · " + LocaleController.formatDateChat(entry.date) + " · #" + entry.messageId;
            if (!entry.mediaFile.isEmpty()) detail += " · " + getString(R.string.V4MemorialAttachmentSaved);
            UItem item = TextDetailCell.Factory.of(1, preview, detail);
            item.object = entry;
            items.add(item);
        }
        if (offset + entries.size() < memorial.count && !entries.isEmpty()) items.add(UItem.asButton(3, getString(R.string.V4MemorialNext)));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (loading) return;
        if (item.id == 2) loadPage(Math.max(0, offset - 100));
        else if (item.id == 3) loadPage(offset + entries.size());
        else if (item.id == 4) loadPage(offset);
        else if (item.object instanceof MessageMemorialStore.Entry) {
            MessageMemorialStore.Entry entry = (MessageMemorialStore.Entry) item.object;
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), resourceProvider)
                    .setTitle(entry.sender).setMessage(entry.text + (!entry.mediaName.isEmpty() ? "\n\n" + entry.mediaName : ""))
                    .setPositiveButton(getString(R.string.Close), null);
            if (!entry.mediaFile.isEmpty()) {
                builder.setNeutralButton(getString(R.string.V4MemorialOpenAttachment), (dialog, which) ->
                        MessageMemorialStore.mediaForViewing(currentAccount, memorial.id, entry, (file, error) -> {
                            if (isFinished || getParentActivity() == null) return;
                            try {
                                if (error || !AndroidUtilities.openForView(file, entry.mediaName, entry.mime, getParentActivity(), resourceProvider, true))
                                    Toast.makeText(getParentActivity(), getString(R.string.V4LocalReadError), Toast.LENGTH_SHORT).show();
                            } catch (Exception e) { Toast.makeText(getParentActivity(), getString(R.string.V4LocalReadError), Toast.LENGTH_SHORT).show(); }
                        }));
            }
            showDialog(builder.create());
        }
    }
}
