#!/usr/bin/env python3
"""Exercise production v4 device/scheduler/file helpers only in a temporary directory."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
java = Path(os.environ.get('JAVA_HOME', '/Applications/Android Studio.app/Contents/jbr/Contents/Home'))
source = root / 'TMessagesProj/src/main/java/org/telegram/messenger'
with tempfile.TemporaryDirectory(prefix='mango-v4-device-tests-') as temporary:
    out = Path(temporary) / 'classes'
    files = [source / name for name in ('DeviceToolsUnlockGate.java', 'RootCommandRunner.java', 'DailyScheduleWindow.java', 'DuplicateFileScanner.java')]
    files += [root / 'Tools/tests/DeviceFileToolsTest.java', root / 'Tools/tests/DailyScheduleWindowTest.java']
    subprocess.run([str(java / 'bin/javac'), '-d', str(out)] + list(map(str, files)), check=True)
    subprocess.run([str(java / 'bin/java'), '-cp', str(out), 'DailyScheduleWindowTest'], check=True)
    subprocess.run([str(java / 'bin/java'), '-cp', str(out), 'org.telegram.messenger.DeviceFileToolsTest', temporary], check=True)
