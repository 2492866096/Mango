#!/usr/bin/env python3
"""Compile production helpers against isolated desktop fixtures; never send Telegram messages."""

import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", type=Path)
    parser.add_argument("--android-sdk", type=Path)
    args = parser.parse_args()
    here = Path(__file__).resolve().parent
    repo = here.parents[1]
    sdk = args.android_sdk or Path(os.environ.get("ANDROID_HOME", Path.home() / "Library/Android/sdk"))
    java_home = args.java_home or Path(os.environ.get("JAVA_HOME", "/Applications/Android Studio.app/Contents/jbr/Contents/Home"))
    javac = java_home / "bin/javac"
    java = java_home / "bin/java"
    if not javac.is_file():
        javac = shutil.which("javac")
        java = shutil.which("java")
    if not javac or not java:
        parser.error("Set --java-home to a JDK installation")
    json_sources = sorted(sdk.glob("sources/android-*/org/json/JSONObject.java"))
    if not json_sources:
        parser.error("Install Android SDK Sources, or pass --android-sdk")
    with tempfile.TemporaryDirectory(prefix="extension-media-regression-") as directory:
        temporary = Path(directory)
        generated = temporary / "org/json"
        generated.mkdir(parents=True)
        # Execute Android's real JSON implementation, removing platform-only annotations.
        for source in json_sources[-1].parent.glob("*.java"):
            text = source.read_text()
            text = re.sub(r"^import (?:static )?(?:android\.|libcore\.).*;\n", "", text, flags=re.M)
            text = re.sub(r"@(?:UnsupportedAppUsage|SystemApi|NonNull|Nullable)(?:\([^)]*\))?", "", text)
            (generated / source.name).write_text(text)
        production = repo / "TMessagesProj/src/main/java/org/telegram/messenger"
        sources = list((here / "fixtures").rglob("*.java"))
        sources += list((here / "tests").rglob("*.java"))
        sources += list(generated.glob("*.java"))
        sources += [production / name for name in (
            "BuiltinProxyController.java", "ExtensionMediaSettings.java", "CustomWallpaperStore.java")]
        classes = temporary / "classes"
        subprocess.run([str(javac), "-d", str(classes), *map(str, sources)], check=True)
        for test in ("BuiltinProxyRegression", "CustomWallpaperStoreSmoke"):
            subprocess.run([str(java), "-Dextension.test.dir=" + str(temporary), "-cp", str(classes), "org.telegram.messenger." + test], check=True)
        aapt2 = sorted(sdk.glob("build-tools/*/aapt2"))
        if aapt2:
            for qualifier in ("values", "values-zh"):
                resource = repo / "TMessagesProj/src/main/res" / qualifier / "extension_media.xml"
                subprocess.run([str(aapt2[-1]), "compile", str(resource), "-o", str(temporary)], check=True)
            print("PASS: English and Chinese media resources compile with AAPT2")
        else:
            print("SKIP: AAPT2 unavailable; resource compilation was not checked")


if __name__ == "__main__":
    main()
