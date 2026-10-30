#!/usr/bin/env python3
"""Exercise the actual send-confirmation scope, cancellation and worker handoffs without sending messages."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
JAVA = Path('/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin')
FIXTURES = {
    'android/os/Looper.java': '''package android.os; public class Looper {
      private static final Looper MAIN=new Looper(); public static boolean main=true;
      public static Looper myLooper(){return main?MAIN:null;} public static Looper getMainLooper(){return MAIN;}}
''',
    'org/telegram/messenger/MangoSettings.java': '''package org.telegram.messenger; public class MangoSettings {
      public static final java.util.Set<String> enabled=new java.util.HashSet<>();
      public static boolean getBoolean(int account,String key){return enabled.contains(account+":"+key);}}
''',
    'org/telegram/messenger/AndroidUtilities.java': '''package org.telegram.messenger; public class AndroidUtilities {
      public static java.util.List<Runnable> pending=new java.util.ArrayList<>();
      public static void runOnUIThread(Runnable action){pending.add(action);}}
''',
    'org/telegram/messenger/R.java': '''package org.telegram.messenger; public class R {public static class string {
      public static int MangoConfirmBotTitle=1,MangoConfirmVoiceTitle=2,MangoConfirmSendTitle=3,MangoConfirmSendRecipient=4,Cancel=5,Send=6;}}
''',
    'org/telegram/messenger/LocaleController.java': '''package org.telegram.messenger; public class LocaleController {
      public static String getString(int id){return "s"+id;} public static String formatString(int id,Object...args){return "to "+args[0];}}
''',
    'org/telegram/tgnet/TLRPC.java': '''package org.telegram.tgnet; public class TLRPC {
      public static class User {} public static class Chat {public String title="group";}}
''',
    'org/telegram/messenger/UserObject.java': '''package org.telegram.messenger; public class UserObject {
      public static String getUserName(org.telegram.tgnet.TLRPC.User user){return "recipient";}}
''',
    'org/telegram/messenger/DialogObject.java': '''package org.telegram.messenger; public class DialogObject {
      public static boolean isEncryptedDialog(long id){return false;}}
''',
    'org/telegram/messenger/MessagesController.java': '''package org.telegram.messenger; public class MessagesController {
      public static MessagesController getInstance(int account){return new MessagesController();}
      public org.telegram.tgnet.TLRPC.User getUser(long id){return new org.telegram.tgnet.TLRPC.User();}
      public org.telegram.tgnet.TLRPC.Chat getChat(long id){return new org.telegram.tgnet.TLRPC.Chat();}}
''',
    'org/telegram/ui/ActionBar/BaseFragment.java': '''package org.telegram.ui.ActionBar; public class BaseFragment {
      public Object getParentActivity(){return this;} public Object getResourceProvider(){return null;}}
''',
    'org/telegram/ui/LaunchActivity.java': '''package org.telegram.ui; public class LaunchActivity {
      public static org.telegram.ui.ActionBar.BaseFragment fragment=new org.telegram.ui.ActionBar.BaseFragment();
      public static org.telegram.ui.ActionBar.BaseFragment getSafeLastFragment(){return fragment;}}
''',
    'org/telegram/ui/ActionBar/AlertDialog.java': '''package org.telegram.ui.ActionBar; public class AlertDialog {
      public interface Click {void run(Object dialog,int which);}
      public static int shown; public static Click positive; public static String title;
      public static class Builder {public Builder(Object context,Object resources){}
      public Builder setTitle(String s){title=s;return this;} public Builder setMessage(String s){return this;}
      public Builder setNegativeButton(String s,Click c){return this;} public Builder setPositiveButton(String s,Click c){positive=c;return this;}
      public void show(){shown++;}}}
''',
    'SendGuardTest.java': '''import org.telegram.messenger.*; import org.telegram.ui.ActionBar.AlertDialog;
public class SendGuardTest {
 static int sent;
 static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception {
  require(MangoSendGuard.isBotCommand(" /start@hello_bot abc"),"bot command with args");
  require(!MangoSendGuard.isBotCommand("https://a/b"),"ordinary URL");
  require(!MangoSendGuard.isBotCommand("hello /start"),"embedded slash");
  require(!MangoSendGuard.confirm(0,1,false,false,()->sent++),"off preserves normal send");
  MangoSettings.enabled.add("0:confirm_send");
  require(MangoSendGuard.confirm(0,1,false,false,()->sent++),"send intercepted");
  require(sent==0,"dialog/cancel must not send");
  AlertDialog.positive.run(null,0);require(sent==1,"positive sends once");
  require(!MangoSendGuard.isConfirmed(),"approval not leaked");
  MangoSendGuard.runConfirmed(()->require(!MangoSendGuard.confirm(0,1,false,false,()->sent++),"nested send no second dialog"));
  require(!MangoSendGuard.confirm(1,1,false,false,()->sent++),"account isolated");
  MangoSettings.enabled.clear();MangoSettings.enabled.add("0:confirm_voice");
  require(!MangoSendGuard.confirm(0,1,false,false,()->sent++),"voice switch leaves text alone");
  require(MangoSendGuard.confirm(0,1,true,false,()->sent++),"voice intercepted");require(AlertDialog.title.equals("s2"),"voice title");
  MangoSettings.enabled.clear();MangoSettings.enabled.add("0:confirm_bot_command");
  require(!MangoSendGuard.confirm(0,1,false,false,()->sent++),"bot switch leaves normal messages alone");
  require(MangoSendGuard.confirm(0,1,false,true,()->sent++),"bot intercepted");require(AlertDialog.title.equals("s1"),"bot title");
  Runnable[] task={null};
  MangoSendGuard.runConfirmed(()->MangoSendGuard.runLargeFileConfirmed(()->task[0]=MangoSendGuard.carryConfirmation(()->{
   require(MangoSendGuard.isConfirmed(),"approved action crosses worker boundary");
   require(MangoSendGuard.isLargeFileConfirmed(),"large-file choice crosses worker boundary");
  })));
  Thread worker=new Thread(task[0]);worker.start();worker.join();
  require(!MangoSendGuard.isConfirmed()&&!MangoSendGuard.isLargeFileConfirmed(),"thread scopes restored");
  try{MangoSendGuard.runConfirmed(()->{throw new IllegalStateException();});}catch(IllegalStateException expected){}
  require(!MangoSendGuard.isConfirmed(),"exception restores scope");
  android.os.Looper.main=false;
  require(MangoSendGuard.confirm(0,1,false,true,()->sent++),"worker is intercepted");
  require(AndroidUtilities.pending.size()==1,"UI handoff queued");
  MangoSettings.enabled.clear();android.os.Looper.main=true;AndroidUtilities.pending.remove(0).run();
  require(sent==2,"switch changed while queued still executes once");
  System.out.println("PASS: cancellation, recipient/account switches, voice/bot rules, nested and asynchronous scopes, exception cleanup");
 }
}'''
}
with tempfile.TemporaryDirectory(prefix='mango-send-tests-') as directory:
    temp = Path(directory)
    sources = []
    for name, content in FIXTURES.items():
        source = temp / name
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_text(content)
        sources.append(source)
    sources.append(ROOT / 'TMessagesProj/src/main/java/org/telegram/messenger/MangoSendGuard.java')
    subprocess.run([str(JAVA / 'javac'), '-d', str(temp), *map(str, sources)], check=True)
    subprocess.run([str(JAVA / 'java'), '-cp', str(temp), 'SendGuardTest'], check=True)
