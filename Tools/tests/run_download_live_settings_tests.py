#!/usr/bin/env python3
"""Exercise the production live speed-settings callback with queued and paused transfers."""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
source = (ROOT / 'TMessagesProj/src/main/java/org/telegram/messenger/FileLoadOperation.java').read_text()

def method(marker):
    start = source.index(marker)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

fixture = '''
package org.telegram.messenger;
public class LiveDownloadSettingsTest {
 static class SharedConfig { static int multiplier=1; static int getDownloadSpeedMultiplier(){return multiplier;} }
 static class Utilities {
  static final Queue stageQueue = new Queue();
  static class Queue { void postRunnable(Runnable r){r.run();} }
 }
 int currentBaseDownloadChunkSize=128*1024, currentBaseMaxDownloadRequests=4;
 int currentDownloadChunkSize=128*1024, currentMaxDownloadRequests=4;
 static final int stateDownloading=1;
 int state=stateDownloading, startCalls, inFlight=4, delayed, added;
 boolean paused, forceSmallChunk;
 void startDownloadRequest(int ignored){
  startCalls++;
  added=DownloadRequestPolicy.getAvailableRequestSlots(inFlight,delayed,currentMaxDownloadRequests);
  inFlight+=added;
 }
 //METHODS
 static int checks;
 static void check(boolean condition,String why){checks++;if(!condition)throw new AssertionError(why);}
 public static void main(String[] args){
  LiveDownloadSettingsTest operation=new LiveDownloadSettingsTest();
  SharedConfig.multiplier=5; operation.updateDownloadSpeedSettings();
  check(operation.currentMaxDownloadRequests==8 && operation.added==4,"5x setting takes effect with four requests already in flight");
  check(operation.currentDownloadChunkSize==128*1024,"setting does not change active file chunk offsets");
  SharedConfig.multiplier=50; operation.updateDownloadSpeedSettings();
  check(operation.currentMaxDownloadRequests==16 && operation.inFlight==16,"higher setting remains capped");
  SharedConfig.multiplier=1; operation.updateDownloadSpeedSettings();
  check(operation.currentMaxDownloadRequests==4 && operation.added==0,"normal setting drains existing window without scheduling extra work");
  operation.paused=true; int starts=operation.startCalls;
  SharedConfig.multiplier=5; operation.updateDownloadSpeedSettings();
  check(operation.currentMaxDownloadRequests==8 && operation.startCalls==starts,"paused transfers receive setting for resume without being restarted");
  operation.forceSmallChunk=true; operation.updateDownloadSpeedSettings();
  check(operation.currentMaxDownloadRequests==4,"server fallback retains safe window");
  operation.currentBaseDownloadChunkSize=0; operation.updateDownloadSpeedSettings();
  check(operation.startCalls==starts,"uninitialized queued operation waits until start chooses params");
  System.out.println("PASS: "+checks+" live download-settings behavior checks");
 }
}
'''
fixture=fixture.replace('//METHODS','\n'.join([
    method('    private int getAdjustedMaxDownloadRequests('),
    method('    public void updateDownloadSpeedSettings()'),
]))
java=Path(os.environ.get('JAVA_HOME','/Applications/Android Studio.app/Contents/jbr/Contents/Home'))/'bin'
with tempfile.TemporaryDirectory(prefix='mango-download-live-') as directory:
    file=Path(directory)/'LiveDownloadSettingsTest.java';file.write_text(fixture)
    subprocess.run([str(java/'javac'),'-d',directory,str(file),str(ROOT/'TMessagesProj/src/main/java/org/telegram/messenger/DownloadRequestPolicy.java')],check=True)
    subprocess.run([str(java/'java'),'-cp',directory,'org.telegram.messenger.LiveDownloadSettingsTest'],check=True)
