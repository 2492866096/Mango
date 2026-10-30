#!/usr/bin/env python3
"""Compile actual keyword/cache/timer/JobService code against network-free fixtures.

This does not run Gradle, build an APK, use a device, or contact the live CDN.
"""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
JAVA = Path(os.environ.get('JAVA_HOME', '/Applications/Android Studio.app/Contents/jbr/Contents/Home')) / 'bin'
FIXTURES = {
    'org/telegram/messenger/ScamUsersManager.java': 'package org.telegram.messenger; public final class ScamUsersManager { public static void refreshAll() {} public static boolean isAnyRefreshing(){return false;} public static boolean hasRefreshFailed(){return false;} public static void addListener(Runnable r){} public static void removeListener(Runnable r){} }',
    'android/content/SharedPreferences.java': '''package android.content;
public interface SharedPreferences {
 String getString(String key,String fallback); long getLong(String key,long fallback); boolean getBoolean(String key,boolean fallback);
 Editor edit(); interface Editor { Editor putString(String key,String value); Editor putLong(String key,long value); Editor putBoolean(String key,boolean value); boolean commit(); void apply(); }
}''',
    'android/content/Context.java': '''package android.content;
import java.util.*;
public class Context {
 public static final int MODE_PRIVATE=0; public static final String JOB_SCHEDULER_SERVICE="jobs";
 public boolean failCommit; private final Map<String,Prefs> preferences=new HashMap<>();
 public android.app.job.JobScheduler scheduler=new android.app.job.JobScheduler();
 public Object getSystemService(String name){return scheduler;}
 public SharedPreferences getSharedPreferences(String name,int mode){return preferences.computeIfAbsent(name,key->new Prefs());}
 private final class Prefs implements SharedPreferences {
  private Map<String,Object> values=new HashMap<>();
  public String getString(String key,String fallback){return (String)values.getOrDefault(key,fallback);}
  public long getLong(String key,long fallback){return (Long)values.getOrDefault(key,fallback);}
  public boolean getBoolean(String key,boolean fallback){return (Boolean)values.getOrDefault(key,fallback);}
  public Editor edit(){return new Editor(){
   final Map<String,Object> next=new HashMap<>(values);
   public Editor putString(String key,String value){next.put(key,value);return this;}
   public Editor putLong(String key,long value){next.put(key,value);return this;}
   public Editor putBoolean(String key,boolean value){next.put(key,value);return this;}
   public boolean commit(){if(failCommit)return false;values=next;return true;}
   public void apply(){commit();}
  };}
 }
}''',
    'android/content/ComponentName.java': '''package android.content;
public class ComponentName { private final Class<?> type; public ComponentName(Context context,Class<?> type){this.type=type;}
 public boolean equals(Object other){return other instanceof ComponentName && ((ComponentName)other).type==type;} public int hashCode(){return type.hashCode();} }''',
    'android/os/SystemClock.java': '''package android.os; public final class SystemClock { public static long now=1000000; public static long elapsedRealtime(){return now;} }''',
    'android/text/TextUtils.java': '''package android.text; public final class TextUtils {
 public static String join(CharSequence delimiter,Iterable<?> values){StringBuilder out=new StringBuilder();for(Object value:values){if(out.length()>0)out.append(delimiter);out.append(value);}return out.toString();}
}''',
    'android/app/job/JobInfo.java': '''package android.app.job;
import android.content.ComponentName;
public final class JobInfo {
 public static final int NETWORK_TYPE_ANY=1,BACKOFF_POLICY_EXPONENTIAL=1;
 int id,network,backoffPolicy; long interval,backoff; boolean persisted; ComponentName service;
 public int getId(){return id;} public ComponentName getService(){return service;} public boolean isPeriodic(){return interval>0;}
 public boolean isPersisted(){return persisted;} public long getIntervalMillis(){return interval;} public int getNetworkType(){return network;}
 public long getInitialBackoffMillis(){return backoff;} public int getBackoffPolicy(){return backoffPolicy;}
 public static final class Builder {
  final JobInfo info=new JobInfo(); public Builder(int id,ComponentName service){info.id=id;info.service=service;}
  public Builder setRequiredNetworkType(int value){info.network=value;return this;} public Builder setPeriodic(long value){info.interval=value;return this;}
  public Builder setBackoffCriteria(long value,int policy){info.backoff=value;info.backoffPolicy=policy;return this;}
  public Builder setPersisted(boolean value){info.persisted=value;return this;} public JobInfo build(){return info;}
 }
}''',
    'android/app/job/JobScheduler.java': '''package android.app.job;
import java.util.*;
public final class JobScheduler {
 public static final int RESULT_SUCCESS=1; public int scheduleCalls; public boolean fail;
 private final List<JobInfo> jobs=new ArrayList<>();
 public List<JobInfo> getAllPendingJobs(){return new ArrayList<>(jobs);}
 public int schedule(JobInfo info){scheduleCalls++;if(fail)return 0;jobs.removeIf(job->job.getId()==info.getId());jobs.add(info);return RESULT_SUCCESS;}
}''',
    'android/app/job/JobParameters.java': '''package android.app.job; public final class JobParameters {}''',
    'android/app/job/JobService.java': '''package android.app.job;
public abstract class JobService extends android.content.Context {
 public int finishCalls; public JobParameters finished; public boolean retry;
 public abstract boolean onStartJob(JobParameters params); public abstract boolean onStopJob(JobParameters params);
 public void jobFinished(JobParameters params,boolean needsReschedule){finishCalls++;finished=params;retry=needsReschedule;}
 public void onDestroy(){}
}''',
    'org/telegram/messenger/ApplicationLoader.java': '''package org.telegram.messenger;
public final class ApplicationLoader { public static android.content.Context applicationContext=new android.content.Context(); public static void postInitApplication(){} }''',
    'org/telegram/messenger/UserConfig.java': '''package org.telegram.messenger;
public final class UserConfig {
 private static final UserConfig[] accounts={new UserConfig(),new UserConfig(),new UserConfig()}; public long owner;
 public static UserConfig getInstance(int id){return accounts[id];} public long getClientUserId(){return owner;} public boolean isClientActivated(){return owner!=0;}
 public static int[] getActivatedAccounts(){java.util.ArrayList<Integer> active=new java.util.ArrayList<>();for(int i=0;i<accounts.length;i++)if(accounts[i].owner!=0)active.add(i);return active.stream().mapToInt(Integer::intValue).toArray();}
}''',
    'org/telegram/messenger/DispatchQueue.java': '''package org.telegram.messenger;
public final class DispatchQueue {
 public static final java.util.ArrayList<Runnable> pending=new java.util.ArrayList<>(); public DispatchQueue(String name){}
 public boolean postRunnable(Runnable runnable){pending.add(runnable);return true;} public static void drain(){while(!pending.isEmpty())pending.remove(0).run();}
}''',
    'org/telegram/messenger/AndroidUtilities.java': '''package org.telegram.messenger;
public final class AndroidUtilities {
 public static final class Delayed {public Runnable runnable;public long delay;Delayed(Runnable r,long d){runnable=r;delay=d;}}
 public static final java.util.ArrayList<Delayed> delayed=new java.util.ArrayList<>();
 public static void runOnUIThread(Runnable r){r.run();} public static void runOnUIThread(Runnable r,long delay){delayed.add(new Delayed(r,delay));}
 public static void cancelRunOnUIThread(Runnable r){delayed.removeIf(item->item.runnable==r);}
}''',
    'org/telegram/messenger/FileLog.java': '''package org.telegram.messenger; public final class FileLog { public static int errors; public static void e(Exception error){errors++;} }''',
    'org/telegram/tgnet/TLRPC.java': '''package org.telegram.tgnet;
public final class TLRPC { public static class MessageAction {} public static class TL_messageActionEmpty extends MessageAction {} public static class Message {public String message; public MessageAction action; public int edit_date;} }''',
    'org/telegram/messenger/MessageObject.java': '''package org.telegram.messenger;
public final class MessageObject { public org.telegram.tgnet.TLRPC.Message messageOwner=new org.telegram.tgnet.TLRPC.Message(); public boolean out;public long dialog=100;public int id=1;
 public boolean isOut(){return out;}public long getDialogId(){return dialog;}public int getId(){return id;} }
''',
}

with tempfile.TemporaryDirectory(prefix='mango-scam-tests-') as folder:
    base = Path(folder)
    sources = []
    for name, source in FIXTURES.items():
        file = base / name
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(source)
        sources.append(file)
    production = ROOT / 'TMessagesProj/src/main/java/org/telegram/messenger'
    sources.extend(production / name for name in ('MessageInsightsRules.java', 'RemoteScamKeywords.java', 'ScamProtectionManager.java', 'ScamKeywordsUpdateService.java'))
    sources.append(ROOT / 'Tools/tests/ScamKeywordsTest.java')
    classes = base / 'classes'
    subprocess.run([str(JAVA / 'javac'), '-d', str(classes)] + list(map(str, sources)), check=True)
    subprocess.run([str(JAVA / 'java'), '-cp', str(classes), 'org.telegram.messenger.ScamKeywordsTest'], check=True)
