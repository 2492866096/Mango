#!/usr/bin/env python3
"""Exercise real MangoSettings + synchronization with in-memory account/preference services."""
import ast
from pathlib import Path
import os, re, subprocess, tempfile
root = Path(__file__).resolve().parents[2]
java = Path(os.environ.get('JAVA_HOME', '/Applications/Android Studio.app/Contents/jbr/Contents/Home'))
sdk = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Library/Android/sdk')))
tree = ast.parse((root/'Tools/tests/run_mango_settings_tests.py').read_text())
fixtures = next(ast.literal_eval(n.value) for n in tree.body if isinstance(n, ast.Assign) and any(isinstance(t, ast.Name) and t.id == 'fixtures' for t in n.targets))
fixtures['org/telegram/messenger/UserConfig.java'] = '''package org.telegram.messenger;public class UserConfig {static final UserConfig[] instances={new UserConfig(101,true),new UserConfig(202,true),new UserConfig(303,false)};public long owner;public boolean active;UserConfig(long o,boolean a){owner=o;active=a;}public static UserConfig getInstance(int account){return instances[account];}public long getClientUserId(){return owner;}public boolean isClientActivated(){return active;}public static int[] getActivatedAccounts(){return java.util.stream.IntStream.range(0,instances.length).filter(i->instances[i].active).toArray();}}'''
keys = re.findall(r'new Feature\(\d+, "([^"]+)"', (root/'TMessagesProj/src/main/java/org/telegram/messenger/MangoFeatureRegistry.java').read_text())
fixtures['org/telegram/messenger/MangoFeatureRegistry.java'] = 'package org.telegram.messenger;public class MangoFeatureRegistry {public static class Feature {public String key;Feature(String k){key=k;}}public static final Feature[] FEATURES={'+','.join('new Feature("'+key+'")' for key in keys)+'};}'
fixtures['org/telegram/messenger/MangoLegacySettings.java'] = '''package org.telegram.messenger;import java.util.*;public class MangoLegacySettings {static final Map<Integer,Map<String,Boolean>> stores=new HashMap<>();static Map<String,Boolean> store(int a){return stores.computeIfAbsent(a,k->new HashMap<>());}public static Map<String,Boolean> booleanSwitches(int a){Map<String,Boolean> v=new HashMap<>();v.put("hide_online",store(a).getOrDefault("hide_online",false));v.put("anti_revoke",store(a).getOrDefault("anti_revoke",true));return v;}public static void initialize(int a){MangoSettingsSync.initializeAccount(a);}public static void applyBooleanSwitch(int a,String k,boolean v){if(k.startsWith("daily_")||k.startsWith("echo_"))return;store(a).put(k,v);MangoSettingsSync.onLegacyChanged(a,k,v);}public static void restore(int a,String k,Object v){}}
'''
fixtures.pop('org/telegram/messenger/MangoSettingsSync.java',None)
fixtures['org/telegram/messenger/SyncTest.java'] = '''package org.telegram.messenger;
import org.telegram.ui.Components.MangoDownloadSpeedHud;
public class SyncTest {
 static int checks;static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 public static void main(String[]args){
  MangoSettings.setBoolean(0,"hide_proxy_icon",true);
  MangoSettings.setBoolean(1,"confirm_send",true);
  MangoSettings.setString(0,"main_tabs_order","7,6,5");
  MangoSettings.setString(0,"auto_read_random_reply_groups","-999");
  MangoSettings.setInt(0,"scam_refresh_time",123);
  MangoLegacySettings.store(0).put("hide_online",true);
  MangoLegacySettings.store(1).put("anti_revoke",false);
  check(!MangoSettingsSync.isEnabled(),"sync defaults off");
  MangoSettingsSync.setEnabled(0,true);
  check(MangoSettingsSync.isEnabled(),"enabled persists");
  check(MangoSettings.getBoolean(1,"hide_proxy_icon"),"explicit switch copied");
  check(!MangoSettings.getBoolean(1,"confirm_send"),"unset false default replaces target true");
  check(MangoSettings.getBoolean(1,"cross_account_inbox",false),"true fallback copied even without stored source value");
  check(MangoLegacySettings.booleanSwitches(1).get("hide_online"),"legacy switch applied");
  check(MangoLegacySettings.booleanSwitches(1).get("anti_revoke"),"legacy true default applied");
  check(MangoSettings.getString(1,"main_tabs_order","local").equals("local"),"tab order not copied");
  check(MangoSettings.getString(1,"auto_read_random_reply_groups","local").equals("local"),"chat-specific targets not copied");
  check(MangoSettings.getInt(1,"scam_refresh_time",0)==0,"runtime state not copied");
  MangoSettings.setBoolean(1,"hide_proxy_icon",false);
  check(!MangoSettings.getBoolean(0,"hide_proxy_icon"),"edits from second account sync back");
  MangoSettings.setBoolean(1,"scam_users_enabled",false);
  check(!MangoSettings.getBoolean(0,"scam_users_enabled",true),"false overrides true default");
  MangoSettings.restore(1,MangoSettings.getHistory(1).get(0));
  check(MangoSettings.getBoolean(0,"scam_users_enabled",true),"undo propagates effective restored value");
  MangoSettings.setBoolean(1,"collapse_message_menu",true);
  MangoSettings.setBoolean(0,MangoDownloadSpeedHud.SETTING_KEY,true);
  check(MangoDownloadSpeedHud.enabled[1],"synced HUD switch refreshes existing account");
  UserConfig.getInstance(2).active=true;MangoSettingsSync.initializeAccount(2);
  check(MangoSettings.getBoolean(2,"collapse_message_menu"),"newly logged-in account inherits current switches");
  check(MangoDownloadSpeedHud.changes[2]==1&&MangoDownloadSpeedHud.enabled[2],"newly logged-in account refreshes HUD when inheriting enabled switch");
  check(MangoLegacySettings.booleanSwitches(2).get("hide_online"),"new account inherits legacy switches");
  MangoLegacySettings.store(2).put("hide_online",false);MangoSettingsSync.onLegacyChanged(2,"hide_online",false);
  check(!MangoLegacySettings.booleanSwitches(0).get("hide_online")&&!MangoLegacySettings.booleanSwitches(1).get("hide_online"),"legacy changes sync both directions");
  MangoSettingsSync.onLegacyChanged(0,"daily_999",true);MangoSettingsSync.onLegacyChanged(0,"echo_123",true);
  check(!MangoLegacySettings.store(1).containsKey("daily_999")&&!MangoLegacySettings.store(1).containsKey("echo_123"),"task-specific toggles remain account-owned");
  UserConfig.getInstance(2).owner=404;MangoSettingsSync.initializeAccount(2);
  check(MangoSettings.getBoolean(2,"collapse_message_menu"),"reused account slot initialized for new owner");
  MangoSettings.reset(1);
  check(!MangoSettings.getBoolean(0,"collapse_message_menu"),"reset propagates boolean defaults");
  check(!MangoDownloadSpeedHud.enabled[0]&&!MangoDownloadSpeedHud.enabled[1]&&!MangoDownloadSpeedHud.enabled[2],"reset hides HUD on every synchronized account");
  MangoSettingsSync.setEnabled(0,false);MangoSettings.setBoolean(0,"collapse_message_menu",true);
  check(!MangoSettings.getBoolean(1,"collapse_message_menu"),"disabled sync leaves other accounts unchanged");
  MangoSettingsSync.setEnabled(0,true);
  check(MangoSettings.getBoolean(1,"collapse_message_menu"),"reenabling captures latest current settings");
  System.out.println("PASS: "+checks+" account switch sync behavior checks");
 }
}'''
with tempfile.TemporaryDirectory(prefix='mango-sync-test-') as folder:
    base=Path(folder);sources=[]
    for name,text in fixtures.items():
        p=base/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text);sources.append(p)
    json=sorted(sdk.glob('sources/android-*/org/json/JSONObject.java'))[-1].parent
    for original in json.glob('*.java'):
        text=original.read_text();text=re.sub(r'^import (?:static )?(?:android\.|libcore\.).*;\n','',text,flags=re.M);text=re.sub(r'@(?:UnsupportedAppUsage|SystemApi|NonNull|Nullable)(?:\([^)]*\))?','',text)
        p=base/'org/json'/original.name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text);sources.append(p)
    sources += [root/'TMessagesProj/src/main/java/org/telegram/messenger'/n for n in ['MangoSettings.java','MangoSettingsSync.java']]
    out=base/'classes';subprocess.run([str(java/'bin/javac'),'-d',str(out)]+list(map(str,sources)),check=True)
    subprocess.run([str(java/'bin/java'),'-cp',str(out),'org.telegram.messenger.SyncTest'],check=True)
