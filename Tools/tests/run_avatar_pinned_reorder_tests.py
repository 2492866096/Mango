#!/usr/bin/env python3
"""Exercise the real hidden-story avatar fallback and pinned drag handler offline."""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "TMessagesProj/src/main/java"
JAVA = Path(os.environ.get("JAVA_HOME", "/Applications/Android Studio.app/Contents/Home/Contents/Home")) / "bin"
if not JAVA.exists():
    JAVA = Path("/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin")


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


stories = (SOURCE / "org/telegram/ui/Stories/StoriesUtilities.java").read_text()
hidden_draw = extract(stories, "    static boolean drawAvatarWithoutHiddenStoryRing(")
adapter = (SOURCE / "org/telegram/ui/Adapters/DialogsAdapter.java").read_text()
move = extract(adapter, "    public void moveDialogs(RecyclerListView recyclerView, int fromPosition, int toPosition)")
fix_position = extract(adapter, "    public int fixPosition(int position)")
fixture = r'''
import java.util.*;
class RectF { float left,top,right,bottom; RectF(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;} }
class Canvas {}
class ImageReceiver {
 RectF coords; int draws;
 void setImageCoords(RectF r){coords=r;}
 boolean draw(Canvas c){draws++;return true;}
}
class StoriesController { boolean hidden; boolean isStoriesHidden(long id){return hidden;} }
class AvatarStoryParams {
 long dialogId; RectF originalAvatarRect=new RectF(10,20,66,76); int resets;
 void reset(){resets++;}
}
class RecyclerListView {}
class TLObject {}
class TLRPC {
 static class Dialog extends TLObject {long id;int pinnedNum;boolean pinned;Dialog(long id,int n){this.id=id;pinned=true;pinnedNum=n;}}
 static class TL_dialogFolder extends Dialog {TL_dialogFolder(){super(-999,0);}}
}
class LongSparseIntArray {
 final LinkedHashMap<Long,Integer> map=new LinkedHashMap<>();
 int indexOfKey(long id){return map.containsKey(id)?1:-1;}
 int get(long id){return map.getOrDefault(id,0);}
 void put(long id,int value){map.put(id,value);}
}
class MessagesController {
 static class DialogFilter {LongSparseIntArray pinnedDialogs=new LongSparseIntArray();}
 final DialogFilter[] selectedDialogFilter={new DialogFilter(),new DialogFilter()};
 static final MessagesController INSTANCE=new MessagesController();
 static MessagesController getInstance(int account){return INSTANCE;}
 void rememberPinnedDialogsOrder(int folderId){}
}
class MessagesStorage {
 static final MessagesStorage INSTANCE=new MessagesStorage();
 static MessagesStorage getInstance(int account){return INSTANCE;}
 void saveDialogFilter(MessagesController.DialogFilter filter,boolean atBegin,boolean peers){}
}
class DialogsActivity {
 ArrayList<TLRPC.Dialog> dialogs=new ArrayList<>();
 ArrayList<TLRPC.Dialog> getDialogsArray(int a,int b,int c,boolean d){return dialogs;}
}
class ItemInternal {TLRPC.Dialog dialog;ItemInternal(TLRPC.Dialog d){dialog=d;}}
public class AvatarPinnedReorderRegression {
 int currentAccount,dialogsType,folderId;boolean dialogsListFrozen;int updates;
 final DialogsActivity parentFragment=new DialogsActivity();
 final ArrayList<ItemInternal> itemInternals=new ArrayList<>();
 final ArrayList<String> notifications=new ArrayList<>();
 static int checks;
 static void check(boolean b,String msg){checks++;if(!b)throw new AssertionError(msg);}
 TLObject getItem(int p){return p<0||p>=itemInternals.size()?null:itemInternals.get(p).dialog;}
 boolean isPinnedPlaceholderId(long id){return id<=-1000;}
 void updateList(Object scroll){updates++;}
 void notifyItemMoved(int from,int to){notifications.add(from+">"+to);}
 void fixtureDialog(TLRPC.Dialog d){parentFragment.dialogs.add(d);itemInternals.add(new ItemInternal(d));}
''' + hidden_draw + '\n' + fix_position + '\n' + move + r'''
 static void storyFallback(){
  StoriesController stories=new StoriesController();stories.hidden=true;
  ImageReceiver image=new ImageReceiver();AvatarStoryParams params=new AvatarStoryParams();params.dialogId=123;
  boolean drawn=drawAvatarWithoutHiddenStoryRing(456,new Canvas(),image,params,stories);
  check(drawn,"hidden story draws the underlying avatar");
  check(params.resets==1&&params.dialogId==456,"recycled cell story state is reset for the new dialog");
  check(image.coords==params.originalAvatarRect&&image.draws==1,"hidden-story branch restores the full avatar bounds");
  params.originalAvatarRect=new RectF(1,2,53,54);
  drawn=drawAvatarWithoutHiddenStoryRing(456,new Canvas(),image,params,stories);
  check(drawn&&params.resets==1,"same hidden dialog avoids unnecessary reset");
  check(image.coords==params.originalAvatarRect&&image.draws==2,"full bounds are restored after prior story inset even for same dialog");
  stories.hidden=false;
  drawn=drawAvatarWithoutHiddenStoryRing(456,new Canvas(),image,params,stories);
  check(!drawn&&image.draws==2,"visible stories proceed through standard story drawing");
 }
 static void defaultDrag(){
  AvatarPinnedReorderRegression a=new AvatarPinnedReorderRegression();
  a.itemInternals.add(new ItemInternal(new TLRPC.Dialog(-1001,0))); // adapter-only forced row
  TLRPC.Dialog x=new TLRPC.Dialog(10,3),y=new TLRPC.Dialog(20,2),z=new TLRPC.Dialog(30,1);
  a.fixtureDialog(x);a.fixtureDialog(y);a.fixtureDialog(z);
  a.moveDialogs(new RecyclerListView(),1,3);
  check(dialogIds(a.parentFragment.dialogs).equals("20,30,10"),"long drag moves item through the list instead of swapping endpoints");
  check(rowIds(a.itemInternals).equals("-1001,20,30,10"),"adapter snapshot remains aligned without a diff rebuild");
  check(a.notifications.equals(Arrays.asList("1>3"))&&a.updates==0,"drag emits one incremental adapter move and avoids full diff updates");
  check(y.pinnedNum==3&&z.pinnedNum==2&&x.pinnedNum==1,"server pin ranks track the displayed order");
  ArrayList<TLRPC.Dialog> sorted=new ArrayList<>(a.parentFragment.dialogs);
  sorted.sort((left,right)->Integer.compare(right.pinnedNum,left.pinnedNum));
  check(dialogIds(sorted).equals("20,30,10"),"a later normal sort preserves the drag position");
 }
 static void filteredDrag(int dialogsType){
  AvatarPinnedReorderRegression a=new AvatarPinnedReorderRegression();a.dialogsType=dialogsType;
  TLRPC.Dialog x=new TLRPC.Dialog(10,0),y=new TLRPC.Dialog(20,0),z=new TLRPC.Dialog(30,0);
  x.pinned=false;y.pinned=false;z.pinned=false; // Pins belong only to this folder, not the main list.
  MessagesController.DialogFilter filter=MessagesController.getInstance(0).selectedDialogFilter[dialogsType==8?1:0];
  filter.pinnedDialogs.put(10,0);filter.pinnedDialogs.put(20,1);filter.pinnedDialogs.put(30,2);
  a.itemInternals.add(new ItemInternal(new TLRPC.Dialog(-1001,0)));a.fixtureDialog(x);a.fixtureDialog(y);a.fixtureDialog(z);
  a.moveDialogs(new RecyclerListView(),1,3);
  check(dialogIds(a.parentFragment.dialogs).equals("20,30,10"),"folder-filter pinned rows move in their own list");
  check(filter.pinnedDialogs.get(20)==0&&filter.pinnedDialogs.get(30)==1&&filter.pinnedDialogs.get(10)==2,"filter pin ranks are persisted in the same order");
  ArrayList<TLRPC.Dialog> sorted=new ArrayList<>(a.parentFragment.dialogs);
  sorted.sort((left,right)->Integer.compare(filter.pinnedDialogs.get(left.id),filter.pinnedDialogs.get(right.id)));
  check(dialogIds(sorted).equals("20,30,10"),"filter sorting preserves new pin order");
  TLRPC.Dialog unpinned=new TLRPC.Dialog(40,0);unpinned.pinned=false;a.fixtureDialog(unpinned);
  a.moveDialogs(new RecyclerListView(),1,4);
  check(dialogIds(a.parentFragment.dialogs).equals("20,30,10,40"),"folder pins cannot move into unpinned folder rows");
 }
 static void rejectUnpinnedDrag(){
  AvatarPinnedReorderRegression a=new AvatarPinnedReorderRegression();
  TLRPC.Dialog x=new TLRPC.Dialog(10,1),y=new TLRPC.Dialog(20,0);y.pinned=false;
  a.fixtureDialog(x);a.fixtureDialog(y);
  a.moveDialogs(new RecyclerListView(),0,1);
  check(dialogIds(a.parentFragment.dialogs).equals("10,20")&&a.notifications.isEmpty(),"main-list pins cannot move into unpinned rows");
  a.moveDialogs(new RecyclerListView(),1,0);
  check(dialogIds(a.parentFragment.dialogs).equals("10,20")&&a.notifications.isEmpty(),"main-list unpinned rows cannot be dragged");
  a.fixtureDialog(new TLRPC.TL_dialogFolder());
  a.moveDialogs(new RecyclerListView(),0,2);
  check(dialogIds(a.parentFragment.dialogs).equals("10,20,-999")&&a.notifications.isEmpty(),"archive folder cannot be a drag target");
  a.moveDialogs(new RecyclerListView(),2,0);
  check(dialogIds(a.parentFragment.dialogs).equals("10,20,-999")&&a.notifications.isEmpty(),"archive folder cannot be dragged as a pinned chat");
 }
 static String dialogIds(List<TLRPC.Dialog> dialogs){StringJoiner j=new StringJoiner(",");for(TLRPC.Dialog d:dialogs)j.add(Long.toString(d.id));return j.toString();}
 static String rowIds(ArrayList<ItemInternal> rows){StringJoiner j=new StringJoiner(",");for(ItemInternal row:rows)j.add(Long.toString(row.dialog.id));return j.toString();}
 public static void main(String[] args){storyFallback();defaultDrag();filteredDrag(7);filteredDrag(8);rejectUnpinnedDrag();System.out.println("PASS: "+checks+" production avatar fallback and pinned drag assertions");}
}
'''

with tempfile.TemporaryDirectory(prefix="avatar-pinned-tests-") as directory:
    java = Path(directory) / "AvatarPinnedReorderRegression.java"
    java.write_text(fixture)
    subprocess.run([str(JAVA / "javac"), "-d", directory, str(java)], check=True)
    subprocess.run([str(JAVA / "java"), "-cp", directory, "AvatarPinnedReorderRegression"], check=True)
