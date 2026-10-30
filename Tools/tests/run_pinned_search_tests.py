#!/usr/bin/env python3
"""Run the real pin store, TL byte serializer and search filtering/history behavior offline."""
from pathlib import Path
import ast
import os
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
JAVA = Path(os.environ.get('JAVA_HOME', '/Applications/Android Studio.app/Contents/jbr/Contents/Home')) / 'bin'
SOURCE = (ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/Adapters/DialogsSearchAdapter.java').read_text()


def block(marker):
    start = SOURCE.index(marker)
    end = SOURCE.index('{', start) + 1
    depth = 1
    while depth:
        depth += (SOURCE[end] == '{') - (SOURCE[end] == '}')
        end += 1
    return SOURCE[start:end]


tree = ast.parse((ROOT / 'Tools/tests/run_mango_settings_tests.py').read_text())
original = ast.literal_eval(next(n.value for n in tree.body if isinstance(n, ast.Assign)
                                and any(isinstance(t, ast.Name) and t.id == 'fixtures' for t in n.targets)))
fixtures = {name: original[name] for name in ['android/content/SharedPreferences.java',
                                            'android/content/Context.java',
                                            'org/telegram/messenger/ApplicationLoader.java']}
fixtures.update({
    'android/util/Base64.java': '''package android.util;public class Base64 {public static final int NO_WRAP=2;public static byte[] decode(String s,int flags){return java.util.Base64.getDecoder().decode(s);}public static String encodeToString(byte[] b,int flags){return java.util.Base64.getEncoder().encodeToString(b);}}''',
    'android/text/TextUtils.java': '''package android.text;public class TextUtils {public static boolean isEmpty(CharSequence s){return s==null||s.length()==0;}}''',
    'org/telegram/messenger/UserConfig.java': '''package org.telegram.messenger;public class UserConfig {public static final UserConfig[] all={new UserConfig(10),new UserConfig(20)};public long owner;public boolean active=true;UserConfig(long id){owner=id;}public static UserConfig getInstance(int account){return all[account];}public long getClientUserId(){return owner;}public boolean isClientActivated(){return active;}}''',
    'org/telegram/messenger/BuildVars.java': '''package org.telegram.messenger;public class BuildVars {public static final boolean LOGS_ENABLED=false;}''',
    'org/telegram/messenger/FileLog.java': '''package org.telegram.messenger;public class FileLog {public static void e(Object e){}}''',
    'org/telegram/tgnet/NativeByteBuffer.java': '''package org.telegram.tgnet;public class NativeByteBuffer {}''',
    'org/telegram/tgnet/TLObject.java': '''package org.telegram.tgnet;public class TLObject {}''',
    'org/telegram/tgnet/TLRPC.java': '''package org.telegram.tgnet;import java.util.*;public class TLRPC {
        public static class TL_username {public boolean active;public String username;}
        public static class Chat extends TLObject {public long id,access_hash;public String title,username;public boolean megagroup,monoforum,left;public ArrayList<TL_username> usernames=new ArrayList<>();
            public void serializeToStream(SerializedData d){d.writeInt32(123);d.writeInt64(id);d.writeInt64(access_hash);d.writeString(title==null?"":title);d.writeString(username==null?"":username);d.writeBool(megagroup);d.writeBool(left);d.writeInt32(usernames.size());for(TL_username u:usernames){d.writeBool(u.active);d.writeString(u.username);}}
            public static Chat TLdeserialize(SerializedData d,int constructor,boolean exception){if(constructor!=123)throw new IllegalArgumentException();Chat c=new Chat();c.id=d.readInt64(true);c.access_hash=d.readInt64(true);c.title=d.readString(true);c.username=d.readString(true);c.megagroup=d.readBool(true);c.left=d.readBool(true);int n=d.readInt32(true);for(int i=0;i<n;i++){TL_username u=new TL_username();u.active=d.readBool(true);u.username=d.readString(true);c.usernames.add(u);}return c;}}
        public static class User extends TLObject {public String username;}
        public static class ChatInvite extends TLObject {public String title;}
    }''',
    'org/telegram/messenger/ChatObject.java': '''package org.telegram.messenger;public class ChatObject {public static boolean isChannelAndNotMegaGroup(org.telegram.tgnet.TLRPC.Chat c){return c!=null&&!c.megagroup;}}''',
    'org/telegram/messenger/MessagesController.java': '''package org.telegram.messenger;import org.telegram.tgnet.TLRPC;import java.util.*;public class MessagesController {public static final MessagesController[] all={new MessagesController(),new MessagesController()};public Map<Long,TLRPC.Chat> chats=new HashMap<>();public static MessagesController getInstance(int a){return all[a];}public void putChat(TLRPC.Chat c,boolean cached){chats.putIfAbsent(c.id,c);}public TLRPC.Chat getChat(long id){return chats.get(id);}}''',
    'org/telegram/messenger/MessagesStorage.java': '''package org.telegram.messenger;import java.util.*;public class MessagesStorage {static final MessagesStorage[] all={new MessagesStorage(),new MessagesStorage()};public ArrayList<String> sql=new ArrayList<>();public static MessagesStorage getInstance(int a){return all[a];}public Queue getStorageQueue(){return new Queue();}public MessagesStorage getDatabase(){return this;}public Statement executeFast(String query){sql.add(query);return new Statement();}public static class Queue {public void postRunnable(Runnable r){r.run();}}public static class Statement {public Statement stepThis(){return this;}public void dispose(){}}}''',
})

methods = '\n'.join(block(marker) for marker in [
    '    public static class RecentSearchObject',
    '    private boolean isSearchOwnerCurrent()',
    '    private void loadPinnedSearchChannels()',
    '    public boolean isPinnedSearchChannel(',
    '    public void setSearchChannelPinned(',
    '    public void clearRecentSearch()',
    '    public void filterRecent(',
    '    private boolean wordStartsWith(',
])
test = r'''
package org.telegram.messenger;
import android.text.TextUtils;
import java.util.*;
import org.telegram.tgnet.*;
public class PinnedSearchRegression {
 static int checks;
 static void check(boolean pass,String text){checks++;if(!pass)throw new AssertionError(text);}
 static TLRPC.Chat chat(long id,String name){TLRPC.Chat c=new TLRPC.Chat();c.id=id;c.title=name;c.username="channel"+id;c.access_hash=50000+id;c.left=true;return c;}
 static class ForumUtilities {static String getMonoForumTitle(int account,TLRPC.Chat c){return c.title;}}
 static class UserObject {static String getUserName(TLRPC.User u){return u.username;}}
 static class Helper {void mergeResults(Object result,Object recent){}}
 static class Delegate {long forum;long getSearchForumDialogId(){return forum;}}
 static class Harness {
  int currentAccount;long selfUserId;boolean searchWas;String lastSearchText,filteredRecentQuery;long forced;
  ArrayList<TLRPC.Chat> pinnedSearchChannels=new ArrayList<>();
  ArrayList<RecentSearchObject> recentSearchObjects=new ArrayList<>(),filteredRecentSearchObjects=new ArrayList<>(),filtered2RecentSearchObjects=new ArrayList<>();
  Map<Long,RecentSearchObject> recentSearchObjectsById=new HashMap<>();
  ArrayList<Object> searchResult=new ArrayList<>();ArrayList<CharSequence> searchResultNames=new ArrayList<>();
  Helper searchAdapterHelper=new Helper();Delegate delegate;
  Harness(int account){currentAccount=account;selfUserId=UserConfig.getInstance(account).owner;loadPinnedSearchChannels();}
  boolean filter(Object o){return true;}boolean isForcedRecentDialog(long id){return id==forced;}void notifyDataSetChanged(){}
  void recent(TLRPC.Chat c){RecentSearchObject o=new RecentSearchObject();o.object=c;o.did=-c.id;recentSearchObjects.add(o);recentSearchObjectsById.put(o.did,o);}
  /*METHODS*/
 }
 public static void main(String[] args) {
  TLRPC.Chat a=chat(101,"Apple News"),b=chat(102,"Banana News");
  check(MangoPinnedSearchChannels.setPinned(0,10,a,true),"pin succeeds");
  check(MangoPinnedSearchChannels.setPinned(0,10,b,true),"second pin succeeds");
  ArrayList<TLRPC.Chat> loaded=MangoPinnedSearchChannels.load(0,10);
  check(loaded.size()==2&&loaded.get(0).id==102&&loaded.get(1).id==101,"newest pinned first");
  check(loaded.get(1)!=a&&loaded.get(1).access_hash==a.access_hash&&loaded.get(1).left,"complete unjoined channel restored as a new object");
  check(loaded.get(1).title.equals("Apple News"),"title survives serialization");
  check(MangoPinnedSearchChannels.load(1,20).isEmpty(),"accounts isolated");
  UserConfig.all[0].owner=30;
  check(MangoPinnedSearchChannels.load(0,10).isEmpty(),"old owner cannot read");
  check(MangoPinnedSearchChannels.load(0,30).isEmpty(),"replacement owner sees no pins");
  check(!MangoPinnedSearchChannels.setPinned(0,10,b,false),"stale owner cannot unpin");
  UserConfig.all[0].owner=10;UserConfig.all[0].active=false;
  check(!MangoPinnedSearchChannels.setPinned(0,10,b,false),"logged out cannot mutate");
  UserConfig.all[0].active=true;
  check(MangoPinnedSearchChannels.load(0,10).size()==2,"failed mutations preserved original pins");
  TLRPC.Chat group=chat(500,"group");group.megagroup=true;
  check(!MangoPinnedSearchChannels.setPinned(0,10,group,true),"groups rejected");
  check(MangoPinnedSearchChannels.setPinned(0,10,b,true)&&MangoPinnedSearchChannels.load(0,10).size()==2,"repeated pin deduplicates");
  check(MangoPinnedSearchChannels.matches(a,"APPLE")&&MangoPinnedSearchChannels.matches(a,"@CHANNEL101"),"case-insensitive title and handle search");
  TLRPC.TL_username alternate=new TLRPC.TL_username();alternate.username="other_handle";alternate.active=true;a.usernames.add(alternate);
  check(MangoPinnedSearchChannels.matches(a,"@other_handle"),"active alias matches");
  alternate.active=false;
  check(!MangoPinnedSearchChannels.matches(a,"other_handle"),"inactive alias ignored");
  check(!MangoPinnedSearchChannels.matches(a,"Banana"),"unrelated query excluded");
  Harness h=new Harness(0);h.recent(a);h.recent(chat(103,"Coconut News"));h.filterRecent(null);
  check(h.filteredRecentSearchObjects.size()==3&&h.filteredRecentSearchObjects.get(0).did==-102&&h.filteredRecentSearchObjects.get(2).did==-103,"pins precede deduplicated recents");
  h.filterRecent("@channel101");
  check(h.filtered2RecentSearchObjects.size()==1&&h.filtered2RecentSearchObjects.get(0).did==-101,"query displays matching pin only");
  h.filterRecent(null);h.clearRecentSearch();
  check(h.filteredRecentSearchObjects.size()==2&&MangoPinnedSearchChannels.load(0,10).size()==2,"clear all recent history preserves pins");
  Harness reopened=new Harness(0);reopened.filterRecent(null);
  check(reopened.filteredRecentSearchObjects.size()==2,"reopened adapter restores pins without history database");
  reopened.searchWas=true;reopened.lastSearchText="Apple";reopened.filterRecent("Apple");reopened.clearRecentSearch();
  check(reopened.filtered2RecentSearchObjects.size()==1&&MangoPinnedSearchChannels.load(0,10).size()==2,"clearing filtered history preserves matching pin");
  check(MessagesStorage.getInstance(0).sql.get(MessagesStorage.getInstance(0).sql.size()-1).endsWith("WHERE 0"),"clearing pins-only match does not erase unrelated history");
  reopened.searchResult.add(chat(104,"Date News"));reopened.searchResultNames.add("Date News");reopened.lastSearchText="Date";
  reopened.setSearchChannelPinned((TLRPC.Chat)reopened.searchResult.get(0),true);
  check(reopened.searchResult.isEmpty()&&reopened.searchResultNames.isEmpty()&&reopened.filtered2RecentSearchObjects.size()==1,"pinning local search removes duplicate local row and name");
  reopened.setSearchChannelPinned(chat(104,"Date News"),false);
  check(reopened.filtered2RecentSearchObjects.isEmpty()&&MangoPinnedSearchChannels.load(0,10).size()==2,"unpin removes persisted shortcut");
  for(int i=0;i<7;i++)MangoPinnedSearchChannels.setPinned(0,10,chat(200+i,"News "+i),true);
  Harness many=new Harness(0);for(int i=0;i<8;i++)many.recent(chat(300+i,"News "+i));many.filterRecent("News");
  check(many.filtered2RecentSearchObjects.size()==14,"all nine matching pins plus five recent results, no five-pin cap");
  many.forced=-101;many.filterRecent(null);
  check(many.filteredRecentSearchObjects.stream().noneMatch(o->o.did==-101),"forced entry remains independent without duplicate");
  many.delegate=new Delegate();many.delegate.forum=-102;many.filterRecent(null);
  check(many.filteredRecentSearchObjects.stream().noneMatch(o->o.did==-102),"forum exclusion retained");
  UserConfig.all[0].owner=99;many.filterRecent(null);
  check(many.filteredRecentSearchObjects.isEmpty()&&!many.isPinnedSearchChannel(101),"existing adapter hides pins after slot replacement");
  UserConfig.all[0].owner=10;
  android.content.SharedPreferences p=ApplicationLoader.applicationContext.getSharedPreferences("mango_pinned_search_0_10",0);
  p.edit().putString("order","bad,102,102,999,101").putString("chat_999","broken-data").apply();
  check(MangoPinnedSearchChannels.load(0,10).size()==2,"malformed or duplicate persisted rows do not hide healthy pins");
  System.out.println("Pinned search: "+checks+" behavioral assertions passed");
 }
}
'''.replace('/*METHODS*/', methods)

with tempfile.TemporaryDirectory(prefix='mango-pinned-search-') as tmp:
    base = Path(tmp)
    sources = []
    fixtures['org/telegram/messenger/PinnedSearchRegression.java'] = test
    for name, source in fixtures.items():
        path = base / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source)
        sources.append(path)
    production = ROOT / 'TMessagesProj/src/main/java'
    sources.append(production / 'org/telegram/messenger/MangoPinnedSearchChannels.java')
    for name in ['SerializedData', 'AbstractSerializedData', 'InputSerializedData', 'OutputSerializedData', 'TLDataSourceType']:
        sources.append(production / f'org/telegram/tgnet/{name}.java')
    subprocess.run([str(JAVA / 'javac'), '-d', str(base / 'classes')] + list(map(str, sources)), check=True)
    subprocess.run([str(JAVA / 'java'), '-cp', str(base / 'classes'), 'org.telegram.messenger.PinnedSearchRegression'], check=True)

    # Removing either owner validation or the separate recent-result budget must fail.
    store_path = production / 'org/telegram/messenger/MangoPinnedSearchChannels.java'
    original_store = store_path.read_text()
    mutant_store = base / 'mutants/org/telegram/messenger/MangoPinnedSearchChannels.java'
    mutant_store.parent.mkdir(parents=True, exist_ok=True)
    test_path = base / 'org/telegram/messenger/PinnedSearchRegression.java'
    variants = [
        ('owner guard', original_store.replace('&& UserConfig.getInstance(account).getClientUserId() == owner', ''), test),
        ('matching pin budget', original_store, test.replace('filtered2RecentSearchObjects.size() - pinnedCount >= 5', 'filtered2RecentSearchObjects.size() >= 5')),
    ]
    for label, store_source, harness_source in variants:
        mutant_store.write_text(store_source)
        test_path.write_text(harness_source)
        mutant_sources = [mutant_store if path == store_path else path for path in sources]
        subprocess.run([str(JAVA / 'javac'), '-d', str(base / 'mutant-classes')] + list(map(str, mutant_sources)), check=True)
        result = subprocess.run([str(JAVA / 'java'), '-cp', str(base / 'mutant-classes'), 'org.telegram.messenger.PinnedSearchRegression'], text=True, capture_output=True)
        assert result.returncode != 0 and 'AssertionError' in result.stderr, f'Mutation survived: {label}'
    print('Pinned search: 2 behavioral mutations rejected')

en = ET.parse(ROOT / 'TMessagesProj/src/main/res/values/v45_pinned_search.xml').getroot()
zh = ET.parse(ROOT / 'TMessagesProj/src/main/res/values-zh/v45_pinned_search.xml').getroot()
assert {item.attrib['name'] for item in en} == {item.attrib['name'] for item in zh}
print('Pinned search: English/Chinese resource parity passed')
