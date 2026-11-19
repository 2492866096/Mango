package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.app.TimePickerDialog;
import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DailyMessageTasksManager;
import org.telegram.messenger.LocaleController;
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
import java.util.Date;
import java.util.Locale;

public class DailyMessageTasksActivity extends BaseFragment {
    private UniversalRecyclerView listView;
    private boolean resumed;
    private final Runnable refreshRunnable = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            refresh();
            AndroidUtilities.runOnUIThread(this, 5000);
        }
    };

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(getString(R.string.DailyMessageTasksTitle));
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

    private void refresh() { if (listView != null) listView.adapter.update(false); }

    private DailyMessageTasksManager manager() { return DailyMessageTasksManager.getInstance(currentAccount); }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asShadow(getString(R.string.DailyMessageTasksOnlineWarning)));
        items.add(UItem.asButton(1, "+ " + getString(R.string.DailyMessageTasksAdd)).accent());
        ArrayList<DailyMessageTasksManager.Task> tasks = manager().getTasks();
        if (tasks.isEmpty()) {
            items.add(UItem.asShadow(getString(R.string.DailyMessageTasksEmpty)));
        }
        for (DailyMessageTasksManager.Task task : tasks) {
            String value = (task.mode == DailyMessageTasksManager.RANDOM_ONLINE ? getString(R.string.DailyMessageTasksRandomMode)
                    + " " + time(task.randomWindowStart) + "–" + time(task.randomWindowEnd) : time(task.hour * 60 + task.minute)) + " · "
                    + getString(task.automatic ? R.string.DailyMessageTasksAutomatic : R.string.DailyMessageTasksManual);
            UItem item = UItem.asButton(2, task.title, value);
            item.object = task;
            items.add(item);
            String info = task.text.length() > 120 ? task.text.substring(0, 120) + "…" : task.text;
            if (task.lastAttemptAt != 0) {
                info += "\n" + LocaleController.formatString(R.string.DailyMessageTasksLastAttempt,
                        DateFormat.getDateFormat(getParentActivity()).format(new Date(task.lastAttemptAt)) + " "
                                + DateFormat.getTimeFormat(getParentActivity()).format(new Date(task.lastAttemptAt)),
                        getString(resultString(task.lastResult)));
            }
            items.add(UItem.asShadow(info));
        }
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 1) {
            openEditor(new DailyMessageTasksManager.Task());
        } else if (item.id == 2 && item.object instanceof DailyMessageTasksManager.Task && getParentActivity() != null) {
            DailyMessageTasksManager.Task task = (DailyMessageTasksManager.Task) item.object;
            showDialog(new AlertDialog.Builder(getParentActivity(), resourceProvider).setTitle(task.title)
                    .setItems(new CharSequence[] {getString(R.string.Edit), getString(R.string.DailyMessageTasksSendNow),
                            getString(task.automatic ? R.string.DailyMessageTasksDisable : R.string.DailyMessageTasksEnable), getString(R.string.Delete)},
                            (dialog, which) -> {
                                if (which == 0) {
                                    openEditor(task);
                                } else if (which == 1) {
                                    showDialog(new AlertDialog.Builder(getParentActivity(), resourceProvider)
                                            .setTitle(getString(R.string.DailyMessageTasksSendNow))
                                            .setMessage(LocaleController.formatString(R.string.DailyMessageTasksSendConfirm, task.title, task.text))
                                            .setPositiveButton(getString(R.string.Send), (d, w) -> {
                                                toast(resultString(manager().triggerNow(task.id)));
                                                refresh();
                                            }).setNegativeButton(getString(R.string.Cancel), null).create());
                                } else if (which == 2) {
                                    Runnable toggle = () -> {
                                        task.automatic = !task.automatic;
                                        if (!manager().save(task)) toast(R.string.DailyMessageTasksStorageError);
                                        refresh();
                                    };
                                    if (task.automatic) toggle.run(); else showAutomaticWarning(this, toggle);
                                } else {
                                    showDialog(new AlertDialog.Builder(getParentActivity(), resourceProvider).setTitle(getString(R.string.Delete))
                                            .setMessage(LocaleController.formatString(R.string.DailyMessageTasksDeleteConfirm, task.title))
                                            .setPositiveButton(getString(R.string.Delete), (d, w) -> {
                                                if (!manager().remove(task.id)) toast(R.string.DailyMessageTasksStorageError);
                                                refresh();
                                            }).setNegativeButton(getString(R.string.Cancel), null).create());
                                }
                            }).create());
        }
    }

    private void openEditor(DailyMessageTasksManager.Task task) {
        Editor editor = new Editor(task);
        editor.setCurrentAccount(currentAccount);
        presentFragment(editor);
    }

    private void toast(int resource) { if (getParentActivity() != null) Toast.makeText(getParentActivity(), getString(resource), Toast.LENGTH_LONG).show(); }

    private static String time(int minute) { return String.format(Locale.getDefault(), "%02d:%02d", minute / 60, minute % 60); }

    private static int resultString(int result) {
        switch (result) {
            case DailyMessageTasksManager.QUEUED: return R.string.DailyMessageTasksQueued;
            case DailyMessageTasksManager.OFFLINE: return R.string.DailyMessageTasksOffline;
            case DailyMessageTasksManager.INVALID_TARGET: return R.string.DailyMessageTasksInvalidTarget;
            case DailyMessageTasksManager.INVALID_TEXT: return R.string.DailyMessageTasksInvalidText;
            case DailyMessageTasksManager.PAID_TARGET: return R.string.DailyMessageTasksPaidTarget;
            default: return R.string.DailyMessageTasksStorageError;
        }
    }

    static void showAutomaticWarning(BaseFragment parent, Runnable accepted) {
        if (parent.getParentActivity() == null) return;
        parent.showDialog(new AlertDialog.Builder(parent.getParentActivity(), parent.getResourceProvider())
                .setTitle(getString(R.string.DailyMessageTasksAutomatic))
                .setMessage(getString(R.string.DailyMessageTasksOnlineWarning))
                .setPositiveButton(getString(R.string.OK), (dialog, which) -> accepted.run())
                .setNegativeButton(getString(R.string.Cancel), null).create());
    }

    private static final class Editor extends BaseFragment {
        private final DailyMessageTasksManager.Task task;
        private UniversalRecyclerView listView;

        Editor(DailyMessageTasksManager.Task source) { task = source.copy(); }

        @Override public View createView(Context context) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setTitle(getString(R.string.DailyMessageTasksEdit));
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

        private void refresh() { if (listView != null) listView.adapter.update(false); }

        private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            items.add(UItem.asButton(1, getString(R.string.DailyMessageTasksTarget), TextUtils.isEmpty(task.title) ? getString(R.string.DailyMessageTasksChoose) : task.title));
            items.add(UItem.asButton(2, getString(R.string.DailyMessageTasksText), TextUtils.isEmpty(task.text) ? getString(R.string.DailyMessageTasksChoose) : task.text.replace('\n', ' ')));
            items.add(UItem.asButton(6, getString(R.string.DailyMessageTasksMode), getString(task.mode == DailyMessageTasksManager.RANDOM_ONLINE
                    ? R.string.DailyMessageTasksRandomMode : R.string.DailyMessageTasksFixedMode)));
            if (task.mode == DailyMessageTasksManager.RANDOM_ONLINE) {
                items.add(UItem.asButton(7, getString(R.string.DailyMessageTasksWindowStart), time(task.randomWindowStart)));
                items.add(UItem.asButton(8, getString(R.string.DailyMessageTasksWindowEnd), time(task.randomWindowEnd)));
                items.add(UItem.asShadow(getString(R.string.DailyMessageTasksRandomInfo)));
            } else {
                items.add(UItem.asButton(3, getString(R.string.DailyMessageTasksTime), time(task.hour * 60 + task.minute)));
            }
            items.add(UItem.asSwitch(4, getString(R.string.DailyMessageTasksAutomatic)).setChecked(task.automatic));
            items.add(UItem.asShadow(getString(R.string.DailyMessageTasksOnlineWarning)));
            items.add(UItem.asButton(5, getString(R.string.Save)).accent());
        }

        private void onItemClick(UItem item, View view, int position, float x, float y) {
            if (getParentActivity() == null) return;
            if (item.id == 1) {
                ExtensionAutomationTargetPicker.open(this, currentAccount, false, (dialogId, title) -> {
                    task.dialogId = dialogId;
                    task.title = title;
                    refresh();
                });
            } else if (item.id == 2) {
                EditTextBoldCursor text = new EditTextBoldCursor(getParentActivity());
                text.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                text.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
                text.setTextSize(18);
                text.setBackgroundDrawable(Theme.createEditTextDrawable(getParentActivity(), true));
                text.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                text.setMinLines(3);
                text.setMaxLines(8);
                text.setFilters(new InputFilter[] {new InputFilter.LengthFilter(4096)});
                text.setText(task.text);
                FrameLayout container = new FrameLayout(getParentActivity());
                container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), 0);
                container.addView(text, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                showDialog(new AlertDialog.Builder(getParentActivity(), resourceProvider).setTitle(getString(R.string.DailyMessageTasksText)).setView(container)
                        .setPositiveButton(getString(R.string.Save), (d, w) -> { task.text = text.getText().toString().trim(); refresh(); })
                        .setNegativeButton(getString(R.string.Cancel), null).create());
                text.requestFocus();
                AndroidUtilities.showKeyboard(text);
            } else if (item.id == 3) {
                TimePickerDialog dialog = new TimePickerDialog(getParentActivity(), (picker, hour, minute) -> {
                    task.hour = hour;
                    task.minute = minute;
                    refresh();
                }, task.hour, task.minute, DateFormat.is24HourFormat(getParentActivity()));
                dialog.setTitle(getString(R.string.DailyMessageTasksTime));
                showDialog(dialog);
            } else if (item.id == 4) {
                Runnable toggle = () -> { task.automatic = !task.automatic; refresh(); };
                if (task.automatic) toggle.run(); else showAutomaticWarning(this, toggle);
            } else if (item.id == 6) {
                showDialog(new AlertDialog.Builder(getParentActivity(), resourceProvider).setTitle(getString(R.string.DailyMessageTasksMode))
                        .setItems(new CharSequence[] {getString(R.string.DailyMessageTasksRandomMode), getString(R.string.DailyMessageTasksFixedMode)},
                                (d, which) -> { task.mode = which; refresh(); }).create());
            } else if (item.id == 7 || item.id == 8) {
                boolean start = item.id == 7;
                int initial = start ? task.randomWindowStart : task.randomWindowEnd;
                TimePickerDialog dialog = new TimePickerDialog(getParentActivity(), (picker, hour, minute) -> {
                    int selected = hour * 60 + minute;
                    if (start) task.randomWindowStart = selected; else task.randomWindowEnd = selected;
                    refresh();
                }, initial / 60, initial % 60, DateFormat.is24HourFormat(getParentActivity()));
                dialog.setTitle(getString(start ? R.string.DailyMessageTasksWindowStart : R.string.DailyMessageTasksWindowEnd));
                showDialog(dialog);
            } else if (item.id == 5) {
                if (!DailyMessageTasksManager.isValidTarget(currentAccount, task.dialogId, false)) {
                    toast(R.string.DailyMessageTasksInvalidTarget);
                } else if (TextUtils.isEmpty(task.text.trim())) {
                    toast(R.string.DailyMessageTasksInvalidText);
                } else if (task.randomWindowStart > task.randomWindowEnd) {
                    toast(R.string.DailyMessageTasksInvalidWindow);
                } else if (DailyMessageTasksManager.getInstance(currentAccount).save(task)) {
                    finishFragment();
                } else {
                    toast(R.string.DailyMessageTasksStorageError);
                }
            }
        }

        private void toast(int res) { Toast.makeText(getParentActivity(), getString(res), Toast.LENGTH_LONG).show(); }
    }
}
