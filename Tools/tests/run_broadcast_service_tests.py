#!/usr/bin/env python3
"""Compile the actual broadcast Service against desktop Android lifecycle doubles."""
from pathlib import Path
import os
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "TMessagesProj/src/main/java"
JAVA = Path(os.environ.get("JAVA_HOME", "/Applications/Android Studio.app/Contents/jbr/Contents/Home")) / "bin"

STUBS = {
    "android/content/Context.java": r'''
package android.content;
public class Context {
 public static final String POWER_SERVICE="power", NOTIFICATION_SERVICE="notification";
 public static int foregroundStarts, ordinaryStarts; public static boolean failStart; public static Intent lastStart;
 public Object getSystemService(String key){return POWER_SERVICE.equals(key)?android.os.PowerManager.instance:android.app.NotificationManager.instance;}
 public Object startForegroundService(Intent intent){if(failStart)throw new IllegalStateException("denied"); foregroundStarts++;lastStart=intent;return null;}
 public Object startService(Intent intent){if(failStart)throw new IllegalStateException("denied"); ordinaryStarts++;lastStart=intent;return null;}
}''',
    "android/content/Intent.java": r'''
package android.content;
public class Intent {
 public Class<?> target; public String action;
 public Intent(Context context,Class<?> target){this.target=target;}
 public Intent setAction(String action){this.action=action;return this;}
 public String getAction(){return action;}
}''',
    "android/app/Service.java": r'''
package android.app;
public class Service extends android.content.Context {
 public static final int START_NOT_STICKY=2; public int stops, foregrounds, foregroundStops, destroys;
 public boolean removedNotification; public Notification lastNotification;
 public void onCreate(){} public int onStartCommand(android.content.Intent i,int flags,int id){return 0;}
 public void onTimeout(int id,int type){} public void onDestroy(){destroys++;}
 public android.os.IBinder onBind(android.content.Intent intent){return null;}
 public Object getMainLooper(){return this;} public void stopSelf(){stops++;}
 public void startForeground(int id,Notification notification){foregrounds++;lastNotification=notification;}
 public void stopForeground(boolean remove){foregroundStops++;removedNotification=remove;}
}''',
    "android/app/Notification.java": r'''
package android.app;
public class Notification {
 public int max,progress,icon; public boolean indeterminate,ongoing,silent,once;
 public String title,text; public PendingIntent content,cancel;
}''',
    "android/app/NotificationChannel.java": r'''
package android.app;
public class NotificationChannel {
 public String id,title; public int importance; public boolean sound=true,vibration=true,badge=true;
 public NotificationChannel(String id,String title,int importance){this.id=id;this.title=title;this.importance=importance;}
 public void setSound(Object a,Object b){sound=a!=null;} public void enableVibration(boolean value){vibration=value;}
 public void setShowBadge(boolean value){badge=value;}
}''',
    "android/app/NotificationManager.java": r'''
package android.app;
public class NotificationManager {
 public static final int IMPORTANCE_LOW=2; public static final NotificationManager instance=new NotificationManager();
 public NotificationChannel channel; public void createNotificationChannel(NotificationChannel channel){this.channel=channel;}
}''',
    "android/app/PendingIntent.java": r'''
package android.app;
public class PendingIntent {
 public static final int FLAG_UPDATE_CURRENT=1, FLAG_IMMUTABLE=2;
 public android.content.Intent intent; public String kind; public int flags;
 private static PendingIntent create(android.content.Intent intent,int flags,String kind){PendingIntent result=new PendingIntent();result.intent=intent;result.flags=flags;result.kind=kind;return result;}
 public static PendingIntent getActivity(android.content.Context c,int code,android.content.Intent i,int flags){return create(i,flags,"activity");}
 public static PendingIntent getService(android.content.Context c,int code,android.content.Intent i,int flags){return create(i,flags,"service");}
}''',
    "android/os/Build.java": r'''
package android.os;
public class Build {public static class VERSION {public static int SDK_INT=35;}}
''',
    "android/os/SystemClock.java": r'''
package android.os;
public class SystemClock {public static long now=10000; public static long elapsedRealtime(){return now;}}
''',
    "android/os/IBinder.java": "package android.os; public interface IBinder {}",
    "android/os/Handler.java": r'''
package android.os;
public class Handler {
 public static Handler last; public final java.util.ArrayList<Runnable> queue=new java.util.ArrayList<>();
 public long delay; public Handler(Object looper){last=this;}
 public void post(Runnable runnable){queue.add(runnable);}
 public void postDelayed(Runnable runnable,long ms){queue.add(runnable);delay=ms;}
 public void removeCallbacks(Runnable runnable){queue.removeIf(item->item==runnable);}
 public void runNext(){if(queue.isEmpty())throw new AssertionError("no scheduled tick");queue.remove(0).run();}
}''',
    "android/os/PowerManager.java": r'''
package android.os;
public class PowerManager {
 public static final int PARTIAL_WAKE_LOCK=1; public static final PowerManager instance=new PowerManager();
 public WakeLock last; public int type; public String tag;
 public WakeLock newWakeLock(int type,String tag){this.type=type;this.tag=tag;return last=new WakeLock();}
 public static class WakeLock {
  public int acquires,releases; public long expires; public boolean held;
  public boolean isHeld(){return held&&SystemClock.now<expires;}
  public void acquire(long timeout){acquires++;held=true;expires=SystemClock.now+timeout;}
  public void release(){releases++;held=false;}
 }
}''',
    "android/R.java": "package android; public class R {public static class drawable {public static final int stat_sys_upload=1;}}",
    "androidx/core/app/NotificationCompat.java": r'''
package androidx.core.app;
public class NotificationCompat {
 public static class Builder {
  private final android.app.Notification value=new android.app.Notification();
  public Builder(android.content.Context context,String channel){}
  public Builder setSmallIcon(int icon){value.icon=icon;return this;}
  public Builder setContentTitle(String title){value.title=title;return this;}
  public Builder setContentText(String text){value.text=text;return this;}
  public Builder setProgress(int max,int progress,boolean indeterminate){value.max=max;value.progress=progress;value.indeterminate=indeterminate;return this;}
  public Builder setContentIntent(android.app.PendingIntent intent){value.content=intent;return this;}
  public Builder setOngoing(boolean b){value.ongoing=b;return this;}
  public Builder setSilent(boolean b){value.silent=b;return this;}
  public Builder setOnlyAlertOnce(boolean b){value.once=b;return this;}
  public Builder addAction(int icon,String title,android.app.PendingIntent intent){value.cancel=intent;return this;}
  public android.app.Notification build(){return value;}
 }
}''',
    "org/telegram/ui/LaunchActivity.java": "package org.telegram.ui; public class LaunchActivity {}",
    "org/telegram/messenger/ApplicationLoader.java": r'''
package org.telegram.messenger;
public class ApplicationLoader {
 public static android.content.Context applicationContext=new android.content.Context(); public static int inits;
 public static void postInitApplication(){inits++;}
}''',
    "org/telegram/messenger/UserConfig.java": r'''
package org.telegram.messenger;
public class UserConfig {public static final int MAX_ACCOUNT_COUNT=4;public static int[] active={0,1,2};public static int[] getActivatedAccounts(){return active;}}
''',
    "org/telegram/messenger/MangoUserBroadcastManager.java": r'''
package org.telegram.messenger;
public class MangoUserBroadcastManager {
 public static final long INTERVAL_MS=3000; public static final MangoUserBroadcastManager[] instances={new MangoUserBroadcastManager(),new MangoUserBroadcastManager(),new MangoUserBroadcastManager(),new MangoUserBroadcastManager()};
 public static final java.util.ArrayList<String> events=new java.util.ArrayList<>();
 public boolean running,completeOnTick; public int total,processed,skipped,ticks,pauses,cancels; private int account;
 public static MangoUserBroadcastManager getInstance(int account){instances[account].account=account;return instances[account];}
 public boolean isRunning(){return running;} public int total(){return total;} public int processed(){return processed;} public int skipped(){return skipped;}
 public void tick(long now){ticks++;events.add("tick"+account);if(completeOnTick)running=false;}
 public void cancel(){cancels++;running=false;} public void pause(){pauses++;running=false;}
 public static void reset(){events.clear();for(MangoUserBroadcastManager m:instances){m.running=m.completeOnTick=false;m.total=m.processed=m.skipped=m.ticks=m.pauses=m.cancels=0;}}
}''',
    "org/telegram/tgnet/ConnectionsManager.java": r'''
package org.telegram.tgnet;
public class ConnectionsManager {
 public static final ConnectionsManager[] instances={new ConnectionsManager(),new ConnectionsManager(),new ConnectionsManager(),new ConnectionsManager()};
 public int wakes;private int account;
 public static ConnectionsManager getInstance(int account){instances[account].account=account;return instances[account];}
 public void resumeNetworkMaybe(){wakes++;org.telegram.messenger.MangoUserBroadcastManager.events.add("wake"+account);}
}''',
    "org/telegram/messenger/FileLog.java": "package org.telegram.messenger;public class FileLog {public static int errors;public static void e(Exception e){errors++;}}",
    "org/telegram/messenger/R.java": "package org.telegram.messenger;public class R {public static class string {public static final int MangoBroadcastTitle=1,MangoBroadcastProgress=2,Cancel=3;}}",
    "org/telegram/messenger/LocaleController.java": r'''
package org.telegram.messenger;
public class LocaleController {
 public static String getString(int id){return "string"+id;}
 public static String formatString(int id,Object...args){return java.util.Arrays.toString(args);}
}''',
}

HARNESS = r'''
package org.telegram.messenger;
import android.app.*;
import android.content.*;
import android.os.*;
import org.telegram.tgnet.ConnectionsManager;
public class BroadcastServiceRegression {
 static int assertions;
 static void check(boolean ok,String label){assertions++;if(!ok)throw new AssertionError(label);}
 static MangoUserBroadcastManager manager(int account){return MangoUserBroadcastManager.getInstance(account);}
 static int wakes(int account){return ConnectionsManager.getInstance(account).wakes;}
 static void step(long now){SystemClock.now=now;Handler.last.runNext();}
 static void reset(){MangoUserBroadcastManager.reset();for(ConnectionsManager cm:ConnectionsManager.instances)cm.wakes=0;UserConfig.active=new int[]{0,1,2};SystemClock.now=10000;}
 public static void main(String[] args){
  check(MangoUserBroadcastService.start()&&Context.foregroundStarts==1,"API 26+ foreground start");
  check(Context.lastStart.target==MangoUserBroadcastService.class,"explicit service target");
  Build.VERSION.SDK_INT=25;
  check(MangoUserBroadcastService.start()&&Context.ordinaryStarts==1,"legacy ordinary start");
  Context.failStart=true;
  check(!MangoUserBroadcastService.start()&&FileLog.errors==1,"start failure reported instead of crashing caller");
  Context.failStart=false;Build.VERSION.SDK_INT=35;

  reset();MangoUserBroadcastService service=new MangoUserBroadcastService();service.onCreate();
  check(ApplicationLoader.inits==1&&Handler.last!=null,"application and main handler initialized");
  NotificationChannel channel=NotificationManager.instance.channel;
  check(channel!=null&&channel.importance==NotificationManager.IMPORTANCE_LOW,"low importance channel");
  check(!channel.sound&&!channel.vibration&&!channel.badge,"channel is silent without badge");
  check(PowerManager.instance.type==PowerManager.PARTIAL_WAKE_LOCK&&"mango:broadcast".equals(PowerManager.instance.tag),"partial wake lock created");
  check(service.onBind(null)==null,"not a bound or exported control endpoint");
  check(service.onStartCommand(null,0,1)==Service.START_NOT_STICKY,"no silent process-death restart");
  check(service.foregrounds==1&&Handler.last.queue.size()==1,"immediate foreground promotion before tick");
  check(service.lastNotification.indeterminate,"initial foreground notification supplied for empty/cancelled jobs");
  service.onStartCommand(new Intent(service,MangoUserBroadcastService.class),0,2);
  check(Handler.last.queue.size()==1,"repeated starts do not create duplicate loops");

  manager(0).running=true;manager(0).total=10;manager(0).processed=2;manager(0).skipped=1;
  manager(2).running=true;manager(2).total=20;manager(2).processed=3;manager(2).skipped=1;
  step(10000);
  check(MangoUserBroadcastManager.events.toString().equals("[wake0, tick0, wake2, tick2]"),"partial network wake precedes every running account's first tick");
  check(wakes(1)==0&&manager(1).ticks==0,"paused account neither woken nor executed");
  check(PowerManager.instance.last.isHeld()&&PowerManager.instance.last.acquires==1,"active execution holds CPU awake");
  check(Handler.last.queue.size()==1&&Handler.last.delay==500,"single paced polling callback");
  Notification notification=service.lastNotification;
  check(notification.max==30&&notification.progress==5&&!notification.indeterminate,"aggregate processed progress");
  check("[3, 30, 2]".equals(notification.text),"submitted and skipped counts separated");
  check(notification.ongoing&&notification.silent&&notification.once,"ongoing silent notification");
  check("activity".equals(notification.content.kind)&&notification.content.intent.target==org.telegram.ui.LaunchActivity.class,"notification opens client");
  check("service".equals(notification.cancel.kind)&&"mango.broadcast.cancel".equals(notification.cancel.intent.action),"notification cancel targets service");
  check((notification.cancel.flags&PendingIntent.FLAG_IMMUTABLE)!=0,"control PendingIntent is immutable");
  step(10500);
  check(wakes(0)==1&&wakes(2)==1&&manager(0).ticks==2,"network wake throttled independently of 500 ms ticks");
  manager(1).running=true;step(11000);
  check(wakes(1)==1&&wakes(0)==1,"new account wakes immediately without waiting for another account's throttle");
  step(13000);
  check(wakes(0)==2&&wakes(2)==2&&wakes(1)==1,"each running account wakes at most once per 3 seconds");
  step(14000);
  check(wakes(1)==2,"per-account wake deadline respected");
  step(610001);
  check(PowerManager.instance.last.acquires==2&&PowerManager.instance.last.isHeld(),"timed wake lock renewed for long jobs");
  check(service.onStartCommand(new Intent(service,MangoUserBroadcastService.class).setAction("mango.broadcast.cancel"),0,3)==Service.START_NOT_STICKY,"notification cancel is nonsticky");
  check(service.stops==1&&!manager(0).running&&!manager(1).running&&!manager(2).running,"cancel stops every active broadcast and service");
  check(manager(0).cancels==1&&manager(2).cancels==1,"cancel reaches account-specific managers");
  service.onDestroy();
  check(Handler.last.queue.isEmpty(),"destroy removes scheduled callbacks");
  check(!PowerManager.instance.last.isHeld()&&PowerManager.instance.last.releases==1,"destroy releases held wake lock");
  check(manager(0).pauses==1&&manager(2).pauses==1,"destroy asks managers to preserve resumable progress");
  check(service.foregroundStops==1&&service.removedNotification&&service.destroys==1,"destroy removes foreground notification and calls super");

  reset();service=new MangoUserBroadcastService();service.onCreate();manager(0).running=true;manager(0).completeOnTick=true;
  service.onStartCommand(null,0,1);step(10000);
  check(service.stops==1&&Handler.last.queue.isEmpty(),"last completed job stops without scheduling again");
  check(!PowerManager.instance.last.isHeld(),"empty service does not acquire wake lock");service.onDestroy();
  reset();service=new MangoUserBroadcastService();service.onCreate();manager(0).running=true;
  service.onStartCommand(null,0,1);step(10000);
  ((Service)service).onTimeout(1,1);
  check(service.stops==1,"Android 15 dataSync timeout dispatch stops service");
  service.onDestroy();check(!manager(0).running&&manager(0).pauses==1,"timeout destruction pauses instead of cancelling progress");
  reset();Build.VERSION.SDK_INT=25;NotificationManager.instance.channel=null;
  service=new MangoUserBroadcastService();service.onCreate();service.onStartCommand(null,0,1);step(10000);
  check(NotificationManager.instance.channel==null&&service.foregrounds==1,"pre-O avoids notification channel API and still promotes service");
  check(service.stops==1&&wakes(0)==0,"empty startup does not resume old tasks or wake inactive accounts");service.onDestroy();
  System.out.println("PASS: "+assertions+" broadcast service production lifecycle assertions");
 }
}
'''

with tempfile.TemporaryDirectory(prefix="mango-broadcast-service-") as temporary:
    temporary = Path(temporary)
    sources = []
    for relative, contents in STUBS.items():
        path = temporary / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(contents)
        sources.append(str(path))
    harness = temporary / "org/telegram/messenger/BroadcastServiceRegression.java"
    harness.write_text(HARNESS)
    sources += [str(harness), str(SRC / "org/telegram/messenger/MangoUserBroadcastService.java")]
    subprocess.run([str(JAVA / "javac"), "-d", str(temporary), *sources], check=True)
    subprocess.run([str(JAVA / "java"), "-cp", str(temporary), "org.telegram.messenger.BroadcastServiceRegression"], check=True)

ANDROID = "{http://schemas.android.com/apk/res/android}"
manifest = ET.parse(ROOT / "TMessagesProj/src/main/AndroidManifest.xml").getroot()
permissions = {node.get(ANDROID + "name") for node in manifest.findall("uses-permission")}
for permission in ("FOREGROUND_SERVICE", "FOREGROUND_SERVICE_DATA_SYNC", "WAKE_LOCK", "POST_NOTIFICATIONS"):
    assert "android.permission." + permission in permissions, permission
service = next(node for node in manifest.findall("application/service")
               if node.get(ANDROID + "name") == "org.telegram.messenger.MangoUserBroadcastService")
assert service.get(ANDROID + "exported") == "false"
assert service.get(ANDROID + "foregroundServiceType") == "dataSync"
source = (SRC / "org/telegram/messenger/MangoUserBroadcastService.java").read_text()
assert "setAppPaused" not in source, "background work must not alter online state or UI resume counters"
assert "BOOT_COMPLETED" not in source and "START_STICKY" not in source
print("PASS: 8 foreground manifest and non-resurrection integration checks")
