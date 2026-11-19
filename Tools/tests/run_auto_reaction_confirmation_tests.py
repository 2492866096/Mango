#!/usr/bin/env python3
"""Exercise production reaction preferences, confirmation callbacks and synchronization barriers."""
from pathlib import Path
import ast
import os
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / 'TMessagesProj/src/main/java'
JAVA = Path(os.environ.get('JAVA_HOME', '/Applications/Android Studio.app/Contents/jbr/Contents/Home')) / 'bin'
SDK = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Library/Android/sdk')))

def method(source, marker):
    start = source.index(marker)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

tree = ast.parse((ROOT / 'Tools/tests/run_mango_settings_tests.py').read_text())
fixtures = next(ast.literal_eval(n.value) for n in tree.body if isinstance(n, ast.Assign) and any(isinstance(t, ast.Name) and t.id == 'fixtures' for t in n.targets))
fixtures.pop('org/telegram/messenger/MangoSettingsSync.java')
shared = (SRC / 'org/telegram/messenger/SharedConfig.java').read_text()
constants = '\n'.join(re.findall(r'    private static final String PREF_AUTO_REACTION_(?:ENABLED|CONFIRMED|CHANNELS|GROUPS|PRIVATE) = .*?;', shared))
initial = re.search(r'    public static boolean autoReactionEnabled = .*?;', shared).group(0)
load = re.search(r'            autoReactionEnabled = preferences\.getBoolean\(PREF_AUTO_REACTION_CONFIRMED,[\s\S]*?;', shared).group(0)
shared_methods = '\n'.join(method(shared, marker) for marker in (
    '    public static boolean isAutoReactionEnabled()', '    public static void setAutoReactionEnabled(',
    '    public static boolean isAutoReactionChannelsEnabled()', '    public static void setAutoReactionChannelsEnabled(',
    '    public static boolean isAutoReactionGroupsEnabled()', '    public static void setAutoReactionGroupsEnabled(',
    '    public static boolean isAutoReactionPrivateEnabled()', '    public static void setAutoReactionPrivateEnabled(',
    '    private static void saveExtensionPreference(String key, boolean value)',
))
fixtures['org/telegram/messenger/SharedConfig.java'] = '''package org.telegram.messenger;import android.content.*;public class SharedConfig {\n''' + constants + '\n' + initial + '\n' + shared_methods + '\npublic static void reload(){SharedPreferences preferences=ApplicationLoader.applicationContext.getSharedPreferences("userconfing",Context.MODE_PRIVATE);' + load + '}\n}'
legacy = (SRC / 'org/telegram/messenger/MangoLegacySettings.java').read_text()
specs = '\n'.join(line for line in legacy.splitlines() if 'items.add(new Setting(' in line and any('"'+key+'"' in line for key in ('auto_reaction','reaction_channels','reaction_groups','reaction_private')))
legacy_methods = '\n'.join(method(legacy, marker) for marker in (
    '    public static Map<String, Boolean> booleanSwitches(',
    '    public static void applyBooleanSwitch(', '    public static void restore(',
))
fixtures['org/telegram/messenger/MangoLegacySettings.java'] = '''package org.telegram.messenger;import java.util.*;public class MangoLegacySettings {
 private interface Getter {Object get();} private interface Setter {void set(Object v);}
 ''' + method(legacy, '    private static final class Setting {') + '''
 private static class Observer {Map<String,Object> previous=new HashMap<>();} private static Map<Integer,Observer> observers=new HashMap<>();
 public static void initialize(int a){}public static void capture(int a){}private static void notifyChanged(int a){}
 private static ArrayList<Setting> settings(int account){ArrayList<Setting> items=new ArrayList<>();
 ''' + specs + '\nreturn items;}\n' + legacy_methods + '\n}'
fixtures['org/telegram/messenger/MessageEchoManager.java'] = '''package org.telegram.messenger;public class MessageEchoManager {public static void setTarget(int a,long d,boolean v){}}'''
fixtures['org/telegram/messenger/UserConfig.java'] = '''package org.telegram.messenger;public class UserConfig {static final UserConfig[] instances={new UserConfig(101,true),new UserConfig(202,true),new UserConfig(303,false)};public long owner;public boolean active;UserConfig(long o,boolean a){owner=o;active=a;}public static UserConfig getInstance(int account){return instances[account];}public long getClientUserId(){return owner;}public boolean isClientActivated(){return active;}public static int[] getActivatedAccounts(){return java.util.stream.IntStream.range(0,instances.length).filter(i->instances[i].active).toArray();}}'''
keys = re.findall(r'new Feature\(\d+, "([^"]+)"', (SRC / 'org/telegram/messenger/MangoFeatureRegistry.java').read_text())
fixtures['org/telegram/messenger/MangoFeatureRegistry.java'] = 'package org.telegram.messenger;public class MangoFeatureRegistry {public static class Feature {public String key;Feature(String k){key=k;}}public static final Feature[] FEATURES={'+','.join('new Feature("'+key+'")' for key in keys)+'};}'
confirmation = (SRC / 'org/telegram/ui/Components/AutoReactionConfirmation.java').read_text()
resources = sorted(set(re.findall(r'R.string.(\w+)', confirmation + specs)))
fixtures['org/telegram/messenger/R.java'] = 'package org.telegram.messenger;public class R {public static class string {' + ''.join('public static final int '+key+'='+str(i)+';' for i,key in enumerate(resources)) + '}}'
fixtures['org/telegram/messenger/LocaleController.java'] = '''package org.telegram.messenger;public class LocaleController {public static String getString(int r){return "label";}}'''
fixtures['org/telegram/ui/ActionBar/AlertDialog.java'] = '''package org.telegram.ui.ActionBar;public class AlertDialog {public interface Click {void onClick(Object d,int w);}public Click positive,negative;public void confirm(){if(positive!=null)positive.onClick(this,1);}public void cancel(){if(negative!=null)negative.onClick(this,-1);}public static class Builder {AlertDialog dialog=new AlertDialog();public Builder(Object a,Object p){}public Builder setTitle(String s){return this;}public Builder setMessage(String s){return this;}public Builder setNegativeButton(String s,Click c){dialog.negative=c;return this;}public Builder setPositiveButton(String s,Click c){dialog.positive=c;return this;}public AlertDialog create(){return dialog;}}}'''
fixtures['org/telegram/ui/ActionBar/BaseFragment.java'] = '''package org.telegram.ui.ActionBar;public class BaseFragment {public boolean isFinished;public int account;public Object parent=new Object();public AlertDialog shown;public Object getParentActivity(){return parent;}public int getCurrentAccount(){return account;}public Object getResourceProvider(){return null;}public void showDialog(AlertDialog d){shown=d;}}'''
fixtures['org/telegram/messenger/ReactionConfirmationTest.java'] = r'''
package org.telegram.messenger;
import android.content.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.AutoReactionConfirmation;
public class ReactionConfirmationTest {
 static int checks,enabledCallbacks;
 static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
 static SharedPreferences prefs(){return ApplicationLoader.applicationContext.getSharedPreferences("userconfing",Context.MODE_PRIVATE);}
 static SharedPreferences sync(){return ApplicationLoader.applicationContext.getSharedPreferences("mango_extension_switch_sync",Context.MODE_PRIVATE);}
 static BaseFragment prompt(){BaseFragment f=new BaseFragment();AutoReactionConfirmation.show(f,()->enabledCallbacks++);return f;}
 public static void main(String[] args){
  check(!SharedConfig.isAutoReactionEnabled(),"in-memory master default disabled before preferences load");SharedConfig.reload();
  check(!SharedConfig.isAutoReactionEnabled(),"fresh install master defaults off");
  check(SharedConfig.isAutoReactionChannelsEnabled()&&!SharedConfig.isAutoReactionGroupsEnabled()&&!SharedConfig.isAutoReactionPrivateEnabled(),"channel-only scope defaults");
  prefs().edit().putBoolean("autoReactionEnabled",true).apply();SharedConfig.reload();
  check(!SharedConfig.isAutoReactionEnabled(),"old default true without fresh consent migrates disabled");
  prefs().edit().putBoolean("autoReactionConfirmedV51",true).putBoolean("autoReactionEnabled",false).apply();SharedConfig.reload();
  check(!SharedConfig.isAutoReactionEnabled(),"consent marker alone never overrides disabled master");
  prefs().edit().putBoolean("autoReactionConfirmedV51",false).apply();
  BaseFragment cancel=prompt();check(cancel.shown!=null&&!SharedConfig.isAutoReactionEnabled(),"opening enable confirmation leaves master off");cancel.shown.cancel();
  check(!SharedConfig.isAutoReactionEnabled()&&!prefs().getBoolean("autoReactionConfirmedV51",false)&&enabledCallbacks==0,"cancel does not grant consent or execute enable callback");
  BaseFragment closed=prompt();closed.isFinished=true;closed.shown.confirm();check(!SharedConfig.isAutoReactionEnabled(),"closed fragment confirmation ignored");
  BaseFragment switched=prompt();switched.account=1;switched.shown.confirm();check(!SharedConfig.isAutoReactionEnabled(),"switching account during prompt cancels consent");
  BaseFragment owner=prompt();UserConfig.getInstance(0).owner=404;owner.shown.confirm();check(!SharedConfig.isAutoReactionEnabled(),"reused login slot during prompt cancels consent");UserConfig.getInstance(0).owner=101;
  BaseFragment logout=prompt();UserConfig.getInstance(0).active=false;logout.shown.confirm();check(!SharedConfig.isAutoReactionEnabled(),"logged out owner cannot confirm");UserConfig.getInstance(0).active=true;
  BaseFragment absent=new BaseFragment();absent.parent=null;AutoReactionConfirmation.show(absent,null);check(absent.shown==null,"detached fragment does not create confirmation");
  BaseFragment live=prompt();live.shown.confirm();check(SharedConfig.isAutoReactionEnabled()&&enabledCallbacks==1,"explicit confirmation enables once");
  check(prefs().getBoolean("autoReactionEnabled",false)&&prefs().getBoolean("autoReactionConfirmedV51",false),"manual enable persists both enable and consent flags");
  SharedConfig.autoReactionEnabled=false;SharedConfig.reload();check(SharedConfig.isAutoReactionEnabled(),"confirmed enable survives process state reload");
  SharedConfig.setAutoReactionEnabled(false);SharedConfig.reload();check(!SharedConfig.isAutoReactionEnabled()&&!prefs().getBoolean("autoReactionConfirmedV51",true),"disable persists and clears consent marker");
  MangoLegacySettings.restore(0,"auto_reaction",true);check(!SharedConfig.isAutoReactionEnabled(),"legacy history setter cannot silently enable");
  MangoLegacySettings.applyBooleanSwitch(0,"auto_reaction",true);check(!SharedConfig.isAutoReactionEnabled(),"legacy boolean import cannot silently enable");
  check(!MangoLegacySettings.booleanSwitches(0).containsKey("auto_reaction"),"master excluded from generated boolean snapshot");
  SharedConfig.setAutoReactionChannelsEnabled(false);SharedConfig.setAutoReactionGroupsEnabled(true);SharedConfig.setAutoReactionPrivateEnabled(true);SharedConfig.reload();
  check(!SharedConfig.isAutoReactionChannelsEnabled()&&SharedConfig.isAutoReactionGroupsEnabled()&&SharedConfig.isAutoReactionPrivateEnabled(),"independent scopes persist without enabling master");
  check(!SharedConfig.isAutoReactionEnabled(),"scope changes never grant master consent");
  SharedConfig.setAutoReactionChannelsEnabled(true);SharedConfig.setAutoReactionGroupsEnabled(false);SharedConfig.setAutoReactionPrivateEnabled(false);
  MangoSettingsSync.setEnabled(0,true);check(!sync().getAll().containsKey("legacy:auto_reaction"),"new synchronization snapshot excludes master");
  check(Boolean.TRUE.equals(sync().getAll().get("legacy:reaction_channels"))&&Boolean.FALSE.equals(sync().getAll().get("legacy:reaction_groups")),"scope settings remain available for synchronization");
  MangoSettingsSync.onLegacyChanged(0,"auto_reaction",true);check(!sync().getAll().containsKey("legacy:auto_reaction")&&!SharedConfig.isAutoReactionEnabled(),"legacy change event cannot insert or apply master enable");
  sync().edit().putBoolean("legacy:auto_reaction",true).apply();UserConfig.getInstance(2).active=true;MangoSettingsSync.initializeAccount(2);
  check(!SharedConfig.isAutoReactionEnabled(),"pre-upgrade snapshot true cannot enable a new login");
  BaseFragment enabled=prompt();enabled.shown.confirm();sync().edit().putBoolean("legacy:auto_reaction",false).apply();UserConfig.getInstance(2).owner=505;MangoSettingsSync.initializeAccount(2);
  check(SharedConfig.isAutoReactionEnabled(),"stale snapshot false cannot override manually confirmed master");
  MangoSettingsSync.setEnabled(0,true);check(!sync().getAll().containsKey("legacy:auto_reaction"),"recapturing snapshot purges old master key");
  MangoLegacySettings.restore(0,"auto_reaction",false);check(!SharedConfig.isAutoReactionEnabled(),"history restore can still disable master");
  MangoSettings.setBoolean(0,"auto_read_random_reply",true);MangoSettings.setString(0,"auto_read_random_reply_groups","-500");
  check(!MangoSettings.getBoolean(0,"auto_read_random_reply",true)&&MangoSettings.getString(0,"auto_read_random_reply_groups","removed").equals("removed"),"retired reply settings cannot be written or reactivated");
  MangoSettingsSync.onMangoChanged(0,"auto_read_random_reply");check(!sync().getAll().containsKey("mango:auto_read_random_reply"),"retired reply switch excluded from sync registry");
  System.out.println("PASS: "+checks+" reaction confirmation/migration/persistence/sync behavior checks");
 }
}
'''
with tempfile.TemporaryDirectory(prefix='mango-reaction-confirmation-') as directory:
    base = Path(directory)
    sources = []
    for name, content in fixtures.items():
        target = base / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content)
        sources.append(target)
    json_source = sorted(SDK.glob('sources/android-*/org/json/JSONObject.java'))[-1].parent
    for original in json_source.glob('*.java'):
        content = original.read_text()
        content = re.sub(r'^import (?:static )?(?:android\.|libcore\.).*;\n', '', content, flags=re.M)
        content = re.sub(r'@(?:UnsupportedAppUsage|SystemApi|NonNull|Nullable)(?:\([^)]*\))?', '', content)
        target = base / 'org/json' / original.name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content)
        sources.append(target)
    sources.extend(SRC / 'org/telegram/messenger' / name for name in ('MangoSettings.java', 'MangoSettingsSync.java'))
    sources.append(SRC / 'org/telegram/ui/Components/AutoReactionConfirmation.java')
    subprocess.run([str(JAVA / 'javac'), '-d', str(base / 'classes'), *map(str, sources)], check=True)
    subprocess.run([str(JAVA / 'java'), '-cp', str(base / 'classes'), 'org.telegram.messenger.ReactionConfirmationTest'], check=True)

history = (SRC / 'org/telegram/ui/MangoSettingsHistoryActivity.java').read_text()
settings = (SRC / 'org/telegram/ui/ExtensionFeaturesActivity.java').read_text()
assert '"legacy:auto_reaction".equals(change.key) && Boolean.TRUE.equals(change.before)' in history
assert 'AutoReactionConfirmation.show(this' in history and 'AutoReactionConfirmation.show(this' in settings
assert 'setAutoReactionEnabled(true)' not in settings and 'setAutoReactionEnabled(true)' not in history
print('PASS: 3 switch/history confirmation routing checks')
