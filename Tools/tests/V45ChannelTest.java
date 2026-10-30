package org.telegram.messenger;
import java.util.*;
import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import android.os.SystemClock;
import android.content.Context;
import org.telegram.tgnet.TLRPC;

public final class V45ChannelTest {
 static int checks,requests,status=200;
 static String body="@first_channel",contentType="text/plain";
 static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 static void invalid(String value)throws Exception{try{MangoAutoJoinChannels.parse(value);throw new AssertionError("accepted: "+value);}catch(IOException expected){checks++;}}
 static Map<?,?> states()throws Exception{Field f=MangoAutoJoinChannels.class.getDeclaredField("states");f.setAccessible(true);return (Map<?,?>)f.get(null);}
 static void setState(String field,long value)throws Exception{for(Object state:states().values()){Field f=state.getClass().getDeclaredField(field);f.setAccessible(true);f.setLong(state,value);}}
 static void reset()throws Exception{
  states().clear();ApplicationLoader.applicationContext=new Context();SystemClock.now=1000000;
  requests=0;status=200;body="@first_channel";contentType="text/plain";DispatchQueue.pending.clear();AndroidUtilities.delayed.clear();
  for(int i=0;i<3;i++){UserConfig.getInstance(i).owner=0;MessagesController.getInstance(i).reset();}
 }
 static TLRPC.Chat channel(int account,long id,String username,boolean left){TLRPC.Chat chat=new TLRPC.Chat();chat.id=id;chat.username=username;chat.left=left;MessagesController.getInstance(account).chats.put(id,chat);return chat;}
 static void tick(){MangoAutoJoinChannels.tick();DispatchQueue.drain();}
 static void expire()throws Exception{setState("updated",System.currentTimeMillis()-MangoAutoJoinChannels.REFRESH_INTERVAL_MS-1);SystemClock.now+=MangoAutoJoinChannels.REFRESH_INTERVAL_MS;}
 public static void main(String[] args)throws Exception{
  URL.setURLStreamHandlerFactory(protocol->protocol.equals("https")?new URLStreamHandler(){protected URLConnection openConnection(URL url){return new HttpURLConnection(url){public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}public int getResponseCode(){requests++;return status;}public String getContentType(){return contentType;}public InputStream getInputStream(){return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));}};}}:null);
  check(MangoAutoJoinChannels.SOURCE_URL.endsWith("/GY/MG.txt"),"exact MG feed without fragment");
  check(MangoAutoJoinChannels.parse("\uFEFF # comment\n@First_Channel\nhttps://t.me/second_channel#\nhttps://telegram.me/third_channel?foo=1\nFIRST_CHANNEL").equals(Arrays.asList("first_channel","second_channel","third_channel")),"BOM links comments and case-insensitive dedupe");
  check(MangoAutoJoinChannels.parse("").isEmpty(),"empty published feed clears joins");
  for(String bad:new String[]{"https://evil.test/channel","https://t.me/+privateHash","https://t.me/channel/123","<html>oops</html>","user name","123456", "@invalid-name"})invalid(bad);
  reset();tick();check(requests==0,"no activated account means no request");
  UserConfig.getInstance(0).owner=42;channel(0,100,"first_channel",true);tick();
  check(requests==1&&MessagesController.getInstance(0).joined.equals(Arrays.asList(100L)),"existing account joins first published channel");
  tick();check(requests==1&&MessagesController.getInstance(0).joined.size()==1,"ticker is idempotent while feed fresh");
  expire();tick();check(requests==2&&MessagesController.getInstance(0).joined.size()==1,"already joined channel skipped at feed refresh");
  UserConfig.getInstance(1).owner=99;channel(1,100,"first_channel",true);tick();check(MessagesController.getInstance(1).joined.equals(Arrays.asList(100L)),"second logged-in account joins independently");
  expire();body="@first_channel\n@second_channel\n@group_channel\n@missing_channel";channel(0,200,"second_channel",true);channel(0,300,"group_channel",true).megagroup=true;tick();
  check(MessagesController.getInstance(0).joined.equals(Arrays.asList(100L,200L)),"later feed entries join and groups do not");
  expire();status=503;int before=MessagesController.getInstance(0).joined.size();tick();check(MessagesController.getInstance(0).joined.size()==before,"HTTP failure does not erase or rejoin entries");
  int failedRequests=requests;tick();check(requests==failedRequests,"network failures back off for five minutes");
  SystemClock.now+=300000;status=200;body="@third_channel";channel(0,400,"third_channel",true);tick();check(MessagesController.getInstance(0).joined.contains(400L),"failed HTTP recovers automatically after backoff");
  reset();UserConfig.getInstance(0).owner=42;channel(0,100,"first_channel",true);MangoAutoJoinChannels.tick();UserConfig.getInstance(0).owner=77;DispatchQueue.drain();check(MessagesController.getInstance(0).joined.isEmpty(),"in-flight download cannot join for reused account slot");
  reset();UserConfig.getInstance(0).owner=42;channel(0,100,"first_channel",true);MessagesController.getInstance(0).delayed=true;tick();UserConfig.getInstance(0).owner=77;MessagesController.getInstance(0).pending.remove(0).run();check(MessagesController.getInstance(0).joined.isEmpty(),"in-flight resolution cannot join for reused account slot");
  reset();UserConfig.getInstance(0).owner=42;channel(0,100,"first_channel",true);MessagesController.getInstance(0).error="CHANNEL_PRIVATE";tick();check(MessagesController.getInstance(0).joined.isEmpty(),"failed join remains retryable");MessagesController.getInstance(0).error=null;SystemClock.now+=300000;tick();check(MessagesController.getInstance(0).joined.equals(Arrays.asList(100L)),"failed join retries even with fresh HTTP cache");
  reset();UserConfig.getInstance(0).owner=42;channel(0,100,"first_channel",true);MessagesController.getInstance(0).error="FLOOD_WAIT_600";tick();MessagesController.getInstance(0).error=null;SystemClock.now+=300000;tick();check(MessagesController.getInstance(0).joined.isEmpty(),"server flood wait respected");SystemClock.now+=300000;tick();check(MessagesController.getInstance(0).joined.size()==1,"join resumes after flood wait");
  reset();UserConfig.getInstance(0).owner=42;channel(0,100,"first_channel",true);ApplicationLoader.applicationContext.failCommit=true;tick();check(MessagesController.getInstance(0).joined.isEmpty(),"failed cache commit prevents join from uncommitted feed");
  reset();UserConfig.getInstance(0).owner=42;channel(0,100,"first_channel",true);tick();states().clear();MessagesController.getInstance(0).chats.get(100L).left=true;tick();check(MessagesController.getInstance(0).joined.size()==2&&requests==1,"startup checks cached feed membership without duplicate download");
  body="@second_channel";channel(0,200,"second_channel",true);MangoAutoJoinChannels.onAccountActivated(0);ArrayList<AndroidUtilities.Delayed> pending=new ArrayList<>(AndroidUtilities.delayed);AndroidUtilities.delayed.clear();for(AndroidUtilities.Delayed d:pending)d.runnable.run();DispatchQueue.drain();check(MessagesController.getInstance(0).joined.contains(200L)&&requests==2,"fresh login always reloads published list");
  reset();UserConfig.getInstance(0).owner=42;channel(0,100,"first_channel",true);MessagesController.getInstance(0).delayed=true;tick();SystemClock.now+=60000;tick();MessagesController.getInstance(0).delayed=false;SystemClock.now+=300000;tick();check(MessagesController.getInstance(0).joined.size()==1,"stalled resolve times out and retries");MessagesController.getInstance(0).pending.remove(0).run();check(MessagesController.getInstance(0).joined.size()==1,"late resolve callback after timeout ignored");
  System.out.println("V45ChannelTest: "+checks+" offline checks passed");
 }
}
