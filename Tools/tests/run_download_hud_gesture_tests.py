#!/usr/bin/env python3
"""Run the actual HUD touch/long-press/bounds methods with deterministic touch events."""
from pathlib import Path
import os, subprocess, tempfile
ROOT=Path(__file__).resolve().parents[2]
JAVA=Path(os.environ.get('JAVA_HOME','/Applications/Android Studio.app/Contents/jbr/Contents/Home'))/'bin'
source=(ROOT/'TMessagesProj/src/main/java/org/telegram/ui/Components/MangoDownloadSpeedHud.java').read_text()
def block(marker):
    start=source.index(marker); pos=source.index('{',start)+1; depth=1
    while depth:
        depth+=(source[pos]=='{')-(source[pos]=='}');pos+=1
    return source[start:pos]
methods='\n'.join(block(m) for m in ('    private static boolean onTouch(', '    private static void finishGesture(', '    private static void clampTranslation(', '    public static void detach()'))
press=block('    private static final Runnable longPress =')+';'
java=r'''
import java.util.*;
class View {
 ViewGroup parent; float x,y; int width=80,height=30,left=180,top=50,haptics,announcements;
 Object getContext(){return this;} ViewGroup getParent(){return parent;}
 float getTranslationX(){return x;}float getTranslationY(){return y;}
 void setTranslationX(float v){x=v;}void setTranslationY(float v){y=v;}
 int getWidth(){return width;}int getHeight(){return height;}int getLeft(){return left;}int getTop(){return top;}
 void performHapticFeedback(int type){haptics++;}void announceForAccessibility(String text){announcements++;}
 void removeOnLayoutChangeListener(Object listener){}
}
class ViewGroup extends View {
 boolean intercept;void requestDisallowInterceptTouchEvent(boolean v){intercept=v;}
 void removeView(View view){view.parent=null;}
 int getPaddingLeft(){return 10;}int getPaddingRight(){return 10;}int getPaddingTop(){return 10;}int getPaddingBottom(){return 10;}
}
class MotionEvent {
 static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3,ACTION_POINTER_DOWN=5,ACTION_POINTER_UP=6;
 final int action,id;final float x,y;MotionEvent(int a,float x,float y,int id){action=a;this.x=x;this.y=y;this.id=id;}
 int getActionMasked(){return action;}int getPointerId(int i){return id;}int findPointerIndex(int id){return id==this.id?0:-1;}
 float getRawX(){return x;}float getRawY(){return y;}float getX(int i){return x;}float getY(int i){return y;}
}
class ViewConfiguration {static ViewConfiguration get(Object context){return new ViewConfiguration();}int getScaledTouchSlop(){return 8;}static int getLongPressTimeout(){return 500;}}
class HapticFeedbackConstants {static final int LONG_PRESS=0;}
class MangoDownloadSpeedMonitor {static void removeListener(Object listener){}}
class R {static class string {static final int MangoDownloadHudUnlockHint=1,MangoDownloadHudLockHint=2;}}
class LocaleController {static String getString(int id){return "hint"+id;}}
class AndroidUtilities {
 static final List<Runnable> pending=new ArrayList<>();static int delay;
 static void runOnUIThread(Runnable r,int ms){pending.add(r);delay=ms;}
 static void cancelRunOnUIThread(Runnable r){pending.removeIf(x->x==r);}
 static void fire(){while(!pending.isEmpty())pending.remove(0).run();}
}
public class HudGestureRegression {
 private static View view,gestureView;
 private static ViewGroup container;
 private static Object activity,layoutListener,taskListener;
 private static Runnable ticker=()->{};
 private static boolean dragging,pinned,hideOnUpdate;
 private static int activePointerId=-1,checks;
 private static float downX,downY,startX,startY,savedX,savedY;
 private static int update(){if(hideOnUpdate)finishGesture();return hideOnUpdate?0:1;}
 /*PRESS*/
 /*METHODS*/
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static void event(int action,float x,float y){check(onTouch(view,new MotionEvent(action,x,y,7)),"touch consumed");}
 static void reset(){finishGesture();pinned=false;hideOnUpdate=false;view=new View();view.parent=new ViewGroup();view.parent.width=300;view.parent.height=600;}
 static void down(){event(MotionEvent.ACTION_DOWN,100,100);}
 static void up(){event(MotionEvent.ACTION_UP,100,100);}
 public static void main(String[] args){
  reset();down();check(view.parent.intercept&&AndroidUtilities.delay==500,"capture parent until release and use platform timeout");
  up();AndroidUtilities.fire();check(!pinned&&activePointerId==-1&&!view.parent.intercept,"tap never pins or leaves parent captured");
  down();event(MotionEvent.ACTION_MOVE,120,140);AndroidUtilities.fire();
  check(!pinned&&view.x==20&&view.y==40,"drag cancels long press and translates");up();check(!dragging,"release resets drag");
  down();event(MotionEvent.ACTION_MOVE,102,101);AndroidUtilities.fire();check(pinned&&view.haptics==1&&view.announcements==1,"stationary long press pins with feedback");up();
  float oldX=view.x,oldY=view.y;down();event(MotionEvent.ACTION_MOVE,60,40);up();check(view.x==oldX&&view.y==oldY&&pinned,"pinned HUD stays in position while dragging");
  down();AndroidUtilities.fire();check(!pinned&&view.haptics==2,"second long press unlocks");
  event(MotionEvent.ACTION_MOVE,80,120);check(view.x==oldX-20&&view.y==oldY+20,"unlock gesture can immediately continue as drag");up();
  down();event(MotionEvent.ACTION_CANCEL,100,100);AndroidUtilities.fire();check(!pinned&&gestureView==null&&!view.parent.intercept,"system cancellation discards pending pin");
  down();event(MotionEvent.ACTION_POINTER_DOWN,100,100);AndroidUtilities.fire();check(!pinned&&activePointerId==-1,"multitouch cancels gesture safely");
  down();onTouch(view,new MotionEvent(MotionEvent.ACTION_MOVE,120,120,8));check(gestureView==null,"missing active pointer cancels");
  down();event(MotionEvent.ACTION_MOVE,140,160);event(MotionEvent.ACTION_CANCEL,140,160);down();AndroidUtilities.fire();check(pinned,"long press works after cancelled drag");up();
  reset();down();event(MotionEvent.ACTION_MOVE,9999,9999);check(view.x==30&&view.y==510,"right/bottom bounds clamp");up();
  down();event(MotionEvent.ACTION_MOVE,-9999,-9999);check(view.x==-170&&view.y==-40,"left/top bounds clamp independent of END anchoring");up();
  reset();down();hideOnUpdate=true;AndroidUtilities.fire();check(gestureView==null&&view.announcements==0,"tasks ending during pin feedback remains null-safe");
  reset();down();Runnable stale=longPress;finishGesture();view=null;stale.run();check(activePointerId==-1,"detached long press ignored");
  reset();down();AndroidUtilities.fire();up();detach();
  check(pinned&&view==null&&gestureView==null,"background detach preserves pin and clears touch state");
  view=new View();view.parent=new ViewGroup();view.parent.width=300;view.parent.height=600;
  down();event(MotionEvent.ACTION_MOVE,120,150);up();check(pinned&&view.x==0&&view.y==0,"returning to foreground remains pinned");
  down();AndroidUtilities.fire();up();check(!pinned,"long press after reattachment unlocks");
  System.out.println("PASS: "+checks+" production HUD gesture, pin/unpin and bounds assertions");
 }
}
'''.replace('/*PRESS*/',press).replace('/*METHODS*/',methods)
with tempfile.TemporaryDirectory(prefix='mango-hud-gesture-') as temp:
    p=Path(temp)/'HudGestureRegression.java';p.write_text(java)
    subprocess.run([str(JAVA/'javac'),'-d',temp,str(p)],check=True)
    subprocess.run([str(JAVA/'java'),'-cp',temp,'HudGestureRegression'],check=True)
for marker in ('    public static void detach()',):
    assert 'finishGesture();' in block(marker)
assert 'finishGesture();' in block('    private static int update()')
print('PASS: 2 HUD hide/detach gesture cleanup integration checks')
