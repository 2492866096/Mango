package org.telegram.messenger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.TimeZone;
import android.content.Context;
import org.telegram.tgnet.ConnectionsManager;

public final class DailyMessageTasksManagerTest {
    private static int checks;
    private static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
    private static void field(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    private static DailyMessageTasksManager restart()throws Exception{
        Field f=DailyMessageTasksManager.class.getDeclaredField("instances");f.setAccessible(true);
        ((DailyMessageTasksManager[])f.get(null))[0]=null;
        return DailyMessageTasksManager.getInstance(0);
    }
    private static void makeDue(DailyMessageTasksManager m)throws Exception{
        Field f=m.getClass().getDeclaredField("tasks");f.setAccessible(true);
        ArrayList<DailyMessageTasksManager.Task> tasks=(ArrayList<DailyMessageTasksManager.Task>)f.get(m);
        for(DailyMessageTasksManager.Task t:tasks)t.randomDueAt=System.currentTimeMillis()-1000;
        field(m,"previousTime",System.currentTimeMillis()-1000);
        field(m,"previousElapsed",android.os.SystemClock.elapsedRealtime()-1000);
        field(m,"previousZone",TimeZone.getDefault().getID());
    }
    public static void main(String[] args)throws Exception{
        DailyMessageTasksManager m=DailyMessageTasksManager.getInstance(0);
        DailyMessageTasksManager.Task draft=new DailyMessageTasksManager.Task();
        draft.dialogId=123;draft.text="/checkin";draft.automatic=true;
        check(draft.mode==DailyMessageTasksManager.RANDOM_ONLINE,"new task defaults to online random");
        check(m.save(draft),"valid task saved");
        m.tick();long due=m.getTasks().get(0).randomDueAt;
        check(due>System.currentTimeMillis()&&due<=System.currentTimeMillis()+90000,"online schedules random delay without immediate send");
        check(SendMessagesHelper.count==0,"planning does not send");
        m=restart();check(m.getTasks().get(0).randomDueAt==due,"random due persisted across restart");
        m.tick();check(m.getTasks().get(0).randomDueAt==due,"restart retains unexpired random due");
        makeDue(m);ApplicationLoader.mainInterfacePaused=true;m.tick();check(SendMessagesHelper.count==0,"background cannot send");
        ApplicationLoader.mainInterfacePaused=false;m.tick();check(m.getTasks().get(0).randomDueAt>System.currentTimeMillis(),"return online rerandomizes missed due");
        makeDue(m);ConnectionsManager.state=0;m.tick();check(SendMessagesHelper.count==0,"offline cannot send");
        ConnectionsManager.state=1;m.tick();check(SendMessagesHelper.count==0,"reconnect does not catch up immediately");
        makeDue(m);m.tick();check(SendMessagesHelper.count==1,"continuous online at due queues once");
        m.tick();check(SendMessagesHelper.count==1,"next tick deduplicated");
        m=restart();makeDue(m);m.tick();check(SendMessagesHelper.count==1,"restart cannot duplicate today");
        DailyMessageTasksManager.Task edited=m.getTasks().get(0);edited.text="/checkin2";m.save(edited);makeDue(m);m.tick();
        check(SendMessagesHelper.count==1,"editing does not erase today record");
        check(m.triggerNow(draft.id)==DailyMessageTasksManager.QUEUED&&SendMessagesHelper.count==2,"manual resend remains explicit");
        check(m.remove(draft.id),"task removal persisted");m=restart();check(m.getTasks().isEmpty(),"removed task stays removed after restart");
        check(m.save(draft),"resave fixture");m.tick();makeDue(m);Context.failCommit=true;m.tick();
        check(SendMessagesHelper.count==2&&m.getTasks().get(0).lastDay.isEmpty(),"failed durable commit cannot enqueue and restores dedup record");
        Context.failCommit=false;makeDue(m);m.tick();check(SendMessagesHelper.count==3,"successful retry after storage recovery queues once");
        m.cleanup();check(m.getTasks().isEmpty(),"logout cleanup removes tasks");
        m.save(draft);UserConfig.getInstance(0).owner=99;check(m.getTasks().isEmpty(),"new account owner cannot inherit previous tasks");
        UserConfig.getInstance(0).owner=42;m=restart();check(m.getTasks().size()==1,"original owner persisted tasks remain isolated");
        String persisted=ApplicationLoader.applicationContext.getSharedPreferences("extension_daily_tasks_0",0).getString("tasks","");
        persisted=persisted.replaceAll("\"mode\":0,?","").replace(",}","}");
        ApplicationLoader.applicationContext.getSharedPreferences("extension_daily_tasks_0",0).edit().putString("tasks",persisted).commit();
        m=restart();check(m.getTasks().get(0).mode==DailyMessageTasksManager.RANDOM_ONLINE,"legacy tasks migrate to requested random mode");
        DailyMessageTasksManager.Task invalid=draft.copy();invalid.randomWindowStart=600;invalid.randomWindowEnd=100;
        check(!m.save(invalid),"invalid overnight range rejected");
        System.out.println("DailyMessageTasksManagerTest: "+checks+" checks passed");
    }
}
