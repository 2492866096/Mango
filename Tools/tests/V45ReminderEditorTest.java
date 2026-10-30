package org.telegram.messenger;

import android.app.DatePickerDialog;
import android.widget.TimePicker;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.MangoMessageReminderSheet;
import java.util.Calendar;
import java.util.TimeZone;

/** Runs the real reminder editor callbacks with deterministic dialog and UI-queue fixtures. */
public final class V45ReminderEditorTest {
    private static int checks;
    private static void check(boolean ok, String label) { checks++; if (!ok) throw new AssertionError(label); }
    private static AlertDialog open(BaseFragment fragment, MessageObject message) {
        MangoMessageReminderSheet.show(fragment,message,null);
        return (AlertDialog) fragment.visibleDialog;
    }
    private static DatePickerDialog custom(BaseFragment fragment, MessageObject message) {
        open(fragment,message).select(0);
        check(fragment.visibleDialog==null,"menu dismissal clears only the menu before queued picker");
        AndroidUtilities.drain();
        return (DatePickerDialog) fragment.visibleDialog;
    }
    private static AlertDialog time(BaseFragment fragment, MessageObject message, Calendar day) {
        DatePickerDialog date=custom(fragment,message);
        date.choose(day.get(Calendar.YEAR),day.get(Calendar.MONTH),day.get(Calendar.DAY_OF_MONTH));
        check(fragment.visibleDialog==null,"date dismissal completes before time picker is displayed");
        AndroidUtilities.drain();
        return (AlertDialog) fragment.visibleDialog;
    }
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        UserConfig.getInstance(0).owner=101;
        AndroidUtilities.queueImmediate=true;
        BaseFragment fragment=new BaseFragment();
        MessageObject message=new MessageObject();
        AlertDialog menu=open(fragment,message);
        check(menu.items.length==3&&menu.items[0].equals(LocaleController.getString(R.string.MangoMessageReminderCustomTime)),"custom date and time is the first option");
        Calendar future=Calendar.getInstance();future.add(Calendar.DAY_OF_MONTH,2);
        AlertDialog editor=time(fragment,message,future);
        TimePicker picker=(TimePicker) editor.view;picker.setCurrentHour(14);picker.setCurrentMinute(37);
        editor.confirm();
        long due=MangoMessageReminderManager.localTime(future.get(Calendar.YEAR),future.get(Calendar.MONTH),future.get(Calendar.DAY_OF_MONTH),14,37);
        check(MangoMessageReminderManager.getReminderTime(0,message)==due&&fragment.visibleDialog==null,"custom selection persists exact local minute and dismisses");
        menu=open(fragment,message);
        check(menu.items.length==4&&menu.items[0].equals(LocaleController.getString(R.string.MangoMessageReminderEditTime))&&menu.message!=null,"existing reminder displays current time and edit/cancel actions");
        DatePickerDialog date=custom(fragment,message);
        check(date.year==future.get(Calendar.YEAR)&&date.month==future.get(Calendar.MONTH)&&date.day==future.get(Calendar.DAY_OF_MONTH),"existing reminder pre-fills date");
        date.choose(date.year,date.month,date.day);AndroidUtilities.drain();
        editor=(AlertDialog)fragment.visibleDialog;picker=(TimePicker)editor.view;
        check(picker.getCurrentHour()==14&&picker.getCurrentMinute()==37,"existing reminder pre-fills time");
        picker.setCurrentMinute(42);editor.changeDate();AndroidUtilities.drain();date=(DatePickerDialog)fragment.visibleDialog;
        date.choose(date.year,date.month,date.day);AndroidUtilities.drain();editor=(AlertDialog)fragment.visibleDialog;
        check(((TimePicker)editor.view).getCurrentMinute()==42,"changing date retains time entered by user");
        editor.cancel();check(MangoMessageReminderManager.getReminderTime(0,message)==due,"cancelling edit leaves existing reminder unchanged");

        editor=time(fragment,message,Calendar.getInstance());picker=(TimePicker)editor.view;
        picker.setCurrentHour(0);picker.setCurrentMinute(0);editor.confirm();
        check(!editor.dismissed&&fragment.visibleDialog==editor&&MangoMessageReminderManager.getReminderTime(0,message)==due,"past time stays editable without changing old reminder");
        check(editor.message.toString().contains(LocaleController.getString(R.string.MangoMessageReminderFutureTime)),"past selection explains the required future time");
        editor.cancel();

        editor=time(fragment,message,future);UserConfig.getInstance(0).owner=202;editor.confirm();
        check(fragment.visibleDialog==null&&MangoMessageReminderManager.getReminderTime(0,message)==0,"account owner change rejects stale confirmation");
        UserConfig.getInstance(0).owner=101;
        check(MangoMessageReminderManager.getReminderTime(0,message)==due,"stale editor left original owner reminder intact");
        date=custom(fragment,message);fragment.onPause();date.choose(date.year,date.month,date.day);AndroidUtilities.drain();
        check(fragment.visibleDialog==null&&MangoMessageReminderManager.getReminderTime(0,message)==due,"paused page cannot open next picker or change reminder");
        fragment.paused=false;
        editor=time(fragment,message,future);message.deleted=true;editor.confirm();message.deleted=false;
        check(fragment.visibleDialog==null&&MangoMessageReminderManager.getReminderTime(0,message)==due,"deleted message rejects pending confirmation");
        editor=time(fragment,message,future);message.id++;editor.confirm();message.id--;
        check(fragment.visibleDialog==null&&MangoMessageReminderManager.getReminderTime(0,message)==due,"message replacement rejects pending confirmation");
        menu=open(fragment,message);menu.select(3);
        check(!MangoMessageReminderManager.hasReminder(0,message)&&ApplicationLoader.applicationContext.alarm.pending.isEmpty(),"cancel action removes custom alarm and record");
        System.out.println("PASS: "+checks+" reminder editor assertions");
    }
}
