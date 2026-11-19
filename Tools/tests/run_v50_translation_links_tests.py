#!/usr/bin/env python3
"""Execute the production link planner/request lifecycle; remote responses are controlled fixtures."""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
JAVA = Path(os.environ.get('JAVA_HOME', '/Applications/Android Studio.app/Contents/jbr/Contents/Home')) / 'bin'
SOURCE = ROOT / 'TMessagesProj/src/main/java'
alert = (SOURCE / 'org/telegram/ui/Components/TranslateAlert2.java').read_text()
controller = (SOURCE / 'org/telegram/messenger/TranslateController.java').read_text()


def extract(source, marker):
    start = source.index(marker)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


fixtures = {
'org/telegram/messenger/FileLog.java': '''package org.telegram.messenger;public class FileLog {public static void e(Exception e){throw new AssertionError(e);}}''',
'android/os/SystemClock.java': '''package android.os;public class SystemClock {public static long now=1000;public static long elapsedRealtime(){return now;}}''',
'android/net/Uri.java': '''package android.net;public class Uri {public static String encode(String text){return java.net.URLEncoder.encode(text,java.nio.charset.StandardCharsets.UTF_8).replace("+","%20");}public static String decode(String text){return java.net.URLDecoder.decode(text,java.nio.charset.StandardCharsets.UTF_8);}}''',
'android/text/TextUtils.java': '''package android.text;public class TextUtils {public static boolean isEmpty(CharSequence s){return s==null||s.length()==0;}}''',
'org/telegram/messenger/AndroidUtilities.java': '''package org.telegram.messenger;import java.util.*;public class AndroidUtilities {public static final ArrayList<Runnable> posted=new ArrayList<>(),delayed=new ArrayList<>();public static void runOnUIThread(Runnable r){posted.add(r);}public static void runOnUIThread(Runnable r,long delay){delayed.add(r);}public static void cancelRunOnUIThread(Runnable r){posted.remove(r);delayed.remove(r);}public static void flush(){while(!posted.isEmpty())posted.remove(0).run();}public static void expire(){for(Runnable r:new ArrayList<>(delayed))r.run();delayed.clear();}}''',
'org/telegram/messenger/Utilities.java': '''package org.telegram.messenger;public class Utilities {public interface Callback<T>{void run(T value);}public interface Callback2<T,U>{void run(T value,U second);}}''',
'org/telegram/messenger/UserConfig.java': '''package org.telegram.messenger;public class UserConfig {static final UserConfig[] instances={new UserConfig(),new UserConfig()};public long owner=100;public static UserConfig getInstance(int a){return instances[a];}public long getClientUserId(){return owner;}}''',
'org/telegram/messenger/TranslateController.java': '''package org.telegram.messenger;public class TranslateController {public static String normalizeTranslateLanguage(String s){return s==null?"en":s.equalsIgnoreCase("zh-CN")?"zh":s;}}''',
'org/telegram/messenger/MangoSettings.java': '''package org.telegram.messenger;public class MangoSettings {public static String getString(int a,String k,String d){return d;}public static void setString(int a,String k,String v){}}''',
'org/telegram/tgnet/SerializedData.java': r'''
package org.telegram.tgnet;import java.io.*;public class SerializedData {
 ByteArrayOutputStream bytes;DataOutputStream output;DataInputStream input;
 public SerializedData(){bytes=new ByteArrayOutputStream();output=new DataOutputStream(bytes);}public SerializedData(byte[] b){input=new DataInputStream(new ByteArrayInputStream(b));}
 public void writeInt32(int x){try{output.writeInt(x);}catch(IOException e){throw new RuntimeException(e);}}
 public int readInt32(boolean exception){try{return input.readInt();}catch(IOException e){throw new RuntimeException(e);}}
 public void writeInt64(long x){try{output.writeLong(x);}catch(IOException e){throw new RuntimeException(e);}}
 public long readInt64(boolean exception){try{return input.readLong();}catch(IOException e){throw new RuntimeException(e);}}
 public void writeString(String s){try{byte[] b=(s==null?"":s).getBytes(java.nio.charset.StandardCharsets.UTF_8);output.writeInt(b.length);output.write(b);}catch(IOException e){throw new RuntimeException(e);}}
 public String readString(boolean exception){try{return new String(input.readNBytes(input.readInt()),java.nio.charset.StandardCharsets.UTF_8);}catch(IOException e){throw new RuntimeException(e);}}
 public byte[] toByteArray(){return bytes.toByteArray();}public void cleanup(){}
}''',
'org/telegram/tgnet/TLRPC.java': r'''
package org.telegram.tgnet;import java.util.*;public class TLRPC {
 public static class MessageEntity {
  public int offset,length;public String url,language;public long user_id;
  public void serializeToStream(SerializedData d){d.writeInt32(1);d.writeString(getClass().getSimpleName());d.writeInt32(offset);d.writeInt32(length);d.writeString(url);d.writeInt64(user_id);d.writeInt64(this instanceof TL_messageEntityCustomEmoji?((TL_messageEntityCustomEmoji)this).document_id:0);}
  public static MessageEntity TLdeserialize(SerializedData d,int tag,boolean exception){try{MessageEntity e=(MessageEntity)Class.forName("org.telegram.tgnet.TLRPC$"+d.readString(true)).getConstructor().newInstance();e.offset=d.readInt32(true);e.length=d.readInt32(true);e.url=d.readString(true);e.user_id=d.readInt64(true);long id=d.readInt64(true);if(e instanceof TL_messageEntityCustomEmoji)((TL_messageEntityCustomEmoji)e).document_id=id;return e;}catch(Exception e){throw new RuntimeException(e);}}
 }
 public static class TL_messageEntityUrl extends MessageEntity{} public static class TL_messageEntityTextUrl extends MessageEntity{}
 public static class TL_messageEntityEmail extends MessageEntity{} public static class TL_messageEntityMention extends MessageEntity{}
 public static class TL_messageEntityMentionName extends MessageEntity{} public static class TL_messageEntityBold extends MessageEntity{}
 public static class TL_inputMessageEntityMentionName extends MessageEntity{}
 public static class TL_messageEntityCustomEmoji extends MessageEntity {public long document_id;public Object document;}
 public static class TL_textWithEntities {public String text;public ArrayList<MessageEntity> entities=new ArrayList<>();public void serializeToStream(SerializedData d){d.writeString(text);d.writeInt32(entities.size());for(MessageEntity entity:entities)entity.serializeToStream(d);}}
 public static class TL_messages_translateText {public int flags;public String to_lang;public ArrayList<TL_textWithEntities> text=new ArrayList<>();}
 public static class TL_messages_translateResult {public ArrayList<TL_textWithEntities> result=new ArrayList<>();}
 public static class TL_error {public String text;public TL_error(String text){this.text=text;}}
}''',
'org/telegram/tgnet/ConnectionsManager.java': r'''
package org.telegram.tgnet;import java.util.*;public class ConnectionsManager {
 public static final int RequestFlagFailOnServerErrors=1;public interface Callback {void run(Object response,TLRPC.TL_error error);}
 public static class Request {public int account,id;public TLRPC.TL_messages_translateText text;public Callback callback;}
 public static final ArrayList<Request> sent=new ArrayList<>();public static final ArrayList<Integer> canceled=new ArrayList<>();final int account;
 ConnectionsManager(int a){account=a;}public static ConnectionsManager getInstance(int a){return new ConnectionsManager(a);}
 public int sendRequest(TLRPC.TL_messages_translateText text,Callback callback,int flags){Request r=new Request();r.account=account;r.id=sent.size()+1;r.text=text;r.callback=callback;sent.add(r);return r.id;}
 public void cancelRequest(int id,boolean force){canceled.add(id);}
}''',
'org/telegram/ui/Components/TranslateAlert2.java': r'''
package org.telegram.ui.Components;import java.util.*;import android.net.Uri;import org.telegram.tgnet.TLRPC;import org.telegram.messenger.Utilities;
public class TranslateAlert2 {
 public static class Alternative {public String text,from,to;public boolean canceled;public Utilities.Callback2<String,Boolean> done;}
 public static final ArrayList<Alternative> alternatives=new ArrayList<>();
 public static void alternativeTranslate(String text,String from,String to,Utilities.Callback2<String,Boolean> callback){Alternative a=new Alternative();a.text=text;a.from=from;a.to=to;a.done=callback;alternatives.add(a);}
 public static Runnable alternativeTranslateCancelable(String text,String from,String to,Utilities.Callback2<String,Boolean> callback){alternativeTranslate(text,from,to,callback);Alternative a=alternatives.get(alternatives.size()-1);return () -> a.canceled=true;}
 public static TLRPC.TL_textWithEntities preprocess(TLRPC.TL_textWithEntities original,TLRPC.TL_textWithEntities received){return received;}
 //CUT
}''',
'org/telegram/messenger/TranslationLinksTest.java': r'''
package org.telegram.messenger;import java.util.*;import java.lang.reflect.*;import org.telegram.tgnet.*;import org.telegram.ui.Components.TranslateAlert2;
public class TranslationLinksTest {
 static int checks;static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
 static TLRPC.TL_textWithEntities text(String value){TLRPC.TL_textWithEntities result=new TLRPC.TL_textWithEntities();result.text=value;return result;}
 static <T extends TLRPC.MessageEntity>T entity(T entity,int offset,int length){entity.offset=offset;entity.length=length;return entity;}
 static TLRPC.TL_textWithEntities source(String url){TLRPC.TL_textWithEntities src=text("😀 Read Site and https://example.org today");TLRPC.TL_messageEntityTextUrl link=entity(new TLRPC.TL_messageEntityTextUrl(),8,4);link.url=url;src.entities.add(link);src.entities.add(entity(new TLRPC.TL_messageEntityUrl(),17,19));return src;}
 static ArrayList<TLRPC.TL_textWithEntities> translated(String...texts){ArrayList<TLRPC.TL_textWithEntities> result=new ArrayList<>();for(String s:texts)result.add(text(s));return result;}
 static TLRPC.MessageEntity find(TLRPC.TL_textWithEntities src,Class<?> type){for(TLRPC.MessageEntity e:src.entities)if(e.getClass()==type)return e;throw new AssertionError("missing "+type);}
 static String label(TLRPC.TL_textWithEntities src,TLRPC.MessageEntity e){return src.text.substring(e.offset,e.offset+e.length);}
 static void reply(int index,String...values){TLRPC.TL_messages_translateResult res=new TLRPC.TL_messages_translateResult();res.result=translated(values);ConnectionsManager.sent.get(index).callback.run(res,null);AndroidUtilities.flush();}
 static void clear(){MangoTranslationLinks.clear(0);MangoTranslationLinks.clear(1);ConnectionsManager.sent.clear();ConnectionsManager.canceled.clear();TranslateAlert2.alternatives.clear();AndroidUtilities.posted.clear();AndroidUtilities.delayed.clear();}
 @SuppressWarnings("unchecked") static Map<String,?> cache()throws Exception{Field f=MangoTranslationLinks.class.getDeclaredField("cache");f.setAccessible(true);return(Map<String,?>)f.get(null);}
 public static void main(String[] args)throws Exception{
  TLRPC.TL_textWithEntities src=source("https://hidden.example/a?x=😀");
  ManualTranslationEntry manual=new ManualTranslationEntry(src.text,src.entities,null,12,false);
  manual.translate();check(manual.plan!=null&&manual.plan.hasLinks&&manual.plan.requests.size()==3,"real manual constructor and request-source assembly protect hidden links");
  check(manual.reqMessageEntities!=src.entities,"manual constructor snapshots entity list before display");
  check(manual.currentAccount==1,"manual constructor sets supplied account before initial translation");
  TLRPC.TL_textWithEntities manualResult=manual.plan.restore(translated("😀 阅读","和","今天"));
  check(find(manualResult,TLRPC.TL_messageEntityTextUrl.class).url.equals("https://hidden.example/a?x=😀"),"manual constructor to plan keeps the exact hidden click target");
  ManualTranslationEntry plain=new ManualTranslationEntry("ordinary text",null,null,0,false);plain.translate();check(plain.plan==null&&plain.reqMessageEntities.isEmpty(),"manual entry accepts absent entities without activating link path");
  MangoTranslationLinks.Plan plan=MangoTranslationLinks.prepare(src);
  check(plan.hasLinks&&plan.requests.size()==3,"hidden and visible links excluded from three text fragments");
  check(plan.requests.get(0).text.equals("😀 Read")&&plan.requests.get(1).text.equals("and")&&plan.requests.get(2).text.equals("today"),"translation requests contain surrounding text only");
  for(TLRPC.TL_textWithEntities part:plan.requests)check(!part.text.contains("Site")&&!part.text.contains("https://"),"protected label never sent");
  TLRPC.TL_textWithEntities result=plan.restore(translated("😀 阅读","和","今天"));
  check(result.text.equals("😀 阅读 Site 和 https://example.org 今天"),"translated text reassembled in original link order");
  TLRPC.MessageEntity hidden=find(result,TLRPC.TL_messageEntityTextUrl.class),visible=find(result,TLRPC.TL_messageEntityUrl.class);
  check(hidden.offset==6&&hidden.length==4&&hidden.url.equals("https://hidden.example/a?x=😀"),"hidden link UTF-16 offset and exact click target preserved");
  check(label(result,hidden).equals("Site")&&label(result,visible).equals("https://example.org"),"blue link display text preserved");
  check(src.text.startsWith("😀 Read")&&src.entities.get(0).offset==8,"original message and entities never mutated");
  check(plan.linksPreserved(result),"persisted translated link entities pass validation");
  hidden.url="https://wrong.example";check(!plan.linksPreserved(result),"changed hidden link target invalidates old cache");hidden.url="https://hidden.example/a?x=😀";
  result.entities.clear();check(!plan.linksPreserved(result),"legacy translation missing clickable entities is refreshed");
  check(plan.restore(translated("one"))==null,"partial response not assembled into a broken message");
  check(plan.restore(translated("one",null,"three"))==null,"null translated fragment fails as a whole");
  TLRPC.TL_textWithEntities only=text("  https://example.org\n😀  ");MangoTranslationLinks.Plan onlyPlan=MangoTranslationLinks.prepare(only);
  check(onlyPlan.hasLinks&&onlyPlan.requests.isEmpty(),"URL plus emoji and whitespace needs no translation");
  final ArrayList<TLRPC.TL_textWithEntities> delivered=new ArrayList<>();
  MangoTranslationLinks.translate(0,onlyPlan,"zh",false,(v,l)->delivered.add(v));AndroidUtilities.flush();
  check(ConnectionsManager.sent.isEmpty()&&delivered.get(0).text.equals(only.text),"pure links return unchanged with zero requests");
  check(find(delivered.get(0),TLRPC.TL_messageEntityUrl.class).offset==2,"detected raw link becomes clickable");
  // Identical labels retain their distinct targets and custom emoji inside protected labels.
  TLRPC.TL_textWithEntities twins=text("go 😀 next 😀");
  TLRPC.TL_messageEntityTextUrl a=entity(new TLRPC.TL_messageEntityTextUrl(),3,2);a.url="https://a.example";
  TLRPC.TL_messageEntityTextUrl b=entity(new TLRPC.TL_messageEntityTextUrl(),11,2);b.url="https://b.example";
  TLRPC.TL_messageEntityCustomEmoji emoji=entity(new TLRPC.TL_messageEntityCustomEmoji(),3,2);emoji.document_id=77;emoji.document=new Object();
  twins.entities.add(a);twins.entities.add(b);twins.entities.add(emoji);
  TLRPC.TL_textWithEntities twinResult=MangoTranslationLinks.prepare(twins).restore(translated("去","再去"));
  check(twinResult.text.equals("去 😀 再去 😀"),"multiple identical protected emoji labels survive");
  ArrayList<TLRPC.MessageEntity> links=new ArrayList<>();for(TLRPC.MessageEntity e:twinResult.entities)if(e instanceof TLRPC.TL_messageEntityTextUrl)links.add(e);
  check(links.size()==2&&links.get(0).url.equals(a.url)&&links.get(1).url.equals(b.url),"same label keeps separate hidden targets");
  TLRPC.TL_messageEntityCustomEmoji restored=(TLRPC.TL_messageEntityCustomEmoji)find(twinResult,TLRPC.TL_messageEntityCustomEmoji.class);
  check(restored.document_id==77&&restored.document==emoji.document&&restored.offset==2,"nested custom emoji metadata preserved");
  TLRPC.TL_textWithEntities malformed=text("😀 hello");malformed.entities.add(entity(new TLRPC.TL_messageEntityTextUrl(),1,1));malformed.entities.add(entity(new TLRPC.TL_messageEntityUrl(),-1,5));malformed.entities.add(entity(new TLRPC.TL_messageEntityUrl(),3,Integer.MAX_VALUE));
  check(!MangoTranslationLinks.prepare(malformed).hasLinks,"malformed or split-surrogate entities ignored safely");
  TLRPC.TL_textWithEntities mentions=text("Hello @alice and Mail");mentions.entities.add(entity(new TLRPC.TL_messageEntityMention(),6,6));TLRPC.TL_messageEntityMentionName named=entity(new TLRPC.TL_messageEntityMentionName(),17,4);named.user_id=123;mentions.entities.add(named);
  TLRPC.TL_textWithEntities mentionsResult=MangoTranslationLinks.prepare(mentions).restore(translated("你好","和"));
  check(mentionsResult.text.equals("你好 @alice 和 Mail")&&find(mentionsResult,TLRPC.TL_messageEntityMentionName.class).user_id==123,"mentions preserve display text and user ID");
  TLRPC.TL_textWithEntities draftMention=text("Hello Alice");TLRPC.TL_inputMessageEntityMentionName inputMention=entity(new TLRPC.TL_inputMessageEntityMentionName(),6,5);inputMention.user_id=456;draftMention.entities.add(inputMention);
  MangoTranslationLinks.Plan draftPlan=MangoTranslationLinks.prepare(draftMention);check(draftPlan.hasLinks&&draftPlan.requests.size()==1&&draftPlan.requests.get(0).text.equals("Hello"),"input mention label excluded from outgoing translation");
  TLRPC.TL_textWithEntities draftResult=draftPlan.restore(translated("你好"));check(draftResult.text.equals("你好 Alice")&&find(draftResult,TLRPC.TL_inputMessageEntityMentionName.class).user_id==456,"input mention type and identity preserved in outgoing draft");
  clear();delivered.clear();plan=MangoTranslationLinks.prepare(src);
  Runnable cancelOne=MangoTranslationLinks.translate(0,plan,"zh-CN",false,(v,l)->delivered.add(v));
  MangoTranslationLinks.translate(0,plan,"zh",false,(v,l)->delivered.add(v));
  check(ConnectionsManager.sent.size()==1&&ConnectionsManager.sent.get(0).text.text.size()==3,"concurrent identical calls coalesce into one batch");
  check(ConnectionsManager.sent.get(0).text.to_lang.equals("zh"),"language aliases use normalized cache key");
  cancelOne.run();reply(0,"😀 阅读","和","今天");check(delivered.size()==1&&delivered.get(0)!=null,"canceling one subscriber leaves other caller active");
  int requests=ConnectionsManager.sent.size();delivered.get(0).entities.clear();
  MangoTranslationLinks.translate(0,plan,"zh",false,(v,l)->delivered.add(v));AndroidUtilities.flush();
  check(ConnectionsManager.sent.size()==requests&&delivered.size()==2&&delivered.get(1).entities.size()==2,"cache avoids repeat request and returns defensive entity copies");
  MangoTranslationLinks.translate(0,MangoTranslationLinks.prepare(source("https://different.example")),"zh",false,(v,l)->delivered.add(v));
  check(ConnectionsManager.sent.size()==2,"different hidden target does not reuse another link result");
  reply(1,"😀 阅读","和","今天");check(find(delivered.get(2),TLRPC.TL_messageEntityTextUrl.class).url.equals("https://different.example"),"each cached plan restores its own target");
  MangoTranslationLinks.translate(1,plan,"zh",false,(v,l)->delivered.add(v));
  MangoTranslationLinks.translate(0,plan,"fr",false,(v,l)->delivered.add(v));
  check(ConnectionsManager.sent.size()==4,"different accounts and languages use independent requests");
  clear();delivered.clear();
  Runnable canceled=MangoTranslationLinks.translate(0,plan,"zh",false,(v,l)->delivered.add(v));canceled.run();reply(0,"😀 阅读","和","今天");
  check(delivered.isEmpty()&&!ConnectionsManager.canceled.isEmpty()&&AndroidUtilities.delayed.isEmpty(),"last subscriber cancellation cancels network and timeout, ignores late result");
  clear();MangoTranslationLinks.translate(0,plan,"zh",false,(v,l)->delivered.add(v));AndroidUtilities.expire();reply(0,"😀 阅读","和","今天");
  check(delivered.size()==1&&delivered.get(0)==null,"timeout completes once and ignores late success");
  clear();delivered.clear();MangoTranslationLinks.translate(0,plan,"zh",false,(v,l)->delivered.add(v));UserConfig.getInstance(0).owner++;reply(0,"😀 阅读","和","今天");
  check(delivered.isEmpty()&&cache().isEmpty(),"account replacement drops pending results and cache");
  clear();MangoTranslationLinks.translate(0,plan,"zh",true,(v,l)->delivered.add(v));
  check(ConnectionsManager.sent.isEmpty()&&TranslateAlert2.alternatives.size()==3,"alternative engine starts all unprotected fragments without serial waits");
  for(TranslateAlert2.Alternative alt:TranslateAlert2.alternatives)check(alt.from.equals("auto")&&!alt.text.contains("https://"),"alternative detects language remotely and never sees protected links");
  TranslateAlert2.alternatives.get(2).done.run("今天",false);TranslateAlert2.alternatives.get(0).done.run("😀 阅读",false);AndroidUtilities.flush();check(delivered.isEmpty(),"out-of-order fragments wait for complete response");
  TranslateAlert2.alternatives.get(1).done.run("和",false);AndroidUtilities.flush();check(delivered.size()==1&&MangoTranslationLinks.prepare(src).linksPreserved(delivered.get(0)),"alternative completion restores exact links");
  clear();delivered.clear();MangoTranslationLinks.translate(0,plan,"zh",false,(v,l)->delivered.add(v));ConnectionsManager.sent.get(0).callback.run(null,new TLRPC.TL_error("TRANSLATIONS_DISABLED_ALT"));AndroidUtilities.flush();
  check(TranslateAlert2.alternatives.size()==3,"Telegram disabled response falls back with same protected fragments");
  AndroidUtilities.expire();for(TranslateAlert2.Alternative alt:TranslateAlert2.alternatives)check(alt.canceled,"timeout cancels queued alternative tasks");
  clear();StringBuilder many=new StringBuilder();for(int i=0;i<22;i++)many.append("word ").append("https://example.org/").append(i).append(" ");
  MangoTranslationLinks.Plan manyPlan=MangoTranslationLinks.prepare(text(many.toString()));MangoTranslationLinks.translate(0,manyPlan,"zh",false,(v,l)->delivered.add(v));
  check(ConnectionsManager.sent.size()==2&&ConnectionsManager.sent.get(0).text.text.size()==20&&ConnectionsManager.sent.get(1).text.text.size()==2,"more than 20 fragments launches parallel bounded Telegram batches");
  clear();for(int i=0;i<70;i++){MangoTranslationLinks.translate(0,MangoTranslationLinks.prepare(source("https://cache.example/"+i)),"zh",false,(v,l)->{});reply(i,"😀 阅读","和","今天");}
  check(cache().size()<=64,"translation cache is bounded");
  clear();MangoTranslationLinks.translate(0,plan,"zh",false,(v,l)->{});reply(0,"😀 阅读","和","今天");android.os.SystemClock.now+=300001;MangoTranslationLinks.translate(0,plan,"zh",false,(v,l)->{});
  check(ConnectionsManager.sent.size()==2,"expired translations are refreshed");
  clear();delivered.clear();MangoOutgoingTranslation.translate(0,src,"zh",delivered::add);check(ConnectionsManager.sent.get(0).text.text.size()==3,"outgoing translation uses protected text batches");reply(0,"😀 阅读","和","今天");check(delivered.size()==1&&MangoTranslationLinks.prepare(src).linksPreserved(delivered.get(0)),"outgoing translation returns clickable unchanged links");
  clear();delivered.clear();MangoOutgoingTranslation.translate(0,only,"zh",delivered::add);AndroidUtilities.flush();check(ConnectionsManager.sent.isEmpty()&&delivered.get(0).text.equals(only.text),"outgoing link-only message bypasses network");
  clear();delivered.clear();MangoOutgoingTranslation.translate(0,draftMention,"zh",delivered::add);check(ConnectionsManager.sent.get(0).text.text.get(0).text.equals("Hello"),"outgoing request excludes input mention display name");reply(0,"你好");check(find(delivered.get(0),TLRPC.TL_inputMessageEntityMentionName.class).user_id==456,"outgoing request completion keeps input mention identity");
  clear();delivered.clear();ControllerInvalidation invalidation=new ControllerInvalidation();Runnable obsolete=MangoTranslationLinks.translate(0,plan,"zh",false,(v,l)->delivered.add(v));invalidation.protectedTranslationCancels.put("-20:5:false",obsolete);
  final int[] untouched={0};invalidation.protectedTranslationCancels.put("-30:5:false",()->untouched[0]++);invalidation.invalidate(new ControllerInvalidation.MessageObject(-20,5));reply(0,"😀 阅读","和","今天");
  check(delivered.isEmpty()&&!invalidation.protectedTranslationCancels.containsKey("-20:5:false"),"editing message cancels its protected translation before stale result delivery");check(invalidation.protectedTranslationCancels.containsKey("-30:5:false")&&untouched[0]==0,"same message ID in another dialog stays active");
  MangoTranslationLinks.translate(0,MangoTranslationLinks.prepare(source("https://edited.example")),"zh",false,(v,l)->delivered.add(v));reply(1,"😀 阅读","和","今天");check(find(delivered.get(0),TLRPC.TL_messageEntityTextUrl.class).url.equals("https://edited.example"),"new edit request restores the edited target");
  String multilingual="中文😀日本語한국어 العربية repeated ".repeat(1000);ArrayList<String> chunks=TranslateAlert2.cut(android.net.Uri.encode(multilingual),5000);StringBuilder combined=new StringBuilder();
  for(String chunk:chunks){String decoded=android.net.Uri.decode(chunk);check(chunk.length()<=5000&&!decoded.contains("�"),"UTF-8 encoded chunk is bounded and lossless");combined.append(decoded);}
  check(combined.toString().equals(multilingual),"chunking preserves all multilingual text, emoji and whitespace");
  try{TranslateAlert2.cut("abc",0);throw new AssertionError("invalid maximum accepted");}catch(IllegalArgumentException expected){checks++;}
  System.out.println("PASS: "+checks+" link preservation, batching, cancellation, cache and UTF-16 translation behavior checks");
 }
}'''
}
fixtures['org/telegram/ui/Components/TranslateAlert2.java'] = fixtures['org/telegram/ui/Components/TranslateAlert2.java'].replace('//CUT', extract(alert, '    public static ArrayList<String> cut('))
constructor = extract(alert, '    private TranslateAlert2(')
assignments = constructor[constructor.index('        this.reqText = text;'):constructor.index('        this.fromLanguage = fromLanguage;')]
manual_branch = extract(extract(alert, '    public void translate()'), '        if (!reqSum)')
fixtures['org/telegram/messenger/ManualTranslationEntry.java'] = '''package org.telegram.messenger;
import java.util.*;import org.telegram.tgnet.TLRPC;
public class ManualTranslationEntry {
 CharSequence reqText;ArrayList<TLRPC.MessageEntity> reqMessageEntities;Object reqPeer;int reqMessageId,currentAccount;boolean reqSum;MangoTranslationLinks.Plan plan;
 ManualTranslationEntry(CharSequence text,ArrayList<TLRPC.MessageEntity> entities,Object peer,int messageId,boolean sum){int account=1;
''' + assignments + '''
 }
 void translate(){final String method="telegram";
''' + manual_branch + '''
 }
 void translateLinks(MangoTranslationLinks.Plan links,boolean alternative){plan=links;}
}'''
invalidation = extract(extract(controller, '    public void invalidateTranslation('), '        if (messageObject != null)')
fixtures['org/telegram/messenger/ControllerInvalidation.java'] = '''package org.telegram.messenger;import java.util.*;
class ControllerInvalidation {
 final HashMap<String,Runnable> protectedTranslationCancels=new HashMap<>();
 static class MessageObject {long dialog;int id;MessageObject(long dialog,int id){this.dialog=dialog;this.id=id;}long getDialogId(){return dialog;}int getId(){return id;}}
 void invalidate(MessageObject messageObject){
''' + invalidation + '''
 }
}'''
with tempfile.TemporaryDirectory(prefix='mango-translation-links-') as directory:
    base = Path(directory)
    sources = []
    for name, text in fixtures.items():
        path = base / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        sources.append(path)
    sources += [SOURCE / 'org/telegram/messenger' / name for name in ('MangoTranslationLinks.java', 'MangoOutgoingTranslation.java', 'LinkifyPort.java')]
    subprocess.run([str(JAVA / 'javac'), '-d', str(base / 'classes'), *map(str, sources)], check=True)
    subprocess.run([str(JAVA / 'java'), '-cp', str(base / 'classes'), 'org.telegram.messenger.TranslationLinksTest'], check=True)

assert '__TGIGNORE' not in controller and 'restoreIgnoredTranslationRanges' not in controller
assert 'MangoTranslationLinks.translate(currentAccount, linkPlan' in controller
assert 'MangoTranslationLinks.clear(currentAccount)' in controller
assert 'protectedTranslationCancels.remove(key).run()' in controller
assert 'if (links.hasLinks)' in extract(alert, '    public void translate()')
assert 'this.reqMessageEntities = entities == null ? new ArrayList<>() : new ArrayList<>(entities);' in extract(alert, '    private TranslateAlert2(')
assert alert.count('new TranslateAlert2(context, currentAccount,') == 2
assert constructor.index('this.currentAccount = account;') < constructor.index('translate();')
assert 'MessageObject.addEntitiesToText(translated, result.entities' in extract(alert, '    private void translateLinks(')
assert 'cancelLinkTranslation.run()' in extract(alert, '    public void dismissInternal()')
assert 'newFixedThreadPool(3)' in alert
assert 'LanguageDetector.detectLanguage' not in extract(alert, '    public static Runnable alternativeTranslateCancelable(')
assert '&dt=at' not in extract(alert, '    private static Runnable alternativeTranslateInternal(')
assert 'future.cancel(true)' in extract(alert, '    private static Runnable alternativeTranslateInternal(')
assert 'activeConnection.getAndSet(null)' in extract(alert, '    private static Runnable alternativeTranslateInternal(')
print('PASS: 15 translation entry, lifecycle and network optimization wiring checks')
