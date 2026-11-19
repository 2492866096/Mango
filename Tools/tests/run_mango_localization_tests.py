#!/usr/bin/env python3
"""Validate Mango resource coverage and exercise the production locale-selection methods."""
from pathlib import Path
import json
import os
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / 'TMessagesProj/src/main/res'
SOURCE = (ROOT / 'TMessagesProj/src/main/java/org/telegram/messenger/LocaleController.java').read_text()
MANIFEST = json.loads((Path(__file__).with_name('mango_extension_keys.json')).read_text())
FORMAT = re.compile(r'%(?:\d+\$)?(?:[-#+ 0,(]*\d*(?:\.\d+)?)?[sdf]|%%')
CJK = re.compile(r'[\u3400-\u9fff]')
checks = 0


def check(value, message):
    global checks
    checks += 1
    if not value:
        raise AssertionError(message)


def catalog(directory):
    values = {}
    for path in (RES / directory).glob('*.xml'):
        root = ET.parse(path).getroot()
        for item in root:
            if item.tag != 'string':
                continue
            key = item.get('name')
            check(key not in values, f'{directory}: duplicate key {key}')
            values[key] = ''.join(item.itertext())
    return values


base = catalog('values')
keys = set(MANIFEST['keys'])
for key in keys:
    check(key in base, f'Unknown manifest key: {key}')
    check(bool(base[key].strip()), f'Empty default: {key}')
    check(not CJK.search(base[key]), f'Non-English fallback: {key}')
for directory in MANIFEST['locales']:
    values = catalog(directory)
    for key in keys:
        check(key in values, f'{directory}: missing {key}')
        value = values[key]
        check(bool(value.strip()), f'{directory}: empty {key}')
        check(sorted(FORMAT.findall(value)) == sorted(FORMAT.findall(base[key])),
              f'{directory}: incompatible placeholders in {key}')
        check('QZX' not in value and 'PH000' not in value and
              not any(0xE000 <= ord(c) <= 0xF8FF for c in value),
              f'{directory}: translation marker leaked in {key}')
        if directory not in ('values-zh', 'values-zh-rTW', 'values-ja', 'values-ko'):
            check(not CJK.search(value), f'{directory}: Chinese fallback leaked in {key}')
    for key in ('ContactsTabTitle', 'SettingsContactsTab'):
        check('tab' not in values[key].lower(), f'{directory}: Contacts still contains Tab')
    check(values['ContactsTabTitle'] == values['SettingsContactsTab'],
          f'{directory}: Contacts label differs between entry points')
    check('channel:1234567890' in values['MangoScamUsersFormat'] and
          '@channel_name' in values['MangoScamUsersFormat'],
          f'{directory}: channel syntax example must remain a valid identifier')
check(base['ContactsTabTitle'] == base['SettingsContactsTab'] == 'Contacts', 'Default Contacts label')
# Formatted strings and server-string fallback must use the same bundled locale as titles.
check('ApplicationLoader.applicationContext.getString(' not in SOURCE,
      'A resource fallback bypasses the selected language')


def block(marker):
    start = SOURCE.index(marker)
    end = SOURCE.index('{', start) + 1
    depth = 1
    while depth:
        depth += (SOURCE[end] == '{') - (SOURCE[end] == '}')
        end += 1
    return SOURCE[start:end]


methods = '\n'.join(block(marker) for marker in (
    '    private synchronized Context getCurrentLanguageResourceContext()',
    '    private static String getResourceLanguageTag(LocaleInfo info)'))
java = r'''
import java.util.Locale;
class LocaleSelectionTest {
    static class TextUtils { static boolean isEmpty(String value) { return value == null || value.isEmpty(); } }
    static class FileLog { static void e(Exception e) { throw new AssertionError(e); } }
    static class Configuration {
        Locale locale; int density; boolean night;
        Configuration(String tag) { locale = Locale.forLanguageTag(tag); }
        Configuration(Configuration other) { locale=other.locale; density=other.density; night=other.night; }
        void setLocale(Locale locale) { this.locale=locale; }
        public boolean equals(Object o) { Configuration c=(Configuration)o; return locale.equals(c.locale) && density==c.density && night==c.night; }
    }
    static class Resources { Configuration c; Resources(Configuration c) { this.c=c; } Configuration getConfiguration() { return c; } }
    static class Context {
        Resources r; Context(String tag) { r=new Resources(new Configuration(tag)); }
        Context(Configuration c) { r=new Resources(new Configuration(c)); }
        Resources getResources() { return r; }
        Context createConfigurationContext(Configuration c) { return new Context(c); }
    }
    static class ApplicationLoader { static Context applicationContext = new Context("en"); }
    static class LocaleInfo { String shortName, baseLangCode, pluralLangCode;
        LocaleInfo(String shortName, String base, String plural) { this.shortName=shortName; this.baseLangCode=base; this.pluralLangCode=plural; }
    }
    private Context bundledLanguageContext;
    private Configuration bundledLanguageConfiguration;
    private String bundledLanguageTag;
    private LocaleInfo currentLocaleInfo;
    private static int passed;
    static void check(boolean value,String msg) { passed++; if(!value) throw new AssertionError(msg); }
    static void tag(String expected, String shortName,String base,String plural) {
        check(expected.equals(getResourceLanguageTag(new LocaleInfo(shortName,base,plural))),shortName+" => "+expected);
    }
    public static void main(String[] args) {
        tag("en","en",null,null);
        tag("zh","zh_CN",null,"en");
        tag("zh-TW","zh-Hant",null,"zh");
        tag("zh-TW","zh_Hant_TW",null,"zh");
        tag("zh-TW","zh_hk",null,"zh");
        tag("zh-TW","local_zh_tw_raw",null,"zh");
        tag("pt-BR","pt_br",null,null);
        tag("pt-BR","pt-PT",null,null);
        tag("es","es-419",null,"en");
        tag("fr","unofficial-fr",null,"en");
        tag("de","remote_de_legacy",null,"en");
        tag("id","in",null,null);
        tag("ru","custom-pack","ru","en");
        tag("ja","custom-pack",null,"ja");
        tag("en","unknown",null,null);
        LocaleSelectionTest controller=new LocaleSelectionTest();
        check(controller.getCurrentLanguageResourceContext()==ApplicationLoader.applicationContext,"no language returns application context");
        controller.currentLocaleInfo=new LocaleInfo("zh-Hant",null,"zh");
        Context first=controller.getCurrentLanguageResourceContext();
        check(first.r.c.locale.toLanguageTag().equals("zh-TW"),"selected language wins over plural/system language");
        check(first==controller.getCurrentLanguageResourceContext(),"reuse unchanged context");
        ApplicationLoader.applicationContext.r.c.night=true;
        Context night=controller.getCurrentLanguageResourceContext();
        check(first!=night && night.r.c.night,"update night configuration");
        ApplicationLoader.applicationContext.r.c.density=320;
        Context dense=controller.getCurrentLanguageResourceContext();
        check(dense!=night && dense.r.c.density==320,"update density configuration");
        controller.currentLocaleInfo=new LocaleInfo("ar",null,"ar");
        Context arabic=controller.getCurrentLanguageResourceContext();
        check(dense!=arabic && arabic.r.c.locale.toLanguageTag().equals("ar"),"live language switch");
        System.out.println("Locale selection checks: "+passed);
    }
'''
java += methods + '\n}\n'
java_bin = Path(os.environ.get('JAVA_HOME', '/Applications/Android Studio.app/Contents/jbr/Contents/Home')) / 'bin'
with tempfile.TemporaryDirectory(prefix='mango-locales-') as temp:
    target = Path(temp) / 'LocaleSelectionTest.java'
    target.write_text(java)
    subprocess.run([str(java_bin / 'javac'), '-encoding', 'UTF-8', str(target)], check=True)
    subprocess.run([str(java_bin / 'java'), '-cp', temp, 'LocaleSelectionTest'], check=True)
print(f'Mango resource checks: {checks}; {len(keys)} keys across {len(MANIFEST["locales"]) + 1} languages')
