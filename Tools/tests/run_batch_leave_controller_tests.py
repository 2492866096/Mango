#!/usr/bin/env python3
"""Run the production channel exit controller against captured, offline RPC callbacks."""
from pathlib import Path
import os, subprocess, tempfile
ROOT = Path(__file__).resolve().parents[2]
JAVA = Path(os.environ.get('JAVA_HOME','/Applications/Android Studio.app/Contents/jbr/Contents/Home'))/'bin'
fixtures = {
'org/telegram/tgnet/TLObject.java': 'package org.telegram.tgnet; public class TLObject {}',
'org/telegram/tgnet/TLRPC.java': '''package org.telegram.tgnet;
import java.util.*;
public class TLRPC {
 public static class Chat { public long id; public String title; public boolean creator,left,kicked,deactivated,monoforum,channel; public Object migrated_to; }
 public static class TL_chatForbidden extends Chat {} public static class TL_channelForbidden extends Chat {}
 public static class Dialog { public long id; public int top_message; public boolean pinned; }
 public static class TL_dialogFolder extends Dialog {}
 public static class User {} public static class Message { public long dialog_id; public int id,date; }
 public static class InputPeer {public long id;} public static class TL_inputPeerEmpty extends InputPeer {}
 public static class InputChannel {public long id;}
 public static class TL_inputUserSelf {}
 public static class TL_error { public String text; public TL_error(String s){text=s;} }
 public static class Updates extends TLObject {}
 public static class messages_Dialogs extends TLObject {
 public ArrayList<User> users=new ArrayList<>(); public ArrayList<Chat> chats=new ArrayList<>(); public ArrayList<Dialog> dialogs=new ArrayList<>(); public ArrayList<Message> messages=new ArrayList<>(); }
 public static class TL_messages_dialogs extends messages_Dialogs {}
 public static class TL_messages_dialogsSlice extends messages_Dialogs {}
 public static class TL_messages_getDialogs extends TLObject {public int flags,folder_id,limit,offset_id,offset_date;public boolean exclude_pinned;public InputPeer offset_peer;}
 public static class TL_channels_leaveChannel extends TLObject {public InputChannel channel;}
 public static class TL_messages_deleteChatUser extends TLObject {public long chat_id;public TL_inputUserSelf user_id;public boolean revoke_history;}
}''',
'org/telegram/tgnet/ConnectionsManager.java': '''package org.telegram.tgnet; import java.util.*;
public class ConnectionsManager {
 public static final int RequestFlagInvokeAfter=1;
 public interface Callback {void run(TLObject response, TLRPC.TL_error error);}
 public final ArrayList<TLObject> requests=new ArrayList<>(); public final ArrayList<Callback> callbacks=new ArrayList<>();
 static final ConnectionsManager[] all={new ConnectionsManager(),new ConnectionsManager()}; public static ConnectionsManager getInstance(int a){return all[a];}
 public void sendRequest(TLObject req,Callback callback){requests.add(req);callbacks.add(callback);} public void sendRequest(TLObject req,Callback callback,int f){sendRequest(req,callback);}
 public void answer(int index,TLObject response,String error){callbacks.get(index).run(response,error==null?null:new TLRPC.TL_error(error));}
}''',
'org/telegram/messenger/UserConfig.java': '''package org.telegram.messenger;public class UserConfig {
 public static final int MAX_ACCOUNT_COUNT=2;static final UserConfig[] all={new UserConfig(),new UserConfig()};public long id=123;public boolean active=true;
 public static UserConfig getInstance(int a){return all[a];} public long getClientUserId(){return id;}public boolean isClientActivated(){return active;}
}''',
'org/telegram/messenger/AndroidUtilities.java': '''package org.telegram.messenger;public class AndroidUtilities {public static void runOnUIThread(Runnable r){r.run();}public static void runOnUIThread(Runnable r,long ms){r.run();}}''',
'org/telegram/messenger/ChatObject.java': '''package org.telegram.messenger;import org.telegram.tgnet.TLRPC;public class ChatObject {public static boolean isChannel(TLRPC.Chat c){return c.channel;}}''',
'org/telegram/messenger/DialogObject.java': '''package org.telegram.messenger;import org.telegram.tgnet.TLRPC;public class DialogObject {public static void initDialog(TLRPC.Dialog d){}}''',
'org/telegram/messenger/MessageObject.java': '''package org.telegram.messenger;import org.telegram.tgnet.TLRPC;public class MessageObject {public static long getDialogId(TLRPC.Message m){return m.dialog_id;}}''',
'org/telegram/messenger/MessagesStorage.java': '''package org.telegram.messenger;import org.telegram.tgnet.TLRPC;import java.util.*;public class MessagesStorage {static final MessagesStorage INSTANCE=new MessagesStorage();public static MessagesStorage getInstance(int a){return INSTANCE;}public void putUsersAndChats(ArrayList<TLRPC.User> u,ArrayList<TLRPC.Chat> c,boolean a,boolean b){}}''',
'org/telegram/messenger/MessagesController.java': '''package org.telegram.messenger;import java.util.*;import org.telegram.tgnet.TLRPC;public class MessagesController {
 static final MessagesController[] all={new MessagesController(),new MessagesController()};public static MessagesController getInstance(int a){return all[a];}
 public final ArrayList<TLRPC.Dialog> dialogs=new ArrayList<>();public final HashMap<Long,TLRPC.Chat> chats=new HashMap<>();public final ArrayList<Long> deleted=new ArrayList<>();public int updates;
 public ArrayList<TLRPC.Dialog> getAllDialogs(){return dialogs;}public TLRPC.Chat getChat(long id){return chats.get(id);}public void putUsers(ArrayList<TLRPC.User> u,boolean c){}public void putChats(ArrayList<TLRPC.Chat> list,boolean c){for(TLRPC.Chat chat:list)chats.put(chat.id,chat);}public void putChat(TLRPC.Chat c,boolean b){chats.put(c.id,c);}
 public TLRPC.InputPeer getInputPeer(long id){TLRPC.InputPeer p=new TLRPC.InputPeer();p.id=id;return p;}public static TLRPC.InputChannel getInputChannel(TLRPC.Chat chat){TLRPC.InputChannel c=new TLRPC.InputChannel();c.id=chat.id;return c;}
 public void processUpdates(TLRPC.Updates u,boolean c){updates++;}public void deleteDialog(long id,int mode){if(mode!=3)throw new AssertionError("Must delete locally only");deleted.add(id);}
}''',
'ControllerRegression.java': '''import java.util.*;import org.telegram.tgnet.*;import org.telegram.messenger.*;
public class ControllerRegression {
 static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static TLRPC.Chat chat(long id,boolean channel){TLRPC.Chat c=new TLRPC.Chat();c.id=id;c.channel=channel;c.title="Chat "+id;return c;}
 static void add(TLRPC.messages_Dialogs page,TLRPC.Chat c,boolean pinned){page.chats.add(c);TLRPC.Dialog d=new TLRPC.Dialog();d.id=-c.id;d.top_message=42;d.pinned=pinned;page.dialogs.add(d);TLRPC.Message m=new TLRPC.Message();m.dialog_id=d.id;m.id=42;m.date=100;page.messages.add(m);}
 public static void main(String[] args){
 ChannelBatchLeaveController c=ChannelBatchLeaveController.getInstance(0);ConnectionsManager n=ConnectionsManager.getInstance(0);MessagesController m=MessagesController.getInstance(0);
 c.refresh();check(n.requests.get(0) instanceof TLRPC.TL_messages_getDialogs,"Opening only fetches memberships");
 TLRPC.TL_messages_dialogsSlice pins=new TLRPC.TL_messages_dialogsSlice();TLRPC.Chat first=chat(1,true);add(pins,first,true);n.answer(0,pins,null);
 check(((TLRPC.TL_messages_getDialogs)n.requests.get(1)).exclude_pinned,"All-pinned page advances to unpinned page");
 TLRPC.TL_messages_dialogsSlice page=new TLRPC.TL_messages_dialogsSlice();TLRPC.Chat second=chat(2,false);add(page,second,false);n.answer(1,page,null);
 TLRPC.TL_messages_getDialogs next=(TLRPC.TL_messages_getDialogs)n.requests.get(2);check(next.offset_id==42&&next.offset_date==100&&next.offset_peer.id==-2,"Pagination cursor uses last peer and its own message");
 TLRPC.TL_messages_dialogs end=new TLRPC.TL_messages_dialogs();TLRPC.Chat owner=chat(3,true);owner.creator=true;add(end,owner,false);n.answer(2,end,null);
 check(((TLRPC.TL_messages_getDialogs)n.requests.get(3)).folder_id==1,"Archived memberships are loaded");
 TLRPC.TL_messages_dialogs archive=new TLRPC.TL_messages_dialogs();TLRPC.Chat archived=chat(4,true);add(archive,archived,false);n.answer(3,archive,null);
 check(!c.isLoading()&&c.getChats().size()==4,"All folders combine");
 check(!ChannelBatchLeaveController.canLeave(owner),"Owned channel excluded from bulk destructive operations");
 c.start(Arrays.asList(3L));check(n.requests.size()==4,"Selecting owner does not issue delete/leave");
 c.start(Arrays.asList(1L,2L));check(n.requests.get(4) instanceof TLRPC.TL_channels_leaveChannel,"Channel uses leaveChannel");check(m.deleted.isEmpty(),"No local removal before server confirmation");
 n.answer(4,null,"CHANNEL_PRIVATE");check(!first.left&&m.deleted.isEmpty(),"Failed exit preserves membership and local dialog");
 TLRPC.TL_messages_deleteChatUser req=(TLRPC.TL_messages_deleteChatUser)n.requests.get(5);check(req.chat_id==2&&req.user_id instanceof TLRPC.TL_inputUserSelf&&!req.revoke_history,"Legacy group only removes self without revoking history");
 n.answer(5,new TLRPC.Updates(),null);check(second.left&&m.deleted.equals(Arrays.asList(-2L)),"Confirmed exit marks left and removes local dialog");
 check(c.queue().result(1).state==BatchLeaveQueue.State.FAILED&&c.queue().result(2).state==BatchLeaveQueue.State.LEFT,"Per-target outcomes preserved");
 c.queue().retryFailures();check(n.requests.size()==7&&n.requests.get(6) instanceof TLRPC.TL_channels_leaveChannel,"Retry resends only failed channel");
 n.answer(6,null,"USER_NOT_PARTICIPANT");check(first.left&&c.queue().result(1).state==BatchLeaveQueue.State.LEFT,"Already-left response treated idempotently");
 c.start(Arrays.asList(4L));UserConfig.getInstance(0).id=999;n.answer(7,new TLRPC.Updates(),null);check(!archived.left&&!m.deleted.contains(-4L),"Stale account callback cannot mutate replacement account");
 check(ChannelBatchLeaveController.getInstance(0)!=c,"Account replacement obtains a fresh controller");
 ChannelBatchLeaveController other=ChannelBatchLeaveController.getInstance(1);ConnectionsManager network=ConnectionsManager.getInstance(1);other.refresh();network.answer(0,null,"TIMEOUT");check(!other.isLoading()&&"TIMEOUT".equals(other.getLoadError()),"Read failures surfaced and loading released");
 System.out.println("PASS: "+checks+" production batch-exit RPC, pagination, confirmation, failure and account-isolation checks");
 }
}'''
}
with tempfile.TemporaryDirectory(prefix='batch-leave-controller-') as directory:
    dest=Path(directory)
    sources=[]
    for name, content in fixtures.items():
        path=dest/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content);sources.append(path)
    source=ROOT/'TMessagesProj/src/main/java/org/telegram/messenger'
    sources += [source/'BatchLeaveQueue.java',source/'ChannelBatchLeaveController.java']
    subprocess.run([str(JAVA/'javac'),'-d',directory,*map(str,sources)],check=True)
    subprocess.run([str(JAVA/'java'),'-cp',directory,'ControllerRegression'],check=True)
