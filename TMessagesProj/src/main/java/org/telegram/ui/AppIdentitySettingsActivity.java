package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.AppIconsSelectorCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class AppIdentitySettingsActivity extends BaseFragment {

    private static final int ITEM_ID_APP_NAME = 1;
    private static final int ITEM_ID_RESET = 2;
    private static final int ITEM_ID_RESTART = 3;

    private UniversalRecyclerView listView;
    private AppIconsSelectorCell iconSelectorCell;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.AppIdentitySettings));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        iconSelectorCell = new AppIconsSelectorCell(context, this, currentAccount, true);
        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setSections();
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        actionBar.setAdaptiveBackground(listView);

        return fragmentView = contentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(false);
        }
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.AppIdentityNameHeader)));
        items.add(UItem.asButton(ITEM_ID_APP_NAME, R.drawable.msg_contacts_name, getString(R.string.AppIdentityName), LocaleController.getCustomAppName()));
        items.add(UItem.asShadow(getString(R.string.AppIdentityNameInfo)));

        items.add(UItem.asHeader(getString(R.string.AppIdentityBuiltInIcon)));
        if (iconSelectorCell != null) {
            items.add(UItem.asFullyCustom(iconSelectorCell));
        }
        items.add(UItem.asShadow(getString(R.string.AppIdentityBuiltInIconInfo)));

        items.add(UItem.asButton(ITEM_ID_RESTART, R.drawable.msg_topic_restart, getString(R.string.AppIdentityRestart)).accent());
        items.add(UItem.asButton(ITEM_ID_RESET, R.drawable.msg_delete, getString(R.string.AppIdentityReset)).red());
        items.add(UItem.asShadow(getString(R.string.AppIdentityRestartInfo)));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ITEM_ID_APP_NAME:
                showAppNameDialog();
                break;
            case ITEM_ID_RESTART:
                AppIdentityController.restartApp(getParentActivity());
                break;
            case ITEM_ID_RESET:
                showResetDialog();
                break;
        }
    }

    private void showAppNameDialog() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }

        FrameLayout container = new FrameLayout(context);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), 0);

        EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        editText.setBackgroundDrawable(Theme.createEditTextDrawable(context, true));
        editText.setHintText(getString(R.string.AppIdentityNameHint));
        editText.setSingleLine(true);
        editText.setMaxLines(1);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        editText.setText(LocaleController.getCustomAppName());
        editText.setSelection(editText.length());
        editText.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(12), AndroidUtilities.dp(4), AndroidUtilities.dp(12));
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL));

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourceProvider);
        builder.setTitle(getString(R.string.AppIdentityName));
        builder.setMessage(getString(R.string.AppIdentityNameDialogInfo));
        builder.setView(container);
        builder.setPositiveButton(getString(R.string.Save), (dialog, which) -> {
            LocaleController.setCustomAppName(editText.getText().toString());
            updateList();
        });
        builder.setNeutralButton(getString(R.string.Reset), (dialog, which) -> {
            LocaleController.resetCustomAppName();
            updateList();
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        showDialog(dialog);
        AndroidUtilities.runOnUIThread(() -> {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        }, 120);
    }

    private void showResetDialog() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourceProvider);
        builder.setTitle(getString(R.string.AppIdentityReset));
        builder.setMessage(getString(R.string.AppIdentityResetInfo));
        builder.setPositiveButton(getString(R.string.Reset), (dialog, which) -> {
            AppIdentityController.resetToDefault();
            updateList();
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void updateList() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(false);
        }
    }
}
