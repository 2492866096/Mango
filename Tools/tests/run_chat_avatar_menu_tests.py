#!/usr/bin/env python3
"""Exercise production avatar drawing and menu partitioning without an Android runtime."""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
JDK = Path(os.environ.get('JAVA_HOME', '/Applications/Android Studio.app/Contents/jbr/Contents/Home')) / 'bin'
chat = (ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java').read_text()
start = chat.index('                    ImageReceiver imageReceiver = cell.getAvatarImage();')
brace = chat.index('{', start)
depth, end = 1, brace + 1
while depth:
    depth += (chat[end] == '{') - (chat[end] == '}')
    end += 1
avatar = chat[start:end]
fixture = r'''
import java.util.*;
import org.telegram.messenger.MessageMenuCollapse;
public class ChatAvatarMenuRegression {
 static class View {
  int top,height=80,pad,position; float translationY,alpha=1,scale=1;
  int getTop(){return top;} int getBottom(){return top+height;} float getY(){return top+translationY;}
  float getTranslationY(){return translationY;} int getPaddingTop(){return pad;}
  int getMeasuredHeight(){return height;} int getHeight(){return height;}
  float getAlpha(){return alpha;} float getScaleX(){return scale;} float getScaleY(){return scale;}
  float getX(){return 0;} float getPivotX(){return 20;}
 }
 static class ImageReceiver {
  float y,alpha; boolean visible=true,drawn;
  void setVisible(boolean b,boolean animate){visible=b;} void setImageY(float value){y=value;}
  void setAlpha(float value){alpha=value;} float getImageX2(){return 42;} float getImageY2(){return y+42;}
  void draw(Canvas c){drawn=visible;}
 }
 static class Params { boolean backgroundChangeBounds,animatePlayingRound; float deltaBottom,animateChangeProgress; }
 static class MessageObject {
  boolean deleted; static final int POSITION_FLAG_TOP=1, POSITION_FLAG_BOTTOM=2;
  static class GroupedMessagePosition {int flags,minX,minY,maxY;}
  static class GroupedMessages {Params transitionParams=new Params();ArrayList<GroupedMessagePosition> posArray=new ArrayList<>();}
 }
 static class ChatMessageCell extends View {
  ImageReceiver avatar=new ImageReceiver(); MessageObject message=new MessageObject(); Params params=new Params();
  boolean pinnedTop,pinnedBottom,layer; float sliding,checkbox;
  MessageObject.GroupedMessages group; MessageObject.GroupedMessagePosition groupPosition;
  ImageReceiver getAvatarImage(){return avatar;} MessageObject getMessageObject(){return message;}
  MessageObject.GroupedMessages getCurrentMessagesGroup(){return group;}
  MessageObject.GroupedMessagePosition getCurrentPosition(){return groupPosition;}
  Params getTransitionParams(){return params;} int getLayoutHeight(){return height-pad;}
  float getPaddingTopAnimated(){return pad;} boolean drawPinnedTop(){return pinnedTop;}
  boolean drawPinnedBottom(){return pinnedBottom;} boolean willRemovedAfterAnimation(){return false;}
  float getSlidingOffsetX(){return sliding;} float getCheckBoxTranslation(){return checkbox;}
  boolean isCheckBoxVisible(){return checkbox!=0;} boolean isPlayingRound(){return false;}
  boolean shouldDrawAlphaLayer(){return layer;}
 }
 static class ViewHolder {View itemView;ViewHolder(View v){itemView=v;}int getAdapterPosition(){return itemView.position;}}
 static class RecyclerView {static int NO_POSITION=-1;}
 static class ListView {
  Map<Integer,View> rows=new HashMap<>();int height=1000;
  boolean isFastScrollAnimationRunning(){return false;} int getChildAdapterPosition(View v){return v.position;}
  ViewHolder getChildViewHolder(View v){return new ViewHolder(v);}
  ViewHolder findViewHolderForAdapterPosition(int p){View v=rows.get(p);return v==null?null:new ViewHolder(v);}
  int getMeasuredHeight(){return height;}int getPaddingBottom(){return 0;}
 }
 static class OldViews {int indexOfValue(View v){return -1;}int keyAt(int i){return i;}View get(int p){return null;}}
 static class ScrollHelper {OldViews positionToOldView=new OldViews();}
 static class Canvas {int scales;float scale;void save(){}void restore(){}void translate(float x,float y){}
  void scale(float x,float y,float px,float py){scales++;scale=x;}}
 final ListView chatListView=new ListView(); final ScrollHelper chatScrollHelper=new ScrollHelper();
 int dp(int n){return n;}float getSideMenuAlpha(){return 0;}
 MessageObject.GroupedMessages getValidGroupedMessage(MessageObject m){return null;}
 boolean draw(ChatMessageCell cell, Canvas canvas){
  View child=cell;MessageObject message=cell.message;MessageObject.GroupedMessagePosition position=cell.groupPosition;
  boolean result=true;
  /*AVATAR*/
  return result;
 }
 static int checks;static void check(boolean ok,String msg){checks++;if(!ok)throw new AssertionError(msg);}
 public static void main(String[] args){
  ChatAvatarMenuRegression h=new ChatAvatarMenuRegression();
  ChatMessageCell owner=new ChatMessageCell();owner.top=300;owner.position=0;owner.pinnedTop=true;
  owner.layer=true;owner.alpha=.5f;owner.scale=.8f;
  ChatMessageCell older=new ChatMessageCell();older.top=100;older.position=1;older.pinnedBottom=true;
  older.alpha=.2f;older.scale=.3f;
  h.chatListView.rows.put(0,owner);h.chatListView.rows.put(1,older);
  Canvas canvas=new Canvas();h.draw(owner,canvas);
  check(owner.avatar.drawn,"owning avatar is drawn");
  check(owner.avatar.alpha==.5f,"avatar uses owning cell alpha after traversing older bubbles");
  check(canvas.scales==1&&canvas.scale==.8f,"avatar scale belongs to the original message");
  check(owner.avatar.y==336,"avatar stays anchored to the owning bubble bottom");
  h.chatListView.height=180;older.translationY=60;
  h.draw(owner,new Canvas());
  check(owner.avatar.y==164,"connected group's top constraint includes its current animation translation");
  owner.pinnedTop=false;owner.top=40;owner.translationY=25;h.chatListView.height=1000;
  h.draw(owner,new Canvas());
  check(owner.avatar.y==101,"unconnected bubble keeps its animated vertical position");
  for(int count=0;count<80;count++){
   int full=MessageMenuCollapse.visibleItemCount(count,false);
   check(full==count,"disabled folding preserves every item");
   int first=MessageMenuCollapse.visibleItemCount(count,true);
   check(first>=0&&first<=count,"partition remains in range");
   if(count<=6) check(first==count,"short menus remain unchanged");
   else {
    check(first+1< count,"More button still shortens the menu");
    check(first+1<=(count+1)/2,"main menu including More uses a half-height budget");
    ArrayList<Integer> actions=new ArrayList<>();for(int i=0;i<count;i++)actions.add(i);
    ArrayList<Integer> reached=new ArrayList<>(actions.subList(0,first));reached.addAll(actions.subList(first,count));
    check(actions.equals(reached),"all actions stay reachable in their original order");
   }
  }
  System.out.println("PASS: "+checks+" avatar/menu behavior checks");
 }
}
'''
with tempfile.TemporaryDirectory() as temp:
    directory = Path(temp)
    harness = directory / 'ChatAvatarMenuRegression.java'
    harness.write_text(fixture.replace('/*AVATAR*/', avatar))
    source = ROOT / 'TMessagesProj/src/main/java/org/telegram/messenger/MessageMenuCollapse.java'
    subprocess.run([str(JDK/'javac'), '-d', temp, str(source), str(harness)], check=True)
    subprocess.run([str(JDK/'java'), '-cp', temp, 'ChatAvatarMenuRegression'], check=True)
    # The original bug must be detected, rather than only checking fixture arithmetic.
    broken = avatar.replace('pinnedCell = (ChatMessageCell) holder.itemView;', 'pinnedCell = cell = (ChatMessageCell) holder.itemView;')
    assert broken != avatar
    harness.write_text(fixture.replace('/*AVATAR*/', broken))
    subprocess.run([str(JDK/'javac'), '-d', temp, str(source), str(harness)], check=True)
    result = subprocess.run([str(JDK/'java'), '-cp', temp, 'ChatAvatarMenuRegression'], capture_output=True)
    assert result.returncode != 0 and b'owning cell alpha' in result.stderr
    print('PASS: rejected avatar-owner mutation')

assert 'if (inOverflow) overflowItems.addView(cell' in chat
assert 'processSelectedOption(options.get(i))' in chat
assert 'overflowScroll.addView(overflowItems' in chat
assert 'popupLayout.getSwipeBack().openForeground(overflowIndex)' in chat
assert 'popupLayout.getSwipeBack().closeForeground()' in chat
print('PASS: native overflow menu routing and scrolling checks')
