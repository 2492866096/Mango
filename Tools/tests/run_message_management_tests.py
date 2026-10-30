#!/usr/bin/env python3
"""Run production message-preference and tag regressions without Android or network."""
from pathlib import Path
import os
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SDK = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Library/Android/sdk')))
JAVA = Path(os.environ.get('JAVA_HOME', '/Applications/Android Studio.app/Contents/jbr/Contents/Home')) / 'bin'
FIXTURES = {
'android/content/SharedPreferences.java': '''package android.content; import java.util.Map; public interface SharedPreferences { String getString(String k,String d); int getInt(String k,int d); boolean getBoolean(String k,boolean d); Map<String,?> getAll(); Editor edit(); interface Editor { Editor putString(String k,String v); Editor putInt(String k,int v); Editor putBoolean(String k,boolean v); Editor remove(String k); void apply(); } }''',
'android/content/Context.java': '''package android.content; import java.util.*; public class Context { public static final int MODE_PRIVATE=0; final Map<String,SharedPreferences> prefs=new HashMap<>(); public SharedPreferences getSharedPreferences(String k,int m){return prefs.computeIfAbsent(k,v->new Prefs());} static class Prefs implements SharedPreferences, SharedPreferences.Editor { final Map<String,Object> m=new HashMap<>(); public String getString(String k,String d){return (String)m.getOrDefault(k,d);} public int getInt(String k,int d){return (Integer)m.getOrDefault(k,d);} public boolean getBoolean(String k,boolean d){return (Boolean)m.getOrDefault(k,d);} public Map<String,?> getAll(){return new HashMap<>(m);} public Editor edit(){return this;} public Editor putString(String k,String v){m.put(k,v);return this;} public Editor putInt(String k,int v){m.put(k,v);return this;} public Editor putBoolean(String k,boolean v){m.put(k,v);return this;} public Editor remove(String k){m.remove(k);return this;} public void apply(){} } }''',
'android/text/TextUtils.java': '''package android.text; public class TextUtils { public static boolean isEmpty(CharSequence v){return v==null||v.length()==0;} }''',
'org/telegram/messenger/ApplicationLoader.java': '''package org.telegram.messenger; public class ApplicationLoader {public static android.content.Context applicationContext=new android.content.Context();}''',
'org/telegram/messenger/UserConfig.java': '''package org.telegram.messenger; public class UserConfig {static UserConfig[] a={new UserConfig(),new UserConfig()}; public long id; public static UserConfig getInstance(int i){return a[i];} public long getClientUserId(){return id;} }''',
'org/telegram/messenger/MangoSettings.java': '''package org.telegram.messenger; public class MangoSettings {public static final boolean[] blockReactions=new boolean[2]; public static boolean getBoolean(int account,String key){return key.equals("disable_double_tap_reactions")&&blockReactions[account];}}''',
'org/telegram/messenger/NotificationCenter.java': '''package org.telegram.messenger; public class NotificationCenter { public static final int updateInterfaces=1; public static NotificationCenter getInstance(int i){return new NotificationCenter();} public void postNotificationName(int i,Object... args){} }''',
'org/telegram/messenger/LocaleController.java': '''package org.telegram.messenger; public class LocaleController { public static String getString(int i){return "String"+i;} }''',
'org/telegram/messenger/R.java': '''package org.telegram.messenger; public class R { public static class string { public static int MessageTagImportant=1,MessageTagTodo=2,MessageTagReply=3,MessageTagFavorite=4,SecretChat=5,MessageTagPrivateMessage=6; } }''',
'org/telegram/tgnet/TLRPC.java': '''package org.telegram.tgnet; public class TLRPC { public static class Message {public int date;} public static class Chat {public String title;} public static class User {} }''',
'org/telegram/messenger/MessageObject.java': '''package org.telegram.messenger; import org.telegram.tgnet.TLRPC; public class MessageObject { public TLRPC.Message messageOwner=new TLRPC.Message(); public CharSequence caption,messageText; public long dialog,topic; public int id; public boolean secret; public long getDialogId(){return dialog;} public long getTopicId(){return topic;} public int getId(){return id;} public boolean isSecretMedia(){return secret;} }''',
'org/telegram/messenger/MessagesController.java': '''package org.telegram.messenger; import org.telegram.tgnet.TLRPC; public class MessagesController {public static MessagesController getInstance(int a){return new MessagesController();} public TLRPC.Chat getChat(long id){return null;} public TLRPC.User getUser(long id){return null;}}''',
'org/telegram/messenger/DialogObject.java': '''package org.telegram.messenger; public class DialogObject {public static boolean isEncryptedDialog(long id){return id>1000000000000L;}}''',
'org/telegram/messenger/UserObject.java': '''package org.telegram.messenger; import org.telegram.tgnet.TLRPC; public class UserObject {public static String getUserName(TLRPC.User u){return "User";}}''',
'org/telegram/messenger/FileLog.java': '''package org.telegram.messenger; public class FileLog {public static void e(Throwable e){throw new AssertionError(e);}}''',
'org/telegram/messenger/MessageManagementRegression.java': '''package org.telegram.messenger;
import java.util.*;
public class MessageManagementRegression {
 static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
 static MessageTagsController.Entry entry(long dialog,long topic,int message){MessageTagsController.Entry e=new MessageTagsController.Entry();e.dialogId=dialog;e.topicId=topic;e.messageId=message;e.title="chat";e.preview="preview";return e;}
 public static void main(String[] args){
 UserConfig.getInstance(0).id=100;UserConfig.getInstance(1).id=200;
 check(MessageActionSettings.getDoubleTapAction(0)==0,"Existing reaction default");
 MangoSettings.blockReactions[0]=true;
 check(MessageActionSettings.getDoubleTapAction(0)==2,"Disable double-tap reactions blocks the reaction gesture");
 check(MessageActionSettings.getDoubleTapAction(1)==0,"Reaction switch is per account");
 MessageActionSettings.setDoubleTapAction(0,1);
 check(MessageActionSettings.getDoubleTapAction(0)==1,"Double-tap reply remains available while reactions disabled");
 MessageActionSettings.setDoubleTapAction(0,0);MangoSettings.blockReactions[0]=false;
 check(MessageActionSettings.getDoubleTapAction(0)==0,"Turning the switch back off restores saved reaction action");
 MessageActionSettings.setDoubleTapAction(0,1);MessageActionSettings.setCenterEnabled(0,true);
 check(MessageActionSettings.getDoubleTapAction(1)==0&&!MessageActionSettings.isCenterEnabled(1),"Interaction preferences separated by account");
 MessageActionSettings.setMenuOrder(0,new int[]{3,2,1,0});
 ArrayList<Integer> options=new ArrayList<>(Arrays.asList(8,3,99,2,127,128,1));
 ArrayList<Integer> icons=new ArrayList<>();ArrayList<CharSequence> labels=new ArrayList<>();for(int o:options){icons.add(1000+o);labels.add("option"+o);}
 MessageActionSettings.reorderMenu(0,icons,labels,options,3,2,127,128);
 check(options.equals(Arrays.asList(8,128,99,127,2,3,1)),"Ordering applied while unrelated items keep position");
 for(int i=0;i<options.size();i++){check(icons.get(i)==1000+options.get(i)&&labels.get(i).equals("option"+options.get(i)),"Icons and labels travel with actions");}
 options=new ArrayList<>(Arrays.asList(8,127,128));icons=new ArrayList<>(Arrays.asList(8,127,128));labels=new ArrayList<>(Arrays.asList("reply","favorite","todo"));
 MessageActionSettings.reorderMenu(0,icons,labels,options,3,2,127,128);check(options.equals(Arrays.asList(8,128,127)),"Absent copy/forward supported");
 MessageActionSettings.setMenuOrder(0,new int[]{3,3,1,0});check(Arrays.equals(MessageActionSettings.getMenuOrder(0),new int[]{3,2,1,0}),"Reject invalid order");
 MessageTagsController.Entry a=entry(-50,1,10),b=entry(-60,1,10),c=entry(-50,2,10);
 MessageTagsController.setTags(0,a,MessageTagsController.IMPORTANT|MessageTagsController.FAVORITE);MessageTagsController.setTags(0,b,MessageTagsController.TODO);MessageTagsController.setTags(0,c,MessageTagsController.REPLY);
 check(MessageTagsController.getEntries(0,0).size()==3,"Dialog and topic prevent message-id collisions");
 check(MessageTagsController.getEntries(0,MessageTagsController.FAVORITE).size()==1,"Filter combined tags");
 check(MessageTagsController.getEntries(1,0).isEmpty(),"Tags separated by account");
 UserConfig.getInstance(0).id=300;check(MessageTagsController.getEntries(0,0).isEmpty()&&MessageActionSettings.getDoubleTapAction(0)==0,"Reused login slot does not expose prior user's data");
 UserConfig.getInstance(0).id=100;check(MessageTagsController.getTags(0,a)==9,"Prior user retains tags");
 MessageTagsController.setTags(0,a,0);check(MessageTagsController.getEntries(0,0).size()==2,"Clearing removes index entry");
 MessageObject secret=new MessageObject();secret.id=2;secret.dialog=2000000000000L;secret.messageText="secret plaintext";
 check(!MessageTagsController.fromMessage(0,secret).preview.contains("secret plaintext"),"Secret plaintext is not duplicated");
 System.out.println("PASS: account isolation, account-slot reuse, multi-tag filtering, dialog/topic identity, tag removal, secret preview, menu alignment and missing actions");
 }
}'''
}

def main():
    json_sources=sorted(SDK.glob('sources/android-*/org/json/JSONObject.java'))
    if not json_sources: raise SystemExit('Android SDK sources are required')
    with tempfile.TemporaryDirectory(prefix='message-management-tests-') as directory:
        tmp=Path(directory)
        for name,code in FIXTURES.items():
            target=tmp/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(code)
        json_target=tmp/'org/json';json_target.mkdir(parents=True)
        for source in json_sources[-1].parent.glob('*.java'):
            code=re.sub(r'^import (?:static )?(?:android\.|libcore\.).*;\n','',source.read_text(),flags=re.M)
            code=re.sub(r'@(?:UnsupportedAppUsage|SystemApi|NonNull|Nullable)(?:\([^)]*\))?','',code)
            (json_target/source.name).write_text(code)
        sources=list(tmp.rglob('*.java'))+[ROOT/'TMessagesProj/src/main/java/org/telegram/messenger'/name for name in ('MessageActionSettings.java','MessageTagsController.java')]
        subprocess.run([str(JAVA/'javac'),'-d',str(tmp/'classes'),*map(str,sources)],check=True)
        subprocess.run([str(JAVA/'java'),'-cp',str(tmp/'classes'),'org.telegram.messenger.MessageManagementRegression'],check=True)
        aapt=sorted(SDK.glob('build-tools/*/aapt2'))
        if aapt:
            for qualifier in ('values','values-zh'):
                subprocess.run([str(aapt[-1]),'compile',str(ROOT/'TMessagesProj/src/main/res'/qualifier/'message_management.xml'),'-o',str(tmp)],check=True)
            print('PASS: English and Chinese message management resources compile with AAPT2')

if __name__=='__main__':main()
