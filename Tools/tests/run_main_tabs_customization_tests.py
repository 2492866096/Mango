#!/usr/bin/env python3
"""Exercise production tab drag/order and passive search/proxy visibility with JVM doubles."""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
JAVA = Path(os.environ.get('JAVA_HOME', '/Applications/Android Studio.app/Contents/jbr/Contents/Home')) / 'bin'
SRC = ROOT / 'TMessagesProj/src/main/java'
layout = (SRC / 'org/telegram/ui/MainTabsLayout.java').read_text()
dialogs = (SRC / 'org/telegram/ui/DialogsActivity.java').read_text()
activity = (SRC / 'org/telegram/ui/MainTabsActivity.java').read_text()

def block(source, marker):
    start = source.index(marker)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

layout_methods = '\n'.join(block(layout, marker) for marker in (
    '    public void setTabOrder(', '    private void applyTabPriorities(',
    '    public boolean startReordering(', '    private void moveDraggedTab(',
    '    private void applyDragTransform(', '    private void finishReordering('))
dialog_methods = '\n'.join(block(dialogs, marker) for marker in (
    '    private boolean isPullDownSearchDisabled(', '    private int getPullDownSearchHeight(',
    '    private int getMaxScrollYOffsetWithoutSearch(', '    private int getMaxScrollYOffset(',
    '    private void updateProxyIconVisibility(', '    private void checkUi_searchFieldVisibility('))
fixture = '''
import java.util.*;
import org.telegram.messenger.MainTabsOrder;
public class MainTabsRegression {
 static int checks;
 static void check(boolean v,String s){checks++;if(!v)throw new AssertionError(s);}
 static void order(int[] actual,int... expected){check(Arrays.equals(actual,expected),"order "+Arrays.toString(actual));}
 static int dp(float n){return (int)n;}
 static class View {static int GONE=8,VISIBLE=0;float x,scale=1,z,alpha=1;int width=50,left,visibility;Object parent;boolean visible=true;int priority;
  Object getParent(){return parent;} float getX(){return left+x;} int getLeft(){return left;}int getWidth(){return width;}int getMeasuredWidth(){return width;}
  void setTranslationX(float n){x=n;}void setTranslationZ(float n){z=n;}void setScaleX(float n){scale=n;}void setScaleY(float n){}void performHapticFeedback(int n){}void setVisibility(int n){visibility=n;}void setAlpha(float n){alpha=n;}
 }
 static class Parent {void requestDisallowInterceptTouchEvent(boolean b){}}
 static class HapticFeedbackConstants {static int LONG_PRESS=1;}
 static class ViewConfiguration {static ViewConfiguration get(Object c){return new ViewConfiguration();}int getScaledTouchSlop(){return 8;}}
 static class Harness {
  interface ReorderListener {void onOrderChanged(int[] order);void onLongPressWithoutMove(View tab);}
  ArrayList<View> children=new ArrayList<>(),orderedTabs=new ArrayList<>(),orderBeforeDrag=new ArrayList<>();
  ReorderListener reorderListener;View draggedTab;boolean dragMoved,childTouchCancelled;float touchX,startX,draggedOffsetX;int pointerId=-1;
  Parent getParent(){return new Parent();}Object getContext(){return null;}
  int getChildCount(){return children.size();}View getChildAt(int i){return children.get(i);}int indexOfChild(View v){return children.indexOf(v);}
  int getPaddingLeft(){return 0;}int getPaddingRight(){return 0;}int getWidth(){return 200;}void requestLayout(){}
  void setPriority(View v,int i){v.priority=i;}boolean isViewVisible(View v){return v.visible;}
  int[] order(){return orderedTabs.stream().mapToInt(this::indexOfChild).toArray();}
  void begin(int id,float x){pointerId=0;touchX=x;startReordering(children.get(id));}
  void move(float x){touchX=x;dragMoved=true;moveDraggedTab();}
  Harness(){for(int i=0;i<4;i++){View v=new View();v.parent=this;v.left=i*50;children.add(v);}setTabOrder(new int[]{0,1,2,3});}
  /*LAYOUT*/
 }
 static class MangoSettings {static boolean hidden;static boolean getBoolean(int a,String k,boolean d){return hidden;}}
 static class DialogStoriesCell {static int HEIGHT_IN_DP=80;}
 static class MathUtils {static float clamp(float n,float min,float max){return Math.max(min,Math.min(max,n));}}
 static class Animator {float value;boolean bool;float getFloatValue(){return value;}void setValue(boolean b,boolean a){bool=b;}}
 static class DialogHarness {
  boolean pullDownSearchDisabled,hasStories;int currentAccount;float scrollYOffset,progressToActionMode;
  static final int SEARCH_FIELD_HEIGHT=48;
  View proxyItem=new View(),fragmentSearchField=new View();
  Animator animatorActionModeVisible=new Animator(),animatorSearchVisible=new Animator(),animatorDoneButtonVisible=new Animator(),animatorSearchButtonVisible=new Animator();
  boolean isSupportSearch(){return true;}float getRightSlidingProgress(){return 0;}
  /*DIALOGS*/
 }
 public static void main(String[] args) {
  order(MainTabsOrder.decode(null,4),0,1,2,3);
  order(MainTabsOrder.decode("3,1,3,55,-1,bad",4),3,1,0,2);
  order(MainTabsOrder.decode("5,0,7,3,1,2,4,6",8),5,0,7,3,1,2,4,6);
  int[] custom={3,1,0,2};order(MainTabsOrder.decode(MainTabsOrder.encode(custom),4),custom);
  Harness h=new Harness();final int[] savedCount={0},legacyCount={0};
  h.reorderListener=new Harness.ReorderListener(){public void onOrderChanged(int[] o){savedCount[0]++;}public void onLongPressWithoutMove(View v){legacyCount[0]++;}};
  h.children.get(1).visible=false;
  h.begin(3,125);h.move(10);h.finishReordering(false);
  order(h.order(),3,0,1,2);check(savedCount[0]==1,"drag committed once");
  h.children.get(1).visible=true;order(h.order(),3,0,1,2);
  check(h.children.get(3).priority==0&&h.children.get(1).priority==2,"hidden tab keeps stable identity and slot");
  h.begin(0,75);h.move(180);h.finishReordering(true);order(h.order(),3,0,1,2);check(savedCount[0]==1,"cancel does not persist");
  h.begin(0,75);h.finishReordering(false);check(legacyCount[0]==1,"stationary long press preserves shortcut");
  h.pointerId=-1;h.startReordering(h.children.get(0));check(legacyCount[0]==2,"accessibility long click preserves shortcut");
  check(h.draggedTab==null&&h.children.get(0).z==0,"release restores drag state");
  h.setTabOrder(new int[]{2,0,3,1});order(h.order(),2,0,3,1);
  DialogHarness d=new DialogHarness();check(d.getPullDownSearchHeight()==48,"default search space retained");
  d.pullDownSearchDisabled=true;check(d.getPullDownSearchHeight()==0&&d.getMaxScrollYOffset()==0,"disabled pull has no empty gap");
  d.hasStories=true;check(d.getMaxScrollYOffset()==80,"stories remain scrollable without search extension");
  d.checkUi_searchFieldVisibility();check(d.fragmentSearchField.visibility==View.GONE&&d.animatorSearchButtonVisible.bool,"disabled passive search leaves explicit search button");
  d.animatorSearchVisible.value=1;d.checkUi_searchFieldVisibility();check(d.fragmentSearchField.visibility==View.VISIBLE&&d.fragmentSearchField.alpha==1,"explicit search still opens input");
  MangoSettings.hidden=true;d.updateProxyIconVisibility();check(d.proxyItem.visibility==View.GONE,"hide proxy icon");
  MangoSettings.hidden=false;d.updateProxyIconVisibility();check(d.proxyItem.visibility==View.VISIBLE,"restore proxy icon");
  System.out.println("PASS: "+checks+" tab ordering/drag and dialog visibility checks");
 }
}
'''.replace('/*LAYOUT*/',layout_methods).replace('/*DIALOGS*/',dialog_methods)
with tempfile.TemporaryDirectory(prefix='main-tabs-tests-') as directory:
    fixture_file = Path(directory) / 'MainTabsRegression.java'
    fixture_file.write_text(fixture)
    subprocess.run([str(JAVA/'javac'),'-d',directory,str(SRC/'org/telegram/messenger/MainTabsOrder.java'),str(fixture_file)],check=True)
    subprocess.run([str(JAVA/'java'),'-cp',directory,'MainTabsRegression'],check=True)
# Wiring assertions complement the behavioral fixture above.
assert 'MangoSettings.setString(currentAccount, MainTabsOrder.SETTING_KEY' in activity
assert 'MangoSettings.getString(currentAccount, MainTabsOrder.SETTING_KEY' in activity
assert 'tabsView.startReordering(v)' in activity
assert 'setOnClickListener(v -> {\n            showSearch(true, false, true);' in dialogs
assert 'if (mask == 0) applyExtensionAppearance();' in dialogs
assert 'cancel.setAction(MotionEvent.ACTION_CANCEL)' in layout
print('PASS: 6 settings/gesture wiring checks')
