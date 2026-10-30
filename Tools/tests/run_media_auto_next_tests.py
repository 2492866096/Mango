#!/usr/bin/env python3
"""Run production media eligibility, chat queue and completion decisions without Android."""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "TMessagesProj/src/main/java"
JAVA = Path(os.environ.get("JAVA_HOME", "/Applications/Android Studio.app/Contents/jbr/Contents/Home")) / "bin"


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


controller = (SRC / "org/telegram/messenger/MediaController.java").read_text()
viewer = (SRC / "org/telegram/ui/PhotoViewer.java").read_text()
chat = (SRC / "org/telegram/ui/ChatActivity.java").read_text()
no_next = extract(controller, "    public boolean hasNoNextVoiceOrRoundVideoMessage()")
video_end = extract(controller, "    private void updateVideoState(")
audio_source = controller[controller.index("audioPlayer.setDelegate(new VideoPlayer.VideoPlayerDelegate()", controller.index("public boolean playMessage(final MessageObject messageObject, boolean silent)")):]
audio_end = extract(audio_source, "public void onStateChanged(boolean playWhenReady, int playbackState)")
audio_end = audio_end.replace("onStateChanged(", "audioStateChanged(", 1).replace("{", "{\nMessageObject messageObject = playingMessageObject;", 1)
viewer_next = extract(viewer, "    private boolean tryAutoPlayNextVideo()")
chat_queue = extract(chat, "    private ArrayList<MessageObject> createVoiceMessagesPlaylist(")

fixture = r'''
package org.telegram.messenger;
import java.util.*;
class MessageObject {
 Object messageOwner=new Object(); boolean deleted, once, encrypted, hidden, sponsored, unread=true, out, scheduled;
 int type, id, currentAccount; long did=100; float audioProgress;
 MessageObject(int id,int type){this.id=id;this.type=type;}
 boolean isVoice(){return type==1;} boolean isRoundVideo(){return type==2;} boolean isVideo(){return type==3;}
 boolean isVoiceOnce(){return isVoice()&&once;} boolean isRoundOnce(){return isRoundVideo()&&once;}
 boolean shouldEncryptPhotoOrVideo(){return encrypted;} boolean isHiddenSensitive(){return hidden;}
 boolean isSponsored(){return sponsored;} boolean isContentUnread(){return unread;} boolean isOut(){return out;}
 long getDialogId(){return did;} int getId(){return id;} Object getDocument(){return new Object();}
}
class ExtensionMediaSettings {static boolean enabled=true; static boolean isAutoPlayNextEnabled(){return enabled;}}
class ExoPlayer {static final int STATE_IDLE=1, STATE_BUFFERING=2, STATE_READY=3, STATE_ENDED=4;}
class WindowManager {static class LayoutParams {static final int FLAG_KEEP_SCREEN_ON=1;}}
class Activity { Window getWindow(){return new Window();} }
class Window {void addFlags(int f){} void clearFlags(int f){}}
class AndroidUtilities {static void cancelRunOnUIThread(Runnable r){} static void runOnUIThread(Runnable r,int ms){}}
class FileLog {static void e(Exception e){throw new RuntimeException(e);}}
class FileLoader {static FileLoader getInstance(int a){return new FileLoader();} void removeLoadingVideo(Object d,boolean a,boolean b){}}
class NotificationCenter {
 static final int messagePlayingProgressDidChanged=1; static NotificationCenter getInstance(int a){return new NotificationCenter();}
 void postNotificationName(int name,Object... args){}
}
class CastSync {static boolean isActive(){return false;}}
class VideoPlayer {int seeks; long getDuration(){return 1000;} void seekTo(long p){seeks++;} void setMute(boolean m){} boolean isPlaying(){return false;}}
public class MediaAutoNextRegression {
 static int assertions;
 static void check(boolean value,String name){assertions++; if(!value)throw new AssertionError(name);}
 static MessageObject m(int id,int type){return new MessageObject(id,type);}
 static class Controller {
  VideoPlayer videoPlayer=new VideoPlayer(), audioPlayer=new VideoPlayer(); Activity baseActivity=new Activity();
  boolean playerWasReady,currentAspectRatioFrameLayoutReady,isSilent,restoreResult,stopped,advanced;
  int cleanups,restores,musicNext,playerNum=1,tag=1; long lastProgress; float seekToProgressPending;
  Runnable setLoadingRunnable=()->{}; MessageObject playingMessageObject;
  ArrayList<MessageObject> voiceMessagesPlaylist=new ArrayList<>(),playlist=new ArrayList<>();
  boolean isSamePlayingMessage(MessageObject m){return playingMessageObject==m;}
  boolean restoreMusicPlaylistState(){restores++;return restoreResult;}
  void cleanupPlayer(boolean notify,boolean stop,boolean voiceEnd,boolean transfer){cleanups++;stopped=stop;advanced=!stop&&voiceEnd;}
  void playNextMessageWithoutOrder(boolean auto){musicNext++;}
  __NO_NEXT__
  __VIDEO_END__
  __AUDIO_END__
 }
 static class Viewer {
  static final int EDIT_MODE_NONE=0; boolean visible=true,inPreview,isCurrentVideo,playerLooping,isEmbedVideo;
  int currentEditMode,switchingToMode,animationInProgress,currentIndex,switched=-1;
  Object pageBlocksAdapter,currentAnimation; VideoPlayer videoPlayer=new VideoPlayer();
  ArrayList<Object> imagesArrLocals=new ArrayList<>(); ArrayList<MessageObject> imagesArr=new ArrayList<>();
  boolean isVisible(){return visible;} void switchToNextIndex(int add,boolean init,boolean automatic){switched=currentIndex+add;check(automatic,"automatic start requested");}
  __VIEWER_NEXT__
 }
 static class Chat {
  ArrayList<MessageObject> messages=new ArrayList<>(); Object currentEncryptedChat;
  __CHAT_QUEUE__
 }
 static Controller controller(MessageObject current, MessageObject next) {
  Controller c=new Controller(); c.playingMessageObject=current; c.voiceMessagesPlaylist.add(current);
  if(next!=null)c.voiceMessagesPlaylist.add(next); return c;
 }
 static Viewer viewer(MessageObject... messages){Viewer v=new Viewer();v.imagesArr.addAll(Arrays.asList(messages));return v;}
 public static void main(String[] args){
  MessageObject voice=m(1,1),round=m(2,2),video=m(3,3),photo=m(4,0),next=m(5,3);
  check(MediaAutoNextQueue.isPlayableMessage(voice),"voice eligible");
  check(MediaAutoNextQueue.isPlayableMessage(round),"round video eligible");
  check(MediaAutoNextQueue.isPlayableMessage(video),"normal video eligible");
  check(!MediaAutoNextQueue.isPlayableMessage(photo),"photo not inline media");
  check(!MediaAutoNextQueue.isPlayableMessage(null),"null excluded");
  MessageObject bad=m(9,3);bad.messageOwner=null;check(!MediaAutoNextQueue.isPlayableMessage(bad),"missing owner excluded");
  bad=m(9,3);bad.deleted=true;check(!MediaAutoNextQueue.isPlayableMessage(bad),"deleted excluded");
  bad=m(9,1);bad.once=true;check(!MediaAutoNextQueue.isPlayableMessage(bad),"once voice excluded");
  bad=m(9,2);bad.once=true;check(!MediaAutoNextQueue.isPlayableMessage(bad),"once round excluded");
  bad=m(9,3);bad.encrypted=true;check(!MediaAutoNextQueue.isPlayableMessage(bad),"expiring video excluded");
  bad=m(9,3);bad.hidden=true;check(!MediaAutoNextQueue.isPlayableMessage(bad),"hidden excluded");
  bad=m(9,3);bad.sponsored=true;check(!MediaAutoNextQueue.isPlayableMessage(bad),"sponsored excluded");
  List<MessageObject> list=Arrays.asList(video,photo,voice,round,next);
  check(MediaAutoNextQueue.findNextVideoIndex(list,0)==4,"skip photo voice round in viewer");
  check(MediaAutoNextQueue.findNextVideoIndex(list,4)==-1,"do not wrap");
  check(MediaAutoNextQueue.findNextVideoIndex(list,-1)==-1,"negative index");
  check(MediaAutoNextQueue.findNextVideoIndex(list,5)==-1,"out of range");
  check(MediaAutoNextQueue.findNextVideoIndex(null,0)==-1,"missing list");
  check(MediaAutoNextQueue.findNextVideoIndex(list,1)==-1,"do not advance from photo");
  MessageObject other=m(8,3);other.did=101;
  check(MediaAutoNextQueue.findNextVideoIndex(Arrays.asList(video,other,next),0)==2,"skip other chat");
  other.did=100;other.currentAccount=1;
  check(MediaAutoNextQueue.findNextVideoIndex(Arrays.asList(video,other,next),0)==2,"skip other account");
  check(MediaAutoNextQueue.findNextVideoIndex(Arrays.asList(video,video,next),0)==2,"skip duplicate id");
  Controller c=controller(voice,video);check(!c.hasNoNextVoiceOrRoundVideoMessage(),"voice to video queue");
  c=controller(video,next);check(!c.hasNoNextVoiceOrRoundVideoMessage(),"video queue supported");
  c.restoreResult=true;c.updateVideoState(video,null,false,true,ExoPlayer.STATE_ENDED);
  check(c.advanced&&c.cleanups==1,"ended transition even when isPlaying false");
  check(c.restores==0&&c.videoPlayer.seeks==0,"next before music restore or default repeat");
  c=controller(round,video);c.updateVideoState(round,null,true,true,ExoPlayer.STATE_ENDED);
  check(c.advanced,"round to normal video");
  c=controller(video,next);c.updateVideoState(video,null,false,false,ExoPlayer.STATE_ENDED);
  check(c.cleanups==0,"paused ended event does not transition");
  c=controller(video,next);c.updateVideoState(m(90,3),null,false,true,ExoPlayer.STATE_ENDED);
  check(c.cleanups==0,"old message cannot transition current player");
  c=controller(video,null);c.updateVideoState(video,null,false,true,ExoPlayer.STATE_ENDED);
  check(c.stopped&&c.videoPlayer.seeks==0,"last unmuted video stops rather than loop");
  c=controller(voice,video);c.playlist.add(m(99,1));c.restoreResult=true;
  c.audioStateChanged(true,ExoPlayer.STATE_ENDED);
  check(c.advanced&&c.restores==0&&c.musicNext==0,"voice next wins over saved music playlist");
  ExtensionMediaSettings.enabled=false;
  c=controller(video,next);check(c.hasNoNextVoiceOrRoundVideoMessage(),"disabled queue");
  c.updateVideoState(video,null,false,true,ExoPlayer.STATE_ENDED);
  check(c.stopped&&!c.advanced&&c.videoPlayer.seeks==0,"disabled stops video");
  c=controller(voice,video);c.audioStateChanged(true,ExoPlayer.STATE_ENDED);
  check(c.stopped&&!c.advanced,"disabled stops voice");
  Viewer v=viewer(video,photo,next);check(!v.tryAutoPlayNextVideo(),"disabled full screen");
  ExtensionMediaSettings.enabled=true;
  v=viewer(video,photo,next);check(v.tryAutoPlayNextVideo()&&v.switched==2,"viewer skips photo and starts next");
  v=viewer(video);check(!v.tryAutoPlayNextVideo(),"viewer final stops");
  v=viewer(video,next);v.visible=false;check(!v.tryAutoPlayNextVideo(),"closed viewer stops");
  v=viewer(video,next);v.videoPlayer=null;check(!v.tryAutoPlayNextVideo(),"web state cannot advance");
  v=viewer(video,next);v.inPreview=true;check(!v.tryAutoPlayNextVideo(),"preview stops");
  v=viewer(video,next);v.currentEditMode=1;check(!v.tryAutoPlayNextVideo(),"editor stops");
  v=viewer(video,next);v.switchingToMode=1;check(!v.tryAutoPlayNextVideo(),"editor transition stops");
  v=viewer(video,next);v.isCurrentVideo=true;check(!v.tryAutoPlayNextVideo(),"local editor video stops");
  v=viewer(video,next);v.playerLooping=true;check(!v.tryAutoPlayNextVideo(),"explicit loop preserved");
  v=viewer(video,next);v.isEmbedVideo=true;check(!v.tryAutoPlayNextVideo(),"embedded webpage stops");
  v=viewer(video,next);v.pageBlocksAdapter=new Object();check(!v.tryAutoPlayNextVideo(),"article video stops");
  v=viewer(video,next);v.currentAnimation=new Object();check(!v.tryAutoPlayNextVideo(),"animation stops");
  v=viewer(video,next);v.imagesArrLocals.add(new Object());check(!v.tryAutoPlayNextVideo(),"upload gallery stops");
  v=viewer(video,next);v.animationInProgress=1;check(!v.tryAutoPlayNextVideo(),"closing animation stops");
  Chat chat=new Chat();chat.messages.addAll(Arrays.asList(next,photo,video,round,voice));
  ArrayList<MessageObject> queue=chat.createVoiceMessagesPlaylist(voice,false);
  check(queue.equals(Arrays.asList(voice,round,video,next)),"actual chat queue includes video in chronological order");
  next.did=500;queue=chat.createVoiceMessagesPlaylist(voice,false);check(!queue.contains(next),"chat queue excludes another dialog");
  next.did=100;video.encrypted=true;queue=chat.createVoiceMessagesPlaylist(voice,false);check(!queue.contains(video),"chat queue excludes expiring video");
  video.encrypted=false;round.once=true;queue=chat.createVoiceMessagesPlaylist(voice,false);check(!queue.contains(round),"chat queue excludes once video");
  round.once=false;next.unread=false;queue=chat.createVoiceMessagesPlaylist(voice,true);check(!queue.contains(next),"unread queue preserved");
  System.out.println("PASS: "+assertions+" media auto-next production decision assertions");
 }
}
'''
for key, value in {"__NO_NEXT__": no_next, "__VIDEO_END__": video_end, "__AUDIO_END__": audio_end,
                   "__VIEWER_NEXT__": viewer_next, "__CHAT_QUEUE__": chat_queue}.items():
    fixture = fixture.replace(key, value)
with tempfile.TemporaryDirectory(prefix="mango-media-next-") as temp:
    temp = Path(temp)
    test = temp / "MediaAutoNextRegression.java"
    test.write_text(fixture)
    production = SRC / "org/telegram/messenger/MediaAutoNextQueue.java"
    subprocess.run([str(JAVA / "javac"), "-d", str(temp), str(production), str(test)], check=True)
    subprocess.run([str(JAVA / "java"), "-cp", str(temp), "org.telegram.messenger.MediaAutoNextRegression"], check=True)

# Wiring not present in the small desktop player doubles.
assert "if (playWhenReady && tryAutoPlayNextVideo())" in viewer
assert "setImageIndex(currentIndex + add, init, true, Math.abs(add) > 1)" in viewer
assert "if (videoPlayer != statePlayer)" in viewer
assert "currentMessageObject.getDuration() <= 30 && !ExtensionMediaSettings.isAutoPlayNextEnabled()" in viewer
assert "account == messageObject.currentAccount && did == messageObject.getDialogId()" in controller
assert "boolean saved = savedMusicPlaylistState != null;" in controller
print("PASS: 6 video-player integration guard checks")
