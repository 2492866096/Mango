package org.telegram.messenger;
import java.util.*;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.ConnectionsManager;

public class V42AutomationTest {
    static int assertions;
    static void check(boolean value,String label){assertions++;if(!value)throw new AssertionError(label);}
    static MessageObject message(int id) {MessageObject m=new MessageObject();m.id=id;m.messageOwner.from_id=new TLRPC.Peer();m.messageOwner.from_id.user_id=1234;return m;}
    static void queue(int account,MessageObject... list){MangoAutoReadManager.process(account,new ArrayList<>(Arrays.asList(list)));}
    static void flush(){ArrayList<AndroidUtilities.Delayed> pending=new ArrayList<>(AndroidUtilities.delayed);AndroidUtilities.delayed.clear();for(AndroidUtilities.Delayed d:pending)d.runnable.run();}
    static void auto(MessageObject... list){auto(0,list);}
    static void auto(int account,MessageObject... list){queue(account,list);flush();}
    static MessageObject scopedMessage(int id,long dialog){MessageObject m=message(id);m.dialog=dialog;m.messageOwner.date=ConnectionsManager.now;return m;}
    static void actions(String label,String readTarget,String sendTarget){
        check(MessagesController.readTargets.equals(readTarget==null?Collections.emptyList():Collections.singletonList(readTarget)),label+" read targets: "+MessagesController.readTargets);
        check(SendMessagesHelper.sentTargets.equals(sendTarget==null?Collections.emptyList():Collections.singletonList(sendTarget)),label+" send targets: "+SendMessagesHelper.sentTargets);
        MessagesController.readTargets.clear();SendMessagesHelper.sentTargets.clear();
    }
    static void replyGroupScope(){
        MessagesController.readTargets.clear();SendMessagesHelper.sentTargets.clear();
        ConnectionsManager.now=2000;
        MangoSettings.setBoolean(0,"auto_read",false);
        MangoSettings.setBoolean(0,"auto_read_random_reply",true);
        TLRPC.Chat group=new TLRPC.Chat();group.id=6101;MessagesController.chats.put(group.id,group);
        TLRPC.Chat mega=new TLRPC.TL_channel();mega.id=6102;mega.megagroup=true;MessagesController.chats.put(mega.id,mega);
        TLRPC.Chat broadcast=new TLRPC.TL_channel();broadcast.id=6103;MessagesController.chats.put(broadcast.id,broadcast);
        TLRPC.Chat other=new TLRPC.Chat();other.id=6104;MessagesController.chats.put(other.id,other);
        TLRPC.User bot=new TLRPC.TL_user();bot.id=6500;bot.bot=true;MessagesController.users.put(bot.id,bot);

        check(MangoAutoReadManager.replyGroups(0).isEmpty(),"upgrading an enabled reply switch does not select groups");
        auto(scopedMessage(100,-group.id),scopedMessage(100,-mega.id),scopedMessage(100,-broadcast.id));
        actions("default reply scope excludes all groups and channels",null,null);
        auto(scopedMessage(100,500));actions("default private reply remains enabled","0:500","0:500");

        check(MangoAutoReadManager.setReplyGroup(0,-group.id,true),"select ordinary group");
        check(MangoAutoReadManager.setReplyGroup(0,-group.id,true)&&MangoAutoReadManager.replyGroups(0).size()==1,"selecting twice does not duplicate group");
        auto(scopedMessage(100,-group.id));actions("selected group reads and replies without consuming previously excluded messages","0:-6101","0:-6101");
        auto(scopedMessage(101,-mega.id));actions("selection does not opt in another group",null,null);
        check(MangoAutoReadManager.setReplyGroup(0,-mega.id,true),"select supergroup");
        auto(scopedMessage(101,-mega.id));actions("selected supergroup reads and replies","0:-6102","0:-6102");

        ConnectionsManager.now+=4;
        queue(0,scopedMessage(102,-group.id));
        check(AndroidUtilities.delayed.stream().anyMatch(task->task.delay==250),"incoming automation is deferred before evaluating current scope");
        check(MangoAutoReadManager.setReplyGroup(0,-group.id,false),"remove selected group");
        flush();actions("removing group cancels already queued read and reply",null,null);
        auto(scopedMessage(103,-group.id));actions("removed group remains excluded for subsequent messages",null,null);
        check(MangoAutoReadManager.setReplyGroup(0,-group.id,true),"reselect removed group");
        auto(scopedMessage(102,-group.id));actions("canceled queued message was not marked processed","0:-6101","0:-6101");

        for(long invalid:new long[]{0,500,bot.id,42,-broadcast.id,-99999,0x4000000000000001L}){
            check(!MangoAutoReadManager.setReplyGroup(0,invalid,true),"non-group or unavailable target cannot enter reply group selection: "+invalid);
        }
        check(MangoAutoReadManager.replyGroups(0).equals(new HashSet<>(Arrays.asList(-group.id,-mega.id))),"invalid selections leave only explicitly chosen groups");
        mega.canSend=false;
        auto(scopedMessage(104,-mega.id));actions("selected group loses send permission and is skipped",null,null);
        mega.canSend=true;mega.megagroup=false;
        auto(scopedMessage(104,-mega.id));actions("selected target becoming a broadcast channel is skipped",null,null);
        mega.megagroup=true;

        MangoSettings.setBoolean(0,"auto_read",true);
        auto(scopedMessage(105,-other.id));actions("independent read switch reads unselected groups without reply","0:-6104",null);
        auto(scopedMessage(105,-broadcast.id));actions("independent read switch reads channels without reply","0:-6103",null);
        MangoSettings.setBoolean(0,"auto_read_random_reply",false);
        auto(scopedMessage(105,-group.id));actions("independent read remains active with reply disabled","0:-6101",null);
        MangoSettings.setBoolean(0,"auto_read",false);
        MangoSettings.setBoolean(0,"auto_read_random_reply",true);

        UserConfig.getInstance(1).owner=142;
        MangoSettings.setBoolean(1,"auto_read_random_reply",true);
        check(MangoAutoReadManager.replyGroups(1).isEmpty(),"another account does not inherit selected groups");
        auto(1,scopedMessage(106,-group.id));actions("unselected account does not read or reply in selected account's group",null,null);
        check(MangoAutoReadManager.setReplyGroup(1,-mega.id,true),"second account independently selects group");
        check(MangoAutoReadManager.setReplyGroup(0,-mega.id,false),"first account independently removes group");
        auto(1,scopedMessage(106,-mega.id));actions("second account sends from its own account","1:-6102","1:-6102");
        auto(0,scopedMessage(106,-mega.id));actions("second account selection does not leak into first account",null,null);
        ConnectionsManager.now+=4;
        auto(0,scopedMessage(107,500));actions("first account private message reads and replies","0:500","0:500");
        auto(1,scopedMessage(107,500));actions("identical message ID in second account is not duplicate-suppressed","1:500","1:500");

        queue(0,scopedMessage(108,-group.id));
        UserConfig.getInstance(0).owner=43;
        check(MangoAutoReadManager.replyGroups(0).isEmpty(),"replacement account in reused slot has no selected groups");
        check(!MangoSettings.getBoolean(0,"auto_read_random_reply"),"replacement account does not inherit reply switch");
        MangoSettings.setBoolean(0,"auto_read_random_reply",true);
        check(MangoAutoReadManager.setReplyGroup(0,-group.id,true),"replacement account may independently choose same group");
        flush();actions("queued callback cannot act for replacement owner even when its switches and group are enabled",null,null);
        auto(0,scopedMessage(108,-group.id));actions("replacement owner's fresh callback uses its own state","0:-6101","0:-6101");
        UserConfig.getInstance(0).owner=42;
        check(MangoAutoReadManager.replyGroups(0).equals(Collections.singleton(-group.id)),"original owner's group selection survives slot changes");
        auto(0,scopedMessage(108,-group.id));actions("replacement owner does not consume original owner's seen state","0:-6101","0:-6101");

        ConnectionsManager.now+=4;
        queue(0,scopedMessage(109,-group.id),scopedMessage(109,500));
        MangoSettings.setBoolean(0,"auto_read_random_reply",false);
        flush();actions("turning main reply switch off cancels queued group and private actions",null,null);
        auto(0,scopedMessage(110,-group.id),scopedMessage(110,500));
        actions("disabled reply switch leaves selected groups and private chats untouched",null,null);
    }
    public static void main(String[] args)throws Exception {
        UserConfig.getInstance(0).owner=42;
        TLRPC.User privatePeer=new TLRPC.TL_user();privatePeer.id=500;MessagesController.users.put(privatePeer.id,privatePeer);
        auto(message(1));check(MessagesController.reads==0&&SendMessagesHelper.sends==0,"automations default off");
        MangoSettings.setBoolean(0,"auto_read",true);auto(message(1));check(MessagesController.reads==1&&SendMessagesHelper.sends==0,"read only");
        auto(message(1));check(MessagesController.reads==1,"duplicate update suppressed");
        MangoSettings.setBoolean(0,"auto_read_random_reply",true);auto(message(2));check(MessagesController.reads==2&&SendMessagesHelper.sends==1,"random reply includes read");
        check(SendMessagesHelper.last.matches("[\\p{IsHan}]{8}"),"reply exactly eight Han characters");
        auto(message(2));check(SendMessagesHelper.sends==1,"duplicate reply suppressed");
        auto(message(3));check(MessagesController.reads==3&&SendMessagesHelper.sends==1,"burst reads still happen with reply cooldown");
        ApplicationLoader.mainInterfacePaused=true;auto(message(4));check(MessagesController.reads==3,"background skipped");ApplicationLoader.mainInterfacePaused=false;
        DailyMessageTasksManager.connected=false;auto(message(4));check(MessagesController.reads==3,"offline skipped");DailyMessageTasksManager.connected=true;
        MessageObject out=message(5);out.out=true;MessageObject stale=message(6);stale.messageOwner.date=900;MessageObject service=message(7);service.messageOwner.action=new TLRPC.MessageAction();MessageObject read=message(8);read.unread=false;
        auto(out,stale,service,read);check(MessagesController.reads==3,"outgoing, history, service, read messages excluded");
        ConnectionsManager.now=1004;MessagesController.paid=1;auto(message(9));check(MessagesController.reads==4&&SendMessagesHelper.sends==1,"paid target read without automatic paid send");MessagesController.paid=0;
        MangoSettings.setBoolean(0,"silent_send_default",true);MessageObject topic=message(10);topic.messageOwner.reply_to=new TLRPC.Reply();topic.messageOwner.reply_to.forum_topic=true;topic.messageOwner.reply_to.reply_to_top_id=100;
        auto(topic);check(MessagesController.topic==100&&SendMessagesHelper.sends==2&&!SendMessagesHelper.notify,"topic and silent default respected");
        ApplicationLoader.applicationContext.failCommit=true;auto(message(11));check(MessagesController.reads==5,"storage failure stops side effects");ApplicationLoader.applicationContext.failCommit=false;
        UserConfig.getInstance(0).owner=44;auto(message(12));check(MessagesController.reads==5,"new account owner does not inherit automation");UserConfig.getInstance(0).owner=42;
        check(MangoNotificationRules.allows(0,false,"","")&&!MangoNotificationRules.allows(1,false,"word","word"),"mentions mode independent of keywords");
        check(MangoNotificationRules.allows(2,false,"REWARD","reward")&&!MangoNotificationRules.allows(2,true,"plain","reward"),"keyword mode independent of mentions");
        MangoNotificationRules.setMode(0,0,1);check(MangoNotificationRules.mode(0,500)==1,"conversation inherits global mode");
        MangoNotificationRules.setMode(0,500,0);check(MangoNotificationRules.mode(0,500)==0,"explicit all override");
        MangoNotificationRules.setMode(0,500,-1);MessageObject mention=message(20);mention.messageOwner.message="hello @MY_NAME";
        check(MangoNotificationRules.shouldNotify(0,mention),"exact username mention detected");mention.messageOwner.message="hello @my_name_suffix";check(!MangoNotificationRules.shouldNotify(0,mention),"username prefix is not mention");
        replyGroupScope();
        System.out.println("V4.2–4.6 automation tests passed: "+assertions+" assertions");
    }
}
