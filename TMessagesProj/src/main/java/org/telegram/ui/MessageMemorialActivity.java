package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

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

public class MessageMemorialActivity extends BaseFragment {
    private UniversalRecyclerView listView;
    private ArrayList<MessageMemorialStore.Memorial> entries = new ArrayList<>();
    private boolean loading = true, failed, saving;

    @Override public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(getString(R.string.V4MessageMemorialTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, this::onLongClick);
        frame.addView(listView, LayoutHelper.createFrame(-1, -1));
        actionBar.setAdaptiveBackground(listView);
        return fragmentView = frame;
    }

    @Override public void onResume() { super.onResume(); reload(); }

    private void reload() {
        if (listView == null) return;
        MessageMemorialStore.list(currentAccount, (result, error) -> {
            if (isFinished) return;
            loading = false; failed = error; entries = result; listView.adapter.update(true);
        });
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asButton(1, getString(saving ? R.string.V4MemorialSaving : R.string.V4MemorialCreate)).accent());
        items.add(UItem.asShadow(getString(R.string.V4MemorialInfo)));
        if (loading || failed || entries.isEmpty()) items.add(UItem.asShadow(getString(loading ? R.string.Loading : failed ? R.string.V4LocalReadError : R.string.V4MemorialEmpty)));
        for (MessageMemorialStore.Memorial entry : entries) {
            UItem item = TextDetailCell.Factory.of(2, entry.title, LocaleController.formatString(R.string.V4MemorialSummary, entry.count, entry.attachments)
                    + " · " + LocaleController.formatDateChat(entry.created));
            item.object = entry;
            items.add(item);
        }
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 1 && !saving) {
            LocalMessageDialogPickerActivity picker = new LocalMessageDialogPickerActivity(false, (id, title) ->
                    confirmCapture(this, id, title, () -> { saving = true; listView.adapter.update(true); }, () -> {
                        saving = false; reload();
                    }));
            picker.setCurrentAccount(currentAccount);
            presentFragment(picker);
        } else if (item.id == 2 && item.object instanceof MessageMemorialStore.Memorial) {
            MessageMemorialViewerActivity viewer = new MessageMemorialViewerActivity((MessageMemorialStore.Memorial) item.object);
            viewer.setCurrentAccount(currentAccount);
            presentFragment(viewer);
        }
    }

    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (!(item.object instanceof MessageMemorialStore.Memorial)) return false;
        MessageMemorialStore.Memorial entry = (MessageMemorialStore.Memorial) item.object;
        showDialog(new AlertDialog.Builder(getParentActivity(), resourceProvider).setTitle(getString(R.string.V4MemorialDelete))
                .setMessage(getString(R.string.V4MemorialDeleteInfo)).setPositiveButton(getString(R.string.Delete), (dialog, which) ->
                        MessageMemorialStore.delete(currentAccount, entry.id, (deleted, error) -> {
                            if (isFinished) return;
                            if (error) Toast.makeText(getParentActivity(), getString(R.string.V4LocalReadError), Toast.LENGTH_SHORT).show();
                            reload();
                        })).setNegativeButton(getString(R.string.Cancel), null).create());
        return true;
    }

    public static void capture(BaseFragment parent, long id, String title) {
        confirmCapture(parent, id, title, null, null);
    }

    public static void confirmCapture(BaseFragment parent, long id, String title, Runnable started, Runnable finished) {
        if (parent.getParentActivity() == null) return;
        parent.showDialog(new AlertDialog.Builder(parent.getParentActivity(), parent.getResourceProvider())
                .setTitle(getString(R.string.V4MemorialCreate)).setMessage(title + "\n\n" + getString(R.string.V4MemorialCaptureInfo))
                .setPositiveButton(getString(R.string.Save), (dialog, which) -> {
                    if (started != null) started.run();
                    Toast.makeText(parent.getParentActivity(), getString(R.string.V4MemorialSaving), Toast.LENGTH_SHORT).show();
                    MessageMemorialStore.capture(parent.getCurrentAccount(), id, title, (entry, error) -> {
                        if (parent.getParentActivity() != null) Toast.makeText(parent.getParentActivity(), getString(error ? R.string.V4MemorialCaptureFailed : R.string.V4MemorialSaved), Toast.LENGTH_LONG).show();
                        if (finished != null) finished.run();
                    });
                }).setNegativeButton(getString(R.string.Cancel), null).create());
    }
}
