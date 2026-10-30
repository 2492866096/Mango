#!/usr/bin/env python3
"""Run ChatActivity scam-hint scheduling against a small offline UI harness.

The five production methods are extracted from ChatActivity.java on every run.
The harness models the delayed UI queue, recycled visible cells, the two scam
managers, and MangoScamUserWarning.  This keeps the checks behavioral without
starting Gradle or contacting the network.
"""
from pathlib import Path
import os
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java"
JAVA = Path(os.environ.get(
    "JAVA_HOME", "/Applications/Android Studio.app/Contents/jbr/Contents/Home"
)) / "bin"


def method(text, marker):
    start = text.index(marker)
    opening = text.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        if text[end] == "{":
            depth += 1
        elif text[end] == "}":
            depth -= 1
        end += 1
    return text[start:end]


chat = SOURCE.read_text()
production_methods = "\n".join(method(chat, marker) for marker in (
    "    private void startScamHints()",
    "    private void stopScamHints()",
    "    private void scheduleScamHint()",
    "    private boolean showScamUserHint(TLRPC.User user)",
    "    private boolean showScamChannelHint(TLRPC.Chat chat)",
    "    private void checkVisibleScamHints()",
))


fixture = r'''package org.telegram.ui;

import java.util.*;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.R;

public final class ScamHintRegression {
 static class View {
  boolean visible = true;
  boolean getLocalVisibleRect(android.graphics.Rect rect) { return visible; }
 }
 static final class MessageObject {
  final long id;
  final String kind;
  MessageObject(long id, String kind) { this.id = id; this.kind = kind; }
 }
 static final class ChatMessageCell extends View {
  MessageObject message;
  ChatMessageCell(MessageObject message) { this.message = message; }
  MessageObject getMessageObject() { return message; }
 }
 static final class ChatListView {
  final ArrayList<View> children = new ArrayList<>();
  int getChildCount() { return children.size(); }
  View getChildAt(int index) { return children.get(index); }
 }
 static final class Dialog { boolean showing; boolean isShowing() { return showing; } }

 public static final class Harness {
  static final int MODE_DEFAULT = 0;
  int currentAccount;
  int chatMode = MODE_DEFAULT;
  boolean paused;
  boolean isFinished;
  TLRPC.User currentUser;
  TLRPC.Chat currentChat;
  ChatListView chatListView;
  Dialog visibleDialog;
  public final ArrayList<Long> shownUsers = new ArrayList<>();
  private Runnable scamHintRunnable;
  private boolean scamHintsActive;
  private final HashSet<Long> scamUsersShownThisVisit = new HashSet<>();
  private final Runnable scamHintsChanged = this::scheduleScamHint;

''' + production_methods + r'''

  boolean isInPreviewMode() { return false; }
  Object getParentActivity() { return this; }
  Object getParentLayout() { return this; }
  void start() { startScamHints(); }
  void stop() { stopScamHints(); }
  void scanNow() { checkVisibleScamHints(); }
 }

 static void check(boolean value, String message) {
  if (!value) throw new AssertionError(message);
 }
 static TLRPC.User user(long id) {
  TLRPC.User value = new TLRPC.User(); value.id = id; return value;
 }
 static MessageObject message(long id, String kind) { return new MessageObject(id, kind); }
 static ChatMessageCell cell(MessageObject message) { return new ChatMessageCell(message); }
 static void reset() {
  org.telegram.messenger.AndroidUtilities.reset();
  org.telegram.messenger.ScamUsersManager.reset();
  org.telegram.messenger.ScamProtectionManager.reset();
  org.telegram.ui.Components.MangoScamUserWarning.reset();
 }
 static void drain() { org.telegram.messenger.AndroidUtilities.drain(); }

 static Harness groupWith(ChatMessageCell... cells) {
  Harness harness = new Harness(); harness.chatListView = new ChatListView();
  harness.chatListView.children.addAll(Arrays.asList(cells)); return harness;
 }

 static void twoRiskSendersStillBothAppear() {
  reset();
  MessageObject first = message(101, "text"), second = message(202, "text");
  Harness harness = groupWith(cell(first), cell(second));
  org.telegram.messenger.ScamUsersManager.forMessage(first, user(1));
  org.telegram.messenger.ScamUsersManager.forMessage(second, user(2));
  harness.start(); drain();
  check(harness.shownUsers.equals(Arrays.asList(1L)), "first visible sender is shown");
  harness.scheduleScamHint(); drain();
  check(harness.shownUsers.equals(Arrays.asList(1L, 2L)), "second visible sender is not lost");
 }

 static void duplicateBindsCoalesce() {
  reset();
  MessageObject item = message(301, "text");
  Harness harness = groupWith(cell(item));
  org.telegram.messenger.ScamUsersManager.forMessage(item, user(3));
  harness.start();
  harness.scheduleScamHint(); harness.scheduleScamHint();
  check(org.telegram.messenger.AndroidUtilities.pendingCount() == 1, "duplicate binds have one queued scan");
  drain();
  check(org.telegram.ui.Components.MangoScamUserWarning.showCalls == 1, "duplicate binds attempt one warning");
 }

 static void recycledCellsAreScannedAtExecutionTime() {
  reset();
  MessageObject oldItem = message(401, "text"), currentItem = message(402, "photo");
  Harness harness = groupWith(cell(oldItem));
  org.telegram.messenger.ScamUsersManager.forMessage(currentItem, user(4));
  harness.start();
  harness.chatListView.children.set(0, cell(currentItem));
  drain();
  check(harness.shownUsers.equals(Arrays.asList(4L)), "recycled cell uses current message");
 }

 static void listenerUpdateRetriesAfterEmptyList() {
  reset();
  MessageObject item = message(501, "sticker");
  Harness harness = groupWith(cell(item));
  harness.start(); drain();
  check(harness.shownUsers.isEmpty(), "empty list does not warn");
  org.telegram.messenger.ScamUsersManager.forMessage(item, user(5));
  org.telegram.messenger.ScamUsersManager.fireListeners(); drain();
  check(harness.shownUsers.equals(Arrays.asList(5L)), "listener update retries visible cells");
 }

 static void pausedResumeRetriesAndStopCancels() {
  reset();
  MessageObject item = message(601, "text");
  Harness harness = groupWith(cell(item));
  org.telegram.messenger.ScamUsersManager.forMessage(item, user(6));
  harness.start(); harness.stop();
  check(org.telegram.messenger.AndroidUtilities.pendingCount() == 0, "pause cancels pending scan");
  drain(); check(harness.shownUsers.isEmpty(), "cancelled scan does not show");
  check(org.telegram.messenger.ScamUsersManager.listenerCount() == 0, "pause removes manager listener");
  harness.start(); drain();
  check(harness.shownUsers.equals(Arrays.asList(6L)), "resume retries after pause");
 }

 static void reenterClearsPerVisitDedup() {
  reset();
  MessageObject item = message(701, "text");
  Harness harness = groupWith(cell(item));
  org.telegram.messenger.ScamUsersManager.forMessage(item, user(7));
  harness.start(); drain(); harness.stop(); harness.start(); drain();
  check(harness.shownUsers.equals(Arrays.asList(7L, 7L)), "re-enter allows a fresh warning");
 }

 static void rejectedDialogDoesNotConsumeDedup() {
  reset();
  MessageObject item = message(801, "text");
  Harness harness = groupWith(cell(item));
  org.telegram.messenger.ScamUsersManager.forMessage(item, user(8));
  org.telegram.ui.Components.MangoScamUserWarning.accept = false;
  harness.start(); drain();
  check(harness.shownUsers.isEmpty(), "transition rejection is not displayed");
  org.telegram.ui.Components.MangoScamUserWarning.accept = true;
  harness.scheduleScamHint(); drain();
  check(harness.shownUsers.equals(Arrays.asList(8L)), "rejected dialog can retry");
 }

 static void privateChatIdentityWorksWithoutMessages() {
  reset();
  Harness harness = new Harness(); harness.currentUser = user(9);
  org.telegram.messenger.ScamUsersManager.forUser(9, user(9));
  harness.start(); drain();
  check(harness.shownUsers.equals(Arrays.asList(9L)), "private identity warns with empty history");
 }

 static void dismissRescansNextRiskSender() {
  reset();
  MessageObject first = message(901, "text"), second = message(902, "text");
  Harness harness = groupWith(cell(first), cell(second));
  org.telegram.messenger.ScamUsersManager.forMessage(first, user(12));
  org.telegram.messenger.ScamUsersManager.forMessage(second, user(13));
  harness.start(); drain();
  check(harness.shownUsers.equals(Arrays.asList(12L)), "first warning is visible");
  org.telegram.ui.Components.MangoScamUserWarning.dismiss();
  drain();
  check(harness.shownUsers.equals(Arrays.asList(12L, 13L)), "dismiss callback rescans next sender");
 }

 static void outgoingPhotoAndStickerMessagesWork() {
  reset();
  MessageObject outgoingPhoto = message(1001, "photo"), sticker = message(1002, "sticker");
  Harness harness = groupWith(cell(outgoingPhoto), cell(sticker));
  org.telegram.messenger.ScamUsersManager.forMessage(outgoingPhoto, user(10));
  org.telegram.messenger.ScamUsersManager.forMessage(sticker, user(11));
  harness.start(); drain(); harness.scheduleScamHint(); drain();
  check(harness.shownUsers.equals(Arrays.asList(10L, 11L)), "photo and sticker cells warn");
 }

 static void channelIdentityAndNamespaceWork() {
  reset();
  Harness harness = new Harness(); harness.currentChat = new TLRPC.Chat(); harness.currentChat.id = 7;
  org.telegram.messenger.ScamUsersManager.forChannel(harness.currentChat);
  harness.start(); drain();
  check(harness.shownUsers.equals(Arrays.asList(-7L)), "channel itself warns without messages");
  harness.scheduleScamHint(); drain();
  check(harness.shownUsers.size()==1, "channel deduped for same visit");
  harness.stop(); harness.start(); drain();
  check(harness.shownUsers.equals(Arrays.asList(-7L,-7L)), "reenter channel warns again");
  reset();
  MessageObject userPost=message(2001,"photo"), channelPost=message(2002,"sticker");
  harness=groupWith(cell(userPost),cell(channelPost));
  org.telegram.messenger.ScamUsersManager.forMessage(userPost,user(7));
  TLRPC.Chat channel=new TLRPC.Chat();channel.id=7;
  org.telegram.messenger.ScamUsersManager.forChannelMessage(channelPost,channel);
  harness.start();drain();org.telegram.ui.Components.MangoScamUserWarning.dismiss();drain();
  check(harness.shownUsers.equals(Arrays.asList(7L,-7L)), "same-numbered user and channel each show warning");
 }

 public static void main(String[] args) {
  twoRiskSendersStillBothAppear();
  duplicateBindsCoalesce();
  recycledCellsAreScannedAtExecutionTime();
  listenerUpdateRetriesAfterEmptyList();
  pausedResumeRetriesAndStopCancels();
  reenterClearsPerVisitDedup();
  rejectedDialogDoesNotConsumeDedup();
  dismissRescansNextRiskSender();
  privateChatIdentityWorksWithoutMessages();
  outgoingPhotoAndStickerMessagesWork();
  channelIdentityAndNamespaceWork();
  System.out.println("PASS: 11 scam-hint behavioral scenarios, "
      + org.telegram.ui.Components.MangoScamUserWarning.showCalls + " warning attempts");
 }
}
'''


stubs = {
    "android/graphics/Rect.java": "package android.graphics; public final class Rect {}\n",
    "org/telegram/tgnet/TLRPC.java": """package org.telegram.tgnet;
public final class TLRPC { public static final class User { public long id; } public static final class Chat { public long id; public boolean megagroup; } }
""",
    "org/telegram/messenger/AndroidUtilities.java": """package org.telegram.messenger;
import java.util.*;
public final class AndroidUtilities {
 static final ArrayList<Runnable> delayed = new ArrayList<>();
 public static void runOnUIThread(Runnable task, long delay) { delayed.add(task); }
 public static void cancelRunOnUIThread(Runnable task) { delayed.removeIf(item -> item == task); }
 public static int pendingCount() { return delayed.size(); }
 public static void drain() { while (!delayed.isEmpty()) delayed.remove(0).run(); }
 public static void reset() { delayed.clear(); }
}
""",
    "org/telegram/messenger/ChatObject.java": "package org.telegram.messenger;public class ChatObject {public static boolean isChannelAndNotMegaGroup(org.telegram.tgnet.TLRPC.Chat chat){return chat!=null&&!chat.megagroup;}}",
    "org/telegram/messenger/ScamUsersManager.java": """package org.telegram.messenger;
import java.util.*;
import org.telegram.tgnet.TLRPC;
public final class ScamUsersManager {
 static final HashMap<Object,TLRPC.User> byMessage = new HashMap<>();
 static final HashMap<Long,TLRPC.User> byId = new HashMap<>();
 static final HashMap<Long,TLRPC.Chat> channels = new HashMap<>();
 static final HashMap<Object,TLRPC.Chat> channelMessages = new HashMap<>();
 public static TLRPC.Chat matchChannel(int account,long id) { return channels.get(id); }
 public static TLRPC.Chat matchChannelSender(int account,Object message) { return channelMessages.get(message); }
 public static void forChannel(TLRPC.Chat chat) { channels.put(chat.id,chat); }
 public static void forChannelMessage(Object message,TLRPC.Chat chat) { channelMessages.put(message,chat); }
 static final HashSet<Runnable> listeners = new HashSet<>();
 public static void addListener(Runnable listener) { listeners.add(listener); }
 public static void removeListener(Runnable listener) { listeners.remove(listener); }
 public static void refresh(int account, boolean force) {}
 public static TLRPC.User matchUser(int account, long id) { return byId.get(id); }
 public static TLRPC.User match(int account, Object message) { return byMessage.get(message); }
 public static void forMessage(Object message, TLRPC.User user) { byMessage.put(message, user); }
 public static void forUser(long id, TLRPC.User user) { byId.put(id, user); }
 public static void fireListeners() { for (Runnable listener : new ArrayList<>(listeners)) listener.run(); }
 public static int listenerCount() { return listeners.size(); }
 public static void reset() { byMessage.clear(); byId.clear(); channels.clear(); channelMessages.clear(); listeners.clear(); }
}
""",
    "org/telegram/messenger/ScamProtectionManager.java": """package org.telegram.messenger;
import java.util.*;
public final class ScamProtectionManager {
 static final HashSet<Runnable> listeners = new HashSet<>();
 public static void addListener(Runnable listener) { listeners.add(listener); }
 public static void removeListener(Runnable listener) { listeners.remove(listener); }
 public static void refreshIfNeeded(int account) {}
 public static String match(int account, Object message) { return null; }
 public static boolean markShown(int account, Object message, String keyword) { return true; }
 public static void reset() { listeners.clear(); }
}
""",
    "org/telegram/ui/Components/MangoScamUserWarning.java": """package org.telegram.ui.Components;
import org.telegram.tgnet.TLRPC;
public final class MangoScamUserWarning {
 public static boolean accept = true; public static int showCalls; static Runnable onDismiss;
 public static boolean show(Object activity, TLRPC.User user, Runnable dismissCallback) {
  showCalls++;
  if (accept && activity instanceof org.telegram.ui.ScamHintRegression.Harness) {
   ((org.telegram.ui.ScamHintRegression.Harness) activity).shownUsers.add(user.id);
   onDismiss = dismissCallback;
  }
  return accept;
 }
 public static boolean showChannel(Object activity, TLRPC.Chat chat, Runnable dismissCallback) {
  TLRPC.User key = new TLRPC.User(); key.id = -chat.id; return show(activity,key,dismissCallback);
 }
 public static void dismiss() { Runnable callback = onDismiss; onDismiss = null; if (callback != null) callback.run(); }
 public static void reset() { accept = true; showCalls = 0; onDismiss = null; }
}
""",
    "org/telegram/messenger/LocaleController.java": """package org.telegram.messenger;
public final class LocaleController { public static String formatString(int id, String value) { return value; } }
""",
    "org/telegram/ui/R.java": """package org.telegram.ui;
public final class R { public static final class raw { public static final int error = 1; } public static final class string { public static final int V4ScamRiskWarning = 2; } }
""",
    "org/telegram/ui/Components/BulletinFactory.java": """package org.telegram.ui.Components;
public final class BulletinFactory {
 public static BulletinFactory of(Object activity) { return new BulletinFactory(); }
 public Bulletin createSimpleBulletin(int icon, String text) { return new Bulletin(); }
 public static final class Bulletin { public void show() {} }
}
""",
}


with tempfile.TemporaryDirectory(prefix="scam-hint-tests-") as directory:
    base = Path(directory)
    sources = []
    for name, source in stubs.items():
        path = base / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source)
        sources.append(path)
    main = base / "org/telegram/ui/ScamHintRegression.java"
    main.parent.mkdir(parents=True, exist_ok=True)
    main.write_text(fixture)
    sources.append(main)
    classes = base / "classes"
    subprocess.run([str(JAVA / "javac"), "-d", str(classes)] + [str(path) for path in sources], check=True)
    subprocess.run([str(JAVA / "java"), "-cp", str(classes), "org.telegram.ui.ScamHintRegression"], check=True)
