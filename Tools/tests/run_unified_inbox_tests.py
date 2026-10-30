#!/usr/bin/env python3
"""Execute production inbox aggregation/navigation using small Android/cache doubles.

Compile actual Entry, fillItems, open, row binding and identity methods on every run. Four
mutations must be rejected by the same behavior checks, preventing vacuous tests.
"""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
JAVA = Path(os.environ.get('JAVA_HOME', '/Applications/Android Studio.app/Contents/jbr/Contents/Home')) / 'bin'
source = (ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/CrossAccountInboxActivity.java').read_text()


def block(marker):
    start = source.index(marker)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


entry = block('    private static class Entry')
fill = block('    private void fillItems(')
create_row_item = block('    private UItem createRowItem(')
rebuild = block('    private void rebuildSnapshot()')
rebuild_dirty = block('    private void rebuildDirtySnapshots()')
index_rows = block('    private HashMap<Integer, HashMap<Long, Entry>> indexCachedRows(')
build_account_rows = block('    private ArrayList<Entry> buildRowsForAccount(')
merge_rows = block('    private ArrayList<Entry> mergeSortedRows(')
refresh_has_more = block('    private void refreshHasMoreDialogs()')
has_more = block('    private boolean hasMoreDialogs(')
same_entry = block('    private boolean sameEntryContents(')
create_entry = block('    private Entry createEntry(')
update_dialog = block('    private boolean updateDialogSnapshot(')
find_row_index = block('    private int findDialogRowIndex(')
opening = block('    private void open(Entry row)')
identity = block('public boolean equals(UItem a, UItem b)')
binding = block('private void bind(Entry row, boolean divider)')
fixture = r'''
import java.util.*;
public class InboxRegression {
 static class R {
  static class string {
   static final int MangoUnifiedUnread=1,MangoUnifiedInfo=2,SecretChat=3,SavedMessages=4,
    MessageTagPrivateMessage=5,MangoUnifiedOpenChat=6,MangoUnifiedEmpty=7,
    MangoUnifiedUnreadCount=8,MangoUnifiedAccount=9,MangoUnifiedLoadMore=10;
  }
  static class drawable { static final int msg_retry=1; }
 }
 static String getString(int id) { return "string:"+id; }
 static class TextUtils {
  static boolean isEmpty(CharSequence s){return s==null||s.length()==0;}
  static boolean equals(CharSequence a,CharSequence b){return a==b||a!=null&&a.equals(b);}
 }
 static class LocaleController {
  static String formatString(int id,Object... args){return getString(id)+Arrays.toString(args);}
  static String formatDateChat(int date){return "date:"+date;}
  static String stringForMessageListDate(int date){return "time:"+date;}
 }
 static class TLRPC {
  static class User { long id; String name,username; User(long i){id=i;name="user"+i;username="name"+i;} }
  static class Chat { String title="group"; }
  static class Dialog {long id;int unread_count,last_message_date,folder_id,top_message,flags,unread_mentions_count,unread_reactions_count,pinnedNum;boolean unread_mark,pinned;Object draft,notify_settings;Dialog(long i){id=i;} }
  static class EncryptedChat {long user_id=77;}
  static class Message {int date,id;}
 }
 static class MessageObject {
  TLRPC.Message messageOwner=new TLRPC.Message();boolean deleted,secret;
  CharSequence caption,messageText;
  MessageObject(int id,int date,String text){messageOwner.id=id;messageOwner.date=date;messageText=text;}
  int getId(){return messageOwner.id;} boolean isSecretMedia(){return secret;}
 }
 static class DialogObject {
  static boolean isChannel(TLRPC.Dialog d){return d!=null&&(d.flags&1)!=0;}
  static boolean isFolderDialogId(long id){return id==Long.MIN_VALUE;}
  static boolean isEncryptedDialog(long id){return id>0&&(id&0xffffffffL)==0;}
  static int getEncryptedChatId(long id){return (int)(id>>32);}
 }
 static class UserObject {
  static String getPublicUsername(TLRPC.User u){return u==null?null:u.username;}
  static String getUserName(TLRPC.User u){return u==null?"":u.name;}
 }
 static class UserConfig {
  static UserConfig[] configs={new UserConfig(100),new UserConfig(200)};
  long owner;boolean active=true;UserConfig(long id){owner=id;}
  static int[] getActivatedAccounts(){return new int[]{0,1};}
  static UserConfig getInstance(int n){return configs[n];}
  boolean isClientActivated(){return active;}long getClientUserId(){return owner;}
  TLRPC.User getCurrentUser(){return new TLRPC.User(owner);}
 }
 static class MessagesController {
  static MessagesController[] instances={new MessagesController(),new MessagesController()};
  ArrayList<TLRPC.Dialog> dialogs=new ArrayList<>();
  Map<Long,TLRPC.Dialog> dialogs_dict=new HashMap<>();
  Map<Long,ArrayList<MessageObject>> dialogMessage=new HashMap<>();
  Map<Integer,MessageObject> dialogMessagesByIds=new HashMap<>();
  boolean canOpen=true;int openChecks;
  static MessagesController getInstance(int n){return instances[n];}
  boolean isServerDialogsEndReached(int folder){return true;}
  ArrayList<TLRPC.Dialog> getAllDialogs(){return dialogs;}
  TLRPC.User getUser(long id){return new TLRPC.User(id);}
  TLRPC.Chat getChat(long id){return new TLRPC.Chat();}
  TLRPC.EncryptedChat getEncryptedChat(int id){return new TLRPC.EncryptedChat();}
  boolean isDialogMuted(long id,int topic){return false;}
  boolean checkCanOpenChat(Bundle args,Harness h){openChecks++;return canOpen;}
 }
 static class UItem {
  int id;Object object;CharSequence text,subtext;
  static UItem asSwitch(int id,String text){UItem i=new UItem();i.id=id;return i;}
  UItem setChecked(boolean b){return this;}
  static UItem asShadow(String text){return new UItem();}
  static UItem asButton(int id,int icon,String text){return asSwitch(id,text);}
 }
 static class UniversalAdapter {int updates;void update(boolean animate){updates++;}}
 static class ListView {UniversalAdapter adapter=new UniversalAdapter();}
 static class Bundle extends HashMap<String,Long> {
  void putInt(String key,int value){put(key,(long)value);}void putLong(String key,long value){put(key,value);}
 }
 static class ChatActivity {Bundle args;int account=-1;ChatActivity(Bundle a){args=a;}void setCurrentAccount(int n){account=n;}}
 static class LaunchActivity {
  int switched=-1;ChatActivity presented;ArrayList<String> events=new ArrayList<>();
  void switchToAccount(int account,boolean remove){switched=account;events.add("switch");}
  void presentFragment(ChatActivity chat){presented=chat;events.add("present");}
 }
 static class TextView {
  CharSequence text; void setText(CharSequence value){text=value;} CharSequence getText(){return text;}
  void setTextColor(int color){}
 }
 static class Theme {
  static int key_windowBackgroundWhite=1,key_windowBackgroundWhiteBlueText=2;
  static int getColor(int key,Object resources){return key;}
 }
 static class DialogsActivity {static int DIALOGS_TYPE_DEFAULT=0;}
 static class LayoutHelper {static int MATCH_PARENT=-1,WRAP_CONTENT=-2;static Object createLinear(int w,int h){return null;}}
 static class DialogCell {
  final int account; TLRPC.Dialog normal; CustomDialog hidden;
  boolean useSeparator,drawAvatarStories=true;
  DialogCell(Object parent,Object context,boolean check,boolean lines,int account,Object resources){this.account=account;}
  static class CustomDialog {long id;String name,message;int date,unread_count,type;boolean muted;Object dialogObject;}
  void setImportantForAccessibility(int value){}
  void setDialog(TLRPC.Dialog dialog,int type,int folder){normal=dialog;hidden=null;}
  void setDialog(CustomDialog dialog){hidden=dialog;normal=null;}
 }
 static class Harness {
  static final int ITEM_UNREAD=1,ITEM_MORE=2,ITEM_MESSAGE=3;
  boolean unreadOnly;ListView listView=new ListView();Object parent=new LaunchActivity();ChatActivity presented;
  ArrayList<Entry> cachedRows=new ArrayList<>();HashSet<Integer> dirtyAccounts=new HashSet<>();boolean snapshotDirty=true,cachedUnreadOnly,cachedHasMore;
  Object getParentActivity(){return parent;}void presentFragment(ChatActivity c){presented=c;}
  /*ENTRY*/
  /*FILL*/
  /*CREATE_ROW_ITEM*/
  /*REBUILD*/
  /*REBUILD_DIRTY*/
  /*INDEX_ROWS*/
  /*BUILD_ACCOUNT_ROWS*/
  /*MERGE_ROWS*/
  /*REFRESH_HAS_MORE*/
  /*HAS_MORE*/
  /*SAME_ENTRY*/
  /*CREATE_ENTRY*/
  /*UPDATE_DIALOG*/
  /*FIND_ROW_INDEX*/
  /*OPEN*/
  static class InboxDialogCell {
   Object resources; TextView accountLabel=new TextView(); DialogCell dialogCell;
   int boundAccount=-1; long boundOwner; boolean boundPrivate,divider;
   static int IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS=4;
   CharSequence description;
   Object getContext(){return null;}void removeView(Object view){}void addView(Object view,int index,Object params){}
   void setBackgroundColor(int color){}void setContentDescription(CharSequence text){description=text;}void invalidate(){}
   /*BINDING*/
  }
  static class InboxRowFactory {
   /*IDENTITY*/
   static UItem of(int id,CharSequence title,CharSequence detail){UItem i=new UItem();i.id=id;i.text=title;i.subtext=detail;return i;}
  }
  ArrayList<UItem> rows(){ArrayList<UItem> all=new ArrayList<>();fillItems(all,new UniversalAdapter());all.removeIf(i->i.id!=ITEM_MESSAGE);return all;}
 }
 static int checks;
 static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
 static TLRPC.Dialog add(int account,long id,int date,int unread,String text){
  MessagesController c=MessagesController.getInstance(account);TLRPC.Dialog d=new TLRPC.Dialog(id);
  d.unread_count=unread;d.last_message_date=date;d.top_message=date;c.dialogs.add(d);c.dialogs_dict.put(id,d);
  MessageObject message=new MessageObject(date,date,text);
  c.dialogMessage.put(id,new ArrayList<>(Arrays.asList(message)));c.dialogMessagesByIds.put(message.getId(),message);return d;
 }
 static Harness.Entry row(UItem i){return (Harness.Entry)i.object;}
 public static void main(String[] args){
  TLRPC.Dialog first=add(0,77,100,0,"account-zero");
  add(1,77,200,2,"account-one");
  add(0,9L<<32,300,0,"secret-body");
  add(1,88,250,0,"ephemeral-body");
  TLRPC.Dialog channel=add(0,-900,75,0,"channel-top-message");channel.flags=1;channel.top_message=100;
  MessagesController.getInstance(1).dialogMessage.get(88L).get(0).secret=true;
  Harness h=new Harness();ArrayList<UItem> rows=h.rows();
  ArrayList<UItem> cached=h.rows();
  check(row(cached.get(0))==row(rows.get(0)),"unchanged refresh reuses the inbox snapshot");
  h.snapshotDirty=true;ArrayList<UItem> rebuilt=h.rows();
  check(row(rebuilt.get(0))==row(rows.get(0)),"full snapshot refresh reuses unchanged row payloads");
  rows=rebuilt;
  check(rows.size()==5,"both accounts retain their own dialogs");
  check(row(rows.get(0)).date==300&&row(rows.get(1)).date==250&&row(rows.get(2)).date==200&&row(rows.get(3)).date==100,"descending date order");
  UItem accountOne=rows.get(2),accountZero=rows.get(3);
  check(row(accountOne).account==1&&row(accountZero).account==0,"same dialog preserves account ownership");
  check(row(accountOne).preview.equals("account-one")&&row(accountZero).preview.equals("account-zero"),"previews use owning cache");
  check(row(rows.get(4)).preview.equals("channel-top-message"),"channel top-message ids do not collide with account message index");
  Harness.Entry beforeEdit=null;
  for(UItem item:rows)if(row(item).account==0&&row(item).dialogId==77)beforeEdit=row(item);
  MessageObject editedMessage=MessagesController.getInstance(0).dialogMessagesByIds.get(100);
  editedMessage.messageText="edited preview";
  h.snapshotDirty=true;ArrayList<UItem> editedRows=h.rows();
  Harness.Entry afterEdit=null;
  for(UItem item:editedRows)if(row(item).account==0&&row(item).dialogId==77)afterEdit=row(item);
  check(beforeEdit!=afterEdit&&afterEdit.preview.equals("edited preview"),"changed preview gets a new row payload for rebinding");
  rows=editedRows;
  check(!new Harness.InboxRowFactory().equals(accountOne,accountZero),"row identity isolates accounts");
  Harness.Entry alias=new Harness.Entry();alias.account=0;alias.owner=row(accountOne).owner;alias.dialogId=77;
  UItem aliasItem=new UItem();aliasItem.object=alias;
  check(!new Harness.InboxRowFactory().equals(accountOne,aliasItem),"row identity isolates accounts");
  check(new Harness.InboxRowFactory().equals(accountOne,accountOne),"same row remains stable");
  check(row(rows.get(0)).preview.equals(getString(R.string.MessageTagPrivateMessage)),"secret chat preview redacted");
  check(row(rows.get(1)).preview.equals(getString(R.string.MessageTagPrivateMessage)),"secret media preview redacted");
  check(!rows.get(0).subtext.toString().contains("secret-body")&&!rows.get(1).subtext.toString().contains("ephemeral-body"),"rendered details do not leak secret bodies");
  Harness.InboxDialogCell cell=new Harness.InboxDialogCell();
  cell.bind(row(accountOne),true);
  check(cell.dialogCell.account==1&&cell.dialogCell.normal==row(accountOne).dialog,"native row binds original dialog and account");
  check(cell.accountLabel.getText().toString().contains("user200"),"account label belongs to row owner");
  check(!cell.dialogCell.drawAvatarStories,"cross-account row does not render selected-account stories");
  DialogCell original=cell.dialogCell;cell.bind(row(accountOne),true);
  check(cell.dialogCell==original,"same account reuses native cell");
  row(accountZero).owner=row(accountOne).owner;cell.bind(row(accountZero),false);
  check(cell.dialogCell!=original&&cell.dialogCell.account==0,"recycling across accounts recreates native cell");
  original=cell.dialogCell;row(accountZero).owner=101;cell.bind(row(accountZero),true);
  check(cell.dialogCell!=original,"recycling across owners recreates native cell");
  row(accountZero).owner=100;
  cell.bind(row(rows.get(0)),true);
  check(cell.dialogCell.hidden!=null&&cell.dialogCell.normal==null,"private row never receives real dialog");
  check(cell.dialogCell.hidden.message.equals(getString(R.string.MessageTagPrivateMessage))&&!cell.description.toString().contains("secret-body"),"private native rendering and accessibility stay redacted");
  cell.bind(row(rows.get(1)),true);
  check(cell.dialogCell.hidden!=null&&!cell.description.toString().contains("ephemeral-body"),"ephemeral native rendering stays redacted");
  original=cell.dialogCell;cell.bind(row(accountOne),true);
  check(cell.dialogCell!=original&&cell.dialogCell.normal!=null,"private to normal rebinding resets render state");
  h.unreadOnly=true;check(h.rows().size()==1&&row(h.rows().get(0)).account==1,"unread count filter");
  first.unread_mark=true;h.snapshotDirty=true;check(h.rows().size()==2,"manual unread mark retained");
  LaunchActivity activity=(LaunchActivity)h.parent;
  h.open(row(accountOne));
  check(activity.switched==1&&activity.presented.account==1,"navigation uses owning account");
  check(activity.presented.args.get("user_id")==77L,"navigation uses original dialog");
  check(activity.events.equals(Arrays.asList("switch","present")),"switch happens before presentation");
  activity.events.clear();activity.presented=null;activity.switched=-1;
  UserConfig.getInstance(1).owner=201;
  h.open(row(accountOne));
  check(activity.events.isEmpty()&&activity.presented==null,"changed owner refuses stale row navigation");
  check(h.listView.adapter.updates==1,"stale row triggers refresh");
  UserConfig.getInstance(1).owner=200;UserConfig.getInstance(1).active=false;
  h.open(row(accountOne));check(activity.events.isEmpty(),"logged out account refuses navigation");
  h.snapshotDirty=true;
  check(h.rows().size()==1,"inactive account excluded from listing");
  UserConfig.getInstance(1).active=true;MessagesController.getInstance(1).canOpen=false;
  h.open(row(accountOne));check(activity.events.isEmpty(),"chat access check blocks navigation");
  h.parent=null;h.open(row(accountZero));check(h.listView.adapter.updates==3,"detached view refuses navigation and refreshes");
  h.unreadOnly=false;h.snapshotDirty=true;rows=h.rows();
  Harness.Entry untouchedAccountRow=row(rows.get(0));
  first.last_message_date=350;first.top_message=350;
  MessageObject accountReplacement=new MessageObject(350,350,"account-dirty-refresh");
  MessagesController.getInstance(0).dialogMessagesByIds.put(350,accountReplacement);
  MessagesController.getInstance(0).dialogMessage.put(77L,new ArrayList<>(Arrays.asList(accountReplacement)));
  h.dirtyAccounts.add(0);h.rebuildDirtySnapshots();
  ArrayList<UItem> mergedDirty=h.rows();
  check(row(mergedDirty.get(0)).account==0&&row(mergedDirty.get(0)).dialogId==77,"dirty-account rebuild reorders the changed account row");
  check(row(mergedDirty.get(1))==untouchedAccountRow,"dirty-account rebuild preserves other account payloads");
  rows=mergedDirty;
  h.snapshotDirty=false;h.cachedUnreadOnly=false;
  Harness.Entry stableOther=row(rows.get(1));
  first.last_message_date=400;first.top_message=400;
  MessageObject fresh=new MessageObject(400,400,"incremental-message");
  MessagesController.getInstance(0).dialogMessagesByIds.put(400,fresh);
  MessagesController.getInstance(0).dialogMessage.put(77L,new ArrayList<>(Arrays.asList(fresh)));
  check(h.updateDialogSnapshot(0,77),"single-dialog update finds the source dialog");
  ArrayList<UItem> incremental=h.rows();
  check(row(incremental.get(0)).dialogId==77&&row(incremental.get(0)).preview.equals("incremental-message"),"single-dialog update refreshes and reorders the changed row");
  check(row(incremental.get(1))==stableOther,"single-dialog update preserves unchanged row snapshots");
  System.out.println("PASS: "+checks+" unified inbox behavior checks");
 }
}
'''


def render(parts):
    result = fixture
    for name, value in parts.items():
        result = result.replace('/*' + name + '*/', value)
    return result


parts = {'ENTRY': entry, 'FILL': fill, 'CREATE_ROW_ITEM': create_row_item, 'REBUILD': rebuild, 'REBUILD_DIRTY': rebuild_dirty,
         'INDEX_ROWS': index_rows, 'BUILD_ACCOUNT_ROWS': build_account_rows, 'MERGE_ROWS': merge_rows,
         'REFRESH_HAS_MORE': refresh_has_more, 'HAS_MORE': has_more, 'SAME_ENTRY': same_entry, 'CREATE_ENTRY': create_entry,
         'UPDATE_DIALOG': update_dialog, 'OPEN': opening, 'IDENTITY': identity, 'BINDING': binding}
parts['FIND_ROW_INDEX'] = find_row_index
mutations = (
    ('IDENTITY', 'left.account == right.account && ', '', 'row identity isolates accounts'),
    ('OPEN', 'config.getClientUserId() != row.owner || ', '', 'changed owner refuses stale row navigation'),
    ('CREATE_ENTRY', 'if (DialogObject.isEncryptedDialog(dialog.id) || latest != null && latest.isSecretMedia())', 'if (false)', 'secret chat preview redacted'),
    ('BINDING', 'boundAccount != row.account || ', '', 'recycling across accounts recreates native cell'),
)
with tempfile.TemporaryDirectory(prefix='unified-inbox-tests-') as directory:
    java = Path(directory) / 'InboxRegression.java'
    def run(current):
        java.write_text(render(current))
        subprocess.run([str(JAVA / 'javac'), '-d', directory, str(java)], check=True)
        return subprocess.run([str(JAVA / 'java'), '-cp', directory, 'InboxRegression'], capture_output=True, text=True)
    result = run(parts)
    if result.returncode:
        raise AssertionError(result.stdout + result.stderr)
    print(result.stdout.strip())
    for key, old, new, expected in mutations:
        if parts[key].count(old) != 1:
            raise AssertionError('Mutation target changed: ' + expected)
        mutated = dict(parts)
        mutated[key] = mutated[key].replace(old, new, 1)
        result = run(mutated)
        if result.returncode == 0 or 'AssertionError: ' + expected not in result.stderr:
            raise AssertionError('Mutation escaped expected behavior check: ' + expected + '\n' + result.stdout + result.stderr)
        print('PASS: rejected mutation — ' + expected)
