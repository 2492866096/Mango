package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.widget.LinearLayout;

import java.util.ArrayList;

import org.telegram.messenger.MessageTagsController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.LayoutHelper;

/** Shared tag picker for the message menu and the management center. */
public final class MessageTagEditor {
    private MessageTagEditor() {}

    public static void show(BaseFragment fragment, MessageTagsController.Entry entry, Runnable onSaved) {
        if (fragment.getParentActivity() == null || entry == null) return;
        ArrayList<MessageTagsController.Entry> entries = new ArrayList<>();
        entries.add(entry);
        showMany(fragment, entries, onSaved);
    }

    public static void showMany(BaseFragment fragment, ArrayList<MessageTagsController.Entry> entries, Runnable onSaved) {
        if (fragment.getParentActivity() == null || entries == null || entries.isEmpty()) return;
        int account = fragment.getCurrentAccount();
        int common = 15;
        for (MessageTagsController.Entry entry : entries) common &= MessageTagsController.getTags(account, entry);
        int[] selected = {common};
        int[] touched = {0};
        LinearLayout content = new LinearLayout(fragment.getParentActivity());
        content.setOrientation(LinearLayout.VERTICAL);
        for (int tag : MessageTagsController.TAGS) {
            CheckBoxCell cell = new CheckBoxCell(fragment.getParentActivity(), 1, fragment.getResourceProvider());
            cell.setText(getString(MessageTagsController.tagName(tag)), "", (selected[0] & tag) != 0, false);
            cell.setOnClickListener(v -> {
                selected[0] ^= tag;
                touched[0] |= tag;
                cell.setChecked((selected[0] & tag) != 0, true);
            });
            content.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
        }
        fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider())
                .setTitle(getString(R.string.MessageTags))
                .setMessage(entries.size() > 1 ? getString(R.string.MessageTagsBatchInfo) : null)
                .setView(content)
                .setPositiveButton(getString(R.string.Save), (dialog, which) -> {
                    for (MessageTagsController.Entry entry : entries) {
                        int tags = MessageTagsController.getTags(account, entry);
                        MessageTagsController.setTags(account, entry, (tags & ~touched[0]) | (selected[0] & touched[0]));
                    }
                    if (onSaved != null) onSaved.run();
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .setNeutralButton(getString(R.string.MessageTagsClear), (dialog, which) -> {
                    for (MessageTagsController.Entry entry : entries) MessageTagsController.setTags(account, entry, 0);
                    if (onSaved != null) onSaved.run();
                }).create());
    }
}
