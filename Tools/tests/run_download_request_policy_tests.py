#!/usr/bin/env python3
"""Compile and exercise the bounded download request policy."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
java = Path(os.environ.get("JAVA_HOME", "/Applications/Android Studio.app/Contents/jbr/Contents/Home"))
with tempfile.TemporaryDirectory(prefix="mango-download-policy-") as folder:
    base = Path(folder)
    out = base / "classes"
    subprocess.run([
        str(java / "bin" / "javac"), "-d", str(out),
        str(root / "TMessagesProj/src/main/java/org/telegram/messenger/DownloadRequestPolicy.java"),
        str(root / "Tools/tests/DownloadRequestPolicyTest.java"),
    ], check=True)
    subprocess.run([
        str(java / "bin" / "java"), "-cp", str(out),
        "org.telegram.messenger.DownloadRequestPolicyTest",
    ], check=True)
