#!/usr/bin/env python3
"""Runs production keyword/date rules and the actual SQL against a fixture database."""
from pathlib import Path
import os
import re
import sqlite3
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
JAVA = Path(os.environ.get('JAVA_HOME', '/Applications/Android Studio.app/Contents/jbr/Contents/Home')) / 'bin'
with tempfile.TemporaryDirectory(prefix='message-insights-') as temp:
    subprocess.run([str(JAVA / 'javac'), '-d', temp,
                    str(ROOT / 'TMessagesProj/src/main/java/org/telegram/messenger/MessageInsightsRules.java'),
                    str(ROOT / 'Tools/tests/MessageInsightsRulesTest.java')], check=True)
    subprocess.run([str(JAVA / 'java'), '-cp', temp, 'MessageInsightsRulesTest'], check=True)

source = (ROOT / 'TMessagesProj/src/main/java/org/telegram/messenger/LocalMessageInsightsRepository.java').read_text()
stat_query = re.search(r'STATISTICS_SQL = "([^"]+)"', source)[1]
dialog_query = re.search(r'DIALOGS_SQL = "([^"]+)"', source)[1]
memorial = (ROOT / 'TMessagesProj/src/main/java/org/telegram/messenger/MessageMemorialStore.java').read_text()
capture_query = re.search(r'CAPTURE_SQL = "([^"]+)"', memorial)[1]
storage = (ROOT / 'TMessagesProj/src/main/java/org/telegram/messenger/MessagesStorage.java').read_text()
schema = re.search(r'"(CREATE TABLE messages_v2\([^"\n]+)"', storage)[1]
db = sqlite3.connect(':memory:')
db.execute(schema)
fixtures = [(1, 100, 100, 0, 0), (2, 100, 150, 1, 0), (3, 100, 200, 0, 0),
            (4, 100, 180, 1, 1), (-1, 100, 160, 1, 0), (1, 200, 140, 0, 0), (1, -200, 130, 0, 0)]
for mid, uid, date, out, state in fixtures:
    db.execute('INSERT INTO messages_v2(mid,uid,date,out,send_state,data) VALUES(?,?,?,?,?,?)', (mid,uid,date,out,state,b'fixture'))
assert db.execute(stat_query, (100, 100, 200)).fetchall() == [(100, 0), (150, 1)], 'range boundaries, peer isolation, pending exclusions'
assert db.execute(stat_query, (100, 100, 201)).fetchall() == [(100, 0), (150, 1), (200, 0)], 'inclusive end day expressed through exclusive midnight'
assert {x[0] for x in db.execute(dialog_query)} == {100, 200, -200}, 'includes inaccessible peers with cache even if no dialogs row'
assert len(db.execute(capture_query, (100,)).fetchall()) == 5, 'snapshot preserves all cached records rather than visible page'
assert len(db.execute(capture_query, (200,)).fetchall()) == 1, 'snapshot peer isolation'
print('MessageInsights SQL: 5 fixture assertions passed')
