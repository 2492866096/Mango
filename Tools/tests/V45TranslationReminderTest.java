package org.telegram.messenger;

import android.app.PendingIntent;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.core.app.NotificationManagerCompat;
import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.TranslateAlert2;

import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

public final class V45TranslationReminderTest {
    private static int checks;
    private static void check(boolean ok, String label) { checks++; if (!ok) throw new AssertionError(label); }
    private static TLRPC.TL_textWithEntities text(String value) { TLRPC.TL_textWithEntities t=new TLRPC.TL_textWithEntities();t.text=value;return t; }
    private static TLRPC.TL_messages_translateResult translated(String value) { TLRPC.TL_messages_translateResult r=new TLRPC.TL_messages_translateResult();r.result.add(text(value));return r; }
    private static SharedPreferences prefs(int account, long owner) { return ApplicationLoader.applicationContext.getSharedPreferences("mango_message_reminders_"+account+"_"+owner,0); }
    private static void makeDue(int account,long owner,String key) throws Exception {SharedPreferences p=prefs(account,owner);JSONObject j=new JSONObject(p.getString(key,null));j.put("due",System.currentTimeMillis()-2000);p.edit().putString(key,j.toString()).commit();}

    public static void main(String[] args) throws Exception {
        UserConfig.getInstance(0).owner=101;
        UserConfig.getInstance(1).owner=202;
        check(MangoOutgoingTranslation.getLanguage(0,10).equals("en"), "default language");
        MangoOutgoingTranslation.setLanguage(0,10,"zh-CN");
        check(MangoOutgoingTranslation.getLanguage(0,10).equals("zh"), "normalize selected language");
        check(MangoOutgoingTranslation.getLanguage(0,11).equals("en"), "dialog isolation");
        check(MangoOutgoingTranslation.getLanguage(1,10).equals("en"), "account isolation");
        UserConfig.getInstance(0).owner=303;
        check(MangoOutgoingTranslation.getLanguage(0,10).equals("en"), "owner isolation");
        UserConfig.getInstance(0).owner=101;
        final TLRPC.TL_textWithEntities[] result={null}; final int[] callbacks={0};
        Runnable cancel=MangoOutgoingTranslation.translate(0,text("你好"),"en",r->{result[0]=r;callbacks[0]++;});
        check(ConnectionsManager.request.flags==2 && ConnectionsManager.request.text.get(0).text.equals("你好"),"raw-text API request");
        ConnectionsManager.pending.run(translated("Hello"),null);
        check(result[0].text.equals("Hello") && callbacks[0]==1,"translation result");
        check(AndroidUtilities.delayed.isEmpty(),"completion cancels timeout");
        cancel.run();
        ConnectionsManager.pending.run(translated("late"),null);
        check(callbacks[0]==1,"completed request is exactly once");
        cancel=MangoOutgoingTranslation.translate(0,text("draft"),"en",r->callbacks[0]++);
        cancel.run(); ConnectionsManager.pending.run(translated("late"),null);
        check(callbacks[0]==1 && AndroidUtilities.delayed.isEmpty(),"cancel blocks late send");
        MangoOutgoingTranslation.translate(0,text("draft"),"en",r->{result[0]=r;callbacks[0]++;});
        Runnable timeout=AndroidUtilities.delayed.get(0);timeout.run();ConnectionsManager.pending.run(translated("late"),null);
        check(result[0]==null && callbacks[0]==2,"timeout fails once and ignores late result");
        MangoOutgoingTranslation.translate(0,text("draft"),"en",r->{result[0]=r;callbacks[0]++;});
        ConnectionsManager.pending.run(null,new Object());
        check(TranslateAlert2.fallbacks==1,"plain draft gets endpoint fallback");
        TranslateAlert2.pending.run("translated",false);
        check(result[0].text.equals("translated") && callbacks[0]==3,"fallback success");
        TLRPC.TL_textWithEntities rich=text("hidden link");rich.entities.add(new TLRPC.MessageEntity());
        MangoOutgoingTranslation.translate(0,rich,"en",r->{result[0]=r;callbacks[0]++;});ConnectionsManager.pending.run(null,new Object());
        check(result[0]==null && TranslateAlert2.fallbacks==1,"rich draft never loses semantic entities through plain fallback");
        MangoOutgoingTranslation.translate(0,rich,"en",r->{result[0]=r;callbacks[0]++;});
        TLRPC.TL_messages_translateResult response=translated("rich");response.result.get(0).entities.add(new TLRPC.MessageEntity());ConnectionsManager.pending.run(response,null);
        check(result[0].entities.size()==1,"Telegram translation retains entities");
        int previousCallbacks=callbacks[0];
        MangoOutgoingTranslation.translate(0,text("draft"),"en",r->{result[0]=r;callbacks[0]++;});
        ConnectionsManager.pending.run(null,new Object());TranslateAlert2.pending.run(null,true);
        check(result[0]==null&&callbacks[0]==previousCallbacks+1,"fallback 429 fails without a sendable result");
        previousCallbacks=callbacks[0];
        cancel=MangoOutgoingTranslation.translate(0,text("draft"),"en",r->callbacks[0]++);
        ConnectionsManager.pending.run(null,new Object());cancel.run();TranslateAlert2.pending.run("late fallback",false);
        check(callbacks[0]==previousCallbacks,"cancel also blocks late fallback result");

        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        long now=ZonedDateTime.of(2026,3,7,10,30,0,0,ZoneId.of("America/New_York")).toInstant().toEpochMilli();
        check(MangoMessageReminderManager.dueAt(0,now)-now==3600000L,"one hour is elapsed time");
        ZonedDateTime tomorrow=java.time.Instant.ofEpochMilli(MangoMessageReminderManager.dueAt(1,now)).atZone(ZoneId.of("America/New_York"));
        check(tomorrow.getDayOfMonth()==8&&tomorrow.getHour()==9&&tomorrow.getMinute()==0,"tomorrow at 9 across DST");
        check(MangoMessageReminderManager.localTime(2026,2,8,2,30)==0,"nonexistent DST picker time is rejected instead of shifted");
        check(MangoMessageReminderManager.localTime(2026,2,8,3,30)==ZonedDateTime.of(2026,3,8,3,30,0,0,ZoneId.of("America/New_York")).toInstant().toEpochMilli(),"custom picker respects DST offset");
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        check(MangoMessageReminderManager.localTime(2027,1,29,10,15)==0,"invalid leap day is rejected");
        check(MangoMessageReminderManager.localTime(2028,1,29,10,15)==ZonedDateTime.of(2028,2,29,10,15,0,0,ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli(),"custom leap-day reminder retains exact minute");
        check(MangoMessageReminderManager.localTime(2028,1,29,24,15)==0,"invalid custom hour rejected");
        Context context=ApplicationLoader.applicationContext;
        MessageObject message=new MessageObject();
        long customTime=System.currentTimeMillis()+172800000L+17*60000L;
        check(!MangoMessageReminderManager.scheduleAt(0,101,message,0),"zero custom time rejected");
        check(!MangoMessageReminderManager.scheduleAt(0,101,message,System.currentTimeMillis()-60000),"past custom time rejected");
        check(!MangoMessageReminderManager.scheduleAt(-1,101,message,customTime),"invalid account cannot save custom reminder");
        check(!MangoMessageReminderManager.hasReminder(-1,message),"invalid account lookup is safe");
        check(MangoMessageReminderManager.scheduleAt(0,101,message,customTime),"arbitrary custom time saved");
        check(MangoMessageReminderManager.getReminderTime(0,message)==customTime,"editor can read saved custom deadline");
        check(context.alarm.pending.size()==1,"custom reminder armed once");
        check(!MangoMessageReminderManager.scheduleAt(0,101,message,System.currentTimeMillis())&&MangoMessageReminderManager.getReminderTime(0,message)==customTime,"past edit preserves previous deadline");
        check(!MangoMessageReminderManager.scheduleAt(0,999,message,customTime+60000)&&MangoMessageReminderManager.getReminderTime(0,message)==customTime,"editor from stale owner cannot overwrite reminder");
        UserConfig.getInstance(0).owner=999;
        check(!MangoMessageReminderManager.scheduleAt(0,101,message,customTime)&&MangoMessageReminderManager.getReminderTime(0,message)==0,"reused account slot rejects old editor");
        UserConfig.getInstance(0).owner=101;
        message.currentAccount=1;
        check(!MangoMessageReminderManager.scheduleAt(0,101,message,customTime),"cross-account message rejected");
        message.currentAccount=0;message.deleted=true;
        check(!MangoMessageReminderManager.scheduleAt(0,101,message,customTime),"deleted message cannot get reminder");
        message.deleted=false;message.deletedByThanos=true;
        check(!MangoMessageReminderManager.scheduleAt(0,101,message,customTime),"message pending delete animation rejected");
        message.deletedByThanos=false;
        check(MangoMessageReminderManager.scheduleAt(0,101,message,customTime+60000)&&context.alarm.pending.size()==1&&MangoMessageReminderManager.getReminderTime(0,message)==customTime+60000,"editing custom time replaces one alarm and persisted deadline");
        Context.failCommit=true;
        check(!MangoMessageReminderManager.scheduleAt(0,101,message,customTime+120000)&&MangoMessageReminderManager.getReminderTime(0,message)==customTime+60000,"failed custom edit keeps old reminder");
        Context.failCommit=false;
        MangoMessageReminderManager.cancel(0,message);
        check(MangoMessageReminderManager.getReminderTime(0,message)==0&&context.alarm.pending.isEmpty(),"custom reminder can be cancelled");
        check(MangoMessageReminderManager.schedule(0,message,0),"reminder saved");
        check(MangoMessageReminderManager.hasReminder(0,message),"reminder persisted");
        check(context.alarm.pending.size()==1,"alarm armed");
        check(!MangoMessageReminderManager.hasReminder(1,message),"reminders isolated by account");
        check(MangoMessageReminderManager.schedule(0,message,1)&&context.alarm.pending.size()==1,"rescheduling replaces same message alarm");
        message.topic=13;check(MangoMessageReminderManager.schedule(0,message,0)&&context.alarm.pending.size()==2,"topic IDs separate reminders");
        MangoMessageReminderManager.cancel(0,message);check(!MangoMessageReminderManager.hasReminder(0,message)&&context.alarm.pending.size()==1,"cancel removes persistence and alarm");
        message.topic=12;
        String key="reminder_-100_12_10";
        UserConfig.getInstance(0).owner=999;check(!MangoMessageReminderManager.hasReminder(0,message),"reminders isolated on reused slot");
        MangoMessageReminderManager.fire(context,0,101,key);check(context.notifications.count==0,"old owner alarm cannot notify new account");UserConfig.getInstance(0).owner=101;
        MangoMessageReminderManager.fire(context,0,101,key);check(context.notifications.count==0,"early alarm ignored");
        makeDue(0,101,key);NotificationManagerCompat.enabled=false;
        MangoMessageReminderManager.fire(context,0,101,key);check(MangoMessageReminderManager.hasReminder(0,message),"denied notifications retain due reminder");
        NotificationManagerCompat.enabled=true;MangoMessageReminderManager.fire(context,0,101,key);
        check(context.notifications.count==1&&!MangoMessageReminderManager.hasReminder(0,message),"due reminder delivered exactly once");
        check(context.notifications.last.click.intent.extras.get("chatId").equals(100L),"notification opens group/channel");
        check(context.notifications.last.click.intent.extras.get("message_id").equals(10)&&context.notifications.last.click.intent.extras.get("topicId").equals(12L),"notification retains message and topic");
        check(context.notifications.last.click.intent.extras.get("currentAccount").equals(0)&&context.notifications.last.click.intent.extras.get("reminderOwner").equals(101L),"click retains original account owner");
        MangoMessageReminderManager.fire(context,0,101,key);check(context.notifications.count==1,"duplicate delivery ignored");
        context.alarm.exactAllowed=false;MangoMessageReminderManager.schedule(0,message,0);check(context.alarm.inexactCount>0,"restricted exact alarms use supported fallback");
        context.alarm.pending.clear();MangoMessageReminderManager.rescheduleAll();check(context.alarm.pending.size()==1,"restart restores pending alarm");
        Context.failCommit=true;message.id=999;check(!MangoMessageReminderManager.schedule(0,message,0)&&!MangoMessageReminderManager.hasReminder(0,message),"disk failure does not report success");Context.failCommit=false;
        message.id=0;check(!MangoMessageReminderManager.schedule(0,message,0),"invalid message rejected");
        System.out.println("PASS: "+checks+" translation/reminder assertions");
    }
}
