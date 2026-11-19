package org.telegram.messenger;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.SystemClock;
import android.widget.TextView;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.MangoUserBroadcastActivity;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.EditTextBoldCursor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Exercises production classes; fixture assertions describe boundaries, not source patterns. */
public final class UserBroadcastRegression {
    static int checks;
    static void check(boolean pass, String description) {
        checks++;
        if (!pass) throw new AssertionError("#" + checks + " " + description);
    }
    static Object field(Object value, String name) throws Exception {
        Field f = value.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(value);
    }
    static void dropInstances() throws Exception {
        Field f = MangoUserBroadcastManager.class.getDeclaredField("instances"); f.setAccessible(true);
        Arrays.fill((Object[]) f.get(null), null);
    }
    static void reset() throws Exception {
        ApplicationLoader.applicationContext = new Context();
        ApplicationLoader.initCalls = 0;
        dropInstances();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            UserConfig.getInstance(a).owner = a == 0 ? 100 : 0;
            UserConfig.getInstance(a).active = true; UserConfig.getInstance(a).premium = false;
            MessagesController m = MessagesController.getInstance(a);
            m.users.clear(); m.paid.clear(); m.contactPaid.clear(); m.premiumBlocked.clear(); m.blockePeers.clear(); m.maxMessageLength = 4096;
            MessagesStorage.getInstance(a).disk.clear();
        }
        ConnectionsManager.reset(); DispatchQueue.pending.clear(); AndroidUtilities.delayed.clear();
        DailyMessageTasksManager.connected = true; SendMessagesHelper.sent.clear(); SendMessagesHelper.fail = false;
        SystemClock.now = 1000000;
    }
    static TLRPC.User user(long id) { return user(0, id); }
    static TLRPC.User user(int account, long id) {
        TLRPC.User u = new TLRPC.TL_user(); u.id = id;
        MessagesController.getInstance(account).putUser(u, false);
        MessagesStorage.getInstance(account).disk.put(id, u); return u;
    }
    static MangoUserBroadcastManager manager() { return MangoUserBroadcastManager.getInstance(0); }
    static void tick(MangoUserBroadcastManager m, long time) { SystemClock.now = time; m.tick(time); }
    static void managerBehavior() throws Exception {
        reset();
        MessagesController c = MessagesController.getInstance(0);
        for (long id : new long[]{100,1,2,3,4,5,6,7,777000,1271266957L}) user(id);
        c.getUser(3).bot = true; c.getUser(4).deleted = true; c.getUser(5).send_paid_messages_stars = 1;
        c.blockePeers.put(6, 1); c.paid.put(7L, 1L); user(8); c.contactPaid.put(8L, 1L);
        for (long id : new long[]{100,3,4,5,6,7,8,777000,1271266957L,-50,0,0x4000000000000001L,999})
            check(!MangoUserBroadcastManager.canSend(0,id), "ineligible id excluded: " + id);
        check(MangoUserBroadcastManager.canSend(0,1), "ordinary user eligible");
        user(9).contact_require_premium=true; user(10); c.premiumBlocked.add(10L);
        check(!MangoUserBroadcastManager.canSend(0,9), "premium-required user flag excludes non-premium sender");
        check(!MangoUserBroadcastManager.canSend(0,10), "premium-required fetched contact info excludes non-premium sender");
        UserConfig.getInstance(0).premium=true;
        check(MangoUserBroadcastManager.canSend(0,9)&&MangoUserBroadcastManager.canSend(0,10), "premium sender can send when no Stars charged");
        c.paid.put(9L,1L); check(!MangoUserBroadcastManager.canSend(0,9), "premium subscription cannot bypass Stars exclusion");
        UserConfig.getInstance(0).premium=false;
        MangoUserBroadcastManager m = manager();
        check(!m.start("a", null), "null confirmed snapshot rejected");
        check(!m.start(" ", Arrays.asList(1L)), "blank rejected");
        check(!m.start("a".repeat(4097), Arrays.asList(1L)), "Telegram message limit enforced");
        check(m.start("  hello  ", Arrays.asList(1L,1L,null,3L,4L,5L,6L,7L,8L,9L,10L,100L,-1L,2L)), "start accepts eligible snapshot");
        check(m.total()==2 && m.processed()==0 && m.message().equals("hello"), "deduplicated and trimmed snapshot");
        check(SendMessagesHelper.sent.isEmpty(), "start itself never enqueues");
        check(!m.start("replace",Arrays.asList(1L)), "running snapshot cannot be overwritten");
        long now = SystemClock.now;
        tick(m,now); check(SendMessagesHelper.sent.size()==1 && m.processed()==1, "first tick submits one");
        check(!SendMessagesHelper.sent.get(0).notify, "individual messages silent");
        tick(m,now); tick(m,now+2999); check(SendMessagesHelper.sent.size()==1, "no send before 3000 ms");
        tick(m,now+3000); check(SendMessagesHelper.sent.size()==2 && m.state()==MangoUserBroadcastManager.DONE, "next at exactly 3000 ms");
        check(m.message().isEmpty() && !m.hasPending(), "completion clears message and pending");
        tick(m,now+9000); check(SendMessagesHelper.sent.size()==2, "completed task never repeats");

        reset(); user(1); user(2); user(3); m=manager(); m.start("offline",Arrays.asList(1L,2L,3L)); now=SystemClock.now;
        DailyMessageTasksManager.connected=false; tick(m,now+60000);
        check(SendMessagesHelper.sent.isEmpty()&&m.processed()==0, "offline leaves cursor unchanged");
        DailyMessageTasksManager.connected=true; tick(m,now+60000); tick(m,now+60000);
        check(SendMessagesHelper.sent.size()==1, "reconnection never catches up in a burst");
        tick(m,now+62999); check(SendMessagesHelper.sent.size()==1, "interval anchored to actual reconnect send");
        m.pause(); tick(m,now+90000); check(m.state()==MangoUserBroadcastManager.PAUSED&&SendMessagesHelper.sent.size()==1, "pause prevents submission");
        check(m.resume(), "manual resume accepted");
        tick(m,now+92999); check(SendMessagesHelper.sent.size()==1, "resume waits a fresh 3 seconds");
        tick(m,now+93000); check(SendMessagesHelper.sent.size()==2, "resume preserves next cursor");
        m.cancel(); tick(m,now+99000); check(m.state()==MangoUserBroadcastManager.CANCELLED&&SendMessagesHelper.sent.size()==2, "cancel stops remaining target");
        check(!m.resume()&&m.message().isEmpty(), "cancel cannot resume and removes content");

        reset(); user(1); user(2); m=manager(); ApplicationLoader.applicationContext.failCommit=true;
        check(!m.start("disk",Arrays.asList(1L,2L))&&!m.isRunning(), "failed initial persistence never starts");
        tick(m,SystemClock.now+10000); check(SendMessagesHelper.sent.isEmpty(), "failed persistence cannot leak send");
        ApplicationLoader.applicationContext.failCommit=false; check(m.start("disk",Arrays.asList(1L,2L)), "can start after disk recovers");
        ApplicationLoader.applicationContext.failCommit=true; tick(m,SystemClock.now);
        check(m.state()==MangoUserBroadcastManager.PAUSED&&m.processed()==0&&SendMessagesHelper.sent.isEmpty(), "durable claim failure pauses without enqueue or cursor change");
        check(!m.resume()&&m.state()==MangoUserBroadcastManager.PAUSED, "resume commit failure stays paused");
        ApplicationLoader.applicationContext.failCommit=false; check(m.resume(), "resume succeeds after disk recovery");
        tick(m,SystemClock.now+3000); check(SendMessagesHelper.sent.size()==1&&m.processed()==1, "unclaimed target retained");
        dropInstances(); m=manager(); check(m.state()==MangoUserBroadcastManager.PAUSED&&m.processed()==1, "process restart restores running snapshot as paused");
        tick(m,SystemClock.now+30000); check(SendMessagesHelper.sent.size()==1, "restart does not automatically resume");
        check(m.resume(), "restart requires explicit resume"); tick(m,SystemClock.now+3000);
        check(SendMessagesHelper.sent.size()==2&&SendMessagesHelper.sent.get(1).id==2, "durable cursor prevents duplicate first recipient");

        reset(); user(1); user(2); m=manager(); m.start("bound",Arrays.asList(1L,2L));
        UserConfig.getInstance(0).owner=200; tick(m,SystemClock.now);
        check(m.state()==MangoUserBroadcastManager.IDLE&&m.total()==0&&SendMessagesHelper.sent.isEmpty(), "new owner cannot reuse old snapshot");
        UserConfig.getInstance(0).owner=100; check(manager().state()==MangoUserBroadcastManager.IDLE, "switching back does not recover cleared foreign state");
        m=manager(); m.start("inactive",Arrays.asList(1L)); UserConfig.getInstance(0).active=false; tick(m,SystemClock.now);
        check(SendMessagesHelper.sent.isEmpty(), "inactive account cannot submit");
        UserConfig.getInstance(0).active=true; MangoUserBroadcastManager.clearForLogout(0); UserConfig.getInstance(0).owner=0;
        UserConfig.getInstance(0).owner=100; check(manager().state()==MangoUserBroadcastManager.IDLE&&!manager().hasPending(), "same-owner logout/login cannot restart previous task");

        reset(); user(1); user(2); m=manager(); m.start("late exclusion",Arrays.asList(1L,2L)); c=MessagesController.getInstance(0); c.blockePeers.put(1,1);
        tick(m,SystemClock.now); check(m.skipped()==1&&SendMessagesHelper.sent.isEmpty(), "blocked-after-confirm user rechecked at send time");
        c.getUser(2).bot=true; tick(m,SystemClock.now+3000); check(m.skipped()==2&&m.state()==MangoUserBroadcastManager.DONE, "bot conversion skipped and completes");

        reset(); user(1); user(2); m=manager(); m.start("read identity",Arrays.asList(1L,2L)); c=MessagesController.getInstance(0); c.users.remove(1L);
        tick(m,SystemClock.now); tick(m,SystemClock.now);
        check(DispatchQueue.pending.size()==1&&SendMessagesHelper.sent.isEmpty()&&m.processed()==0, "missing in-memory user read asynchronously once");
        DispatchQueue.drain(); tick(m,SystemClock.now);
        check(SendMessagesHelper.sent.size()==1&&SendMessagesHelper.sent.get(0).id==1, "persisted user identity restored then rechecked");
        c.users.remove(2L); MessagesStorage.getInstance(0).disk.remove(2L); tick(m,SystemClock.now+3000); DispatchQueue.drain(); tick(m,SystemClock.now);
        check(m.skipped()==1&&m.state()==MangoUserBroadcastManager.DONE, "missing persisted user skipped rather than hanging");

        reset(); user(1); user(2); m=manager(); m.start("cancel load",Arrays.asList(1L)); MessagesController.getInstance(0).users.remove(1L);
        tick(m,SystemClock.now); m.cancel(); check(m.start("new generation",Arrays.asList(2L)), "new task allowed after cancel");
        DispatchQueue.drain(); check(MessagesController.getInstance(0).getUser(1)==null, "stale asynchronous storage response ignored after cancel");
        tick(m,SystemClock.now); check(SendMessagesHelper.sent.size()==1&&SendMessagesHelper.sent.get(0).id==2, "stale load cannot block replacement task");
        reset(); user(1); m=manager(); m.start("old owner",Arrays.asList(1L)); MessagesController.getInstance(0).users.remove(1L); tick(m,SystemClock.now);
        UserConfig.getInstance(0).owner=200; DispatchQueue.drain(); check(MessagesController.getInstance(0).getUser(1)==null, "stale storage user ignored after owner switch");

        reset(); user(1); user(2); m=manager(); m.start("transport",Arrays.asList(1L,2L)); SendMessagesHelper.fail=true; tick(m,SystemClock.now);
        check(m.processed()==1&&m.skipped()==1&&SendMessagesHelper.sent.isEmpty(), "enqueue exception recorded once");
        SendMessagesHelper.fail=false; tick(m,SystemClock.now+3000);
        check(SendMessagesHelper.sent.size()==1&&SendMessagesHelper.sent.get(0).id==2, "failed claim not retried or duplicated");
    }
    static class Result implements MangoBroadcastTargets.Callback {
        int calls; ArrayList<Long> users; String error;
        @Override public void complete(ArrayList<Long> u,String e){calls++;users=u;error=e;}
    }
    static TLRPC.contacts_Blocked blocked(boolean slice,int total,long... ids) {
        TLRPC.contacts_Blocked page=slice?new TLRPC.TL_contacts_blockedSlice():new TLRPC.TL_contacts_blocked();page.count=total;
        for(long id:ids){TLRPC.TL_peerBlocked p=new TLRPC.TL_peerBlocked();p.peer_id.user_id=id;page.blocked.add(p);}return page;
    }
    static void emptyBlocked() { ConnectionsManager.respond(blocked(false,0)); }
    static TLRPC.messages_Dialogs dialogs(boolean slice,long... ids) {
        TLRPC.messages_Dialogs p=slice?new TLRPC.TL_messages_dialogsSlice():new TLRPC.TL_messages_dialogs();
        for(long id:ids){TLRPC.Dialog d=new TLRPC.TL_dialog();d.id=id;d.top_message=(int)Math.abs(id)+100;p.dialogs.add(d);
            TLRPC.Message msg=new TLRPC.Message();msg.id=d.top_message;msg.dialog=id;msg.date=10000+(int)Math.abs(id);p.messages.add(msg);
            if(id>0){TLRPC.User u=new TLRPC.TL_user();u.id=id;p.users.add(u);}}
        return p;
    }
    static TLRPC.TL_messages_getDialogs dialogRequest() { return (TLRPC.TL_messages_getDialogs)ConnectionsManager.first().value; }
    static void targetsBehavior() throws Exception {
        reset(); Result out=new Result(); MangoBroadcastTargets loader=new MangoBroadcastTargets(0,out); loader.load(); loader.load();
        check(ConnectionsManager.pending.size()==1&&ConnectionsManager.first().value instanceof TLRPC.TL_contacts_getBlocked, "recipient collection starts with one full blocked read");
        TLRPC.TL_contacts_getBlocked b=(TLRPC.TL_contacts_getBlocked)ConnectionsManager.first().value;
        check(b.offset==0&&b.limit==100&&!b.my_stories_from, "first blocked page parameters");
        long[] hundred=new long[100];for(int i=0;i<100;i++)hundred[i]=1000+i;
        MessagesController.getInstance(0).blockePeers.put(77,1);
        ConnectionsManager.respond(blocked(true,101,hundred));
        check(out.calls==0&&ConnectionsManager.first().value instanceof TLRPC.TL_contacts_getBlocked, "blocked slice continues before any dialogs");
        b=(TLRPC.TL_contacts_getBlocked)ConnectionsManager.first().value;
        check(b.offset==100&&MessagesController.getInstance(0).blockePeers.indexOfKey(1000)<0, "blocked merge waits for complete list");
        ConnectionsManager.respond(blocked(true,101,42));
        check(MessagesController.getInstance(0).blockePeers.indexOfKey(42)>=0&&MessagesController.getInstance(0).blockePeers.indexOfKey(77)>=0, "second-page blocked exclusion merged without clearing concurrent local block");
        TLRPC.TL_messages_getDialogs req=dialogRequest();check(req.folder_id==0&&!req.exclude_pinned&&req.limit==100&&req.flags==2, "ordinary folder starts including pins");
        TLRPC.messages_Dialogs p=dialogs(true,3);p.dialogs.get(0).pinned=true; ConnectionsManager.respond(p);
        req=dialogRequest();check(req.folder_id==0&&req.exclude_pinned&&req.offset_id==0, "pinned-only slice requests non-pinned first page");
        p=dialogs(true,20,42,77,50,51,52,100,-99,53,21);p.users.stream().filter(u->u.id==50).findFirst().get().bot=true;p.users.stream().filter(u->u.id==51).findFirst().get().deleted=true;p.users.stream().filter(u->u.id==52).findFirst().get().send_paid_messages_stars=1;p.dialogs.get(8).top_message=0;
        ConnectionsManager.respond(p);req=dialogRequest();
        check(req.folder_id==0&&req.exclude_pinned&&req.offset_id==121&&req.offset_date==10021&&req.offset_peer.id==21, "normal page advances exact top-message/date/peer cursor");
        ConnectionsManager.respond(dialogs(false,22,20));req=dialogRequest();check(req.folder_id==1&&req.offset_id==0&&!req.exclude_pinned, "normal completion starts archived folder independently");
        ConnectionsManager.respond(dialogs(true,23));req=dialogRequest();check(req.folder_id==1&&req.offset_id==123, "archive pagination preserved");
        ConnectionsManager.respond(dialogs(false,24,20));
        check(out.calls==1&&out.error==null&&out.users.equals(Arrays.asList(3L,20L,21L,22L,23L,24L)), "complete normal/archive snapshot deduplicates and filters all exclusions");
        check(ConnectionsManager.pending.isEmpty()&&SendMessagesHelper.sent.isEmpty(), "enumeration never sends messages");
        loader.load();check(ConnectionsManager.pending.isEmpty()&&out.calls==1, "finished loader cannot restart");

        reset();out=new Result();loader=new MangoBroadcastTargets(0,out);loader.load();emptyBlocked();ConnectionsManager.respond(dialogs(true,1));ConnectionsManager.respond(null,"NETWORK_FAIL");
        check(out.calls==1&&out.users.isEmpty()&&"NETWORK_FAIL".equals(out.error), "dialog failure discards partial snapshot");
        reset();out=new Result();loader=new MangoBroadcastTargets(0,out);loader.load();emptyBlocked();ConnectionsManager.respond(dialogs(true,1));ConnectionsManager.respond(dialogs(true,1));
        check(out.calls==1&&out.users.isEmpty()&&"PAGINATION_STALLED".equals(out.error), "repeated dialog cursor stops safely without partial recipients");
        reset();out=new Result();loader=new MangoBroadcastTargets(0,out);loader.load();emptyBlocked();p=dialogs(true,1);p.messages.clear();ConnectionsManager.respond(p);
        check(out.calls==1&&"PAGINATION_STALLED".equals(out.error)&&out.users.isEmpty(), "missing pagination date is failure not success");
        reset();out=new Result();loader=new MangoBroadcastTargets(0,out);loader.load();ConnectionsManager.respond(null,"BLOCKED_ERROR");
        check(out.calls==1&&out.users.isEmpty()&&ConnectionsManager.pending.isEmpty()&&"BLOCKED_ERROR".equals(out.error), "blocked-list failure never starts dialogs");
        reset();out=new Result();loader=new MangoBroadcastTargets(0,out);loader.load();ConnectionsManager.respond(blocked(true,2,1));ConnectionsManager.respond(blocked(true,3,1));
        check(out.calls==1&&out.users.isEmpty()&&"BLOCKED_PAGINATION_STALLED".equals(out.error), "repeated blocked page rejected");
        reset();out=new Result();loader=new MangoBroadcastTargets(0,out);loader.load();ConnectionsManager.respond(blocked(true,1));
        check(out.calls==1&&"BLOCKED_PAGINATION_STALLED".equals(out.error), "empty unfinished blocked slice rejected");
        reset();out=new Result();loader=new MangoBroadcastTargets(0,out);loader.loadBlockedOnly();emptyBlocked();
        check(out.calls==1&&out.error==null&&ConnectionsManager.pending.isEmpty(), "resume preflight refreshes blocks without scanning new targets");
        reset();out=new Result();loader=new MangoBroadcastTargets(0,out);loader.load();ConnectionsManager.Request pending=ConnectionsManager.first();loader.cancel();
        check(pending.cancelled, "cancel propagates to network request");emptyBlocked();check(out.calls==0&&ConnectionsManager.pending.isEmpty(), "late cancelled callback cannot deliver results or paginate");
        reset();out=new Result();loader=new MangoBroadcastTargets(0,out);loader.load();UserConfig.getInstance(0).owner=200;emptyBlocked();
        check(out.calls==0&&ConnectionsManager.pending.isEmpty(), "owner change suppresses late network response");
        reset();out=new Result();loader=new MangoBroadcastTargets(0,out);loader.load();UserConfig.getInstance(0).active=false;emptyBlocked();
        check(out.calls==0&&ConnectionsManager.pending.isEmpty(), "inactive account suppresses late response");
    }
    static void serviceBehavior() throws Exception {
        reset();user(1);user(2);manager().start("background",Arrays.asList(1L,2L));
        MangoUserBroadcastService s=new MangoUserBroadcastService();s.onCreate();
        check(ApplicationLoader.initCalls==1&&s.notifications.channel!=null, "service initializes application and notification channel");
        check(s.notifications.channel.importance==2&&!s.notifications.channel.vibration&&!s.notifications.channel.badge&&s.notifications.channel.sound==null, "low-importance silent channel without badge");
        check(s.onStartCommand(null,0,1)==android.app.Service.START_NOT_STICKY, "OS cannot sticky-restart sending service");
        check(s.foregroundCalls==1&&s.latest.silent&&s.latest.ongoing&&s.latest.onlyOnce&&s.latest.actions==1, "foreground progress notification is immediate, silent and cancellable");
        Handler h=(Handler)field(s,"handler");check(h.queue.size()==1, "service schedules one runner");
        s.onStartCommand(null,0,2);check(h.queue.size()==1, "duplicate start replaces rather than multiplies runner");
        h.runNext();check(SendMessagesHelper.sent.size()==1&&h.queue.size()==1&&h.delays.get(0)==500, "background runner sends once and polls at 500 ms");
        check(s.power.last.isHeld()&&s.power.last.timeout==600000, "bounded partial wakelock held while active");
        SystemClock.now+=2999;h.runNext();check(SendMessagesHelper.sent.size()==1, "service ticks respect manager 3-second spacing");
        s.onDestroy();check(manager().state()==MangoUserBroadcastManager.PAUSED&&h.queue.isEmpty(), "service destruction pauses durable task and removes callbacks");
        check(!s.power.last.isHeld()&&s.power.last.releases==1&&s.foregroundStops==1, "service destruction releases foreground and wakelock");
        check(manager().resume(), "explicit manual resume after service stop");
        s=new MangoUserBroadcastService();s.onCreate();s.onStartCommand(null,0,3);h=(Handler)field(s,"handler");SystemClock.now+=3000;h.runNext();
        check(SendMessagesHelper.sent.size()==2&&s.stopped==1&&h.queue.isEmpty(), "completion stops service without another scheduled runner");s.onDestroy();
        reset();user(1);manager().start("cancel",Arrays.asList(1L));s=new MangoUserBroadcastService();s.onCreate();
        s.onStartCommand(new Intent(s,MangoUserBroadcastService.class).setAction("mango.broadcast.cancel"),0,1);
        check(manager().state()==MangoUserBroadcastManager.CANCELLED&&s.stopped==1&&SendMessagesHelper.sent.isEmpty(), "notification cancel stops before any submission");s.onDestroy();
        reset();user(1);manager().start("timeout",Arrays.asList(1L));s=new MangoUserBroadcastService();s.onCreate();s.onStartCommand(null,0,1);s.onTimeout(1,1);s.onDestroy();
        check(manager().state()==MangoUserBroadcastManager.PAUSED&&s.stopped==1, "Android 15 timeout pauses rather than silently restarts task");
        reset();android.os.Build.VERSION.SDK_INT=35;check(MangoUserBroadcastService.start()&&ApplicationLoader.applicationContext.foregroundStarts==1, "modern Android uses startForegroundService");
        android.os.Build.VERSION.SDK_INT=25;check(MangoUserBroadcastService.start()&&ApplicationLoader.applicationContext.serviceStarts==1, "older Android uses startService");
        android.os.Build.VERSION.SDK_INT=35;ApplicationLoader.applicationContext.failStart=true;check(!MangoUserBroadcastService.start(), "service launch denial returned to confirmation UI");
        reset();UserConfig.getInstance(1).owner=200;user(1);user(1,2);manager().start("account0",Arrays.asList(1L));MangoUserBroadcastManager.getInstance(1).start("account1",Arrays.asList(2L));
        s=new MangoUserBroadcastService();s.onCreate();s.onStartCommand(null,0,1);h=(Handler)field(s,"handler");h.runNext();
        check(SendMessagesHelper.sent.size()==2&&SendMessagesHelper.sent.get(0).account==0&&SendMessagesHelper.sent.get(1).account==1, "service executes owner-bound tasks across selected-account changes");s.onDestroy();
    }
    static MangoUserBroadcastActivity activity() throws Exception {
        MangoUserBroadcastActivity a=new MangoUserBroadcastActivity();a.onFragmentCreate();a.createView(new Context());a.onResume();return a;
    }
    static void edit(MangoUserBroadcastActivity a,String s)throws Exception{((EditTextBoldCursor)field(a,"editor")).setText(s);}
    static void clickSend(MangoUserBroadcastActivity a)throws Exception{((TextView)field(a,"send")).performClick();}
    static void finishCollection(long id){emptyBlocked();ConnectionsManager.respond(dialogs(false,id));ConnectionsManager.respond(dialogs(false));}
    static void uiBehavior() throws Exception {
        reset();user(1);MangoUserBroadcastActivity a=activity();
        check(ConnectionsManager.pending.isEmpty()&&SendMessagesHelper.sent.isEmpty()&&manager().state()==MangoUserBroadcastManager.IDLE, "opening and resuming settings never enumerates or sends");
        edit(a,"confirmed text");clickSend(a);check(ConnectionsManager.first().value instanceof TLRPC.TL_contacts_getBlocked&&!manager().isRunning(), "review begins read-only preflight");
        finishCollection(1);check(a.lastDialog.positive!=null&&a.lastDialog.message.contains("confirmed text"), "complete enumeration presents count and message preview");
        check(!manager().isRunning()&&SendMessagesHelper.sent.isEmpty(), "no enqueue before confirmation");
        a.lastDialog.clickPositive();check(manager().isRunning()&&ApplicationLoader.applicationContext.foregroundStarts==1&&SendMessagesHelper.sent.isEmpty(), "positive confirmation persists task and launches separate service only");
        a.onPause();a.onFragmentDestroy();check(manager().isRunning(), "closing settings does not stop running background task");

        reset();user(1);a=activity();edit(a,"cancel review");clickSend(a);finishCollection(1); // The production negative button has no action and cannot start a task.
        check(!manager().isRunning()&&SendMessagesHelper.sent.isEmpty(), "dismissing confirmation leaves task idle");
        AlertDialog saved=a.lastDialog;a.onPause();saved.clickPositive();check(!manager().isRunning(), "paused fragment cannot confirm stale dialog");a.onFragmentDestroy();
        reset();user(1);a=activity();edit(a,"owner preview");clickSend(a);finishCollection(1);saved=a.lastDialog;UserConfig.getInstance(0).owner=200;saved.clickPositive();
        check(!manager().isRunning()&&ApplicationLoader.applicationContext.foregroundStarts==0, "owner swap cannot authorize previous dialog");a.onFragmentDestroy();
        reset();user(1);a=activity();edit(a,"cancel loader");clickSend(a);ConnectionsManager.Request req=ConnectionsManager.first();a.lastDialog.cancel();
        check(req.cancelled&&field(a,"loader")==null, "loading spinner cancel cancels collection");emptyBlocked();check(!manager().isRunning(), "cancelled collection cannot present/send");a.onFragmentDestroy();
        reset();user(1);a=activity();edit(a,"pause loader");clickSend(a);req=ConnectionsManager.first();a.onPause();check(req.cancelled, "leaving during enumeration cancels request");emptyBlocked();check(!manager().isRunning(), "late callback after pause cannot send");a.onFragmentDestroy();
        reset();user(1);a=activity();edit(a,"service denied");clickSend(a);finishCollection(1);ApplicationLoader.applicationContext.failStart=true;a.lastDialog.clickPositive();
        check(manager().state()==MangoUserBroadcastManager.PAUSED&&SendMessagesHelper.sent.isEmpty(), "foreground start failure pauses persisted confirmed task");a.onFragmentDestroy();
        reset();user(1);user(2);manager().start("resume preview",Arrays.asList(1L,2L));manager().pause();a=activity();clickSend(a);
        check(ConnectionsManager.first().value instanceof TLRPC.TL_contacts_getBlocked&&manager().state()==MangoUserBroadcastManager.PAUSED, "resume refreshes blocked exclusions without sending");
        ConnectionsManager.respond(blocked(false,1,1));check(ConnectionsManager.pending.isEmpty()&&a.lastDialog.message.contains("resume preview")&&!manager().isRunning(), "resume blocked-only preflight then confirmation, no target rescan");
        a.lastDialog.clickPositive();check(manager().isRunning(), "resume requires second explicit confirmation");tick(manager(),SystemClock.now+3000);
        check(manager().skipped()==1&&SendMessagesHelper.sent.isEmpty(), "resume newly blocked recipient excluded");a.onFragmentDestroy();
        reset();user(1);manager().start("resume failure",Arrays.asList(1L));manager().pause();a=activity();clickSend(a);ConnectionsManager.respond(null,"ERROR");
        check(manager().state()==MangoUserBroadcastManager.PAUSED&&ApplicationLoader.applicationContext.foregroundStarts==0, "failed resume block preflight keeps task paused");a.onFragmentDestroy();
        reset();a=activity();edit(a,"empty recipients");clickSend(a);emptyBlocked();ConnectionsManager.respond(dialogs(false));ConnectionsManager.respond(dialogs(false));
        check(!manager().isRunning()&&a.lastDialog.positive==null, "zero recipients does not offer send confirmation");a.onFragmentDestroy();
    }
    public static void main(String[] args)throws Exception {
        managerBehavior();targetsBehavior();serviceBehavior();uiBehavior();
        System.out.println("User broadcast: "+checks+" production behavior assertions passed (manager, paginated loader, service lifecycle, confirmation UI).");
    }
}
