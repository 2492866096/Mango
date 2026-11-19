package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.RemoteScamKeywords;
import org.telegram.messenger.ScamProtectionManager;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class ScamProtectionActivity extends BaseFragment {
    private UniversalRecyclerView listView;
    private final Runnable keywordListener = () -> {
        if (listView != null && !isFinished) listView.adapter.update(true);
    };

    @Override public void onResume() {
        super.onResume();
        ScamProtectionManager.addListener(keywordListener);
        ScamProtectionManager.refreshIfNeeded(currentAccount);
        keywordListener.run();
    }

    @Override public void onPause() {
        ScamProtectionManager.removeListener(keywordListener);
        super.onPause();
    }

    @Override public void onFragmentDestroy() {
        ScamProtectionManager.removeListener(keywordListener);
        super.onFragmentDestroy();
    }

    @Override public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(getString(R.string.V4ScamProtectionTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        frame.addView(listView, LayoutHelper.createFrame(-1, -1));
        actionBar.setAdaptiveBackground(listView);
        return fragmentView = frame;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asSwitch(1, getString(R.string.V4ScamProtectionEnabled)).setChecked(ScamProtectionManager.isEnabled(currentAccount)));
        items.add(UItem.asShadow(getString(R.string.V4ScamProtectionInfo)));
        int remoteCount = ScamProtectionManager.getRemoteKeywordCount();
        items.add(UItem.asSettingsCell(5, getString(R.string.V4ScamRemoteKeywords), Integer.toString(remoteCount)).setEnabled(false));
        items.add(UItem.asButton(4, getString(ScamProtectionManager.isRefreshing() ? R.string.V4ScamKeywordsUpdating : R.string.V4ScamKeywordsUpdateNow))
                .setEnabled(!ScamProtectionManager.isRefreshing()));
        long updated = ScamProtectionManager.getLastUpdated();
        String status = updated > 0
                ? LocaleController.formatString(R.string.V4ScamKeywordsLastUpdated, LocaleController.formatDateTime(updated / 1000L, true))
                : getString(R.string.V4ScamKeywordsBundledFallback);
        if (ScamProtectionManager.hasRefreshFailed()) status = getString(R.string.V4ScamKeywordsUpdateFailed) + "\n" + status;
        items.add(UItem.asShadow(status + "\n\n" + getString(R.string.V4ScamKeywordsAutoUpdateInfo) + "\n\n"
                + LocaleController.formatString(R.string.V4ScamKeywordsSource, RemoteScamKeywords.SOURCE_URL)));
        items.add(UItem.asSettingsCell(2, getString(R.string.V4ScamKeywords), Integer.toString(ScamProtectionManager.getCustomKeywords(currentAccount).size())));
        items.add(UItem.asButton(3, getString(R.string.V4RestoreScamKeywords)));
        ArrayList<String> keywords = ScamProtectionManager.getKeywords(currentAccount);
        String preview = TextUtils.join("、", keywords.subList(0, Math.min(30, keywords.size())));
        if (keywords.size() > 30) preview += "\n" + LocaleController.formatString(R.string.V4ScamKeywordsPreviewMore, keywords.size());
        items.add(UItem.asShadow(LocaleController.formatString(R.string.V4ScamKeywordsPreview, preview)));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 1) {
            ScamProtectionManager.setEnabled(currentAccount, !ScamProtectionManager.isEnabled(currentAccount));
            listView.adapter.update(true);
        } else if (item.id == 2) {
            EditTextBoldCursor input = new EditTextBoldCursor(getParentActivity());
            input.setTextSize(16);
            input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            input.setMaxLines(10);
            input.setMinLines(5);
            input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(16200)});
            input.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(12));
            input.setText(TextUtils.join("\n", ScamProtectionManager.getCustomKeywords(currentAccount)));
            showDialog(new AlertDialog.Builder(getParentActivity(), resourceProvider)
                    .setTitle(getString(R.string.V4ScamKeywords))
                    .setMessage(getString(R.string.V4ScamKeywordsInfo))
                    .setView(input)
                    .setPositiveButton(getString(R.string.Save), (dialog, which) -> {
                        ScamProtectionManager.setKeywords(currentAccount, input.getText().toString());
                        listView.adapter.update(true);
                    }).setNegativeButton(getString(R.string.Cancel), null).create());
        } else if (item.id == 3) {
            showDialog(new AlertDialog.Builder(getParentActivity(), resourceProvider)
                    .setTitle(getString(R.string.V4RestoreScamKeywords))
                    .setMessage(getString(R.string.V4RestoreScamKeywordsConfirm))
                    .setPositiveButton(getString(R.string.OK), (dialog, which) -> {
                        ScamProtectionManager.setKeywords(currentAccount, "");
                        listView.adapter.update(true);
                    }).setNegativeButton(getString(R.string.Cancel), null).create());
        } else if (item.id == 4) {
            ScamProtectionManager.refresh(true);
        }
    }
}
