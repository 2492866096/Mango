#!/usr/bin/env python3
"""Compile and exercise the standalone Mango download speed accounting logic."""
from pathlib import Path
import os, subprocess, tempfile

root = Path(__file__).resolve().parents[2]
java = Path(os.environ.get('JAVA_HOME', '/Applications/Android Studio.app/Contents/jbr/Contents/Home'))
fixtures = {
    'org/telegram/messenger/FileLoadOperation.java': '''package org.telegram.messenger; public class FileLoadOperation {}''',
    'org/telegram/messenger/AndroidUtilities.java': '''package org.telegram.messenger; public class AndroidUtilities {public static void runOnUIThread(Runnable r){r.run();}}''',
}
with tempfile.TemporaryDirectory(prefix='mango-download-speed-') as folder:
    base = Path(folder)
    sources = []
    for name, content in fixtures.items():
        path = base / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
        sources.append(path)
    sources += [root / 'TMessagesProj/src/main/java/org/telegram/messenger/MangoDownloadSpeedMonitor.java',
                root / 'Tools/tests/MangoDownloadSpeedMonitorTest.java']
    out = base / 'classes'
    subprocess.run([str(java / 'bin/javac'), '-d', str(out), *map(str, sources)], check=True)
    subprocess.run([str(java / 'bin/java'), '-cp', str(out), 'org.telegram.messenger.MangoDownloadSpeedMonitorTest'], check=True)
