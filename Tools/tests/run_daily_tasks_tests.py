#!/usr/bin/env python3
"""Compile the actual daily task manager against memory/network-free platform fixtures."""
from pathlib import Path
import os, re, subprocess, tempfile

root=Path(__file__).resolve().parents[2]
java=Path(os.environ.get('JAVA_HOME','/Applications/Android Studio.app/Contents/jbr/Contents/Home'))
sdk=Path(os.environ.get('ANDROID_HOME',str(Path.home()/'Library/Android/sdk')))
fixtures={
'org/telegram/messenger/MangoSendGuard.java': '''package org.telegram.messenger; public class MangoSendGuard {public static boolean confirmed; public static void runConfirmed(Runnable action){boolean old=confirmed;confirmed=true;try{action.run();}finally{confirmed=old;}} }''',
'android/content/SharedPreferences.java': '''package android.content; public interface SharedPreferences { String getString(String k,String d); long getLong(String k,long d); Editor edit(); interface Editor { Editor putLong(String k,long v); Editor putString(String k,String v); Editor clear(); boolean commit(); } }''',
'android/content/Context.java': '''package android.content; import java.util.*; public class Context { public static final int MODE_PRIVATE=0; public static boolean failCommit; final Map<String,Prefs> all=new HashMap<>(); public SharedPreferences getSharedPreferences(String k,int mode){return all.computeIfAbsent(k,s->new Prefs());} static class Prefs implements SharedPreferences { Map<String,Object> m=new HashMap<>(); public String getString(String k,String d){return (String)m.getOrDefault(k,d);} public long getLong(String k,long d){return (Long)m.getOrDefault(k,d);} public Editor edit(){return new Editor(){Map<String,Object> changes=new HashMap<>(m); public Editor putLong(String k,long v){changes.put(k,v);return this;} public Editor putString(String k,String v){changes.put(k,v);return this;} public Editor clear(){changes.clear();return this;} public boolean commit(){if(failCommit)return false;m=changes;return true;}};} } }''',
'android/os/SystemClock.java': '''package android.os; public class SystemClock {public static long elapsedRealtime(){return System.nanoTime()/1000000;} }''',
'android/text/TextUtils.java': '''package android.text; public class TextUtils {public static boolean isEmpty(CharSequence s){return s==null||s.length()==0;} }''',
'org/telegram/messenger/ApplicationLoader.java': '''package org.telegram.messenger; public class ApplicationLoader {public static android.content.Context applicationContext=new android.content.Context(); public static boolean mainInterfacePaused; public static boolean isScreenOn=true;}''',
'org/telegram/messenger/UserConfig.java': '''package org.telegram.messenger; public class UserConfig { public static final int MAX_ACCOUNT_COUNT=4; private static final UserConfig[] a={new UserConfig(),new UserConfig(),new UserConfig(),new UserConfig()}; public long owner=42; public static UserConfig getInstance(int account){return a[account];} public long getClientUserId(){return owner;} public boolean isClientActivated(){return owner!=0;} }''',
'org/telegram/messenger/FileLog.java': '''package org.telegram.messenger; public class FileLog {public static void e(Exception e){throw new RuntimeException(e);} }''',
'org/telegram/messenger/Utilities.java': '''package org.telegram.messenger; public class Utilities {public static final java.util.Random random=new java.util.Random(42);}''',
'org/telegram/tgnet/TLRPC.java': '''package org.telegram.tgnet; public class TLRPC {public static class User {public long id; public boolean bot=true,deleted;} public static class Chat{} }''',
'org/telegram/tgnet/ConnectionsManager.java': '''package org.telegram.tgnet; public class ConnectionsManager {public static final int ConnectionStateConnected=1,ConnectionStateUpdating=2;public static int state=1; public static ConnectionsManager getInstance(int a){return new ConnectionsManager();} public int getConnectionState(){return state;} }''',
'org/telegram/messenger/DialogObject.java': '''package org.telegram.messenger; public class DialogObject {public static boolean isEncryptedDialog(long id){return id==Long.MAX_VALUE;} public static long getMessagesStarsPrice(Object o){return 0;} }''',
'org/telegram/messenger/UserObject.java': '''package org.telegram.messenger; public class UserObject {public static boolean isDeleted(org.telegram.tgnet.TLRPC.User u){return u.deleted;} }''',
'org/telegram/messenger/ChatObject.java': '''package org.telegram.messenger; public class ChatObject {public static boolean isNotInChat(org.telegram.tgnet.TLRPC.Chat c){return false;}public static boolean isChannelAndNotMegaGroup(org.telegram.tgnet.TLRPC.Chat c){return false;}public static boolean canSendMessages(org.telegram.tgnet.TLRPC.Chat c){return true;}public static boolean canSendPlain(org.telegram.tgnet.TLRPC.Chat c){return true;} }''',
'org/telegram/messenger/MessagesController.java': '''package org.telegram.messenger; import org.telegram.tgnet.TLRPC; public class MessagesController {public static boolean missing;public static long stars;public static MessagesController getInstance(int a){return new MessagesController();} public TLRPC.User getUser(long id){if(missing)return null;TLRPC.User u=new TLRPC.User();u.id=id;return u;}public TLRPC.Chat getChat(long id){return missing?null:new TLRPC.Chat();}public long getSendPaidMessagesStars(long d){return stars;}public Object isUserContactBlocked(long d){return null;} }''',
'org/telegram/messenger/SendMessagesHelper.java': '''package org.telegram.messenger; public class SendMessagesHelper {public static int count;public static SendMessagesHelper getInstance(int a){return new SendMessagesHelper();}public static class SendMessageParams {public static SendMessageParams of(Object...args){return new SendMessageParams();}}public void sendMessage(SendMessageParams p){if(!MangoSendGuard.confirmed)throw new AssertionError("explicit daily automation bypass missing");count++;} }'''
}
with tempfile.TemporaryDirectory(prefix='mango-daily-tests-') as folder:
 base=Path(folder); sources=[]
 for name,text in fixtures.items():
  f=base/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text);sources.append(f)
 json=list(sorted(sdk.glob('sources/android-*/org/json/JSONObject.java')))[-1].parent
 for original in json.glob('*.java'):
  text=original.read_text();text=re.sub(r'^import (?:static )?(?:android\.|libcore\.).*;\n','',text,flags=re.M);text=re.sub(r'@(?:UnsupportedAppUsage|SystemApi|NonNull|Nullable)(?:\([^)]*\))?','',text)
  f=base/'org/json'/original.name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text);sources.append(f)
 production=root/'TMessagesProj/src/main/java/org/telegram/messenger'
 sources += [production/'DailyMessageTasksManager.java',production/'DailyScheduleWindow.java',root/'Tools/tests/DailyMessageTasksManagerTest.java']
 out=base/'classes'
 subprocess.run([str(java/'bin/javac'),'-d',str(out)]+list(map(str,sources)),check=True)
 subprocess.run([str(java/'bin/java'),'-cp',str(out),'org.telegram.messenger.DailyMessageTasksManagerTest'],check=True)
