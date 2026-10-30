#!/usr/bin/env python3
"""Run real calendar/date-jump methods with a small host-side navigation harness."""
import os
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui'
JAVA = Path(os.environ.get('JAVA_HOME', '/Applications/Android Studio.app/Contents/jbr/Contents/Home')) / 'bin'


def method(source, signature):
    start = source.index(signature)
    opening = source.index('{', start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


chat = (UI / 'ChatActivity.java').read_text()
calendar = (UI / 'CalendarActivity.java').read_text()
date_method = method(calendar, 'static ArrayList<Integer> historyMonthDates(')
select_method = method(calendar, 'private void selectHistoryDate(')
jump_method = method(chat, 'public void jumpToDate(')
open_method = method(chat, 'private void showChatHistoryCalendar(')

source = r'''
import java.util.*;
public class HistoryCalendarTest {
    static int checks;
    static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    static long at(int year, int month, int day) { Calendar c=Calendar.getInstance();c.clear();c.set(year,month,day);return c.getTimeInMillis(); }
    static class MessagesStorage { interface IntCallback { void run(int date); } }
    static class DateHarness {
        int minDate, selected, calls; boolean finished;
        MessagesStorage.IntCallback historyDateCallback = d -> { selected=d;calls++; };
        void finishFragment() { finished=true;historyDateCallback=null; }
        DATE_METHOD
        SELECT_METHOD
    }
    static class TLRPC { static class Message { int date; Message(int d){date=d;} } }
    static class MessageObject {
        TLRPC.Message messageOwner; int id; long dialog; boolean isDateObject;
        MessageObject(int id,int date,long d){this.id=id;messageOwner=new TLRPC.Message(date);dialog=d;}
        int getId(){return id;} long getDialogId(){return dialog;}
    }
    static class DialogObject { static boolean isEncryptedDialog(long d){return d==Long.MIN_VALUE;} }
    static class Layout { int first=0,last=-1;int findFirstVisibleItemPosition(){return first;}int findLastVisibleItemPosition(){return last;} }
    static class Adapter {int messagesStartRow=0,messagesEndRow=0;}
    static class RecyclerAnimationScrollHelper {static final int SCROLL_DIRECTION_UNSET=0,SCROLL_DIRECTION_DOWN=1,SCROLL_DIRECTION_UP=2;void setScrollDirection(int d){} }
    static class AlertDialog {static final int ALERT_TYPE_SPINNER=1; AlertDialog(Object a,int t,Object p){} void dismiss(){}void setOnCancelListener(Object o){}void showDelayed(int d){} }
    static class View { void setAlpha(float f){}void setTag(Object o){} }
    static class Controller { Object[] args;void loadMessages(Object... a){args=a;} }
    static class Config {long owner=808;long getClientUserId(){return owner;} }
    static class Bundle extends HashMap<String,Long> {void putLong(String k,long v){put(k,v);}void putInt(String k,int v){put(k,(long)v);} }
    static class SharedMediaLayout {static final int FILTER_PHOTOS_AND_VIDEOS=0;}
    static class CalendarActivity {
        static final int TYPE_CHAT_HISTORY=4; int account,date; Bundle args; MessagesStorage.IntCallback callback;
        CalendarActivity(Bundle a,int f,int d){args=a;date=d;}void setCurrentAccount(int a){account=a;}void setHistoryDateCallback(MessagesStorage.IntCallback cb){callback=cb;}
    }
    static class ChatHarness {
        ArrayList<MessageObject> messages=new ArrayList<>();boolean[] endReached={false,false};
        Layout chatLayoutManager=new Layout();Adapter chatAdapter=new Adapter();
        long dialog_id=-500,mergeDialogId=-400,threadMessageId=77;int currentAccount=2,chatMode=0,classGuid=99,replyMaxReadId=11,lastLoadIndex=3;
        boolean isTopic=true,finishing=false,attached=true;Object currentEncryptedChat;
        RecyclerAnimationScrollHelper chatScrollHelper=new RecyclerAnimationScrollHelper();AlertDialog progressDialog;
        Object themeDelegate,postponedScrollCancelListener;int postponedScrollToLastMessageQueryIndex,postponedScrollMessageId;
        boolean fakePostponedScroll,postponedScrollIsCanceled;ArrayList<Integer> waitingForLoad=new ArrayList<>();
        View floatingDateView=new View(),floatingTopicSeparator=new View();float floatingTopicViewAlpha;
        Controller controller=new Controller();Config config=new Config();CalendarActivity shown;int scrolled,merged;
        Object getParentActivity(){return attached?this:null;}boolean isFinishing(){return finishing;}
        boolean isSecretChat(){return currentEncryptedChat!=null;}void updatePinnedListButton(boolean b){}void updateFloatingTopicView(){}
        Controller getMessagesController(){return controller;}Config getUserConfig(){return config;}long getTopicId(){return threadMessageId;}
        void presentFragment(CalendarActivity c){shown=c;}
        void scrollToMessageId(int id,int a,boolean b,int m,boolean c,int d){scrolled=id;merged=m;}
        JUMP_METHOD
        OPEN_METHOD
    }
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        ArrayList<Integer> leap=DateHarness.historyMonthDates(2024,Calendar.FEBRUARY,at(2024,Calendar.MARCH,1));
        check(leap.size()==29,"leap February contains every day");
        check(DateHarness.historyMonthDates(2023,Calendar.FEBRUARY,at(2023,Calendar.MARCH,1)).size()==28,"ordinary February");
        check(DateHarness.historyMonthDates(2024,Calendar.APRIL,at(2024,Calendar.MAY,1)).size()==30,"short month");
        check(DateHarness.historyMonthDates(2026,Calendar.OCTOBER,at(2026,Calendar.OCTOBER,16)+3600000).size()==16,"future days disabled");
        check(DateHarness.historyMonthDates(2026,Calendar.NOVEMBER,at(2026,Calendar.OCTOBER,16)).isEmpty(),"future month disabled");
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        ArrayList<Integer> spring=DateHarness.historyMonthDates(2024,Calendar.MARCH,at(2024,Calendar.APRIL,1));
        check(spring.size()==31 && spring.get(10)-spring.get(9)==23*3600,"spring DST keeps local midnights");
        ArrayList<Integer> autumn=DateHarness.historyMonthDates(2024,Calendar.NOVEMBER,at(2024,Calendar.DECEMBER,1));
        check(autumn.size()==30 && autumn.get(3)-autumn.get(2)==25*3600,"autumn DST keeps local midnights");
        for(int i=0;i<spring.size();i++){Calendar c=Calendar.getInstance();c.setTimeInMillis(spring.get(i)*1000L);if(c.get(Calendar.DAY_OF_MONTH)!=i+1||c.get(Calendar.HOUR_OF_DAY)!=0)throw new AssertionError("DST repeated/skipped date");}
        checks++;
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
        ArrayList<Integer> midnightGap=DateHarness.historyMonthDates(2018,Calendar.NOVEMBER,at(2018,Calendar.DECEMBER,1));
        check(midnightGap.size()==30&&midnightGap.get(4)==at(2018,Calendar.NOVEMBER,5)/1000L,"midnight DST gap does not shift later days");
        DateHarness d=new DateHarness();d.minDate=100;d.selectHistoryDate(99);check(d.calls==0&&!d.finished,"minimum enforced");
        d.selectHistoryDate((int)(System.currentTimeMillis()/1000L)+86400);check(d.calls==0,"future rejected");
        d.selectHistoryDate(500);check(d.calls==1&&d.selected==500&&d.finished,"date selection survives closing lifecycle");
        d.selectHistoryDate(600);check(d.calls==1,"double tap cannot navigate twice");
        ChatHarness c=new ChatHarness();c.jumpToDate(1000);
        check(c.controller.args!=null,"empty initial history still requests selected date");
        check(c.controller.args[0].equals(-500L)&&c.controller.args[5].equals(1000),"date request targets current dialog");
        check(c.controller.args[11].equals(0)&&c.controller.args[12].equals(77L)&&c.controller.args[15].equals(true),"topic preserved");
        check(c.controller.args[9].equals(4),"full history date load");
        c=new ChatHarness();c.chatMode=3;c.threadMessageId=700;c.isTopic=false;c.jumpToDate(1000);
        check(c.controller.args[11].equals(3)&&c.controller.args[12].equals(700L),"saved-message context preserved");
        c=new ChatHarness();c.messages.add(new MessageObject(20,2000,-500));c.messages.add(new MessageObject(10,1000,-400));c.jumpToDate(1000);
        check(c.scrolled==10&&c.merged==1&&c.controller.args==null,"cached selected-day message and merged group retained");
        c=new ChatHarness();c.messages.add(new MessageObject(20,2000,-500));c.messages.add(new MessageObject(10,1000,-500));c.endReached[0]=true;c.jumpToDate(10);
        check(c.scrolled==10,"date before history selects first available message");
        c=new ChatHarness();c.attached=false;c.jumpToDate(1000);check(c.controller.args==null,"detached chat cannot request");
        c=new ChatHarness();c.showChatHistoryCalendar(1200);check(c.shown.account==2&&c.shown.args.get("dialog_id")==-500&&c.shown.args.get("topic_id")==77,"calendar account/dialog/topic binding");
        check(c.shown.date==1200&&c.shown.args.get("type")==CalendarActivity.TYPE_CHAT_HISTORY,"history calendar selected month");
        c.shown.callback.run(1000);check(c.controller.args!=null,"calendar selection opens real date history");
        c=new ChatHarness();c.showChatHistoryCalendar(0);c.config.owner=900;c.shown.callback.run(1000);check(c.controller.args==null,"reused account slot rejects old callback");
        c=new ChatHarness();c.showChatHistoryCalendar(0);c.currentAccount=1;c.shown.callback.run(1000);check(c.controller.args==null,"changed account rejects old callback");
        c=new ChatHarness();c.showChatHistoryCalendar(0);c.finishing=true;c.shown.callback.run(1000);check(c.controller.args==null,"closed chat rejects callback");
        c=new ChatHarness();MessageObject date=new MessageObject(0,600,-500);date.isDateObject=true;c.messages.add(date);c.messages.add(new MessageObject(2,700,-500));c.chatLayoutManager.last=1;c.showChatHistoryCalendar(0);check(c.shown.date==700,"starts at visible real message date");
        System.out.println("History calendar: "+checks+" behavior assertions passed");
    }
}
'''
source = source.replace('DATE_METHOD', date_method).replace('SELECT_METHOD', select_method).replace('JUMP_METHOD', jump_method).replace('OPEN_METHOD', open_method)
with tempfile.TemporaryDirectory(prefix='mango-history-calendar-') as folder:
    base = Path(folder)
    file = base / 'HistoryCalendarTest.java'
    file.write_text(source)
    subprocess.run([str(JAVA / 'javac'), '-d', str(base), str(file)], check=True)
    subprocess.run([str(JAVA / 'java'), '-cp', str(base), 'HistoryCalendarTest'], check=True)

# Guard the integration routes that would silently regress to media-only dates.
assert 'calendarType == TYPE_CHAT_HISTORY || loading || endReached' in method(calendar, 'private void loadNext(')
assert 'calendarType != TYPE_CHAT_HISTORY' in method(calendar, 'public boolean needDelayOpenAnimation(')
assert 'currentEncryptedChat == null && chatMode == MODE_DEFAULT' in chat
assert 'headerItem.lazilyAddSubItem(chat_jump_to_date,' in chat
for folder in ('values', 'values-zh'):
    names = {node.attrib['name'] for node in ET.parse(ROOT / f'TMessagesProj/src/main/res/{folder}/v45_calendar.xml').getroot()}
    assert {'MangoHistoryCalendar', 'MangoHistoryCalendarHint'} <= names
print('History calendar: 6 integration/resource checks passed')
