#!/usr/bin/env python3
"""Exercise production preference/history code against in-memory Android storage; no APK or network."""
from pathlib import Path
import os, re, subprocess, tempfile
root = Path(__file__).resolve().parents[2]
java = Path(os.environ.get('JAVA_HOME', '/Applications/Android Studio.app/Contents/jbr/Contents/Home'))
sdk = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Library/Android/sdk')))
fixtures = {
'org/telegram/ui/Components/MangoDownloadSpeedHud.java': '''package org.telegram.ui.Components;public class MangoDownloadSpeedHud {public static final String SETTING_KEY="mango_download_speed_hud";public static final int[] changes=new int[4];public static final boolean[] enabled=new boolean[4];public static void onPreferenceChanged(int account){changes[account]++;enabled[account]=org.telegram.messenger.MangoSettings.getBoolean(account,SETTING_KEY,false);}}''',
'org/telegram/messenger/MangoSettingsSync.java': '''package org.telegram.messenger; public class MangoSettingsSync {public static void onMangoChanged(int a,String k){}public static void onMangoReset(int a){}}''',
'android/content/SharedPreferences.java': '''package android.content; import java.util.Map; public interface SharedPreferences { boolean getBoolean(String k,boolean d);String getString(String k,String d);int getInt(String k,int d);Map<String,?> getAll();Editor edit(); interface Editor { Editor putBoolean(String k,boolean v);Editor putInt(String k,int v);Editor putString(String k,String v);Editor remove(String k);void apply(); }}''',
'android/content/Context.java': '''package android.content; import java.util.*;public class Context {public static final int MODE_PRIVATE=0;final Map<String,Prefs> stores=new HashMap<>();public SharedPreferences getSharedPreferences(String name,int mode){return stores.computeIfAbsent(name,k->new Prefs());} static class Prefs implements SharedPreferences {final Map<String,Object> values=new HashMap<>(); public boolean getBoolean(String k,boolean d){return (Boolean)values.getOrDefault(k,d);}public String getString(String k,String d){return (String)values.getOrDefault(k,d);}public int getInt(String k,int d){return (Integer)values.getOrDefault(k,d);}public Map<String,?> getAll(){return new HashMap<>(values);}public Editor edit(){return new Editor(){final Map<String,Object> changes=new HashMap<>();final Set<String> removes=new HashSet<>();public Editor putBoolean(String k,boolean v){changes.put(k,v);return this;}public Editor putInt(String k,int v){changes.put(k,v);return this;}public Editor putString(String k,String v){changes.put(k,v);return this;}public Editor remove(String k){removes.add(k);return this;}public void apply(){for(String k:removes)values.remove(k);values.putAll(changes);}};} }}''',
'org/telegram/messenger/ApplicationLoader.java': '''package org.telegram.messenger;public class ApplicationLoader {public static final android.content.Context applicationContext=new android.content.Context();}''',
'org/telegram/messenger/UserConfig.java': '''package org.telegram.messenger;public class UserConfig {static final UserConfig[] instances={new UserConfig(),new UserConfig()};public long owner=42;public static UserConfig getInstance(int account){return instances[account];}public long getClientUserId(){return owner;}}''',
'org/telegram/messenger/NotificationCenter.java': '''package org.telegram.messenger;public class NotificationCenter {public static final int updateInterfaces=1,storiesUpdated=2,storiesEnabledUpdate=3;public static final int[] counts=new int[4];public static NotificationCenter getInstance(int a){return new NotificationCenter();}public void postNotificationName(int type,Object...args){counts[type]++;}}''',
'org/telegram/messenger/FileLog.java': '''package org.telegram.messenger;public class FileLog {public static void e(Exception e){throw new AssertionError(e);}}''',
'org/telegram/messenger/MangoLegacySettings.java': '''package org.telegram.messenger;public class MangoLegacySettings {static String lastKey;static Object lastValue;public static void restore(int account,String key,Object value){lastKey=key;lastValue=value;}}''',
'org/telegram/messenger/NotificationsController.java': '''package org.telegram.messenger;public class NotificationsController {public static NotificationsController getInstance(int account){return new NotificationsController();}public void updateReactionNotifications(){}}''',
}
with tempfile.TemporaryDirectory(prefix='mango-settings-test-') as folder:
    base=Path(folder); sources=[]
    for name, text in fixtures.items():
        p=base/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text);sources.append(p)
    json=sorted(sdk.glob('sources/android-*/org/json/JSONObject.java'))[-1].parent
    for original in json.glob('*.java'):
        text=original.read_text();text=re.sub(r'^import (?:static )?(?:android\.|libcore\.).*;\n','',text,flags=re.M);text=re.sub(r'@(?:UnsupportedAppUsage|SystemApi|NonNull|Nullable)(?:\([^)]*\))?','',text)
        p=base/'org/json'/original.name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text);sources.append(p)
    sources += [root/'TMessagesProj/src/main/java/org/telegram/messenger/MangoSettings.java', root/'Tools/tests/MangoSettingsTest.java']
    out=base/'classes'
    subprocess.run([str(java/'bin/javac'),'-d',str(out)]+list(map(str,sources)),check=True)
    subprocess.run([str(java/'bin/java'),'-cp',str(out),'org.telegram.messenger.MangoSettingsTest'],check=True)
