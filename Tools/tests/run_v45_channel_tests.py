#!/usr/bin/env python3
"""Offline behavioral coverage of the real feedback-channel parser and joining scheduler."""
from pathlib import Path
import ast, os, subprocess, tempfile
root=Path(__file__).resolve().parents[2]
java=Path(os.environ.get('JAVA_HOME','/Applications/Android Studio.app/Contents/jbr/Contents/Home'))/'bin'
tree=ast.parse((root/'Tools/tests/run_scam_keywords_tests.py').read_text())
all_fixtures=ast.literal_eval(next(n.value for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='FIXTURES' for t in n.targets)))
needed=['android/content/SharedPreferences.java','android/content/Context.java','android/content/ComponentName.java','android/os/SystemClock.java','android/app/job/JobInfo.java','android/app/job/JobScheduler.java','org/telegram/messenger/ApplicationLoader.java','org/telegram/messenger/UserConfig.java','org/telegram/messenger/DispatchQueue.java','org/telegram/messenger/AndroidUtilities.java']
fixtures={name:all_fixtures[name] for name in needed}
fixtures['org/telegram/messenger/UserConfig.java']=fixtures['org/telegram/messenger/UserConfig.java'].replace('public static int[] getActivatedAccounts()','public org.telegram.tgnet.TLRPC.User getCurrentUser(){org.telegram.tgnet.TLRPC.User u=new org.telegram.tgnet.TLRPC.User();u.id=owner;return u;}public static int[] getActivatedAccounts()')
fixtures.update({
'android/text/TextUtils.java':'package android.text;public class TextUtils {public static boolean isEmpty(String s){return s==null||s.isEmpty();}}',
'org/telegram/messenger/FileLog.java':'package org.telegram.messenger;public class FileLog {public static void e(Object e){}}',
'org/telegram/tgnet/TLRPC.java':'''package org.telegram.tgnet;public class TLRPC {public static class User {public long id;}public static class Chat {public long id;public boolean left,megagroup;public String username;}public static class TL_error {public String text;}}''',
'org/telegram/messenger/ChatObject.java':'package org.telegram.messenger;public class ChatObject {public static boolean isChannelAndNotMegaGroup(org.telegram.tgnet.TLRPC.Chat c){return c!=null&&!c.megagroup;}public static boolean isNotInChat(org.telegram.tgnet.TLRPC.Chat c){return c==null||c.left;}}',
'org/telegram/messenger/MessagesController.java':'''package org.telegram.messenger;
import java.util.*;import java.util.function.Consumer;import org.telegram.tgnet.TLRPC;
public class MessagesController {
 public static final MessagesController[] all={new MessagesController(),new MessagesController(),new MessagesController()};
 public final HashMap<Long,TLRPC.Chat> chats=new HashMap<>();public final ArrayList<Long> joined=new ArrayList<>();public final ArrayList<Runnable> pending=new ArrayList<>();
 public boolean delayed;public String error;public int resolveCalls;public static MessagesController getInstance(int a){return all[a];}
 public TLRPC.Chat getChat(long id){return chats.get(id);}public Resolver getUserNameResolver(){return new Resolver();}
 public final class Resolver {public Runnable resolve(String username,Consumer<Long> done){resolveCalls++;Long id=null;for(TLRPC.Chat c:chats.values())if(c.username.equals(username))id=-c.id;final Long peer=id;Runnable work=()->done.accept(peer);if(delayed)pending.add(work);else work.run();return null;}}
 public interface ErrorDelegate {boolean run(TLRPC.TL_error e);}
 public void addUserToChat(long chatId,TLRPC.User user,int n,String hash,Object f,boolean ignore,Runnable done,ErrorDelegate fail){
  if(error!=null){TLRPC.TL_error e=new TLRPC.TL_error();e.text=error;fail.run(e);return;}joined.add(chatId);chats.get(chatId).left=false;done.run();
 }
 public void reset(){chats.clear();joined.clear();pending.clear();delayed=false;error=null;resolveCalls=0;}
}'''
})
with tempfile.TemporaryDirectory(prefix='mango-v45-channels-') as tmp:
 base=Path(tmp);sources=[]
 for name,source in fixtures.items():
  p=base/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source);sources.append(p)
 sources += [root/'TMessagesProj/src/main/java/org/telegram/messenger/MangoAutoJoinChannels.java',root/'Tools/tests/V45ChannelTest.java']
 subprocess.run([str(java/'javac'),'-d',str(base/'classes')]+list(map(str,sources)),check=True)
 subprocess.run([str(java/'java'),'-cp',str(base/'classes'),'org.telegram.messenger.V45ChannelTest'],check=True)
