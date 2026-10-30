package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.PresenceSimulationManager;
import org.telegram.messenger.R;
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

public class PresenceSimulationActivity extends BaseFragment {
    private UniversalRecyclerView listView;
    private long typingTarget;
    private String typingTargetTitle = "";
    private int typingAction, typingMinutes = 5;
    private boolean resumed;
    private final Runnable refreshRunnable = new Runnable() {
        @Override public void run() {
            if (resumed) {
                refresh();
                AndroidUtilities.runOnUIThread(this, 5000);
            }
        }
    };

    @Override public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(getString(R.string.PresenceSimulationTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        frame.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        actionBar.setAdaptiveBackground(listView);
        return fragmentView = frame;
    }

    @Override public void onResume() {
        super.onResume();
        resumed = true;
        AndroidUtilities.cancelRunOnUIThread(refreshRunnable);
        refreshRunnable.run();
    }

    @Override public void onPause() {
        super.onPause();
        resumed = false;
        AndroidUtilities.cancelRunOnUIThread(refreshRunnable);
    }

    private PresenceSimulationManager manager() { return PresenceSimulationManager.getInstance(currentAccount); }
    private void refresh() { if (listView != null) listView.adapter.update(false); }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asShadow(getString(R.string.PresenceSimulationLimits)));
        int mode = manager().getPresenceMode();
        String value = getString(mode == PresenceSimulationManager.ONLINE ? R.string.PresenceSimulationOnline
                : mode == PresenceSimulationManager.OFFLINE ? R.string.PresenceSimulationOffline : R.string.PresenceSimulationNormal);
        if (mode != PresenceSimulationManager.NORMAL) {
            value += " · " + LocaleController.formatString(R.string.PresenceSimulationMinutes, manager().getPresenceRemainingMinutes());
        }
        items.add(UItem.asButton(1, getString(R.string.PresenceSimulationMode), value));
        items.add(UItem.asShadow(getString(R.string.PresenceSimulationModeInfo)));
        items.add(UItem.asButton(2, getString(R.string.PresenceSimulationTarget), TextUtils.isEmpty(typingTargetTitle)
                ? getString(R.string.DailyMessageTasksChoose) : typingTargetTitle));
        items.add(UItem.asButton(3, getString(R.string.PresenceSimulationAction), actionName(typingAction)));
        items.add(UItem.asButton(4, getString(R.string.PresenceSimulationDuration), LocaleController.formatString(R.string.PresenceSimulationMinutes, typingMinutes)));
        items.add(UItem.asButton(5, getString(R.string.PresenceSimulationStart)).accent());
        String active = manager().getTypingTitle();
        if (!TextUtils.isEmpty(active)) {
            items.add(UItem.asButton(6, getString(R.string.PresenceSimulationStop), active).red());
        }
        items.add(UItem.asShadow(getString(R.string.PresenceSimulationTypingInfo)));
    }

    private String actionName(int action) {
        return getString(action == 1 ? R.string.PresenceSimulationRecording : action == 2 ? R.string.PresenceSimulationSticker : R.string.PresenceSimulationTyping);
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (getParentActivity() == null) return;
        if (item.id == 1) {
            showDialog(new AlertDialog.Builder(getParentActivity(), resourceProvider).setTitle(getString(R.string.PresenceSimulationMode))
                    .setItems(new CharSequence[] {getString(R.string.PresenceSimulationNormal), getString(R.string.PresenceSimulationOnline), getString(R.string.PresenceSimulationOffline)},
                            (dialog, which) -> {
                                if (which == 0) {
                                    manager().setPresence(PresenceSimulationManager.NORMAL, 0);
                                    refresh();
                                } else {
                                    chooseDuration(5, minutes -> { manager().setPresence(which, minutes); refresh(); });
                                }
                            }).create());
        } else if (item.id == 2) {
            ExtensionAutomationTargetPicker.open(this, currentAccount, true, (dialogId, title) -> {
                typingTarget = dialogId;
                typingTargetTitle = title;
                refresh();
            });
        } else if (item.id == 3) {
            showDialog(new AlertDialog.Builder(getParentActivity(), resourceProvider).setTitle(getString(R.string.PresenceSimulationAction))
                    .setItems(new CharSequence[] {actionName(0), actionName(1), actionName(2)}, (dialog, which) -> { typingAction = which; refresh(); }).create());
        } else if (item.id == 4) {
            chooseDuration(typingMinutes, minutes -> { typingMinutes = minutes; refresh(); });
        } else if (item.id == 5) {
            if (!manager().startTyping(typingTarget, typingTargetTitle, typingAction, typingMinutes)) {
                Toast.makeText(getParentActivity(), getString(R.string.PresenceSimulationUnable), Toast.LENGTH_LONG).show();
            }
            refresh();
        } else if (item.id == 6) {
            manager().stopTyping();
            refresh();
        }
    }

    private interface DurationCallback { void onSelected(int minutes); }

    private void chooseDuration(int initial, DurationCallback callback) {
        EditTextBoldCursor input = new EditTextBoldCursor(getParentActivity());
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        input.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        input.setTextSize(18);
        input.setBackgroundDrawable(Theme.createEditTextDrawable(getParentActivity(), true));
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(2)});
        input.setText(Integer.toString(initial));
        input.setSelectAllOnFocus(true);
        FrameLayout container = new FrameLayout(getParentActivity());
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), 0);
        container.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(getParentActivity(), resourceProvider)
                .setTitle(getString(R.string.PresenceSimulationDuration)).setMessage(getString(R.string.PresenceSimulationDurationInfo))
                .setView(container).setPositiveButton(getString(R.string.OK), null).setNegativeButton(getString(R.string.Cancel), null).create();
        showDialog(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int minutes;
            try { minutes = Integer.parseInt(input.getText().toString()); } catch (Exception e) { minutes = 0; }
            if (minutes < 1 || minutes > 60) {
                input.setError(getString(R.string.PresenceSimulationDurationInfo));
                return;
            }
            callback.onSelected(minutes);
            dialog.dismiss();
        });
        input.requestFocus();
        AndroidUtilities.showKeyboard(input);
    }
}
