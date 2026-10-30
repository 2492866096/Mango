package org.telegram.messenger;
import java.util.*;
import java.net.*;
import java.io.*;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.ConnectionsManager;

public class V42AutomationTest {
    static int assertions, requests;
    static String body="user_test/1234";
    static int status=200;
    static void check(boolean value,String label){assertions++;if(!value)throw new AssertionError(label);}
    static void invalid(String s) throws Exception {try{RemoteScamUsers.parse(s);throw new AssertionError("accepted invalid list: "+s);}catch(IOException expected){assertions++;}}
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
        URL.setURLStreamHandlerFactory(protocol -> protocol.equals("https") ? new URLStreamHandler(){protected URLConnection openConnection(URL url){return new HttpURLConnection(url){public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}public int getResponseCode(){requests++;return status;}public String getContentType(){return "text/plain; charset=utf-8";}public InputStream getInputStream(){return new ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));}};}}:null);
        RemoteScamUsers.Entries e=RemoteScamUsers.parse("\uFEFF# comment\n@Test_User/1234\nsecond_user\n5678\n/999\n");
        check(e.size==4 && e.contains(1234,"changed") && e.contains(5678,null) && e.contains(999,null),"ID independent of username");
        check(e.contains(77,"TEST_USER") && e.contains(88,"SECOND_USER"),"exact case insensitive username");
        check(!e.contains(77,"test_user_extra") && !e.contains(12345,null),"no substring matches");
        RemoteScamUsers.Entries simple=RemoteScamUsers.parse("7637374698\r\n@didb431\nother_name\n");
        check(simple.size==3 && simple.contains(7637374698L,null) && simple.contains(1,"DIDB431") && simple.contains(2,"other_name"),"one ID or username per line");
        check(RemoteScamUsers.parse("").size==0,"empty list clears entries");
        for(String bad:new String[]{"<html>error</html>","a/b/c","user/-1","user/0","user/99999999999999999999999","/","@bad name/1"})invalid(bad);
        check(RemoteScamUsers.validUrl("https://example.test/list.txt") && !RemoteScamUsers.validUrl("http://example.test/list.txt") && !RemoteScamUsers.validUrl("https://user:pass@example.test/a"),"URL validation");
        UserConfig.getInstance(0).owner=42;
        check(ScamUsersManager.DEFAULT_SOURCE.equals(ScamUsersManager.source(0)) && ScamUsersManager.enabled(0),"new accounts use enabled built-in list source");
        ScamUsersManager.setEnabled(0,false);
        check(!ScamUsersManager.enabled(0),"default-enabled list can be disabled");
        ScamUsersManager.setSource(0,"https://example.test/list"); DispatchQueue.drain();
        check(ScamUsersManager.source(0).equals("https://example.test/list"),"custom source retained");
        check(ScamUsersManager.count(0)==1 && !ScamUsersManager.failed(0),"fetch and cache");
        check(ScamUsersManager.match(0,message(1))==null,"disabled matching");
        check(ScamUsersManager.matchUser(0,1234)==null,"disabled peer matching");
        MangoSettings.setBoolean(0,"scam_users_enabled",true);
        check(ScamUsersManager.match(0,message(1)).id==1234,"match ID when user not cached");
        check(ScamUsersManager.matchUser(0,1234).id==1234,"empty private chat matches peer without a message");
        check(ScamUsersManager.matchUser(0,1234).id==1234,"reopening a chat does not consume account-wide match state");
        check(ScamUsersManager.matchUser(0,0)==null && ScamUsersManager.matchUser(0,-1234)==null && ScamUsersManager.matchUser(0,42)==null,"non-user peers and own account are excluded");
        check(ScamUsersManager.match(0,null)==null,"null message does not match");
        MessageObject missingOwner=message(1);missingOwner.messageOwner=null;
        check(ScamUsersManager.match(0,missingOwner)==null,"missing message owner does not match");
        MessageObject privateMessage=message(1);privateMessage.messageOwner.from_id=null;privateMessage.dialog=1234;
        check(ScamUsersManager.match(0,privateMessage).id==1234,"private messages without explicit sender use peer ID");
        MessageObject channelPost=message(1);channelPost.messageOwner.from_id.user_id=0;channelPost.messageOwner.from_id.channel_id=1234;channelPost.dialog=1234;
        check(ScamUsersManager.match(0,channelPost)==null,"channel authors do not fall back to a user peer with the same ID");
        MessageObject ownMessage=message(1);ownMessage.out=true;
        check(ScamUsersManager.match(0,ownMessage)==null,"own outgoing messages are not matched as risky senders");
        MessageObject savedMessage=message(1);savedMessage.dialog=42;
        check(ScamUsersManager.match(0,savedMessage)==null,"saved messages do not warn about original senders");
        TLRPC.User emptyUser=new TLRPC.TL_userEmpty();emptyUser.id=1234;MessagesController.users.put(1234L,emptyUser);
        check(ScamUsersManager.matchUser(0,1234).id==1234 && !(ScamUsersManager.matchUser(0,1234) instanceof TLRPC.TL_userEmpty),"ID matching survives incomplete Telegram cache");
        MessagesController.users.remove(1234L);
        int previous=requests;ScamUsersManager.refresh(0,false);DispatchQueue.drain();check(requests==previous,"fresh cache avoids fetch");
        body="invalid/html/response";ScamUsersManager.refresh(0,true);DispatchQueue.drain();check(ScamUsersManager.failed(0)&&ScamUsersManager.count(0)==1,"invalid response retains last list");
        status=503;ScamUsersManager.refresh(0,true);DispatchQueue.drain();check(ScamUsersManager.count(0)==1,"HTTP failure retains cache");status=200;
        body="";ScamUsersManager.refresh(0,true);DispatchQueue.drain();check(ScamUsersManager.count(0)==0,"published empty list clears cache");
        body="another/222";ScamUsersManager.setSource(0,"https://example.test/next");UserConfig.getInstance(0).owner=43;DispatchQueue.drain();
        check(ScamUsersManager.count(0)==0,"in-flight response cannot contaminate reused account slot");UserConfig.getInstance(0).owner=42;
        ScamUsersManager.setSource(0,"");check(ScamUsersManager.count(0)==0 && ScamUsersManager.source(0).isEmpty(),"clearing source clears active list without default fallback");
        body="alias_name";ScamUsersManager.setSource(0,"https://example.test/aliases");DispatchQueue.drain();
        TLRPC.User user=new TLRPC.TL_user();user.id=1234;user.username="primary";TLRPC.TL_username alias=new TLRPC.TL_username();alias.username="ALIAS_NAME";alias.active=true;user.usernames.add(alias);MessagesController.users.put(1234L,user);
        check(ScamUsersManager.match(0,message(2))!=null,"active alternate usernames supported");alias.active=false;check(ScamUsersManager.match(0,message(2))==null,"inactive username not matched");
        body="@adggnbkefu\n@Xiaoyannb666666\n9876";ScamUsersManager.setSource(0,"https://example.test/customer-list");DispatchQueue.drain();
        user.username="ADGGNBKEFU";user.usernames.add(null);
        TLRPC.User second=new TLRPC.TL_user();second.id=5678;second.username="xiaoyannb666666";second.usernames=null;MessagesController.users.put(second.id,second);
        check(ScamUsersManager.count(0)==3,"all customer list rows are loaded");
        check(ScamUsersManager.matchUser(0,1234)==user && ScamUsersManager.matchUser(0,5678)==second && ScamUsersManager.matchUser(0,9876).id==9876,"both reported usernames and later ID match independently");
        check(ScamUsersManager.matchUser(0,1234)==user && ScamUsersManager.matchUser(0,5678)==second,"matching earlier users does not suppress later opens");
        second.username="xiaoyannb666666_suffix";
        check(ScamUsersManager.matchUser(0,5678)==null,"username prefixes do not falsely match");
        second.username="unlisted";second.usernames=new ArrayList<>();second.usernames.add(null);TLRPC.TL_username secondAlias=new TLRPC.TL_username();secondAlias.active=true;secondAlias.username="XIAOYANNB666666";second.usernames.add(secondAlias);
        check(ScamUsersManager.matchUser(0,5678)==second,"secondary username after null entries matches");
        body="@new_username";ScamUsersManager.refresh(0,true);DispatchQueue.drain();
        check(ScamUsersManager.matchUser(0,1234)==null && ScamUsersManager.matchUser(0,5678)==null,"removed names stop matching after refresh");
        second.username="new_username";
        check(ScamUsersManager.matchUser(0,5678)==second,"newly added names match without restarting the app");
        RemoteScamUsers.Entries channels=RemoteScamUsers.parse("-1001234567890\nchannel:987654321\n@risk_channel/-1005678\n@alias_channel\n1234567890");
        check(channels.containsChannel(1234567890L,null)&&channels.containsChannel(987654321L,null)&&channels.containsChannel(5678,null),"channel IDs parsed in explicit namespaces");
        check(channels.contains(1234567890L,null)&&!channels.contains(987654321L,null)&&!channels.containsChannel(42,null),"user and channel numeric IDs remain separate");
        for(String bad:new String[]{"channel:0","channel:-12","channel:","-1000","-100999999999999999999999","user/channel:-1"})invalid(bad);
        body="1234\n-1003456\n@risk_channel\n@alias_channel";ScamUsersManager.setSource(0,"https://example.test/channels");DispatchQueue.drain();
        TLRPC.Chat riskyChannel=new TLRPC.TL_channel();riskyChannel.id=3456;riskyChannel.username="ordinary";MessagesController.chats.put(3456L,riskyChannel);
        check(ScamUsersManager.matchChannel(0,3456)==riskyChannel,"channel ID match");
        TLRPC.Chat userSameId=new TLRPC.TL_channel();userSameId.id=1234;MessagesController.chats.put(1234L,userSameId);
        check(ScamUsersManager.matchChannel(0,1234)==null,"positive user ID never flags same-numbered channel");
        riskyChannel.id=7890;riskyChannel.username="RISK_CHANNEL";MessagesController.chats.put(7890L,riskyChannel);
        check(ScamUsersManager.matchChannel(0,7890)==riskyChannel,"channel username case insensitive exact match");
        riskyChannel.username="risk_channel_suffix";
        check(ScamUsersManager.matchChannel(0,7890)==null,"channel username prefix is not a match");
        TLRPC.TL_username channelAlias=new TLRPC.TL_username();channelAlias.username="ALIAS_CHANNEL";channelAlias.active=true;riskyChannel.usernames.add(null);riskyChannel.usernames.add(channelAlias);
        check(ScamUsersManager.matchChannel(0,7890)==riskyChannel,"channel active alias matches after null alias");channelAlias.active=false;
        check(ScamUsersManager.matchChannel(0,7890)==null,"inactive channel alias ignored");channelAlias.active=true;
        MessageObject asChannel=message(1);asChannel.messageOwner.from_id.user_id=0;asChannel.messageOwner.from_id.channel_id=7890;
        check(ScamUsersManager.matchChannelSender(0,asChannel)==riskyChannel,"send-as-channel messages match sender");asChannel.out=true;
        check(ScamUsersManager.matchChannelSender(0,asChannel)==null,"own outgoing channel message is ignored");asChannel.out=false;
        riskyChannel.megagroup=true;check(ScamUsersManager.matchChannel(0,7890)==null,"groups are not mistaken for broadcast channels");riskyChannel.megagroup=false;
        ScamUsersManager.setEnabled(0,false);check(ScamUsersManager.matchChannel(0,7890)==null,"disabled channel matching");ScamUsersManager.setEnabled(0,true);
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
        System.out.println("V4.2–4.6 automation and risk-list tests passed: "+assertions+" assertions");
    }
}
