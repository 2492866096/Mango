#!/usr/bin/env python3
"""Run production pinned-order persistence/state methods without Android or a server."""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "TMessagesProj/src/main/java"
JAVA = Path(os.environ.get("JAVA_HOME", "/Applications/Android Studio.app/Contents/jbr/Contents/Home")) / "bin"
controller = (SOURCE / "org/telegram/messenger/MessagesController.java").read_text()
adapter = (SOURCE / "org/telegram/ui/Adapters/DialogsAdapter.java").read_text()
dialogs = (SOURCE / "org/telegram/ui/DialogsActivity.java").read_text()


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


methods = "\n".join(extract(controller, marker) for marker in [
    "    private void loadLocalUnlimitedPinnedDialogs()",
    "    private String getLocalUnlimitedPinKey(",
    "    private LongSparseIntArray getLocalUnlimitedPinnedDialogs(",
    "    private boolean isLocallyUnlimitedPinnedDialog(",
    "    private void updateLocalUnlimitedPinnedDialogOrders(",
    "    public void rememberPinnedDialogsOrder(",
    "    private void clearLocalPinnedDialogsOrder(",
    "    private void updateLocalPinnedDialogsOrderMembership(",
    "    private void applyLocalPinnedDialogsOrder()",
    "    private boolean matchesLocalPinnedDialogsOrder(",
    "    private void resetLocalUnlimitedPinnedDialogsState()",
    "    private void applyLocalUnlimitedPinnedDialogsState()",
])

# UI and network integration assertions supplement the executable production-method cases.
checks = 0

def check(condition, name):
    global checks
    checks += 1
    if not condition:
        raise AssertionError(name)

check("rememberPinnedDialogsOrder(folderId)" in extract(adapter, "    public void moveDialogs("), "drag immediately persists full order")
check("saveDialogFilter(filter, false, true)" in extract(adapter, "    public void moveDialogs("), "folder pin drag immediately persists local rank map")
for marker in ("        public void clearView(", "    public void onPause()", "    public void onFragmentDestroy()", "    private void hideActionMode("):
    check("persistMovedPinnedDialogs()" in extract(dialogs, marker), "pin changes flush at " + marker.strip())
reorder = extract(controller, "    public void reorderPinnedDialogs(")
check("matchesLocalPinnedDialogsOrder(folderId, req.order)" in reorder, "only matching server acknowledgment can clear local overlay")
check("orderRevision == pinnedDialogOrderRevisions.get(folderId)" in reorder, "old request acknowledgment cannot clear a newer move")
check("localPins == null || localPins.size() == 0" in reorder, "mixed local/server order remains persisted after acknowledgment")
load = extract(controller, "    public void loadPinnedDialogs(")
check(load.index("orderRevision != pinnedDialogOrderRevisions.get(folderId)") < load.index("applyDialogsNotificationsSettings(newPinnedDialogs)"), "stale refresh rejected before altering pin state/cache")
sort = extract(controller, "    public void sortDialogs(")
check(sort.index("applyLocalUnlimitedPinnedDialogsState()") < sort.index("applyLocalPinnedDialogsOrder()") < sort.index("Collections.sort(allDialogs"), "full overlay applied after local pin membership and before comparator")
options = extract(dialogs, "    private void showItemOptions()")
check("boolean toDark = !isCurrentThemeDark" in options, "theme toggle follows actual brightness rather than remembered theme key")
check("optionsItem.setOnClickListener" in dialogs and "showItemOptions();" in dialogs, "theme toggle is wired to real dialog-list overflow button")

fixture = r'''
import java.util.*;
class SparseArray<T> {
 final TreeMap<Integer,T> values=new TreeMap<>();
 T get(int k){return values.get(k);} void put(int k,T v){values.put(k,v);} void remove(int k){values.remove(k);}
 int size(){return values.size();} int keyAt(int i){return new ArrayList<>(values.keySet()).get(i);}
 T valueAt(int i){return new ArrayList<>(values.values()).get(i);} void clear(){values.clear();}
}
class SparseIntArray {
 final TreeMap<Integer,Integer> values=new TreeMap<>();
 int get(int k){return values.getOrDefault(k,0);} void put(int k,int v){values.put(k,v);} void clear(){values.clear();}
}
class LongSparseIntArray {
 final TreeMap<Long,Integer> values=new TreeMap<>();
 LongSparseIntArray(){} LongSparseIntArray(int capacity){}
 int get(long k){return get(k,0);} int get(long k,int fallback){return values.getOrDefault(k,fallback);}
 void put(long k,int v){values.put(k,v);} int indexOfKey(long k){return values.containsKey(k)?0:-1;}
 int size(){return values.size();} long keyAt(int i){return new ArrayList<>(values.keySet()).get(i);}
 int valueAt(int i){return new ArrayList<>(values.values()).get(i);} void delete(long k){values.remove(k);}
}
class DialogMap {
 final HashMap<Long,TLRPC.Dialog> values=new HashMap<>();
 TLRPC.Dialog get(long k){return values.get(k);} void put(long k,TLRPC.Dialog d){values.put(k,d);}
}
class TLRPC {
 static class Dialog {long id;int pinnedNum,folder_id;boolean pinned;Dialog(long id,int rank,int folder){this.id=id;pinned=true;pinnedNum=rank;folder_id=folder;}}
 static class TL_dialogFolder extends Dialog {TL_dialogFolder(int folder){super(-999,99,folder);}}
 static class InputPeer {long id;InputPeer(long id){this.id=id;}}
 static class InputDialogPeer {}
 static class TL_inputDialogPeer extends InputDialogPeer {InputPeer peer;TL_inputDialogPeer(long id){peer=new InputPeer(id);}}
}
class DialogObject {
 static boolean isEncryptedDialog(long id){return id>100000000000L;}
 static long getPeerDialogId(TLRPC.InputPeer p){return p.id;}
}
class TextUtils {
 static String join(String delimiter,Iterable<?> values){StringJoiner result=new StringJoiner(delimiter);for(Object value:values)result.add(value.toString());return result.toString();}
}
class SharedPreferences {
 final HashMap<String,Object> values=new HashMap<>();
 Map<String,?> getAll(){return new HashMap<>(values);} Editor edit(){return new Editor();}
 class Editor {
  final HashMap<String,Object> put=new HashMap<>();final Set<String> remove=new HashSet<>();
  Editor putString(String k,String v){put.put(k,v);return this;} Editor putInt(String k,int v){put.put(k,v);return this;}
  Editor remove(String k){remove.add(k);return this;} void apply(){for(String k:remove)values.remove(k);values.putAll(put);}
 }
}
class Storage {
 ArrayList<Long> ids;ArrayList<Integer> ranks;
 void setDialogsPinned(ArrayList<Long> ids,ArrayList<Integer> ranks){this.ids=new ArrayList<>(ids);this.ranks=new ArrayList<>(ranks);}
}
public class PinnedPersistenceRegression {
 static final String PREF_LOCAL_UNLIMITED_PIN_PREFIX="local_unlimited_pin_",PREF_LOCAL_PINNED_ORDER_PREFIX="local_pinned_order_";
 final SparseArray<LongSparseIntArray> localUnlimitedPinnedDialogsByFolder=new SparseArray<>();
 final SparseArray<ArrayList<Long>> localPinnedDialogOrdersByFolder=new SparseArray<>();
 final SparseIntArray pinnedDialogOrderRevisions=new SparseIntArray();
 final DialogMap dialogs_dict=new DialogMap(); final ArrayList<TLRPC.Dialog> allDialogs=new ArrayList<>();
 final SparseArray<ArrayList<TLRPC.Dialog>> folders=new SparseArray<>();
 final SharedPreferences mainPreferences;final Storage storage=new Storage();static int checks;
 PinnedPersistenceRegression(){this(new SharedPreferences());} PinnedPersistenceRegression(SharedPreferences prefs){mainPreferences=prefs;}
 Storage getMessagesStorage(){return storage;}
 ArrayList<TLRPC.Dialog> getDialogs(int folder){ArrayList<TLRPC.Dialog> result=folders.get(folder);if(result==null){result=new ArrayList<>();folders.put(folder,result);}return result;}
 void add(long id,int rank,int folder,boolean local){TLRPC.Dialog d=new TLRPC.Dialog(id,rank,folder);allDialogs.add(d);dialogs_dict.put(id,d);getDialogs(folder).add(d);if(local){getLocalUnlimitedPinnedDialogs(folder,true).put(id,rank);mainPreferences.edit().putInt(getLocalUnlimitedPinKey(folder,id),rank).apply();}}
 void refresh(){resetLocalUnlimitedPinnedDialogsState();applyLocalUnlimitedPinnedDialogsState();applyLocalPinnedDialogsOrder();for(int i=0;i<folders.size();i++){ArrayList<TLRPC.Dialog> list=getDialogs(folders.keyAt(i));list.sort((a,b)->Boolean.compare(b.pinned,a.pinned)!=0?Boolean.compare(b.pinned,a.pinned):Integer.compare(b.pinnedNum,a.pinnedNum));}}
 String ids(int folder){StringJoiner j=new StringJoiner(",");for(TLRPC.Dialog d:getDialogs(folder))if(d.pinned)j.add(Long.toString(d.id));return j.toString();}
 static void check(boolean condition,String label){checks++;if(!condition)throw new AssertionError(label);}
''' + methods + r'''
 static void mixedOrderAndRestart(){
  PinnedPersistenceRegression c=new PinnedPersistenceRegression();
  // A server pin dragged above two local-only pins must not fall back on sort.
  c.add(10,4,0,false);c.add(30,3,0,true);c.add(20,2,0,false);c.add(40,1,0,true);
  c.rememberPinnedDialogsOrder(0);
  check("10,30,20,40".equals(c.mainPreferences.values.get(PREF_LOCAL_PINNED_ORDER_PREFIX+0)),"full mixed order persisted, not just local subset");
  check(c.storage.ids.equals(Arrays.asList(10L,20L))&&c.storage.ranks.equals(Arrays.asList(4,2)),"server pin DB updated immediately without leaking local-only pins into server DB");
  check(c.getLocalUnlimitedPinnedDialogs(0,false).get(30)==2&&c.getLocalUnlimitedPinnedDialogs(0,false).get(40)==1,"local pin preference subset follows full order");
  for(int i=0;i<5;i++){c.refresh();check("10,30,20,40".equals(c.ids(0)),"repeated refresh preserves mixed order "+i);}
  PinnedPersistenceRegression restarted=new PinnedPersistenceRegression(c.mainPreferences);
  restarted.loadLocalUnlimitedPinnedDialogs();
  restarted.add(20,20,0,false);restarted.add(10,10,0,false);restarted.add(40,0,0,false);restarted.add(30,0,0,false);
  restarted.refresh();
  check("10,30,20,40".equals(restarted.ids(0)),"preference reload restores mixed order after process restart and stale server ranks");
  check(restarted.matchesLocalPinnedDialogsOrder(0,new ArrayList<>(Arrays.asList(new TLRPC.TL_inputDialogPeer(10),new TLRPC.TL_inputDialogPeer(20)))),"server acknowledgment matching excludes local-only pins");
  check(!restarted.matchesLocalPinnedDialogsOrder(0,new ArrayList<>(Arrays.asList(new TLRPC.TL_inputDialogPeer(20),new TLRPC.TL_inputDialogPeer(10)))),"old server request cannot acknowledge a newer order");
 }
 static void membershipAndFolders(){
  PinnedPersistenceRegression c=new PinnedPersistenceRegression();
  c.add(10,3,0,false);c.add(20,2,0,false);c.add(30,1,0,false);c.add(99,1,1,false);
  c.rememberPinnedDialogsOrder(0);c.rememberPinnedDialogsOrder(1);
  c.dialogs_dict.get(20).pinned=false;c.updateLocalPinnedDialogsOrderMembership(0,20,false);c.refresh();
  check("10,30".equals(c.ids(0)),"unpin never resurrected by saved order");
  check("99".equals(c.ids(1)),"archive folder order isolated");
  c.dialogs_dict.get(20).pinned=true;c.dialogs_dict.get(20).pinnedNum=99;c.updateLocalPinnedDialogsOrderMembership(0,20,true);c.refresh();
  check("20,10,30".equals(c.ids(0)),"repinning remembered chat goes to top instead of old slot");
  c.add(40,100,0,false);c.refresh();check("40,20,10,30".equals(c.ids(0)),"newly pinned chat goes above saved list");
  c.dialogs_dict.get(10).folder_id=1;c.refresh();
  check(c.dialogs_dict.get(10).pinnedNum>c.dialogs_dict.get(99).pinnedNum,"moved chat uses archive membership instead of remembered main-list position");
  int revision=c.pinnedDialogOrderRevisions.get(0);c.clearLocalPinnedDialogsOrder(0);
  check(c.localPinnedDialogOrdersByFolder.get(0)==null&&!c.mainPreferences.values.containsKey(PREF_LOCAL_PINNED_ORDER_PREFIX+0),"matching acknowledgment clears transient normal-pin overlay from memory and prefs");
  check(c.pinnedDialogOrderRevisions.get(0)>revision,"clearing overlay invalidates old refresh requests");
 }
 static void malformedPrefsAndSecrets(){
  SharedPreferences prefs=new SharedPreferences();
  prefs.values.put(PREF_LOCAL_PINNED_ORDER_PREFIX+"0","10,20,10");
  prefs.values.put(PREF_LOCAL_PINNED_ORDER_PREFIX+"bad","garbage");prefs.values.put(PREF_LOCAL_PINNED_ORDER_PREFIX+"2",true);
  prefs.values.put(PREF_LOCAL_UNLIMITED_PIN_PREFIX+"0_30",1);
  PinnedPersistenceRegression c=new PinnedPersistenceRegression(prefs);c.loadLocalUnlimitedPinnedDialogs();
  check(c.localPinnedDialogOrdersByFolder.size()==1&&c.localPinnedDialogOrdersByFolder.get(0).equals(Arrays.asList(10L,20L)),"duplicate/corrupt/untyped saved rows handled safely");
  c.add(10,3,0,false);c.add(100000000001L,2,0,false);c.add(20,1,0,false);c.rememberPinnedDialogsOrder(0);
  int revision=c.pinnedDialogOrderRevisions.get(0);c.rememberPinnedDialogsOrder(0);
  check(c.pinnedDialogOrderRevisions.get(0)==revision,"unchanged order does not spuriously invalidate requests");
  check(c.matchesLocalPinnedDialogsOrder(0,new ArrayList<>(Arrays.asList(new TLRPC.TL_inputDialogPeer(10),new TLRPC.TL_inputDialogPeer(20)))),"secret chats retain local rank but are excluded from remote order");
  check(!c.matchesLocalPinnedDialogsOrder(0,new ArrayList<>(Arrays.asList(new TLRPC.TL_inputDialogPeer(10)))),"incomplete acknowledgment rejected");
  check(!c.matchesLocalPinnedDialogsOrder(0,new ArrayList<>(Arrays.asList(new TLRPC.InputDialogPeer()))),"unknown peer subtype rejected without field access or crash");
 }
 public static void main(String[] args){mixedOrderAndRestart();membershipAndFolders();malformedPrefsAndSecrets();System.out.println("PASS: "+checks+" production pinned persistence assertions");}
}
'''
with tempfile.TemporaryDirectory(prefix="pinned-persistence-") as directory:
    java = Path(directory) / "PinnedPersistenceRegression.java"
    java.write_text(fixture)
    subprocess.run([str(JAVA / "javac"), "-d", directory, str(java)], check=True)
    subprocess.run([str(JAVA / "java"), "-cp", directory, "PinnedPersistenceRegression"], check=True)
print(f"PASS: {checks} pinned lifecycle/network/theme integration assertions")
