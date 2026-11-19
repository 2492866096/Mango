#!/usr/bin/env python3
"""Compile the production MessageEchoManager against deterministic offline fixtures."""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
JAVA_HOME = Path(os.environ.get("JAVA_HOME", "/Applications/Android Studio.app/Contents/jbr/Contents/Home"))
JAVAC = JAVA_HOME / "bin" / "javac"
JAVA = JAVA_HOME / "bin" / "java"
if not JAVAC.is_file():
    import shutil
    JAVAC = Path(shutil.which("javac") or "javac")
    JAVA = Path(shutil.which("java") or "java")

FIXTURES = {
'org/telegram/messenger/MangoSendGuard.java': '''package org.telegram.messenger; public class MangoSendGuard {public static boolean confirmed; public static void runConfirmed(Runnable action){boolean old=confirmed;confirmed=true;try{action.run();}finally{confirmed=old;}} }''',
"android/content/Context.java": r'''
package android.content;
import java.util.*;
public class Context {
 public static final int MODE_PRIVATE = 0; public static boolean failCommits;
 private static final Map<String,Prefs> PREFS = new HashMap<>();
 public SharedPreferences getSharedPreferences(String name, int mode) {
  return PREFS.computeIfAbsent(name, k -> new Prefs());
 }
 static final class Prefs implements SharedPreferences {
  final Map<String,Object> values = new HashMap<>();
  public synchronized String getString(String k,String d){Object v=values.get(k);return v instanceof String?(String)v:d;}
  public synchronized Set<String> getStringSet(String k,Set<String> d){Object v=values.get(k);return v instanceof Set?new HashSet<>((Set<String>)v):new HashSet<>(d);}
  public synchronized long getLong(String k,long d){Object v=values.get(k);return v instanceof Number?((Number)v).longValue():d;}
  public synchronized int getInt(String k,int d){Object v=values.get(k);return v instanceof Number?((Number)v).intValue():d;}
  public synchronized boolean getBoolean(String k,boolean d){Object v=values.get(k);return v instanceof Boolean?(Boolean)v:d;}
  public synchronized Editor edit(){ return new Editor(){
   final Map<String,Object> pending = new HashMap<>();
   public Editor putString(String k,String v){pending.put(k,v);return this;}
   public Editor putStringSet(String k,Set<String> v){pending.put(k,new HashSet<>(v));return this;}
   public Editor putInt(String k,int v){pending.put(k,v);return this;}
   public Editor putLong(String k,long v){pending.put(k,v);return this;}
   public Editor putBoolean(String k,boolean v){pending.put(k,v);return this;}
   public void apply(){commit();}
   public boolean commit(){if(failCommits)return false;synchronized(Prefs.this){values.putAll(pending);}return true;}
  };}
 }
}
''',
"android/app/Activity.java": r'''package android.app; public class Activity extends android.content.Context {}''',
"android/content/SharedPreferences.java": r'''
package android.content;
import java.util.Set;
public interface SharedPreferences {
 String getString(String k,String d); Set<String> getStringSet(String k,Set<String> d); long getLong(String k,long d); int getInt(String k,int d); boolean getBoolean(String k,boolean d); Editor edit();
 interface Editor {Editor putString(String k,String v); Editor putStringSet(String k,Set<String> v); Editor putInt(String k,int v); Editor putLong(String k,long v); Editor putBoolean(String k,boolean v); void apply(); boolean commit();}
}
''',
"org/telegram/tgnet/TLObject.java": r'''package org.telegram.tgnet; public class TLObject {}''',
"org/telegram/tgnet/TLRPC.java": r'''
package org.telegram.tgnet;
public class TLRPC {
 public static class Peer { public long user_id,chat_id,channel_id; }
 public static class MessageReplyHeader { public boolean forum_topic; public int reply_to_top_id,reply_to_msg_id; }
 public static class MessageMedia { public int ttl_seconds; }
 public static class TL_messageMediaPaidMedia extends MessageMedia {}
 public static class MessageAction {}
 public static class TL_messageActionEmpty extends MessageAction {}
 public static class User { public long id; }
 public static class UserFull { public boolean voice_messages_forbidden; }
 public static class Chat { public boolean noforwards; }
 public static class Message { public String message; public int id,date,ttl; public boolean out,noforwards,scheduled; public long dialog_id; public Peer from_id,peer_id; public MessageReplyHeader reply_to; public MessageAction action; public MessageMedia media; }
 public static class TL_message extends Message {}
}
''',
"org/telegram/tgnet/ConnectionsManager.java": r'''
package org.telegram.tgnet;
public class ConnectionsManager {
 public static final int ConnectionStateConnected=3, ConnectionStateUpdating=2;
 private static final ConnectionsManager[] ALL={new ConnectionsManager(),new ConnectionsManager()};
 public long now=1000; public int state=ConnectionStateConnected;
 public static ConnectionsManager getInstance(int account){return ALL[account];}
 public int getCurrentTime(){return (int) now;}
 public int getConnectionState(){return state;}
}
''',
"org/telegram/messenger/ApplicationLoader.java": r'''
package org.telegram.messenger;
public class ApplicationLoader { public static android.app.Activity applicationContext = new android.app.Activity(); public static boolean mainInterfacePaused, isScreenOn=true; }
''',
"org/telegram/messenger/UserConfig.java": r'''
package org.telegram.messenger;
public class UserConfig {
 private static final UserConfig[] ALL={new UserConfig(11),new UserConfig(22)}; private long id; private boolean active=true;
 private UserConfig(long id){this.id=id;} public static UserConfig getInstance(int a){return ALL[a];}
 public long getClientUserId(){return id;} public void setClientUserId(long value){id=value;} public boolean isClientActivated(){return active;} public void setClientActivated(boolean v){active=v;}
}
''',
"org/telegram/messenger/DailyMessageTasksManager.java": r'''
package org.telegram.messenger;
public class DailyMessageTasksManager {
 public static boolean connected=true;
 public static boolean isConnected(int account){return connected && org.telegram.tgnet.ConnectionsManager.getInstance(account).getConnectionState()==org.telegram.tgnet.ConnectionsManager.ConnectionStateConnected;}
 public static boolean isValidTarget(int account,long did,boolean users){return did!=0 && did != UserConfig.getInstance(account).getClientUserId();}
}
''',
"org/telegram/messenger/DialogObject.java": r'''
package org.telegram.messenger;
public class DialogObject {
 public static long getMessagesStarsPrice(boolean blocked){return blocked ? 1 : 0;}
 public static boolean isEncryptedDialog(long id){return false;}
}
''',
"org/telegram/messenger/SendMessagesHelper.java": r'''
package org.telegram.messenger;
import java.util.*;
public class SendMessagesHelper {
 public static final SendMessagesHelper[] ALL={new SendMessagesHelper(),new SendMessagesHelper()};
 public static int chatResult; public boolean lastConfirmed; public Runnable onSend; public int result=0,calls; public ArrayList<org.telegram.messenger.MessageObject> last; public long peer; public boolean forwardFromMyName; public org.telegram.messenger.MessageObject topic;
 public static SendMessagesHelper getInstance(int a){return ALL[a];}
 public int sendMessage(ArrayList<MessageObject> messages,long peer,boolean forwardFromMyName,boolean hideCaption,boolean notify,int scheduleDate,MessageObject topic,int videoTimestamp,long payStars){
  lastConfirmed=MangoSendGuard.confirmed;calls++;last=messages;this.peer=peer;this.forwardFromMyName=forwardFromMyName;this.topic=topic;if(onSend!=null)onSend.run();return result;
 }
 public static int canSendMessageToChat(org.telegram.tgnet.TLRPC.Chat chat,MessageObject message){return chatResult;}
}
''',
"org/telegram/messenger/MessagesController.java": r'''
package org.telegram.messenger;
import java.util.*; import org.telegram.tgnet.TLRPC;
public class MessagesController {
 private static final MessagesController[] ALL={new MessagesController(),new MessagesController()}; public static MessagesController getInstance(int a){return ALL[a];}
 public TLRPC.UserFull full; public TLRPC.Chat chat; public long paid; public boolean blocked;
 public TLRPC.UserFull getUserFull(long id){return full;} public TLRPC.Chat getChat(long id){return chat;} public long getSendPaidMessagesStars(long did){return paid;} public boolean isUserContactBlocked(long did){return blocked;}
}
''',
"org/telegram/messenger/MessageObject.java": r'''
package org.telegram.messenger;
import org.telegram.tgnet.TLRPC;
public class MessageObject {
 public final int currentAccount; public TLRPC.Message messageOwner; public boolean isTopicMainMessage, scheduled, outOwner, sponsored, sending, sendError, voice, round;
 public MessageObject(int a,TLRPC.Message m,boolean b,boolean c){currentAccount=a;messageOwner=m;}
 public int getId(){return messageOwner.id;} public long getDialogId(){return messageOwner.dialog_id;} public boolean isOut(){return messageOwner.out;} public boolean isOutOwner(){return outOwner;}
 public boolean isSponsored(){return sponsored;} public boolean isSending(){return sending;} public boolean isSendError(){return sendError;} public boolean isVoice(){return voice;} public boolean isRoundVideo(){return round;}
}
''',
"EchoManagerRegression.java": r'''
import java.util.*;
import org.telegram.messenger.*;
import org.telegram.tgnet.*;
import android.content.*;

public class EchoManagerRegression {
 static int checks;
 static void check(boolean ok,String what){checks++;if(!ok)throw new AssertionError(what);}
 static MessageObject msg(int account,long did,int id,int date){
  TLRPC.Message m=new TLRPC.Message();m.dialog_id=did;m.id=id;m.date=date;m.peer_id=new TLRPC.Peer();m.from_id=new TLRPC.Peer();
  return new MessageObject(account,m,false,false);
 }
 static ArrayList<MessageObject> one(MessageObject m){return new ArrayList<>(Arrays.asList(m));}
 static void now(int account,int value){ConnectionsManager.getInstance(account).now=value;}
 static SharedPreferences prefs(int a){return ApplicationLoader.applicationContext.getSharedPreferences("message_echo_"+a+"_"+UserConfig.getInstance(a).getClientUserId(),0);}
 static void process(MessageObject m){MessageEchoManager.process(m.currentAccount,one(m));}
 public static void main(String[] args){
  SendMessagesHelper sender=SendMessagesHelper.getInstance(0);
  MessagesController controller=MessagesController.getInstance(0);
  now(0,1000);MessageEchoManager.setTarget(0,-99,true);
  check(prefs(0).getLong("armed_-99",0)==1000,"arming uses server time, not workstation time");
  MessageObject old=msg(0,-99,1,999);process(old);
  process(msg(0,-99,2,1000));check(sender.calls==0,"messages at or before arming are not echoed");
  now(0,1001);MessageObject first=msg(0,-99,3,1001);
  sender.onSend=()->{check(prefs(0).getInt("last_mid_-99",0)==3,"message id persisted before dispatch");check(prefs(0).getLong("last_time_-99",0)==1001,"server dispatch time persisted");};
  process(first);sender.onSend=null;
  check(sender.calls==1,"new incoming message echoes after arming");
  check(sender.lastConfirmed&&!MangoSendGuard.confirmed,"opted-in automatic echo bypasses confirmation without leaking approval");
  check(sender.forwardFromMyName&&sender.peer==-99&&sender.last.get(0)==first,"echo keeps content and dialog but drops forward author");
  process(first);check(sender.calls==1,"duplicate update cannot echo again");
  now(0,1003);process(msg(0,-99,4,1003));check(sender.calls==1,"per-chat three-second interval enforced");
  now(0,1004);MessageObject next=msg(0,-99,5,1004);process(next);check(sender.calls==2,"next message can send at three seconds");
  now(0,1008);process(first);check(sender.calls==2,"old id remains deduplicated after interval");
  // Recreating the Android Context exercises reopening the same preference store.
  ApplicationLoader.applicationContext=new android.app.Activity();process(next);
  check(sender.calls==2,"dedup survives preference reopen");
  MessageEchoManager.setTarget(0,-98,true);now(0,1009);process(msg(0,-98,1,1009));check(sender.calls==3,"another chat has independent throttle and id sequence");
  process(msg(0,-97,1,1009));check(sender.calls==3,"unselected chat never auto-sends");
  now(0,1030);process(msg(0,-99,6,1009));check(sender.calls==3,"updates older than twenty seconds are not auto-sent");
  now(0,1031);MessageObject candidate=msg(0,-99,7,1031);
  ApplicationLoader.mainInterfacePaused=true;process(candidate);check(sender.calls==3,"automatic echo is disabled in background");
  ApplicationLoader.mainInterfacePaused=false;ApplicationLoader.isScreenOn=false;process(candidate);check(sender.calls==3,"automatic echo is disabled with screen off");
  ApplicationLoader.isScreenOn=true;DailyMessageTasksManager.connected=false;process(candidate);check(sender.calls==3,"automatic echo requires connected account");
  check(!MessageEchoManager.echo(0,candidate),"manual echo rejects offline dispatch");
  DailyMessageTasksManager.connected=true;UserConfig.getInstance(0).setClientActivated(false);process(candidate);
  check(sender.calls==3&&!MessageEchoManager.echo(0,candidate),"logged-out account cannot auto or manually send");
  UserConfig.getInstance(0).setClientActivated(true);Context.failCommits=true;process(candidate);check(sender.calls==3,"failure to persist dedup prevents dispatch");
  Context.failCommits=false;process(candidate);check(sender.calls==4,"foreground online echo resumes when persistence succeeds");
  MessageEchoManager.setTarget(0,-99,false);now(0,1035);process(msg(0,-99,8,1035));check(sender.calls==4,"disarming prevents subsequent sends");
  Set<String> copy=MessageEchoManager.targets(0);copy.clear();check(MessageEchoManager.targets(0).contains("-98"),"target snapshot is defensive copy");

  MessageObject out=msg(0,-99,10,1035);out.messageOwner.out=true;out.outOwner=false;
  check(!MessageEchoManager.canEcho(0,out),"raw outgoing bit blocks forwarded outgoing even if isOutOwner is false");
  MessageObject own=msg(0,-99,11,1035);own.messageOwner.from_id.user_id=11;
  check(!MessageEchoManager.canEcho(0,own),"self-authored incoming-shaped message excluded");
  MessageObject owner=msg(0,-99,12,1035);owner.outOwner=true;check(!MessageEchoManager.canEcho(0,owner),"owned message excluded");
  MessageObject invalid=msg(0,-99,0,1035);check(!MessageEchoManager.canEcho(0,invalid),"unsent local id excluded");
  check(!MessageEchoManager.canEcho(0,null),"null message rejected");
  MessageObject noOwner=new MessageObject(0,null,false,false);check(!MessageEchoManager.canEcho(0,noOwner),"missing message body rejected");
  MessageObject flags=msg(0,-99,13,1035);flags.sponsored=true;check(!MessageEchoManager.canEcho(0,flags),"sponsored message excluded");
  flags.sponsored=false;flags.scheduled=true;check(!MessageEchoManager.canEcho(0,flags),"scheduled message excluded");
  flags.scheduled=false;flags.sending=true;check(!MessageEchoManager.canEcho(0,flags),"sending message excluded");
  flags.sending=false;flags.sendError=true;check(!MessageEchoManager.canEcho(0,flags),"failed outgoing message excluded");
  flags.sendError=false;flags.messageOwner.action=new TLRPC.MessageAction();check(!MessageEchoManager.canEcho(0,flags),"service action excluded");
  flags.messageOwner.action=new TLRPC.TL_messageActionEmpty();check(MessageEchoManager.canEcho(0,flags),"empty service action accepted");
  flags.messageOwner.noforwards=true;check(!MessageEchoManager.canEcho(0,flags),"protected message excluded");
  flags.messageOwner.noforwards=false;flags.messageOwner.ttl=5;check(!MessageEchoManager.canEcho(0,flags),"expiring message excluded");
  flags.messageOwner.ttl=0;flags.messageOwner.media=new TLRPC.MessageMedia();flags.messageOwner.media.ttl_seconds=5;check(!MessageEchoManager.canEcho(0,flags),"expiring media excluded");
  flags.messageOwner.media=new TLRPC.TL_messageMediaPaidMedia();check(!MessageEchoManager.canEcho(0,flags),"paid media excluded");
  flags.messageOwner.media=null;controller.chat=new TLRPC.Chat();controller.chat.noforwards=true;check(!MessageEchoManager.canEcho(0,flags),"protected chat excluded");
  controller.chat.noforwards=false;SendMessagesHelper.chatResult=1;check(!MessageEchoManager.canEcho(0,flags),"chat media restriction honored");
  SendMessagesHelper.chatResult=0;controller.paid=1;check(!MessageEchoManager.canEcho(0,flags),"paid destination excluded");
  controller.paid=0;controller.blocked=true;check(!MessageEchoManager.canEcho(0,flags),"non-contact Stars price excluded");controller.blocked=false;
  MessageObject voice=msg(0,77,20,1035);voice.voice=true;controller.full=new TLRPC.UserFull();controller.full.voice_messages_forbidden=true;
  check(!MessageEchoManager.canEcho(0,voice),"voice restriction respected");voice.voice=false;voice.round=true;check(!MessageEchoManager.canEcho(0,voice),"round-video restriction respected");
  voice.round=false;check(MessageEchoManager.canEcho(0,voice),"voice restriction does not block text");controller.full=null;

  MessageObject topicMessage=msg(0,-99,30,1035);topicMessage.messageOwner.reply_to=new TLRPC.MessageReplyHeader();
  topicMessage.messageOwner.reply_to.forum_topic=true;topicMessage.messageOwner.reply_to.reply_to_top_id=77;topicMessage.messageOwner.reply_to.reply_to_msg_id=66;
  check(MessageEchoManager.echo(0,topicMessage),"manual echo can repeat message without automatic target enabled");
  check(!sender.lastConfirmed,"manual echo retains send confirmation");
  check(sender.topic!=null&&sender.topic.isTopicMainMessage&&sender.topic.getId()==77,"forum top id and topic marker preserved");
  check(sender.topic.messageOwner.peer_id==topicMessage.messageOwner.peer_id,"forum destination peer preserved");
  topicMessage.messageOwner.reply_to.reply_to_top_id=0;MessageEchoManager.echo(0,topicMessage);check(sender.topic.getId()==66,"forum fallback reply id preserved");
  topicMessage.messageOwner.reply_to.forum_topic=false;MessageEchoManager.echo(0,topicMessage);check(sender.topic==null,"ordinary reply does not become topic");
  sender.result=7;int before=sender.calls;check(!MessageEchoManager.echo(0,topicMessage)&&sender.calls==before+1,"send rejection returned as failure");sender.result=0;

  now(1,2000);MessageEchoManager.setTarget(1,-98,true);now(1,2001);process(msg(1,-98,1,2001));
  check(SendMessagesHelper.getInstance(1).calls==1,"second account can echo same dialog id independently");
  check(prefs(1).getInt("last_mid_-98",0)==1&&prefs(0).getInt("last_mid_-98",0)==1,"dedup state stored separately by account");
  MessageEchoManager.setTarget(1,-98,false);check(MessageEchoManager.targets(0).contains("-98"),"disarming second account does not affect first");
  UserConfig.getInstance(0).setClientUserId(111);check(MessageEchoManager.targets(0).isEmpty(),"replaced account owner cannot inherit targets");
  now(0,3000);MessageEchoManager.setTarget(0,-98,true);now(0,3001);before=sender.calls;process(msg(0,-98,1,3001));
  check(sender.calls==before+1,"new owner does not inherit prior message-id high water mark");
  UserConfig.getInstance(0).setClientUserId(11);check(prefs(0).getLong("armed_-98",0)==1008,"restoring owner loads own settings only");
  System.out.println("PASS: "+checks+" production MessageEchoManager timing, dedup, eligibility, routing, result and isolation checks");
 }
}
'''
}

def main():
    with tempfile.TemporaryDirectory(prefix="message-echo-tests-") as directory:
        root = Path(directory)
        sources=[]
        for name,text in FIXTURES.items():
            path=root/name; path.parent.mkdir(parents=True,exist_ok=True); path.write_text(text); sources.append(path)
        sources.append(ROOT/"TMessagesProj/src/main/java/org/telegram/messenger/MessageEchoManager.java")
        subprocess.run([str(JAVAC),"-d",directory,*map(str,sources)],check=True)
        subprocess.run([str(JAVA),"-cp",directory,"EchoManagerRegression"],check=True)

if __name__ == "__main__": main()
