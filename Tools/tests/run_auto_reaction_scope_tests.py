#!/usr/bin/env python3
"""Execute the production auto-reaction manager with account, scope and delayed-send changes."""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
JAVA = Path(os.environ.get('JAVA_HOME', '/Applications/Android Studio.app/Contents/jbr/Contents/Home')) / 'bin'
FIXTURES = {
'android/text/TextUtils.java': '''package android.text;public class TextUtils {public static boolean isEmpty(CharSequence v){return v==null||v.length()==0;}}''',
'org/telegram/tgnet/TLRPC.java': '''package org.telegram.tgnet;import java.util.*;public class TLRPC {
 public static class InputPeer {public long id;public InputPeer(long id){this.id=id;}}
 public static class TL_inputPeerChannel extends InputPeer {public TL_inputPeerChannel(long id){super(id);}}
 public static class Peer {public long id;}public static class TL_peerChannel extends Peer {}
 public static class Fwd {public int saved_from_msg_id;}
 public static class Message {public int date=1000;public Object action;public boolean isThreadMessage;public Fwd fwd_from;public Peer peer_id;}
 public static class Chat {public long id;public boolean channel,megagroup,monoforum;}
 public static class Updates {}
 public static class TL_reactionEmoji {public String emoticon;}
 public static class TL_messages_sendReaction {public InputPeer peer;public int msg_id,flags;public ArrayList<TL_reactionEmoji> reaction=new ArrayList<>();}
}''',
'org/telegram/tgnet/ConnectionsManager.java': '''package org.telegram.tgnet;import java.util.*;public class ConnectionsManager {
 public static final ConnectionsManager[] instances={new ConnectionsManager(),new ConnectionsManager(),new ConnectionsManager()};
 public static int now=1000;public static ConnectionsManager getInstance(int a){return instances[a];}public int getCurrentTime(){return now;}
 public interface Callback {void run(Object result,Object error);}public final ArrayList<TLRPC.TL_messages_sendReaction> sent=new ArrayList<>();public final ArrayList<Callback> callbacks=new ArrayList<>();
 public void sendRequest(TLRPC.TL_messages_sendReaction req,Callback callback){sent.add(req);callbacks.add(callback);}
}''',
'org/telegram/messenger/UserConfig.java': '''package org.telegram.messenger;public class UserConfig {
 public static final int MAX_ACCOUNT_COUNT=3;static final UserConfig[] instances={new UserConfig(),new UserConfig(),new UserConfig()};
 public long owner=42;public boolean active=true;public static UserConfig getInstance(int a){return instances[a];}public boolean isClientActivated(){return active;}public long getClientUserId(){return owner;}
}''',
'org/telegram/messenger/SharedConfig.java': '''package org.telegram.messenger;public class SharedConfig {
 public static final int AUTO_REACTION_TARGET_INCOMING=0,AUTO_REACTION_TARGET_OUTGOING=1,AUTO_REACTION_TARGET_BOTH=2;
 public static boolean enabled=false,channels=true,groups=false,privates=false,allowed=true;public static int direction;public static String emoji="👍";public static long sender;public static int account;
 public static boolean isAutoReactionEnabled(){return enabled;}public static String getAutoReactionEmoji(){return emoji;}public static int getAutoReactionTarget(){return direction;}
 public static boolean isAutoReactionUserAllowed(int a,long s){account=a;sender=s;return allowed;}
 public static boolean isAutoReactionChannelsEnabled(){return channels;}public static boolean isAutoReactionGroupsEnabled(){return groups;}public static boolean isAutoReactionPrivateEnabled(){return privates;}
}''',
'org/telegram/messenger/AndroidUtilities.java': '''package org.telegram.messenger;import java.util.*;public class AndroidUtilities {
 public static ArrayList<Runnable> queue=new ArrayList<>();public static void runOnUIThread(Runnable r,int delay){if(delay!=350)throw new AssertionError("reaction delay changed");queue.add(r);}
 public static void flush(){ArrayList<Runnable> pending=new ArrayList<>(queue);queue.clear();for(Runnable r:pending)r.run();}
}''',
'org/telegram/messenger/DialogObject.java': '''package org.telegram.messenger;public class DialogObject {
 public static boolean isEncryptedDialog(long d){return d>0&&(d&0x4000000000000000L)!=0;}public static boolean isUserDialog(long d){return d>0&&!isEncryptedDialog(d)&&(d&0x2000000000000000L)==0;}public static boolean isChatDialog(long d){return d<0;}
}''',
'org/telegram/messenger/ChatObject.java': '''package org.telegram.messenger;import org.telegram.tgnet.TLRPC;public class ChatObject {public static boolean isChannelAndNotMegaGroup(TLRPC.Chat c){return c.channel&&!c.megagroup;}public static boolean isMonoForum(TLRPC.Chat c){return c!=null&&c.monoforum;}}''',
'org/telegram/messenger/MessagesController.java': '''package org.telegram.messenger;import java.util.*;import org.telegram.tgnet.TLRPC;public class MessagesController {
 static final MessagesController[] instances={new MessagesController(),new MessagesController(),new MessagesController()};public static MessagesController getInstance(int a){return instances[a];}
 public final Map<Long,TLRPC.Chat> chats=new HashMap<>();public boolean missingPeer;public int updates;
 public TLRPC.Chat getChat(long id){return chats.get(id);}public TLRPC.InputPeer getInputPeer(long id){if(missingPeer)return null;TLRPC.Chat c=chats.get(-id);return c!=null&&c.channel?new TLRPC.TL_inputPeerChannel(id):new TLRPC.InputPeer(id);}
 public TLRPC.InputPeer getInputPeer(TLRPC.Peer peer){return missingPeer?null:new TLRPC.TL_inputPeerChannel(peer.id);}
 public void processUpdates(TLRPC.Updates u,boolean q){updates++;}
}''',
'org/telegram/messenger/MessageObject.java': '''package org.telegram.messenger;import org.telegram.tgnet.TLRPC;public class MessageObject {
 public int currentAccount,id;public long dialog,sender=77,from;public boolean sending,sponsored,out;public TLRPC.Message messageOwner=new TLRPC.Message();
 public long getDialogId(){return dialog;}public long getFromChatId(){return from;}public int getId(){return id;}public boolean isOutOwner(){return out;}public boolean isSending(){return sending;}public boolean isSponsored(){return sponsored;}public long getSenderId(){return sender;}
}''',
}
TEST = r'''
package org.telegram.messenger;
import java.util.*;
import org.telegram.tgnet.*;
public class AutoReactionScopeTest {
 static int checks,nextId=1;
 static void check(boolean result,String reason){checks++;if(!result)throw new AssertionError(reason);}
 static MessageObject message(int account,long dialog){MessageObject m=new MessageObject();m.currentAccount=account;m.dialog=dialog;m.id=nextId++;return m;}
 static void submit(MessageObject m){AutoReactionManager.getInstance(m.currentAccount).processNewMessages(new ArrayList<>(Arrays.asList(m)));}
 static int sent(int account){return ConnectionsManager.getInstance(account).sent.size();}
 static void expect(MessageObject m,boolean sends,String reason){int before=sent(m.currentAccount);submit(m);AndroidUtilities.flush();check(sent(m.currentAccount)==before+(sends?1:0),reason);}
 static void cancelled(MessageObject m,Runnable change,String reason){int account=m.currentAccount,before=sent(account);submit(m);check(!AndroidUtilities.queue.isEmpty(),reason+" was initially queued");change.run();AndroidUtilities.flush();check(sent(account)==before,reason);}
 static void chat(int account,long id,boolean channel,boolean group){TLRPC.Chat c=new TLRPC.Chat();c.id=id;c.channel=channel;c.megagroup=group;MessagesController.getInstance(account).chats.put(id,c);}
 public static void main(String[] args){
  chat(0,10,true,false);chat(0,20,true,true);chat(0,30,false,false);chat(1,10,true,false);UserConfig.getInstance(1).owner=142;
  expect(message(0,-10),false,"master off suppresses enabled channel scope");
  check(AndroidUtilities.queue.isEmpty(),"master off does not schedule a reaction");
  SharedConfig.enabled=true;
  expect(message(0,-10),true,"manual master enable activates default channel scope");
  chat(0,40,true,false);MessagesController.getInstance(0).getChat(40).monoforum=true;
  expect(message(0,-40),false,"channel direct messages remain disabled under channel-only default scope");
  SharedConfig.groups=true;expect(message(0,-40),false,"group scope cannot enable channel direct messages");SharedConfig.groups=false;
  SharedConfig.privates=true;SharedConfig.channels=false;expect(message(0,-40),true,"private scope independently enables channel direct messages");
  cancelled(message(0,-40),()->SharedConfig.privates=false,"disabling private scope cancels delayed channel direct-message reaction");SharedConfig.channels=true;
  expect(message(0,-20),false,"supergroups excluded until group scope enabled");
  expect(message(0,-30),false,"ordinary groups excluded until group scope enabled");
  expect(message(0,500),false,"private chats excluded until private scope enabled");
  SharedConfig.groups=true;expect(message(0,-20),true,"enabled group scope includes supergroups");expect(message(0,-30),true,"enabled group scope includes ordinary groups");
  SharedConfig.privates=true;expect(message(0,500),true,"enabled private scope reacts");
  SharedConfig.channels=false;expect(message(0,-10),false,"channel scope independently disabled");expect(message(0,-20),true,"disabling channels does not disable groups");SharedConfig.channels=true;
  expect(message(0,0),false,"invalid zero dialog excluded");expect(message(0,0x4000000000000001L),false,"encrypted chats excluded");expect(message(0,0x2000000000000001L),false,"folder dialog excluded");expect(message(0,-999),false,"unknown chat type excluded until cached");
  MessageObject outgoing=message(0,-10);outgoing.out=true;expect(outgoing,false,"incoming direction excludes outgoing");
  SharedConfig.direction=SharedConfig.AUTO_REACTION_TARGET_OUTGOING;expect(message(0,-10),false,"outgoing direction excludes incoming");expect(outgoing,true,"outgoing direction enables sent message");
  SharedConfig.direction=SharedConfig.AUTO_REACTION_TARGET_BOTH;expect(message(0,-10),true,"both direction includes incoming");MessageObject outgoing2=message(0,-10);outgoing2.out=true;
  int before=sent(0);AutoReactionManager.getInstance(0).processSentMessage(outgoing2);AndroidUtilities.flush();check(sent(0)==before+1,"sent-message hook shares scope enforcement");
  SharedConfig.allowed=false;expect(message(0,-10),false,"existing sender restriction retained");SharedConfig.allowed=true;
  MessageObject sender=message(1,-10);sender.sender=123;expect(sender,true,"account-specific sender allowed");check(SharedConfig.account==1&&SharedConfig.sender==123,"sender restriction evaluated for message account");
  MessageObject wrongAccount=message(1,-10);before=sent(0);AutoReactionManager.getInstance(0).processSentMessage(wrongAccount);AndroidUtilities.flush();check(sent(0)==before,"message from another account cannot schedule");
  MessageObject nullOwner=message(0,-10);nullOwner.messageOwner=null;expect(nullOwner,false,"missing message owner ignored");
  before=sent(0);AutoReactionManager.getInstance(0).processNewMessages(null);AutoReactionManager.getInstance(0).processNewMessages(new ArrayList<>());AutoReactionManager.getInstance(0).processSentMessage(null);AndroidUtilities.flush();check(sent(0)==before,"null and empty inputs ignored");
  MessageObject invalid=message(0,-10);invalid.id=0;expect(invalid,false,"local message without server id ignored");
  MessageObject sending=message(0,-10);sending.sending=true;expect(sending,false,"sending message ignored");
  MessageObject sponsored=message(0,-10);sponsored.sponsored=true;expect(sponsored,false,"sponsored message ignored");
  MessageObject service=message(0,-10);service.messageOwner.action=new Object();expect(service,false,"service message ignored");
  MessageObject old=message(0,-10);old.messageOwner.date=399;expect(old,false,"old history ignored");
  MessageObject duplicate=message(0,-10);before=sent(0);submit(duplicate);submit(duplicate);AndroidUtilities.flush();check(sent(0)==before+1,"duplicate updates queue one reaction");expect(duplicate,false,"processed update is not sent again");
  cancelled(message(0,-10),()->SharedConfig.enabled=false,"master disabled during delay cancels send");SharedConfig.enabled=true;
  cancelled(message(0,-10),()->SharedConfig.channels=false,"channel disabled during delay cancels send");SharedConfig.channels=true;
  cancelled(message(0,-20),()->SharedConfig.groups=false,"group disabled during delay cancels send");SharedConfig.groups=true;
  cancelled(message(0,500),()->SharedConfig.privates=false,"private disabled during delay cancels send");SharedConfig.privates=true;
  cancelled(message(0,-10),()->SharedConfig.allowed=false,"sender restriction changed during delay cancels send");SharedConfig.allowed=true;
  cancelled(message(0,-10),()->SharedConfig.direction=SharedConfig.AUTO_REACTION_TARGET_OUTGOING,"direction changed during delay cancels send");SharedConfig.direction=SharedConfig.AUTO_REACTION_TARGET_BOTH;
  cancelled(message(0,-10),()->UserConfig.getInstance(0).active=false,"logout during delay cancels send");UserConfig.getInstance(0).active=true;
  MessageObject switched=message(0,-10);cancelled(switched,()->UserConfig.getInstance(0).owner=43,"replacement login during delay cancels send");
  expect(switched,true,"new owner has independent deduplication state");UserConfig.getInstance(0).owner=42;
  UserConfig.getInstance(0).active=false;expect(message(0,-10),false,"logged-out account cannot queue");UserConfig.getInstance(0).active=true;
  UserConfig.getInstance(0).owner=0;expect(message(0,-10),false,"missing login owner cannot queue");UserConfig.getInstance(0).owner=42;
  MessageObject rebinding=message(0,-10);cancelled(rebinding,()->rebinding.currentAccount=1,"rebound message cannot cross account at send time");
  MessageObject elapsed=message(0,-10);cancelled(elapsed,()->ConnectionsManager.now=2000,"message becoming stale before send is skipped");ConnectionsManager.now=1000;
  MessageObject scopeChange=message(0,-10);SharedConfig.groups=false;cancelled(scopeChange,()->MessagesController.getInstance(0).getChat(10).megagroup=true,"changed chat type is rechecked before send");MessagesController.getInstance(0).getChat(10).megagroup=false;SharedConfig.groups=true;
  MessageObject thread=message(0,-20);thread.from=-10;thread.messageOwner.isThreadMessage=true;thread.messageOwner.fwd_from=new TLRPC.Fwd();thread.messageOwner.fwd_from.saved_from_msg_id=777;
  SharedConfig.channels=false;expect(thread,false,"thread cannot redirect reaction into disabled channel");SharedConfig.channels=true;
  expect(thread,true,"thread with both source and destination enabled sends");TLRPC.TL_messages_sendReaction req=ConnectionsManager.getInstance(0).sent.get(sent(0)-1);
  check(req.peer.id==-10&&req.msg_id==777,"thread reaction retains original post destination");check(req.reaction.size()==1&&req.reaction.get(0).emoticon.equals("👍")&&(req.flags&1)!=0,"reaction payload preserved");
  MessagesController.getInstance(0).missingPeer=true;expect(message(0,-10),false,"unavailable peer cannot submit request");MessagesController.getInstance(0).missingPeer=false;
  SharedConfig.emoji="";expect(message(0,-10),false,"empty emoji cannot submit request");SharedConfig.emoji="👍";
  MessageObject pending=message(0,-10);expect(pending,true,"request issued for current owner");ConnectionsManager.Callback callback=ConnectionsManager.getInstance(0).callbacks.get(sent(0)-1);
  int updates=MessagesController.getInstance(0).updates;UserConfig.getInstance(0).owner=44;callback.run(new TLRPC.Updates(),null);check(MessagesController.getInstance(0).updates==updates,"late response ignored after account replacement");
  UserConfig.getInstance(0).owner=42;callback.run(new TLRPC.Updates(),null);check(MessagesController.getInstance(0).updates==updates+1,"active owner processes reaction update");
  System.out.println("PASS: "+checks+" production auto-reaction scope/delay/account checks");
 }
}
'''
with tempfile.TemporaryDirectory(prefix='mango-reaction-scope-') as directory:
    base = Path(directory)
    files = []
    for name, content in FIXTURES.items():
        target = base / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content)
        files.append(target)
    target = base / 'org/telegram/messenger/AutoReactionScopeTest.java'
    target.write_text(TEST)
    files.append(target)
    files.append(ROOT / 'TMessagesProj/src/main/java/org/telegram/messenger/AutoReactionManager.java')
    subprocess.run([str(JAVA / 'javac'), '-d', str(base / 'classes'), *map(str, files)], check=True)
    subprocess.run([str(JAVA / 'java'), '-cp', str(base / 'classes'), 'org.telegram.messenger.AutoReactionScopeTest'], check=True)
