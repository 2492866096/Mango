#!/usr/bin/env python3
"""Static cross-reference audit only; runtime rendering is not claimed by this check."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[2]
java=root/'TMessagesProj/src/main/java/org/telegram'
center=(java/'ui/ExtensionFeaturesActivity.java').read_text()
registry=(java/'messenger/MangoFeatureRegistry.java').read_text()
index=(java/'ui/MangoSettingsSearchIndex.java').read_text()
legacy=(java/'messenger/MangoLegacySettings.java').read_text()
ids={name:int(value) for name,value in re.findall(r'(ITEM_\w+)\s*=\s*(\d+)',center)}
handlers={ids[name] for name in re.findall(r'case (ITEM_\w+):',center) if name in ids}
destinations=[int(value) for value in re.findall(r'new Entry\((\d+),',index)]
assert set(destinations)<=handlers, (set(destinations)-handlers)
features=re.findall(r'new Feature\((\d+), "([^"]+)"',registry)
# Keep this audit future-proof as the extension catalog grows. The registry is
# the source of truth; the check only requires unique IDs/keys and bilingual
# resources for every registered switch.
assert len(features)>=14 and len({f[0] for f in features})==len(features) and len({f[1] for f in features})==len(features)
assert all('MangoCategory'+category in registry for category in ['All','Chat','Privacy','Media','Automation','Appearance','Tools'])
assert 'standardSearch.fillItems(items)' in center and '.open(getParentLayout())' in center
assert 'MangoResetFirst' in center and 'MangoResetSecond' in center
assert center.index('MangoResetFirst')<center.index('MangoResetSecond')<center.index('MangoLegacySettings.reset')
assert '.clear()' not in legacy and 'clearConfig' not in legacy
assert 'MangoLegacySettings.initialize(account)' in (java/'messenger/ApplicationLoader.java').read_text()
assert 'MangoSettings.restore(currentAccount, change)' in (java/'ui/MangoSettingsHistoryActivity.java').read_text()
allstrings={x.attrib['name'] for p in (root/'TMessagesProj/src/main/res/values').glob('*.xml') for x in ET.parse(p).getroot() if x.tag=='string'}
for path in ['messenger/MangoFeatureRegistry','messenger/MangoLegacySettings','ui/ExtensionFeaturesActivity','ui/MangoSettingsHistoryActivity','ui/MangoSettingsSearchIndex']:
    missing=set(re.findall(r'R.string.(\w+)',(java/f'{path}.java').read_text()))-allstrings
    assert not missing,(path,missing)
a={x.attrib['name'] for x in ET.parse(root/'TMessagesProj/src/main/res/values/v42_center.xml').getroot()}
b={x.attrib['name'] for x in ET.parse(root/'TMessagesProj/src/main/res/values-zh/v42_center.xml').getroot()}
assert a==b
print(f'Mango center static audit passed: {len(features)} switches; {len(destinations)} child search entries; {len(a)} bilingual strings; all destinations have handlers')
