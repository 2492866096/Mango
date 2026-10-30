package org.telegram.ui.Components;

import static org.telegram.messenger.LocaleController.getString;

import android.app.DatePickerDialog;
import android.content.Context;
import android.text.format.DateFormat;
import android.widget.TimePicker;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MangoMessageReminderManager;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.util.Calendar;
import java.util.Date;

/** A local reminder editor; its callbacks remain tied to the message and account that opened it. */
public final class MangoMessageReminderSheet {
    private final BaseFragment fragment;
    private final MessageObject message;
    private final Theme.ResourcesProvider resourcesProvider;
    private final int account, messageId;
    private final long ownerId, dialogId, topicId;

    private MangoMessageReminderSheet(BaseFragment fragment, MessageObject message, Theme.ResourcesProvider resourcesProvider) {
        this.fragment = fragment;
        this.message = message;
        this.resourcesProvider = resourcesProvider;
        account = fragment.getCurrentAccount();
        ownerId = UserConfig.getInstance(account).getClientUserId();
        messageId = message.getId();
        dialogId = message.getDialogId();
        topicId = message.getTopicId();
    }

    public static void show(BaseFragment fragment, MessageObject message, Theme.ResourcesProvider resourcesProvider) {
        if (fragment == null || message == null || !UserConfig.isValidAccount(fragment.getCurrentAccount())) return;
        MangoMessageReminderSheet editor = new MangoMessageReminderSheet(fragment, message, resourcesProvider);
        if (editor.isCurrent()) editor.showChoices();
    }

    private boolean isCurrent() {
        return !fragment.isFinished && !fragment.isPaused() && fragment.getParentActivity() != null &&
                !fragment.getParentActivity().isFinishing() && fragment.getCurrentAccount() == account &&
                ownerId != 0 && UserConfig.getInstance(account).getClientUserId() == ownerId &&
                message.currentAccount == account && message.getId() == messageId && messageId != 0 &&
                message.getDialogId() == dialogId && dialogId != 0 && message.getTopicId() == topicId &&
                !message.deleted && !message.deletedByThanos;
    }

    private void showChoices() {
        final long existing = MangoMessageReminderManager.getReminderTime(account, message);
        final boolean hasReminder = existing > 0;
        CharSequence[] choices = hasReminder ? new CharSequence[]{
                getString(R.string.MangoMessageReminderEditTime),
                getString(R.string.MangoMessageReminderOneHour),
                getString(R.string.MangoMessageReminderTomorrow),
                getString(R.string.MangoMessageReminderCancel)
        } : new CharSequence[]{
                getString(R.string.MangoMessageReminderCustomTime),
                getString(R.string.MangoMessageReminderOneHour),
                getString(R.string.MangoMessageReminderTomorrow)
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity(), resourcesProvider)
                .setTitle(getString(R.string.MangoMessageReminderMenu));
        if (hasReminder) builder.setMessage(LocaleController.formatString(R.string.MangoMessageReminderCurrentTime, formatTime(existing)));
        builder.setItems(choices, (dialog, which) -> {
            if (!isCurrent()) return;
            if (which == 0) {
                long initial = existing > System.currentTimeMillis() ? existing :
                        MangoMessageReminderManager.dueAt(MangoMessageReminderManager.IN_ONE_HOUR, System.currentTimeMillis());
                AndroidUtilities.runOnUIThread(() -> showDate(initial));
            } else if (hasReminder && which == 3) {
                MangoMessageReminderManager.cancel(account, message);
                showResult(true, getString(R.string.MangoMessageReminderRemoved));
            } else {
                long when = MangoMessageReminderManager.dueAt(which == 1 ? MangoMessageReminderManager.IN_ONE_HOUR :
                        MangoMessageReminderManager.TOMORROW, System.currentTimeMillis());
                save(when);
            }
        });
        fragment.showDialog(builder.create());
    }

    private void showDate(long initial) {
        if (!isCurrent()) return;
        Calendar selected = Calendar.getInstance();
        selected.setTimeInMillis(initial);
        DatePickerDialog picker = new DatePickerDialog(fragment.getParentActivity(), (view, year, month, day) -> {
            if (!isCurrent()) return;
            selected.set(year, month, day);
            AndroidUtilities.runOnUIThread(() -> showTime(selected));
        }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), selected.get(Calendar.DAY_OF_MONTH));
        picker.setTitle(getString(R.string.MangoMessageReminderChooseDate));
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        picker.getDatePicker().setMinDate(today.getTimeInMillis());
        fragment.showDialog(picker);
    }

    private void showTime(Calendar selected) {
        if (!isCurrent()) return;
        Context context = fragment.getParentActivity();
        TimePicker picker = new TimePicker(context);
        picker.setIs24HourView(DateFormat.is24HourFormat(context));
        picker.setCurrentHour(selected.get(Calendar.HOUR_OF_DAY));
        picker.setCurrentMinute(selected.get(Calendar.MINUTE));
        final String dateText = java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL,
                LocaleController.getInstance().getCurrentLocale()).format(selected.getTime());
        AlertDialog dialog = new AlertDialog.Builder(context, resourcesProvider)
                .setTitle(getString(R.string.MangoMessageReminderChooseTime))
                .setMessage(dateText)
                .setView(picker)
                .setPositiveButton(getString(R.string.Set), (d, which) -> {
                    if (!isCurrent()) {
                        d.dismiss();
                        return;
                    }
                    picker.clearFocus();
                    long when = MangoMessageReminderManager.localTime(selected.get(Calendar.YEAR),
                            selected.get(Calendar.MONTH), selected.get(Calendar.DAY_OF_MONTH),
                            picker.getCurrentHour(), picker.getCurrentMinute());
                    if (when <= System.currentTimeMillis()) {
                        ((AlertDialog) d).setMessage(dateText + "\n" + getString(R.string.MangoMessageReminderFutureTime));
                        return;
                    }
                    if (save(when)) d.dismiss();
                    else ((AlertDialog) d).setMessage(dateText + "\n" + getString(R.string.MangoMessageReminderFailed));
                })
                .setNegativeButton(getString(R.string.Cancel), (d, which) -> d.dismiss())
                .setNeutralButton(getString(R.string.MangoMessageReminderChooseDate), (d, which) -> {
                    picker.clearFocus();
                    selected.set(Calendar.HOUR_OF_DAY, picker.getCurrentHour());
                    selected.set(Calendar.MINUTE, picker.getCurrentMinute());
                    d.dismiss();
                    AndroidUtilities.runOnUIThread(() -> showDate(selected.getTimeInMillis()));
                }).create();
        // Invalid/past times stay in the editor without changing or cancelling the existing reminder.
        dialog.setDismissDialogByButtons(false);
        fragment.showDialog(dialog);
    }

    private boolean save(long when) {
        if (!isCurrent()) return false;
        boolean saved = MangoMessageReminderManager.scheduleAt(account, ownerId, message, when);
        showResult(saved, saved ? LocaleController.formatString(R.string.MangoMessageReminderSavedAt, formatTime(when)) :
                getString(R.string.MangoMessageReminderFailed));
        return saved;
    }

    private void showResult(boolean success, String text) {
        BulletinFactory.of(fragment).createSimpleBulletin(success ? R.raw.ic_save_to_gallery : R.raw.error, text).show();
    }

    private String formatTime(long when) {
        return java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT,
                LocaleController.getInstance().getCurrentLocale()).format(new Date(when));
    }
}
