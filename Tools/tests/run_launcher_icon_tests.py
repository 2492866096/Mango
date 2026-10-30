#!/usr/bin/env python3
"""Test production launcher switching and artwork resources, without building an APK.

The JVM fixtures model only component state and manifest defaults. Static checks
cover actual Manifest aliases, icon/preview mappings and Android resource sizes.
No Gradle invocation, device, network, or Python imaging package is required.
"""
from pathlib import Path
import hashlib
import os
import re
import shutil
import struct
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / 'TMessagesProj/src/main/res'
CONTROLLER = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/LauncherIconController.java'
ANDROID = '{http://schemas.android.com/apk/res/android}'
ICONS = (
    ('DefaultIcon', 'ic_launcher', 'icon_foreground', 'icon_background', 'galaxy'),
    ('VintageIcon', 'icon_6_launcher', 'icon_6_foreground', 'icon_6_background', 'pink'),
    ('AquaIcon', 'icon_4_launcher', 'icon_4_foreground', 'icon_4_background', 'gold'),
    ('PremiumIcon', 'icon_3_launcher', 'icon_3_foreground', 'icon_3_background', 'moon'),
    ('TurboIcon', 'icon_5_launcher', 'icon_5_foreground', 'icon_5_background', 'speed'),
    ('NoxIcon', 'icon_2_launcher', 'icon_2_foreground', 'icon_2_background', 'neon'),
)
RETIRED = ('StarryIcon', 'ClassicRoundIcon', 'ClassicBlueIcon')
DENSITIES = (('mdpi', 48, 108), ('hdpi', 72, 162), ('xhdpi', 96, 216),
             ('xxhdpi', 144, 324), ('xxxhdpi', 192, 432))
# SHA-256 of the six original user attachments, checked against their Desktop
# originals when importing. Keep tests portable: do not require that Desktop.
SOURCE_SHA256 = {
    '2026-10-03 08.35.52.jpg': '3c7b50124000840c30b866953d577f27f02e3f55688f1af5ee58eb1534216290',
    '2026-10-03 08.35.43.jpg': '96aa46c65b23a47160cb9cef74f87a2332736ebc2b2555ec3e67e960869378cb',
    '2026-10-03 08.36.00.jpg': 'af2714794f78e71c394fbe944a1fbaf44fa8d787785656aa48b264b5aa313a53',
    '2026-10-03 08.35.55.jpg': '85d375a9a323b12a1eeb6cdf272d55e704db17cd089c22418898ae683a24451c',
    '2026-10-03 08.35.57.jpg': '0987f9b37427637d98bdad9bd79a1c6e93a30fbe481fb2fc886b250fef4fff8c',
    '2026-10-03 08.36.05.jpg': '4d7444da3b8ecacc8623a896d0405121a82ccae54229f6cb83a8808a2faaa601',
}
REMOVED_RESOURCE = re.compile(
    r'(?:icon_[789]_(?:launcher(?:_round)?|foreground(?:_sa|_round)?|background(?:_sa|_round)?)'
    r'|icon(?:_[2-6])?_(?:foreground_sa|background_sa|foreground_round|background_round)'
    r'|icon_background_clip(?:_round)?)$'
)
FIXTURES = {
    'android/content/ComponentName.java': '''package android.content;
public final class ComponentName {
 private final String pkg,name;
 public ComponentName(String pkg,String name){this.pkg=pkg;this.name=name;}
 public String getPackageName(){return pkg;} public String getClassName(){return name;}
 public boolean equals(Object obj){if(!(obj instanceof ComponentName))return false;ComponentName o=(ComponentName)obj;return pkg.equals(o.pkg)&&name.equals(o.name);}
 public int hashCode(){return 31*pkg.hashCode()+name.hashCode();}
 public String toString(){return pkg+"/"+name;}
}''',
    'android/content/Context.java': '''package android.content;
public final class Context {
 private final android.content.pm.PackageManager pm=new android.content.pm.PackageManager();
 public String getPackageName(){return "app.mango.im";}
 public android.content.pm.PackageManager getPackageManager(){return pm;}
}''',
    'android/content/pm/PackageManager.java': '''package android.content.pm;
import android.content.ComponentName;
import java.util.*;
public final class PackageManager {
 public static final int COMPONENT_ENABLED_STATE_DEFAULT=0,COMPONENT_ENABLED_STATE_ENABLED=1,
   COMPONENT_ENABLED_STATE_DISABLED=2,DONT_KILL_APP=1;
 private static final String PREFIX="org.telegram.messenger.";
 private static final Set<String> LAUNCHERS=new HashSet<>(Arrays.asList("DefaultIcon","VintageIcon","AquaIcon","PremiumIcon","TurboIcon","NoxIcon"));
 private static final Set<String> COMPAT=new HashSet<>(Arrays.asList("DefaultLauncherIcon","StarryIcon","ClassicRoundIcon","ClassicBlueIcon"));
 private final Map<ComponentName,Integer> states=new HashMap<>();
 public static final class Operation {
  public final ComponentName component; public final int state,flags,launcherCount;
  Operation(ComponentName component,int state,int flags,int count){this.component=component;this.state=state;this.flags=flags;launcherCount=count;}
 }
 public final List<Operation> operations=new ArrayList<>();
 private String key(ComponentName component){
  String name=component.getClassName();
  if(!component.getPackageName().equals("app.mango.im")||!name.startsWith(PREFIX))throw new AssertionError("Wrong package or class: "+component);
  String key=name.substring(PREFIX.length());
  if(!LAUNCHERS.contains(key)&&!COMPAT.contains(key))throw new AssertionError("Undeclared component: "+component);
  return key;
 }
 public int getComponentEnabledSetting(ComponentName component){key(component);return states.getOrDefault(component,COMPONENT_ENABLED_STATE_DEFAULT);}
 public void seed(ComponentName component,int state){key(component);states.put(component,state);}
 public boolean isEffectivelyEnabled(ComponentName component){
  String key=key(component);int state=getComponentEnabledSetting(component);
  return state==COMPONENT_ENABLED_STATE_ENABLED||state==COMPONENT_ENABLED_STATE_DEFAULT&&(key.equals("DefaultIcon")||COMPAT.contains(key));
 }
 public int launcherCount(){int count=0;for(String key:LAUNCHERS)if(isEffectivelyEnabled(new ComponentName("app.mango.im",PREFIX+key)))count++;return count;}
 public void setComponentEnabledSetting(ComponentName component,int state,int flags){
  seed(component,state);operations.add(new Operation(component,state,flags,launcherCount()));
 }
}''',
    'org/telegram/messenger/ApplicationLoader.java': '''package org.telegram.messenger;
public final class ApplicationLoader { public static android.content.Context applicationContext=new android.content.Context(); }
''',
}


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def java_tool(name):
    java_home = Path(os.environ.get('JAVA_HOME', '/Applications/Android Studio.app/Contents/jbr/Contents/Home'))
    bundled = java_home / 'bin' / name
    found = str(bundled) if bundled.is_file() else shutil.which(name)
    require(found, f'{name} not found; set JAVA_HOME')
    return found


def test_controller(tmp):
    sources = []
    for relative, code in FIXTURES.items():
        path = tmp / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(code)
        sources.append(path)
    refs = set(re.findall(r'R\.(\w+)\.(\w+)', CONTROLLER.read_text()))
    test = ROOT / 'Tools/tests/LauncherIconControllerTest.java'
    refs.update(re.findall(r'R\.(\w+)\.(\w+)', test.read_text()))
    classes = {}
    for resource_id, (kind, name) in enumerate(sorted(refs), 1):
        classes.setdefault(kind, []).append(f'public static final int {name}={resource_id};')
    stub = 'package org.telegram.messenger; public final class R {'
    for kind, members in classes.items():
        stub += f'public static final class {kind} {{' + ''.join(members) + '}'
    stub += '}'
    r = tmp / 'org/telegram/messenger/R.java'
    r.write_text(stub)
    sources.extend((r, CONTROLLER, test))
    output = tmp / 'classes'
    subprocess.run([java_tool('javac'), '-d', str(output), *map(str, sources)], check=True)
    subprocess.run([java_tool('java'), '-cp', str(output), 'org.telegram.ui.LauncherIconControllerTest'], check=True)


def test_manifest():
    root = ET.parse(ROOT / 'TMessagesProj/src/main/AndroidManifest.xml').getroot()
    application = root.find('application')
    aliases = {alias.get(ANDROID + 'name').split('.')[-1]: alias
               for alias in application.findall('activity-alias')}
    launcher_aliases = []
    for component in application:
        for filter_node in component.findall('intent-filter'):
            categories = {category.get(ANDROID + 'name') for category in filter_node.findall('category')}
            if 'android.intent.category.LAUNCHER' in categories:
                require(component.tag == 'activity-alias', 'LaunchActivity itself must not duplicate the launcher aliases')
                launcher_aliases.append(component.get(ANDROID + 'name').split('.')[-1])
                actions = {action.get(ANDROID + 'name') for action in filter_node.findall('action')}
                require('android.intent.action.MAIN' in actions, 'Launcher alias requires MAIN')
    require(sorted(launcher_aliases) == sorted(icon[0] for icon in ICONS), 'Exactly six launcher filters')
    for key, launcher, _, _, _ in ICONS:
        alias = aliases[key]
        require(alias.get(ANDROID + 'enabled') == ('true' if key == 'DefaultIcon' else 'false'), f'Manifest default state: {key}')
        require(alias.get(ANDROID + 'icon') == '@mipmap/' + launcher, f'Icon mapping: {key}')
        require(alias.get(ANDROID + 'roundIcon') == '@mipmap/' + launcher + '_round', f'Round mapping: {key}')
        require(alias.get(ANDROID + 'targetActivity') == 'org.telegram.ui.LaunchActivity', f'Target activity: {key}')
        require(alias.get(ANDROID + 'exported') == 'true', f'Launcher must be exported: {key}')
        require(any(metadata.get(ANDROID + 'name') == 'android.app.shortcuts'
                    for metadata in alias.findall('meta-data')), f'Preserve shortcuts: {key}')
    for key in ('DefaultLauncherIcon', *RETIRED):
        require(key in aliases, f'Preserve old explicit shortcut target: {key}')
        alias = aliases[key]
        require(not alias.findall('intent-filter'), f'Retired alias must not create a launcher entry: {key}')
        require(alias.get(ANDROID + 'enabled') == 'true', f'Retired alias default remains callable: {key}')
        require(alias.get(ANDROID + 'exported') == 'true', f'Retired alias remains exported: {key}')
        require(alias.get(ANDROID + 'icon') == '@mipmap/ic_launcher', f'Retired alias uses new default artwork: {key}')
        require(alias.get(ANDROID + 'roundIcon') == '@mipmap/ic_launcher_round', f'Retired round alias uses new artwork: {key}')
        require(alias.get(ANDROID + 'targetActivity') == 'org.telegram.ui.LaunchActivity', f'Retired target activity: {key}')
    print('PASS: Manifest has six launcher filters, one install default and four non-launcher shortcut compatibility aliases')


def check_png(path, side, color_type=None):
    require(path.is_file(), f'Missing image: {path.relative_to(ROOT)}')
    header = path.read_bytes()[:26]
    require(header[:8] == b'\x89PNG\r\n\x1a\n' and header[12:16] == b'IHDR', f'Invalid PNG: {path}')
    require(struct.unpack('>II', header[16:24]) == (side, side), f'Wrong PNG dimensions: {path}')
    if color_type is not None:
        require(header[25] == color_type, f'PNG color/alpha format: {path}')


def test_source_and_retired_resources():
    source = ROOT / 'docs/app-icons/source'
    require({path.name for path in source.glob('*.jpg')} == set(SOURCE_SHA256), 'Six original JPGs are archived')
    for filename, digest in SOURCE_SHA256.items():
        require(hashlib.sha256((source / filename).read_bytes()).hexdigest() == digest,
                f'Archived source must remain byte-identical to user attachment: {filename}')
    for path in RES.glob('*/*'):
        require(not REMOVED_RESOURCE.fullmatch(path.stem), f'Retired artwork resource remains: {path.relative_to(ROOT)}')
    references = re.compile(r'(?:R\.(?:mipmap|drawable)\.|@(?:mipmap|drawable)/)(\w+)')
    checked = 0
    for module in ROOT.glob('TMessagesProj*'):
        for subdirectory in ('src', 'config'):
            for path in (module / subdirectory).rglob('*'):
                if path.suffix not in ('.java', '.kt', '.xml'):
                    continue
                checked += 1
                for name in references.findall(path.read_text()):
                    require(not REMOVED_RESOURCE.fullmatch(name),
                            f'Retired plane/clip/artwork reference: {path.relative_to(ROOT)}: {name}')
    print(f'PASS: six original JPG SHA-256 values; no retired artwork or plane overlay references in {checked} source files')


def test_resources(tmp):
    compile_sources = []
    previews = []
    controller_refs = set(re.findall(r'R\.(\w+)\.(\w+)', CONTROLLER.read_text()))
    title_names = {name for kind, name in controller_refs if kind == 'string'}
    for qualifier in ('values', 'values-zh'):
        path = RES / qualifier / 'mango_app_icons.xml'
        require(path.is_file(), f'Missing localized icon names: {path}')
        titles = ET.parse(path).getroot().findall('string')
        require(len(titles) == 6 and {title.get('name') for title in titles} == title_names,
                f'Six matching localized icon titles: {qualifier}')
        require(all(title.text and title.text.strip() for title in titles), f'Nonempty icon titles: {qualifier}')
        compile_sources.append(path)
    for kind, name in controller_refs:
        if kind in ('drawable', 'mipmap'):
            require(any(RES.glob(f'{kind}*/{name}.*')), f'Controller resource reference exists: {kind}/{name}')
    for _, launcher, foreground, background, preview in ICONS:
        preview_path = RES / 'drawable-nodpi' / f'mango_icon_{preview}.png'
        check_png(preview_path, 384, color_type=6)
        compile_sources.append(preview_path)
        previews.append(hashlib.sha256(preview_path.read_bytes()).digest())
        for density, legacy_side, foreground_side in DENSITIES:
            for suffix in ('', '_round'):
                path = RES / f'mipmap-{density}' / f'{launcher}{suffix}.png'
                check_png(path, legacy_side)
                compile_sources.append(path)
            path = RES / f'mipmap-{density}' / f'{foreground}.png'
            check_png(path, foreground_side)
            compile_sources.append(path)
        for suffix in ('', '_round'):
            path = RES / 'mipmap-anydpi-v26' / f'{launcher}{suffix}.xml'
            require(path.is_file(), f'Missing adaptive icon: {path}')
            adaptive = ET.parse(path).getroot()
            require(adaptive.tag == 'adaptive-icon', f'Adaptive icon XML type: {path}')
            require(adaptive.find('foreground').get(ANDROID + 'drawable') == '@mipmap/' + foreground,
                    f'Correct adaptive foreground: {path}')
            require(adaptive.find('background').get(ANDROID + 'drawable') == '@drawable/' + background,
                    f'Correct adaptive background: {path}')
            compile_sources.append(path)
        background_path = RES / 'drawable' / (background + '.xml')
        require(background_path.is_file(), f'Missing adaptive background: {background_path}')
        wrapper = ET.parse(background_path).getroot()
        require(wrapper.tag == 'bitmap', f'Full-bleed texture replaces the old flat-color frame: {background_path}')
        texture = background + '_texture'
        require(wrapper.get(ANDROID + 'src') == '@mipmap/' + texture,
                f'Adaptive wrapper references its own texture: {background_path}')
        for density, _, side in DENSITIES:
            texture_path = RES / f'mipmap-{density}' / (texture + '.png')
            check_png(texture_path, side, color_type=2)
            compile_sources.append(texture_path)
        compile_sources.append(background_path)
    require(len(set(previews)) == len(ICONS), 'Every supplied artwork needs its own distinct preview')
    for density, side, _ in DENSITIES:
        path = RES / f'drawable-{density}' / 'ic_launcher_dr.png'
        check_png(path, side)
        require(path.read_bytes() == (RES / f'mipmap-{density}' / 'ic_launcher.png').read_bytes(),
                f'Splash/app drawable matches new default: {density}')
        compile_sources.append(path)
    print('PASS: six distinct RGBA previews, five legacy/round densities, safe-area foreground sizes, full-bleed RGB textures, resolved adaptive wrappers and default drawable')

    sdk = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Library/Android/sdk')))
    aapt = sorted(sdk.glob('build-tools/*/aapt2'))
    if aapt:
        output = tmp / 'aapt2'
        output.mkdir()
        for path in compile_sources:
            subprocess.run([str(aapt[-1]), 'compile', str(path), '-o', str(output)], check=True)
        print(f'PASS: AAPT2 compiled {len(compile_sources)} individual resources; no APK built')
    else:
        print('SKIP: AAPT2 unavailable; XML and PNG structural validation completed')


def main():
    with tempfile.TemporaryDirectory(prefix='mango-launcher-tests-') as folder:
        tmp = Path(folder)
        test_controller(tmp)
        test_manifest()
        test_source_and_retired_resources()
        test_resources(tmp)


if __name__ == '__main__':
    main()
