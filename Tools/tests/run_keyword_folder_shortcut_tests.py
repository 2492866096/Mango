#!/usr/bin/env python3
"""Execute production selected-keyword and chat-folder methods without Android."""
from pathlib import Path
import os
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "TMessagesProj/src/main/java/org/telegram"
JAVA = Path(os.environ.get("JAVA_HOME", "/Applications/Android Studio.app/Contents/jbr/Contents/Home")) / "bin"
sheet = (SOURCE / "ui/Components/KeywordBlockSheet.java").read_text()
manager = (SOURCE / "messenger/KeywordMessageFilterManager.java").read_text()
folders = (SOURCE / "ui/Components/FiltersListBottomSheet.java").read_text()
controller = (SOURCE / "messenger/MessagesController.java").read_text()
create = (SOURCE / "ui/FilterCreateActivity.java").read_text()
chat = (SOURCE / "ui/ChatActivity.java").read_text()


def extract(source, marker):
    start = source.index(marker)
    brace = source.index("{", start)
    depth, end = 1, brace + 1
    while depth:
        if source[end] == "{":
            depth += 1
        elif source[end] == "}":
            depth -= 1
        end += 1
    return source[start:end]


checks = 0


def check(condition, label):
    global checks
    checks += 1
    if not condition:
        raise AssertionError(label)


show = extract(sheet, "    public static void show(")
check("source.setTextIsSelectable(true)" in show and "source.setCustomSelectionActionModeCallback" in show,
      "long press uses Android's real selectable text ActionMode")
check("addSelection(selected, text, source.getSelectionStart(), source.getSelectionEnd())" in show,
      "actual selection callback uses executable selection method")
check("menu.clear()" in show and "menu.add(0, ADD_SELECTION" in show,
      "ActionMode exposes the add-keyword action")
check("sheetScroll.addView(content)" in show and "sheet.setCustomView(sheetScroll)" in show,
      "whole sheet scrolls so submit remains reachable in landscape")
check("public boolean dispatchTouchEvent(MotionEvent event)" in show
      and "requestDisallowInterceptTouchEvent(true)" in show
      and "requestDisallowInterceptTouchEvent(false)" in show,
      "source viewport keeps selection/scroll gestures from outer scroll and releases at gesture end")
check("protected boolean canDismissWithSwipe()" in show and "return false;" in show,
      "selection/scroll drags do not dismiss the sheet")
chips = extract(sheet, "    private static void rebuildSelection(")
check("chip.setSingleLine(true)" in chips and "chip.setEllipsize(TextUtils.TruncateAt.END)" in chips,
      "five long selected phrases cannot expand the chips without limit")
check("chip.setContentDescription(value" in chips,
      "ellipsized selections retain full accessibility text")
check("fragment.getCurrentAccount() != account" in show and "getClientUserId() != owner" in show,
      "stale selected-keyword sheet cannot write another account")
shortcut = extract(chat, "    private void showMangoChatFolders()")
check("currentAccount != account" in shortcut and "did != dialog_id" in shortcut,
      "folder callback guards account and active chat")
check("new FilterCreateActivity(account, null, include)" in shortcut,
      "chat shortcut supplies account before new folder construction")
check("filter.neverShow.remove(id)" in shortcut and "filter.alwaysShow.add(id)" in shortcut,
      "adding a chat removes contradictory exclusion")
check("if (checked || include.isEmpty()) return" in shortcut,
      "already included folders are add-only, never accidentally removed")
check("cell.setTextAndIcon(LocaleController.getString(R.string.CreateNewFilter), combinedDrawable);\n                cell.setChecked(false);" in folders,
      "recycled create-folder row clears the old selection checkmark")
check("FiltersListBottomSheet(BaseFragment baseFragment" in folders and "new ArrayList<>(selectedDialogs)" in folders,
      "existing dialogs and chat fragments use one compatible sheet with a defensive input copy")
check("this(UserConfig.selectedAccount, dialogFilter, alwaysShow)" in create,
      "legacy constructors retain selected-account behavior")

selection_methods = "\n".join(extract(sheet, m) for m in (
    "    public static String messageText(", "    static boolean addSelection("))
manager_methods = "\n".join(extract(manager, m) for m in (
    "    public synchronized void addKeywordsAndEnable(",
    "    private int findKeywordIndexLocked(", "    private static String normalizeKeyword("))
folder_methods = "\n".join(extract(folders, m) for m in (
    "    public static ArrayList<Long> getDialogsCount(",
    "    private static boolean isDialogIncludedInFilter("))
include_methods = "\n".join(extract(controller, m) for m in (
    "        public boolean includesDialog(AccountInstance accountInstance, long dialogId)",
    "        public boolean includesDialog(AccountInstance accountInstance, long dialogId, TLRPC.Dialog d)"))
constructors = "\n".join(extract(create, m) for m in (
    "    public FilterCreateActivity()",
    "    public FilterCreateActivity(MessagesController.DialogFilter dialogFilter)",
    "    public FilterCreateActivity(MessagesController.DialogFilter dialogFilter, ArrayList<Long> alwaysShow)",
    "    public FilterCreateActivity(int account, MessagesController.DialogFilter dialogFilter, ArrayList<Long> alwaysShow)"))
flags = "\n".join(re.findall(r"    public static int DIALOG_FILTER_FLAG_\w+ = [^;]+;", controller))

fixture = r'''
import java.util.*;
class TextUtils {static boolean isEmpty(CharSequence v){return v==null||v.length()==0;}}
class TLRPC {
 static class Message {String message;Message(String text){message=text;}}
 static class Dialog {long id;int folder_id,unread_mentions_count,unread_count;boolean unread_mark;Dialog(long id){this.id=id;}}
 static class EncryptedChat {long user_id;EncryptedChat(long id){user_id=id;}}
 static class User {boolean bot,self,contact;}
 static class Chat {boolean channel,megagroup;}
}
class MessageObject {
 TLRPC.Message messageOwner;boolean sponsored;
 MessageObject(String text){messageOwner=new TLRPC.Message(text);} boolean isSponsored(){return sponsored;}
 static CharSequence replaceAnimatedEmoji(CharSequence s,Object entities,Object metrics){return s;}
}
class ChatObject {static boolean isChannel(TLRPC.Chat c){return c.channel;}}
class ContactsController {final Set<Long> contacts=new HashSet<>();boolean isContact(long id){return contacts.contains(id);}}
class DialogObject {
 static boolean isEncryptedDialog(long id){return id>100000000000L;}
 static int getEncryptedChatId(long id){return (int)(id-100000000000L);}
}
class LongSparseIntArray implements Cloneable {
 final HashMap<Long,Integer> values=new HashMap<>();
 public LongSparseIntArray clone(){LongSparseIntArray r=new LongSparseIntArray();r.values.putAll(values);return r;}
}
class MessagesController {
''' + flags + r'''
 final HashMap<Long,TLRPC.Dialog> dialogs_dict=new HashMap<>();
 final HashMap<Integer,TLRPC.EncryptedChat> encrypted=new HashMap<>();
 final HashMap<Long,TLRPC.User> users=new HashMap<>();
 final HashMap<Long,TLRPC.Chat> chats=new HashMap<>();
 final HashMap<Integer,DialogFilter> dialogFiltersById=new HashMap<>();
 final Set<Long> muted=new HashSet<>();
 TLRPC.EncryptedChat getEncryptedChat(int id){return encrypted.get(id);}
 TLRPC.User getUser(long id){return users.get(id);} TLRPC.Chat getChat(long id){return chats.get(id);}
 boolean isDialogMuted(long id,int topic){return muted.contains(id);}
 int getDialogUnreadCount(TLRPC.Dialog d){return d.unread_count;}
 static class DialogFilter {
  int id,color,flags;String name="";boolean title_noanimate;Object entities;
  ArrayList<Long> alwaysShow=new ArrayList<>(),neverShow=new ArrayList<>();LongSparseIntArray pinnedDialogs=new LongSparseIntArray();
''' + include_methods + r'''
 }
}
class AccountInstance {
 static final HashMap<Integer,AccountInstance> all=new HashMap<>();
 static AccountInstance getInstance(int id){return all.computeIfAbsent(id,k->new AccountInstance());}
 final MessagesController controller=new MessagesController();final ContactsController contacts=new ContactsController();
 MessagesController getMessagesController(){return controller;} ContactsController getContactsController(){return contacts;}
}
class UserConfig {static int selectedAccount;}
class BaseFragment {
 int currentAccount=UserConfig.selectedAccount;
 void setCurrentAccount(int account){currentAccount=account;}
 MessagesController getMessagesController(){return AccountInstance.getInstance(currentAccount).getMessagesController();}
 static int dp(int v){return v;}
}
class Paint {static final int ANTI_ALIAS_FLAG=1;}
class TextPaint {TextPaint(int f){}void setTextSize(int v){}Object getFontMetricsInt(){return null;}}
class SpannableStringBuilder implements CharSequence {
 final String value;SpannableStringBuilder(CharSequence s){value=s.toString();}
 public int length(){return value.length();} public char charAt(int i){return value.charAt(i);}
 public CharSequence subSequence(int s,int e){return value.substring(s,e);} public String toString(){return value;}
}
class Emoji {static CharSequence replaceEmoji(CharSequence s,Object m,boolean b){return s;}}
class AnimatedEmojiDrawable {static int lastAccount=-1;static void toggleAnimations(int account,boolean enabled){lastAccount=account;}}
class FilterCreateActivity extends BaseFragment {
 MessagesController.DialogFilter filter;boolean creatingNew,newFilterAnimations;int newFilterFlags,newFilterColor;
 CharSequence newFilterName;ArrayList<Long> newAlwaysShow,newNeverShow;LongSparseIntArray newPinned;
''' + constructors + r'''
}
class KeywordBatchFixture {
 final ArrayList<String> keywords=new ArrayList<>();boolean enabled;int saves,loads;
 void ensureLoaded(){loads++;}void saveSettingsLocked(boolean reload){if(!reload)throw new AssertionError("reload required");saves++;}
''' + manager_methods + r'''
}
public class KeywordFolderShortcutRegression {
 static final int MAX_WORDS=5;static int checks;
 static void check(boolean condition,String label){checks++;if(!condition)throw new AssertionError(label);}
''' + selection_methods + folder_methods + r'''
 static void selection(){
  MessageObject message=new MessageObject("  原文 caption  ");
  check(messageText(message).equals("原文 caption"),"original caption is available");
  message.sponsored=true;check(messageText(message).isEmpty(),"sponsored content excluded");
  check(messageText(null).isEmpty()&&messageText(new MessageObject(null)).isEmpty(),"absent text handled");
  ArrayList<String> selected=new ArrayList<>();String text="  广告词  Hello  😀测试  ";
  check(addSelection(selected,text,5,2)&&selected.equals(Arrays.asList("广告词")),"reverse selection adds exact Chinese phrase");
  check(!addSelection(selected,text,-1,4)&&!addSelection(selected,text,2,999)&&!addSelection(selected,text,2,2),"invalid and empty ranges ignored");
  check(!addSelection(selected,text,0,2),"whitespace does not become a keyword");
  check(addSelection(selected,text,7,12)&&selected.get(1).equals("Hello"),"exact mixed-case selection retained");
  check(!addSelection(selected,"hello",0,5),"case-insensitive duplicate rejected");
  int emoji=text.indexOf("😀");check(addSelection(selected,text,emoji,emoji+4)&&selected.get(2).equals("😀测试"),"emoji and Chinese phrase retained without conversion");
  check(addSelection(selected,"four",0,4)&&addSelection(selected,"five",0,4),"five phrases can be selected");
  check(!addSelection(selected,"six",0,3)&&selected.size()==5,"sixth selection rejected");
  String longText="很长的关键词".repeat(3000);selected.clear();
  check(addSelection(selected,longText,0,longText.length())&&selected.get(0).equals(longText),"long original phrase preserved despite compact chip rendering");
 }
 static void batch(){
  KeywordBatchFixture m=new KeywordBatchFixture();
  m.addKeywordsAndEnable(null);m.addKeywordsAndEnable(Collections.emptyList());m.addKeywordsAndEnable(Arrays.asList(null,"  ","\uFEFF  "));
  check(!m.enabled&&m.keywords.isEmpty()&&m.saves==0,"empty invalid batches do not enable or save");
  m.addKeywordsAndEnable(Arrays.asList("  广告词 ","\uFEFFHello  ","hello","广告词",null));
  check(m.enabled&&m.keywords.equals(Arrays.asList("广告词","Hello")),"batch trims BOM/space and deduplicates case-insensitively");
  check(m.saves==1,"all selected phrases save and reload once");
  m.addKeywordsAndEnable(Arrays.asList("HELLO","广告词"));check(m.saves==1,"enabled duplicate-only batch causes no redundant refresh");
  m.enabled=false;m.addKeywordsAndEnable(Arrays.asList("HELLO"));check(m.enabled&&m.saves==2,"existing valid phrase can re-enable filtering in one save");
  m.addKeywordsAndEnable(Arrays.asList("new","other"));check(m.saves==3&&m.keywords.size()==4,"second batch also saves once");
 }
 static void folderIdsAndMembership(){
  int account=7;BaseFragment fragment=new BaseFragment();fragment.setCurrentAccount(account);
  MessagesController c=fragment.getMessagesController();long secret=100000000001L;
  c.encrypted.put(1,new TLRPC.EncryptedChat(10));TLRPC.Dialog sd=new TLRPC.Dialog(secret);sd.unread_count=1;c.dialogs_dict.put(secret,sd);
  TLRPC.User user=new TLRPC.User();c.users.put(10L,user);
  MessagesController.DialogFilter f=new MessagesController.DialogFilter();f.alwaysShow.add(10L);
  check(isDialogIncludedInFilter(account,f,secret),"secret chat uses partner user ID for explicit folder membership");
  check(isDialogIncludedInFilter(account,f,10L),"explicit membership known even before regular dialog is cached");
  f.neverShow.add(10L);check(!isDialogIncludedInFilter(account,f,secret),"never-show wins over contradictory always-show");f.neverShow.clear();
  check(!isDialogIncludedInFilter(account+1,f,secret),"secret chat lookup is account isolated");
  check(!isDialogIncludedInFilter(account,f,100000000099L),"missing encrypted chat safely excluded");
  f.alwaysShow.clear();f.flags=MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS;
  check(isDialogIncludedInFilter(account,f,secret),"automatic folder resolves encrypted partner user flags");
  f.flags|=MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED;sd.folder_id=1;
  check(!isDialogIncludedInFilter(account,f,secret),"encrypted chat keeps own archive state");sd.folder_id=0;
  f.flags=MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS|MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED;c.muted.add(secret);
  check(!isDialogIncludedInFilter(account,f,secret),"encrypted chat keeps own muted state");c.muted.clear();
  f.flags=MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS|MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ;sd.unread_count=0;
  check(!isDialogIncludedInFilter(account,f,secret),"encrypted chat keeps own read state");
  TLRPC.Chat group=new TLRPC.Chat(),channel=new TLRPC.Chat();channel.channel=true;
  c.chats.put(20L,group);c.chats.put(30L,channel);c.dialogs_dict.put(-20L,new TLRPC.Dialog(-20));c.dialogs_dict.put(-30L,new TLRPC.Dialog(-30));
  f.flags=MessagesController.DIALOG_FILTER_FLAG_GROUPS;check(isDialogIncludedInFilter(account,f,-20)&&!isDialogIncludedInFilter(account,f,-30),"negative group IDs retain group semantics");
  f.flags=MessagesController.DIALOG_FILTER_FLAG_CHANNELS;check(isDialogIncludedInFilter(account,f,-30)&&!isDialogIncludedInFilter(account,f,-20),"negative channel IDs retain channel semantics");
  ArrayList<Long> all=new ArrayList<>(Arrays.asList(secret,10L,-20L,-30L,10L,-20L,100000000099L));
  check(getDialogsCount(fragment,null,all,true,false).equals(Arrays.asList(10L,-20L,-30L)),"secret-first and duplicate inputs normalize to unique folder IDs");
  check(getDialogsCount(fragment,null,new ArrayList<>(Arrays.asList(10L,secret)),true,false).equals(Arrays.asList(10L)),"regular-first secret duplicate also deduplicated");
  f.alwaysShow.add(10L);f.neverShow.add(-20L);
  check(getDialogsCount(fragment,f,all,true,false).equals(Arrays.asList(-20L,-30L)),"additions skip IDs already explicitly included");
  check(getDialogsCount(fragment,f,all,false,false).equals(Arrays.asList(10L,-30L)),"exclusions skip IDs already explicitly excluded");
  check(getDialogsCount(fragment,f,all,true,true).equals(Arrays.asList(-20L)),"check mode returns only first actionable ID");
  check(getDialogsCount(fragment,f,null,true,false).isEmpty(),"null selection has no folder additions");
 }
 static void accountAwareConstruction(){
  UserConfig.selectedAccount=0;
  MessagesController c0=AccountInstance.getInstance(0).controller,c8=AccountInstance.getInstance(8).controller;
  c0.dialogFiltersById.put(2,new MessagesController.DialogFilter());
  c8.dialogFiltersById.put(2,new MessagesController.DialogFilter());c8.dialogFiltersById.put(3,new MessagesController.DialogFilter());
  ArrayList<Long> include=new ArrayList<>(Arrays.asList(-20L));
  FilterCreateActivity target=new FilterCreateActivity(8,null,include);
  check(target.currentAccount==8&&target.filter.id==4,"new folder ID allocated from target account before construction reads controller");
  check(AnimatedEmojiDrawable.lastAccount==8,"animated emoji initialization uses target account");
  check(target.newAlwaysShow.equals(include)&&target.filter.alwaysShow.isEmpty(),"initial shortcut inclusion copied without mutating unsaved filter");
  FilterCreateActivity legacy=new FilterCreateActivity(null,include);
  check(legacy.currentAccount==0&&legacy.filter.id==3,"legacy caller still allocates on selected account");
  MessagesController.DialogFilter existing=new MessagesController.DialogFilter();existing.id=91;existing.alwaysShow.add(42L);existing.pinnedDialogs.values.put(42L,1);
  FilterCreateActivity edit=new FilterCreateActivity(8,existing,null);
  check(edit.filter==existing&&!edit.creatingNew&&edit.newAlwaysShow.equals(Arrays.asList(42L)),"existing folder editing preserved");
  check(edit.newPinned!=existing.pinnedDialogs&&edit.newPinned.values.equals(existing.pinnedDialogs.values),"existing pin order cloned rather than shared");
 }
 public static void main(String[] args){selection();batch();folderIdsAndMembership();accountAwareConstruction();System.out.println("production assertions: "+checks);}
}
'''

with tempfile.TemporaryDirectory(prefix="mango-keyword-folders-") as temp:
    source = Path(temp) / "KeywordFolderShortcutRegression.java"
    source.write_text(fixture)
    subprocess.run([str(JAVA / "javac"), "-encoding", "UTF-8", str(source)], check=True)
    subprocess.run([str(JAVA / "java"), "-cp", temp, "KeywordFolderShortcutRegression"], check=True)
print(f"UI/call-site integration assertions: {checks}")
