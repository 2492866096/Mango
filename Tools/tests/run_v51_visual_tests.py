#!/usr/bin/env python3
"""Execute production online-state/drawing and photo/avatar tap-routing logic offline."""
from pathlib import Path
import os
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'TMessagesProj/src/main/java/org/telegram'
JAVA=Path(os.environ.get('JAVA_HOME','/Applications/Android Studio.app/Contents/jbr/Contents/Home'))/'bin'

def method(source,signature):
    start=source.index(signature)
    end=source.index('{',start)+1
    depth=1
    while depth:
        depth+=(source[end]=='{')-(source[end]=='}'); end+=1
    return source[start:end]

cell=(BASE/'ui/Cells/ChatMessageCell.java').read_text()
viewer=(BASE/'ui/PhotoViewer.java').read_text()
gallery=(BASE/'ui/Components/ProfileGalleryView.java').read_text()
chat=(BASE/'ui/ChatActivity.java').read_text()
helper=(BASE/'messenger/MangoVisualPreferences.java').read_text()
draw=method(cell,'    public void drawAvatarWithOnlineBadge(')
schedule=method(cell,'    private void scheduleOnlineBadgeExpiry(')
touch=method(gallery,'    public boolean onTouchEvent(')
tap=method(viewer,'    public boolean onSingleTapConfirmed(')
tap=tap[:tap.index('        if (currentMessageObject != null && currentMessageObject.isSponsored())')]+'''        fallthrough++; return true;
    }'''

stubs=r'''
package org.telegram.messenger;
import java.util.*;
import org.telegram.tgnet.*;
class MangoSettings {
 static Map<String,Boolean> values=new HashMap<>();
 static boolean getBoolean(int a,String k,boolean d){return values.getOrDefault(a+":"+k,d);}
 static void set(int a,String k,boolean b){values.put(a+":"+k,b);}
}
class MessagesController {
 static final MessagesController[] instances={new MessagesController(),new MessagesController()};
 Map<Long,TLRPC.User> users=new HashMap<>(); Map<Long,Integer> onlinePrivacy=new HashMap<>();
 static MessagesController getInstance(int a){return instances[a];}
 TLRPC.User getUser(long id){return users.get(id);}
 static boolean isSupportUser(TLRPC.User u){return u.support;}
}
class UserConfig {
 static final UserConfig[] instances={new UserConfig(1),new UserConfig(2)}; long id;
 UserConfig(long id){this.id=id;} static UserConfig getInstance(int a){return instances[a];} long getClientUserId(){return id;}
}
class UserObject {
 static final long REPLY_BOT=1271266957L,VERIFY=489000,OAUTH=489001,ANONYMOUS=2666000;
 static boolean isDeleted(TLRPC.User u){return u.deleted;} static boolean isUserSelf(TLRPC.User u){return u.self;}
 static boolean isService(long id){return id==777000||id==333000||id==42777;}
 static boolean isReplyUser(TLRPC.User u){return u.id==REPLY_BOT;}
 static boolean isAnonymous(TLRPC.User u){return u.id==ANONYMOUS;}
}
class DialogObject {static long getPeerDialogId(TLRPC.Peer p){return p==null?0:p.user_id;}}
class View {
 static final int VISIBLE=0; int visibility; int getVisibility(){return visibility;}
 int getMeasuredWidth(){return 800;}
}
class Paint {int color; void setColor(int c){color=c;}}
class Canvas {
 ArrayList<float[]> circles=new ArrayList<>();ArrayList<Integer> colors=new ArrayList<>();
 void drawCircle(float x,float y,float r,Paint p){circles.add(new float[]{x,y,r});colors.add(p.color);}
}
class ImageReceiver {
 float x=6,y=100,w=42,h=42,alpha=1;boolean visible=true,hasImage=true;int draws;
 void draw(Canvas c){draws++;} boolean getVisible(){return visible;} float getAlpha(){return alpha;}
 float getImageWidth(){return w;} float getImageHeight(){return h;} float getImageX2(){return x+w;} float getImageY2(){return y+h;}
 boolean hasImageSet(){return hasImage;}
}
class Theme {
 static final int key_windowBackgroundWhite=1,key_chats_onlineCircle=2;
 static int multAlpha(int c,float a){return (Math.round(((c>>>24)&255)*a)<<24)|(c&0xFFFFFF);}
}
class MessageObject {
 Object customAvatarDrawable;boolean sponsored;long dialogId=-10;TLRPC.Message messageOwner=new TLRPC.Message();
 boolean isSponsored(){return sponsored;}long getDialogId(){return dialogId;}
}
class MotionEvent {
 static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3;
 int action;float x,y; MotionEvent(int a,float x,float y){action=a;this.x=x;this.y=y;}
 int getAction(){return action;}float getX(){return x;}float getY(){return y;}
 void setAction(int a){action=a;}void recycle(){}
 static MotionEvent obtain(MotionEvent e){return new MotionEvent(e.action,e.x,e.y);}
 static MotionEvent obtain(long a,long b,int c,float d,float e,int f){return new MotionEvent(c,d,e);}
}
class RecyclerView {static final int SCROLL_STATE_IDLE=0;}
class ParentList {int touches,moves;int getScrollState(){return 0;} boolean onTouchEvent(MotionEvent e){touches++;if(e.action==2)moves++;return true;}}
class Pager {
 int pagerTouches,pagerMoves; public boolean onTouchEvent(MotionEvent e){pagerTouches++;if(e.action==2)pagerMoves++;return true;}
}
class Adapter {int getCount(){return 5;}int getExtraCount(){return 1;}}
class Callback {int downs,releases;void onDown(boolean left){downs++;}void onRelease(){releases++;}}
class PointF {float x,y;void set(float x,float y){this.x=x;this.y=y;}}
class Pinch {
 boolean active; boolean isInOverlayMode(){return active;}boolean checkPinchToZoom(Object... args){return active;}
}
class ItemView {Object getImageReceiver(){return null;}}
class FileLog {static void e(Exception e){throw new RuntimeException(e);}}
class SharedConfig {static boolean nextMediaTap=true;}
class ActionBar {static int getCurrentActionBarHeight(){return 50;}}
class AndroidUtilities {static int statusBarHeight=20;}
'''
fixture=r'''
package org.telegram.messenger;
import java.util.*;
import org.telegram.tgnet.*;
public class VisualRegression {
 static int checks;
 static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
 static void eq(long a,long b,String n){check(a==b,n+": "+a+" != "+b);}
 static TLRPC.User user(long id,int expiry){TLRPC.User u=new TLRPC.User();u.id=id;u.status=new TLRPC.Status();u.status.expires=expiry;return u;}
 static class Cell {
  int currentAccount;TLRPC.User currentUser;TLRPC.Chat currentChat;MessageObject currentMessageObject=new MessageObject();
  ImageReceiver avatarImage=new ImageReceiver();Paint onlineBadgePaint=new Paint();long onlineBadgeExpiry;
  Runnable onlineBadgeExpiryRunnable=()->{};int posts,cancels;long lastDelay;
  void removeCallbacks(Runnable r){cancels++;}void postDelayed(Runnable r,long d){posts++;lastDelay=d;}
  int getThemedColor(int k){return k==1?0xFFFFFFFF:0xFF00FF00;}
__DRAW__
__SCHEDULE__
 }
 static class Gallery extends Pager {
  int currentAccount,index=1,switches;Adapter adapter=new Adapter();ParentList parentListView=new ParentList();
  boolean isScrollingListView=true,isSwipingViewPager=true,isDownReleased,scrolledByUser;
  PointF downPoint=new PointF();int touchSlop=8;Callback callback=new Callback();Pinch pinchToZoomHelper;
  ItemView getCurrentItemView(){return pinchToZoomHelper==null?null:new ItemView();}
  int getRealCount(){return 3;}int getCurrentItem(){return index;}int getWidth(){return 300;}
  void setCurrentItem(int i,boolean animated){index=i;switches++;}
  boolean canScrollHorizontally(int direction){return true;}
__TOUCH__
  void tap(float x){onTouchEvent(new MotionEvent(0,x,10));onTouchEvent(new MotionEvent(1,x,10));}
 }
 static class Viewer {
  static final int SELECT_TYPE_STICKER=3;int currentAccount,sendPhotoType=1,switched,fallthrough;boolean discardTap;
  View checkImageView=new View(),containerView=new View();ImageReceiver leftImage=new ImageReceiver(),rightImage=new ImageReceiver();
  Viewer(){checkImageView.visibility=8;}
  int dp(int x){return x;} void switchToNextIndex(int d,boolean a){switched+=d;}
__TAP__
 }
 public static void main(String[] args){
  TLRPC.User u=user(5,120);MessagesController.getInstance(0).users.put(5L,u);
  eq(MangoVisualPreferences.getOnlineUntil(0,u),120,"default online badge enabled");
  MangoSettings.set(0,MangoVisualPreferences.SHOW_ONLINE_BADGE,false);
  eq(MangoVisualPreferences.getOnlineUntil(0,u),0,"switch off");
  eq(MangoVisualPreferences.getOnlineUntil(1,u),120,"account settings isolated");
  MangoSettings.set(0,MangoVisualPreferences.SHOW_ONLINE_BADGE,true);
  eq(MangoVisualPreferences.getOnlineUntil(0,null),0,"missing avatar owner");
  for(long id:new long[]{0,1,777000,333000,42777,UserObject.VERIFY,UserObject.OAUTH,UserObject.REPLY_BOT,UserObject.ANONYMOUS})
   eq(MangoVisualPreferences.getOnlineUntil(0,user(id,120)),0,"excluded service/self id "+id);
  u.bot=true;eq(MangoVisualPreferences.getOnlineUntil(0,u),0,"bots");u.bot=false;
  u.deleted=true;eq(MangoVisualPreferences.getOnlineUntil(0,u),0,"deleted user");u.deleted=false;
  u.self=true;eq(MangoVisualPreferences.getOnlineUntil(0,u),0,"self flag");u.self=false;
  u.support=true;eq(MangoVisualPreferences.getOnlineUntil(0,u),0,"support");u.support=false;
  u.status=null;eq(MangoVisualPreferences.getOnlineUntil(0,u),0,"hidden missing status");u.status=new TLRPC.Status();
  for(int expires:new int[]{0,-1,-100,-101,-102,99,100}){
   u.status.expires=expires;eq(MangoVisualPreferences.getOnlineUntil(0,u),0,"offline/private status "+expires);
  }
  u.status.expires=-100;MessagesController.getInstance(0).onlinePrivacy.put(5L,90);
  eq(MangoVisualPreferences.getOnlineUntil(0,u),121,"Telegram short-lived online visibility");
  eq(MangoVisualPreferences.getOnlineUntil(1,u),0,"privacy account isolation");
  MessagesController.getInstance(0).onlinePrivacy.put(5L,69);
  eq(MangoVisualPreferences.getOnlineUntil(0,u),0,"expired privacy entry ignored");
  MessagesController.getInstance(0).onlinePrivacy.clear();
  TLRPC.User old=user(5,500);u.status.expires=120;
  eq(MangoVisualPreferences.getOnlineUntil(0,old),120,"latest cached user wins over recycled/stale user");
  Cell c=new Cell();c.currentUser=old;Canvas canvas=new Canvas();c.drawAvatarWithOnlineBadge(canvas);
  eq(canvas.circles.size(),2,"outline and green dot");eq(c.avatarImage.draws,1,"avatar still drawn");
  check(Arrays.equals(canvas.circles.get(1),new float[]{42,136,4.5f}),"dot tracks avatar bottom-right");
  eq(c.posts,1,"schedule one expiry");eq(c.lastDelay,20000,"server expiry delay");
  c.drawAvatarWithOnlineBadge(new Canvas());eq(c.posts,1,"no per-frame callback accumulation");
  c.avatarImage.alpha=.5f;Canvas faded=new Canvas();c.drawAvatarWithOnlineBadge(faded);
  eq((faded.colors.get(1)>>>24)&255,128,"dot follows avatar alpha");
  c.avatarImage.w=c.avatarImage.h=21;Canvas scaled=new Canvas();c.drawAvatarWithOnlineBadge(scaled);
  check(scaled.circles.get(1)[2]==2.25f,"dot scales with avatar size");
  u.status.expires=130;c.drawAvatarWithOnlineBadge(new Canvas());eq(c.posts,2,"status extension rescheduled once");
  c.currentUser=null;c.currentChat=new TLRPC.Chat();Canvas channel=new Canvas();c.drawAvatarWithOnlineBadge(channel);
  eq(channel.circles.size(),0,"recycled channel has no stale dot");eq(c.onlineBadgeExpiry,0,"recycling cancels expiry");
  c.currentChat.signature_profiles=true;c.currentMessageObject.messageOwner.from_id.user_id=5;
  Canvas signature=new Canvas();c.drawAvatarWithOnlineBadge(signature);eq(signature.circles.size(),2,"channel signed user avatar resolved");
  c.currentMessageObject.customAvatarDrawable=new Object();Canvas custom=new Canvas();c.drawAvatarWithOnlineBadge(custom);eq(custom.circles.size(),0,"custom placeholder not marked online");
  c.currentMessageObject.customAvatarDrawable=null;c.avatarImage.visible=false;Canvas hidden=new Canvas();c.drawAvatarWithOnlineBadge(hidden);eq(hidden.circles.size(),0,"hidden grouped avatar not marked");
  c.avatarImage.visible=true;ConnectionsManager.now=130;Canvas expired=new Canvas();c.drawAvatarWithOnlineBadge(expired);eq(expired.circles.size(),0,"dot gone exactly at expiry");
  ConnectionsManager.now=100;
  check(!MangoVisualPreferences.isTapMediaSwitchDisabled(0),"tap disable defaults false");
  Viewer v=new Viewer();v.onSingleTapConfirmed(new MotionEvent(1,799,200));eq(v.switched,1,"default photo next tap retained");
  v.onSingleTapConfirmed(new MotionEvent(1,0,200));eq(v.switched,0,"default photo previous tap retained");
  Gallery g=new Gallery();g.tap(299);eq(g.index,2,"default avatar next tap retained");g.tap(0);eq(g.index,1,"default avatar previous tap retained");
  MangoSettings.set(0,MangoVisualPreferences.DISABLE_TAP_MEDIA_SWITCH,true);
  v.onSingleTapConfirmed(new MotionEvent(1,799,200));eq(v.switched,0,"disabled right photo tap");eq(v.fallthrough,1,"photo controls continue");
  v.onSingleTapConfirmed(new MotionEvent(1,0,200));eq(v.switched,0,"disabled left photo tap");eq(v.fallthrough,2,"left tap still reaches controls");
  v.currentAccount=1;v.onSingleTapConfirmed(new MotionEvent(1,799,200));eq(v.switched,1,"photo setting per account");
  g=new Gallery();g.tap(299);g.tap(0);eq(g.switches,0,"disabled avatar taps");eq(g.callback.downs,0,"disabled avatar press preview");
  check(g.pagerTouches>0 && g.parentListView.touches>0,"touches still reach original gesture handlers");
  g.onTouchEvent(new MotionEvent(0,100,20));g.onTouchEvent(new MotionEvent(2,60,20));g.onTouchEvent(new MotionEvent(1,60,20));
  eq(g.pagerMoves,1,"horizontal swipe reaches pager");eq(g.switches,0,"swipe release does not trigger tap");
  g=new Gallery();g.onTouchEvent(new MotionEvent(0,100,20));g.onTouchEvent(new MotionEvent(2,100,60));
  eq(g.parentListView.moves,1,"vertical gesture reaches profile list");
  g=new Gallery();g.pinchToZoomHelper=new Pinch();g.pinchToZoomHelper.active=true;
  check(g.onTouchEvent(new MotionEvent(0,100,20)),"pinch zoom retained");eq(g.switches,0,"pinch does not switch");
  g=new Gallery();g.currentAccount=1;g.tap(299);eq(g.index,2,"avatar preference per displayed account");
  MangoSettings.set(0,MangoVisualPreferences.DISABLE_TAP_MEDIA_SWITCH,false);SharedConfig.nextMediaTap=false;
  v=new Viewer();v.onSingleTapConfirmed(new MotionEvent(1,799,200));eq(v.switched,0,"existing next-media setting respected");
  System.out.println("PASS: "+checks+" production visual preference/drawing/gesture assertions");
 }
}
'''.replace('__DRAW__',draw).replace('__SCHEDULE__',schedule).replace('__TOUCH__',touch).replace('__TAP__',tap)

with tempfile.TemporaryDirectory(prefix='v51-visual-') as directory:
    out=Path(directory)
    tg=out/'org/telegram/tgnet';tg.mkdir(parents=True)
    (tg/'TLRPC.java').write_text('''package org.telegram.tgnet; public class TLRPC {
 public static class Status {public int expires;}
 public static class User {public long id; public boolean self,bot,deleted,support;public Status status;}
 public static class Chat {public boolean signature_profiles;}
 public static class Peer {public long user_id;}
 public static class Message {public Peer from_id=new Peer();}
}''')
    (tg/'ConnectionsManager.java').write_text('''package org.telegram.tgnet; public class ConnectionsManager {
 public static int now=100; public static ConnectionsManager getInstance(int a){return new ConnectionsManager();} public int getCurrentTime(){return now;}
}''')
    (out/'MangoVisualPreferences.java').write_text(helper)
    (out/'VisualStubs.java').write_text(stubs)
    (out/'VisualRegression.java').write_text(fixture)
    subprocess.run([str(JAVA/'javac'),'-d',directory,*map(str,out.rglob('*.java'))],check=True)
    subprocess.run([str(JAVA/'java'),'-cp',directory,'org.telegram.messenger.VisualRegression'],check=True)

assert 'cell.drawAvatarWithOnlineBadge(canvas);' in chat
assert cell.count('drawAvatarWithOnlineBadge(canvas);')==2
assert 'removeCallbacks(onlineBadgeExpiryRunnable);' in method(cell,'    protected void onDetachedFromWindow()')
assert 'chatListView.invalidate();' in chat[chat.index('} else if (id == NotificationCenter.updateInterfaces)'):][:900]
assert 'private final int currentAccount;' in gallery and gallery.count('this.currentAccount = currentAccount;')==2
for filename in ('ProfileActivity.java','ProfileActivity2.java','GroupCallActivity.java'):
    assert 'new ProfileGalleryView(context, currentAccount,' in (BASE/'ui'/filename).read_text()
assert 'new ProfileGalleryView(context, fragment.getCurrentAccount(),' in (BASE/'ui/Delegates/MemberRequestsDelegate.java').read_text()
assert 'toggleVideoPlayer();' in method(viewer,'    public boolean onSingleTapConfirmed(')
assert 'canDoubleTapSeekVideo(e)' in method(viewer,'    public boolean onDoubleTap(')
keys={'MangoShowOnlineBadge','MangoShowOnlineBadgeInfo','MangoDisableTapMediaSwitch','MangoDisableTapMediaSwitchInfo'}
resources=list((ROOT/'TMessagesProj/src/main/res').glob('values*/mango_v51_visual.xml'))
assert len(resources)==13
for resource in resources:
    strings={s.attrib['name']:s.text for s in ET.parse(resource).getroot()}
    assert set(strings)==keys and all(strings.values()),resource
print('PASS: lifecycle/account/gesture wiring and 52 localized strings across 13 locales')
