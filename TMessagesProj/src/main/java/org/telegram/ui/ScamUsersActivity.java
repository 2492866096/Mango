package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;
import android.content.Context;
import android.text.InputType;
import android.text.InputFilter;
import android.view.View;
import android.widget.FrameLayout;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.*;
import java.util.ArrayList;

public class ScamUsersActivity extends BaseFragment {
    private UniversalRecyclerView list;
    private final Runnable listener = () -> { if (list != null && !isFinished) list.adapter.update(true); };
    @Override public View createView(Context context) {
        actionBar.setTitle(getString(R.string.MangoScamUsers));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        list = new UniversalRecyclerView(this, this::fill, this::click, null);
        frame.addView(list, LayoutHelper.createFrame(-1, -1));
        actionBar.setAdaptiveBackground(list);
        return fragmentView = frame;
    }
    @Override public void onResume() {
        super.onResume(); ScamUsersManager.addListener(listener); ScamUsersManager.refresh(currentAccount, false); listener.run();
    }
    @Override public void onPause() { ScamUsersManager.removeListener(listener); super.onPause(); }
    @Override public void onFragmentDestroy() { ScamUsersManager.removeListener(listener); super.onFragmentDestroy(); }
    private void fill(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asSwitch(1, getString(R.string.MangoScamUsersEnabled)).setChecked(ScamUsersManager.enabled(currentAccount)));
        items.add(UItem.asShadow(getString(R.string.MangoScamUsersInfo)));
        items.add(UItem.asButton(2, getString(R.string.MangoScamUsersSource)));
        String source = ScamUsersManager.source(currentAccount);
        items.add(UItem.asShadow(source.isEmpty() ? getString(R.string.MangoScamUsersUnconfigured) : source));
        items.add(UItem.asButton(5, getString(R.string.MangoScamUsersRestoreSource))
                .setEnabled(!ScamUsersManager.DEFAULT_SOURCE.equals(source)));
        items.add(UItem.asSettingsCell(4, getString(R.string.MangoScamUsersCount), Integer.toString(ScamUsersManager.count(currentAccount))).setEnabled(false));
        items.add(UItem.asButton(3, getString(ScamUsersManager.busy(currentAccount) ? R.string.V4ScamKeywordsUpdating : R.string.V4ScamKeywordsUpdateNow))
                .setEnabled(!source.isEmpty() && !ScamUsersManager.busy(currentAccount)));
        long updated = ScamUsersManager.updated(currentAccount);
        String status = updated == 0 ? getString(R.string.MangoScamUsersNoCache)
                : LocaleController.formatString(R.string.V4ScamKeywordsLastUpdated, LocaleController.formatDateTime(updated / 1000, true));
        if (ScamUsersManager.failed(currentAccount)) status = getString(R.string.V4ScamKeywordsUpdateFailed) + "\n" + status;
        items.add(UItem.asShadow(status));
    }
    private void click(UItem item, View view, int position, float x, float y) {
        if (item.id == 1) {
            ScamUsersManager.setEnabled(currentAccount, !ScamUsersManager.enabled(currentAccount));
        } else if (item.id == 3) ScamUsersManager.refresh(currentAccount, true);
        else if (item.id == 5) ScamUsersManager.setSource(currentAccount, ScamUsersManager.DEFAULT_SOURCE);
        else if (item.id == 2) {
            EditTextBoldCursor input = new EditTextBoldCursor(getParentActivity());
            input.setTextSize(16); input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
            input.setSingleLine(true); input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2048)});
            input.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(12));
            input.setText(ScamUsersManager.source(currentAccount));
            AlertDialog dialog = new AlertDialog.Builder(getParentActivity(), resourceProvider)
                    .setTitle(getString(R.string.MangoScamUsersSource)).setMessage(getString(R.string.MangoScamUsersFormat)).setView(input)
                    .setPositiveButton(getString(R.string.Save), null).setNegativeButton(getString(R.string.Cancel), null).create();
            showDialog(dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String value = input.getText().toString().trim();
                if (!value.isEmpty() && !RemoteScamUsers.validUrl(value)) { input.setError(getString(R.string.MangoScamUsersInvalidUrl)); return; }
                ScamUsersManager.setSource(currentAccount, value); dialog.dismiss();
            });
        }
    }
}
