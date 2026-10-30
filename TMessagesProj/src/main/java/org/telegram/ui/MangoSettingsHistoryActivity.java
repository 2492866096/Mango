package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MangoFeatureRegistry;
import org.telegram.messenger.MangoLegacySettings;
import org.telegram.messenger.MangoSettings;
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

public class MangoSettingsHistoryActivity extends BaseFragment {
    private UniversalRecyclerView listView;
    @Override public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(getString(R.string.MangoRecentSettings));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        FrameLayout content = new FrameLayout(context);
        content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        MangoLegacySettings.initialize(currentAccount);
        MangoLegacySettings.capture(currentAccount);
        listView = new UniversalRecyclerView(this, this::fill, this::click, null);
        content.addView(listView, LayoutHelper.createFrame(-1, -1));
        actionBar.setAdaptiveBackground(listView);
        return fragmentView = content;
    }
    private String value(Object value) {
        if (value == null || value == org.json.JSONObject.NULL || "".equals(value)) return getString(R.string.MangoValueDefault);
        if (value instanceof Boolean) return getString((Boolean) value ? R.string.MangoValueOn : R.string.MangoValueOff);
        String text = String.valueOf(value);
        return text.length() > 100 ? text.substring(0, 100) + "…" : text;
    }
    private void fill(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asShadow(getString(R.string.MangoRecentSettingsInfo)));
        ArrayList<MangoSettings.Change> history = MangoSettings.getHistory(currentAccount);
        if (history.isEmpty()) items.add(UItem.asShadow(getString(R.string.MangoHistoryEmpty)));
        int id = 1;
        for (MangoSettings.Change change : history) {
            UItem row = UItem.asSettingsCell(id++, MangoFeatureRegistry.label(change.key), value(change.before) + " → " + value(change.after));
            row.object = change;
            items.add(row);
            items.add(UItem.asShadow(LocaleController.formatDateTime(change.timestamp / 1000, true)));
        }
    }
    private void click(UItem item, View view, int position, float x, float y) {
        if (!(item.object instanceof MangoSettings.Change)) return;
        MangoSettings.Change change = (MangoSettings.Change) item.object;
        showDialog(new AlertDialog.Builder(getParentActivity(), resourceProvider)
                .setTitle(getString(R.string.MangoRestoreChange))
                .setMessage(LocaleController.formatString(R.string.MangoRestoreChangeInfo,
                        MangoFeatureRegistry.label(change.key), value(change.before), value(change.after)))
                .setNegativeButton(getString(R.string.Cancel), null)
                .setPositiveButton(getString(R.string.Restore), (dialog, which) -> {
                    MangoSettings.restore(currentAccount, change);
                    listView.adapter.update(false);
                }).create());
    }
}
