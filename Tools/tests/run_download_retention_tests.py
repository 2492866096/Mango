#!/usr/bin/env python3
"""Verify retention policies, persistence, confirmation snapshots and cleanup boundaries."""
from pathlib import Path
import os, re, subprocess, tempfile
root = Path(__file__).resolve().parents[2]
java = Path(os.environ.get('JAVA_HOME', '/Applications/Android Studio.app/Contents/jbr/Contents/Home'))
sdk = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Library/Android/sdk')))
fixtures = {
    'android/content/SharedPreferences.java': '''package android.content; public interface SharedPreferences {String getString(String k,String d); int getInt(String k,int d); Editor edit(); interface Editor {Editor putInt(String k,int v);Editor putString(String k,String v);void apply();}}''',
    'android/content/Context.java': '''package android.content; import java.util.*; public class Context { public static final int MODE_PRIVATE=0; final Map<String,Prefs> all=new HashMap<>(); public SharedPreferences getSharedPreferences(String k,int mode){return all.computeIfAbsent(k,s->new Prefs());} static class Prefs implements SharedPreferences { Map<String,Object> m=new HashMap<>(); public String getString(String k,String d){return (String)m.getOrDefault(k,d);} public int getInt(String k,int d){return (Integer)m.getOrDefault(k,d);} public Editor edit(){return new Editor(){Map<String,Object> changes=new HashMap<>(m); public Editor putInt(String k,int v){changes.put(k,v);return this;} public Editor putString(String k,String v){changes.put(k,v);return this;}public void apply(){m=changes;}};} }}''',
    'org/telegram/messenger/ApplicationLoader.java': '''package org.telegram.messenger; public class ApplicationLoader {public static android.content.Context applicationContext=new android.content.Context();}''',
    'org/telegram/messenger/FileLoader.java': '''package org.telegram.messenger; import java.io.File; public class FileLoader {public static File root;public static boolean active; public static final int MEDIA_DIR_DOCUMENT=1,MEDIA_DIR_FILES=2,MEDIA_DIR_VIDEO=3,MEDIA_DIR_AUDIO=4,MEDIA_DIR_IMAGE=5,MEDIA_DIR_IMAGE_PUBLIC=6,MEDIA_DIR_VIDEO_PUBLIC=7;public static File checkDirectory(int dir){return root;}public static FileLoader getInstance(int a){return new FileLoader();}public boolean isLoadingFile(String n){return active;}public boolean isUploadingFile(String n){return false;}}''',
    'org/telegram/messenger/UserConfig.java': '''package org.telegram.messenger; public class UserConfig {public static int[] getActivatedAccounts(){return new int[]{0,1};}}''',
    'org/telegram/messenger/AutoDeleteMediaTask.java': '''package org.telegram.messenger; public class AutoDeleteMediaTask {public static java.util.Set<String> usingFilePaths=new java.util.HashSet<>();}''',
    'org/telegram/messenger/FileLog.java': '''package org.telegram.messenger; public class FileLog {public static void e(Exception e){throw new RuntimeException(e);}}''',
}
with tempfile.TemporaryDirectory(prefix='mango-retention-tests-') as folder:
    base=Path(folder);sources=[]
    for name,text in fixtures.items():
        file=base/name;file.parent.mkdir(parents=True,exist_ok=True);file.write_text(text);sources.append(file)
    json=list(sorted(sdk.glob('sources/android-*/org/json/JSONObject.java')))[-1].parent
    for original in json.glob('*.java'):
        text=original.read_text();text=re.sub(r'^import (?:static )?(?:android\.|libcore\.).*;\n','',text,flags=re.M);text=re.sub(r'@(?:UnsupportedAppUsage|SystemApi|NonNull|Nullable)(?:\([^)]*\))?','',text)
        file=base/'org/json'/original.name;file.parent.mkdir(parents=True,exist_ok=True);file.write_text(text);sources.append(file)
    sources += [root/'TMessagesProj/src/main/java/org/telegram/messenger/DownloadRetentionManager.java', root/'Tools/tests/DownloadRetentionManagerTest.java']
    out=base/'classes'
    subprocess.run([str(java/'bin/javac'),'-d',str(out)]+list(map(str,sources)),check=True)
    subprocess.run([str(java/'bin/java'),'-cp',str(out),'org.telegram.messenger.DownloadRetentionManagerTest',folder],check=True)
