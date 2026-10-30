#!/usr/bin/env python3
"""Exercise production speed-dialog callbacks/promoted rows; audit measured search layout.

This uses lightweight view stubs, not an Android renderer or device UI test.
"""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
JAVA = Path(os.environ.get("JAVA_HOME", "/Applications/Android Studio.app/Contents/jbr/Contents/Home")) / "bin"
SOURCE = (ROOT / "TMessagesProj/src/main/java/org/telegram/ui/ExtensionFeaturesActivity.java").read_text()


def block(marker):
    start = SOURCE.index(marker)
    end = SOURCE.index("{", start) + 1
    depth = 1
    while depth:
        depth += (SOURCE[end] == "{") - (SOURCE[end] == "}")
        end += 1
    return SOURCE[start:end]


create = block("    public View createView(Context context)")
assert "final int topHeight" not in create, "search must not reserve a hardcoded overlay offset"
assert "new LinearLayout.LayoutParams(-1, 0, 1f)" in create, "results fill the space below the measured header"
assert "setCompoundDrawables" not in create and "setOnTouchListener" not in create, "clear affordance is a real accessible button"
assert "R.drawable.ic_close_white" in create and "R.drawable.msg_clear" not in create
assert 'clearSearchButton.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE)' in create
assert "clearSearchButton.setContentDescription" in create
assert "LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT" in create
assert "searchField.setText(searchQuery)" in create and "selectTabWithId(category, 1f)" in create
fill = block("    private void fillItems(")
assert fill.index("addPromotedItem(items, all, promotedIds, ITEM_ID_DOWNLOAD_SPEED)") < fill.index("for (int group = 1;")
assert "promotedIds.contains(item.id)" in fill, "promoted features must not appear twice in All"
assert "R.string.SwitchAccount" not in fill, "account shortcut must use the translated extension label"
assert "CommandActionCell" not in SOURCE, "common shortcuts use native consistent settings rows"
speed = block("    private void showTransferSpeedDialog(boolean upload)")
assert "builder.setItems" not in speed and "new SeekBarView" in speed
assert speed.index("SharedConfig.setDownloadSpeedMultiplier") > speed.index("builder.setPositiveButton")
assert speed.index("SharedConfig.setUploadSpeedMultiplier") > speed.index("builder.setPositiveButton")
assert "setNegativeButton(getString(R.string.Cancel), null)" in speed

methods = "\n".join(block(marker) for marker in [
    "    private void addPromotedItem(",
    "    private void showDownloadSpeedDialog()",
    "    private String formatSpeedMultiplier(",
    "    private static int speedSliderIndex(",
    "    private void showTransferSpeedDialog(boolean upload)",
    "    private void showUploadSpeedDialog()",
])
fixture = r'''
import java.util.*;
class Context {}
class View {int getMeasuredWidth(){return 20;}int getMeasuredHeight(){return 18;}void layout(int l,int t,int r,int b){}}
class LinearLayout extends View {
 static final int VERTICAL=1;LinearLayout(Context c){}void setOrientation(int i){}void setPadding(int a,int b,int c,int d){}void addView(View v,Object p){}
 static class LayoutParams {static final int WRAP_CONTENT=-2;LayoutParams(int a,int b,float w){}}
}
class FrameLayout extends View {
 ArrayList<View> children=new ArrayList<>();FrameLayout(Context c){}protected void onLayout(boolean c,int l,int t,int r,int b){}
 int getWidth(){return 300;}int getChildCount(){return children.size();}View getChildAt(int i){return children.get(i);}void addView(View v,Object p){children.add(v);}
}
class TextView extends View {String text;TextView(Context c){}void setGravity(int x){}void setTextSize(int a,int b){}void setTypeface(Object o){}void setTextColor(int i){}void setText(String s){text=s;}void setSingleLine(boolean b){}}
class TypedValue {static final int COMPLEX_UNIT_SP=0;}
class Gravity {static final int CENTER=1;}
class LayoutHelper {static final int MATCH_PARENT=-1,WRAP_CONTENT=-2;static Object createLinear(int a,int b,int...m){return null;}static Object createFrame(int a,int b){return null;}}
class AndroidUtilities {static int dp(int v){return v;}static Object bold(){return null;}}
class Theme {static final int key_dialogTextBlue=1,key_dialogGrayLine=2,key_dialogTextGray=3;static int getColor(int key,Object p){return key;}}
class R {static class string {static final int DownloadSpeedBoostNormal=1,DownloadSpeedBoostValue=2,DownloadSpeedBoostTitle=3,UploadSpeedBoostTitle=4,UploadSpeedBoostInfo=5,DownloadSpeedBoostInfo=6,Save=7,Cancel=8,UploadSpeedBoostApplied=9,DownloadSpeedBoostApplied=10;}}
class LocaleController {static String formatString(int r,Object...args){return String.valueOf(args[0]);}}
class SharedConfig {
 static final int[] DOWNLOAD_SPEED_MULTIPLIERS={1,5,10,20,50},UPLOAD_SPEED_MULTIPLIERS=DOWNLOAD_SPEED_MULTIPLIERS;
 static int down=10,up=5,downWrites,upWrites;static int getDownloadSpeedMultiplier(){return down;}static int getUploadSpeedMultiplier(){return up;}
 static void setDownloadSpeedMultiplier(int x){down=x;downWrites++;}static void setUploadSpeedMultiplier(int x){up=x;upWrites++;}
}
class SeekBarView extends View {
 static SeekBarView latest;SeekBarViewDelegate delegate;float progress;int separators,steps;
 SeekBarView(Context c,Object p){latest=this;}void setColors(int a,int b){}void setSeparatorsCount(int c){separators=c;}void setReportChanges(boolean b){}
 void setDelegate(SeekBarViewDelegate d){delegate=d;}void setProgress(float p){progress=p;}void setProgress(float p,boolean b){progress=p;}
 interface SeekBarViewDelegate {void onSeekBarDrag(boolean stop,float p);int getStepsCount();CharSequence getContentDescription();}
}
class Toast {static final int LENGTH_SHORT=0;static int count;static Toast makeText(Context c,String s,int l){count++;return new Toast();}void show(){}}
interface DialogClick {void click(Object d,int which);}
class AlertDialog {
 DialogClick save;static class Builder {AlertDialog dialog=new AlertDialog();Builder(Context c,Object p){}void setTitle(String s){}void setMessage(String s){}void setView(View v){}void setPositiveButton(String s,DialogClick d){dialog.save=d;}void setNegativeButton(String s,DialogClick d){}AlertDialog create(){return dialog;}}
}
class UItem {int id;UItem(int n){id=n;}}
class Adapter {int updates;void update(boolean b){updates++;}}
class ListView {Adapter adapter=new Adapter();}
public class ExtensionCenterUiRegression {
 static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 Object resourceProvider;Context context=new Context();AlertDialog shown;ListView listView=new ListView();int downRefresh,upRefresh;
 Context getParentActivity(){return context;}static String getString(int id){return id==1?"Normal":String.valueOf(id);}void showDialog(AlertDialog d){shown=d;}
 void refreshDownloadSpeedSettings(){downRefresh++;}void refreshUploadSpeedSettings(){upRefresh++;}
 /*METHODS*/
 public static void main(String[]args){
  ExtensionCenterUiRegression h=new ExtensionCenterUiRegression();
  int[] counts={1,2,5,20};
  for(int count:counts){check(speedSliderIndex(-2,count)==0,"lower clamp");check(speedSliderIndex(2,count)==count-1,"upper clamp");check(speedSliderIndex(Float.NaN,count)==0,"invalid progress safe");}
  for(int i=0;i<5;i++)check(speedSliderIndex(i/4f,5)==i,"exact preset maps to itself");
  check(speedSliderIndex(.1249f,5)==0&&speedSliderIndex(.125f,5)==1,"drag snaps at midpoint");
  h.showDownloadSpeedDialog();SeekBarView slider=SeekBarView.latest;
  check(slider.progress==.5f&&slider.separators==5,"download slider restores current multiplier");
  check(slider.delegate.getStepsCount()==4,"accessibility reaches all five presets");
  slider.delegate.onSeekBarDrag(false,.9f);
  check(SharedConfig.down==10&&SharedConfig.downWrites==0&&h.downRefresh==0,"drag previews without changing transfers");
  check(slider.delegate.getContentDescription().toString().endsWith("50"),"accessible description follows preview");
  check(Toast.count==0&&h.listView.adapter.updates==0,"unconfirmed preview is silent");
  h.showDownloadSpeedDialog();slider=SeekBarView.latest;
  check(slider.progress==.5f,"cancel/reopen restores persisted value");
  h.shown.save.click(null,0);
  check(SharedConfig.downWrites==0&&h.downRefresh==0,"saving unchanged value avoids refresh");
  slider.delegate.onSeekBarDrag(true,.64f);
  check(slider.progress==.75f,"release snaps thumb to supported preset");
  h.shown.save.click(null,0);
  check(SharedConfig.down==20&&SharedConfig.downWrites==1&&h.downRefresh==1,"confirmed download applies once");
  check(h.upRefresh==0&&SharedConfig.up==5,"download does not mutate upload");
  check(Toast.count==1&&h.listView.adapter.updates==1,"confirmed download refreshes row and reports value");
  h.showUploadSpeedDialog();slider=SeekBarView.latest;
  check(slider.progress==.25f,"upload slider restores its independent multiplier");
  slider.delegate.onSeekBarDrag(true,1);
  check(SharedConfig.up==5&&SharedConfig.upWrites==0,"upload preview is not saved early");
  h.shown.save.click(null,0);
  check(SharedConfig.up==50&&SharedConfig.upWrites==1&&h.upRefresh==1,"upload applies and refreshes correctly");
  h.context=null;AlertDialog previous=h.shown;h.showDownloadSpeedDialog();
  check(h.shown==previous,"detached fragment does not open a dialog");
  ArrayList<UItem> all=new ArrayList<>(),items=new ArrayList<>();HashSet<Integer> promoted=new HashSet<>();
  UItem down=new UItem(12),up=new UItem(14),other=new UItem(8);all.add(other);all.add(up);all.add(down);
  h.addPromotedItem(items,all,promoted,12);h.addPromotedItem(items,all,promoted,14);
  check(items.size()==2&&items.get(0)==down&&items.get(1)==up,"transfer settings are promoted in desired order");
  check(promoted.equals(new HashSet<>(Arrays.asList(12,14))),"promoted IDs support category deduplication");
  h.addPromotedItem(items,all,promoted,99);
  check(items.size()==2&&promoted.size()==2&&all.size()==3,"missing feature is skipped without altering full category data");
  System.out.println("Extension center: "+checks+" production speed-dialog/promoted-row assertions; 16 layout/source checks passed");
 }
}
'''.replace("/*METHODS*/", methods)
with tempfile.TemporaryDirectory(prefix="extension-center-ui-") as folder:
    source = Path(folder) / "ExtensionCenterUiRegression.java"
    source.write_text(fixture)
    subprocess.run([str(JAVA / "javac"), "-d", folder, str(source)], check=True)
    subprocess.run([str(JAVA / "java"), "-cp", folder, "ExtensionCenterUiRegression"], check=True)
