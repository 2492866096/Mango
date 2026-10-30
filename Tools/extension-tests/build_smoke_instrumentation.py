#!/usr/bin/env python3
"""Build a test-only instrumentation APK without adding app dependencies."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[2]
SDK = Path(os.environ.get('ANDROID_HOME', Path.home() / 'Library/Android/sdk'))
JAVA = Path(os.environ.get('JAVA_HOME', '/Applications/Android Studio.app/Contents/jbr/Contents/Home'))
BT = SDK / 'build-tools/35.0.0'
OUTPUT = ROOT / 'build/extension-tests'
OUTPUT.mkdir(parents=True, exist_ok=True)
env = dict(os.environ, JAVA_HOME=str(JAVA))

with tempfile.TemporaryDirectory(prefix='telegram-extension-smoke-') as temporary:
    base = Path(temporary)
    init = base / 'paths.gradle'
    init.write_text('''gradle.projectsEvaluated {
        def lib = rootProject.project(':TMessagesProj')
        lib.tasks.register('extensionCompilePaths') {
            doLast {
                def task = lib.tasks.named('compileDebugJavaWithJavac').get()
                println('EXTENSION_CLASSPATH=' + task.classpath.asPath)
                println('EXTENSION_CLASSES=' + task.destinationDirectory.get().asFile.absolutePath)
            }
        }
    }''')
    result = subprocess.run([str(ROOT / 'gradlew'), '-I', str(init), ':TMessagesProj:extensionCompilePaths', '--offline', '--console=plain'], cwd=ROOT, env=env, text=True, capture_output=True, check=True)
    paths = dict(line.split('=', 1) for line in result.stdout.splitlines() if line.startswith('EXTENSION_'))
    cp = str(SDK / 'platforms/android-35/android.jar') + os.pathsep + paths['EXTENSION_CLASSES'] + os.pathsep + paths['EXTENSION_CLASSPATH']
    classes, dex = base / 'classes', base / 'dex'
    classes.mkdir()
    dex.mkdir()
    subprocess.run([str(JAVA / 'bin/javac'), '-source', '8', '-target', '8', '-cp', cp, '-d', str(classes), str(Path(__file__).with_name('ExtensionSmokeInstrumentation.java')), str(Path(__file__).with_name('MessageInsightsInstrumentationChecks.java'))], check=True, env=env)
    subprocess.run([str(BT / 'd8'), '--min-api', '21', '--lib', str(SDK / 'platforms/android-35/android.jar'), '--output', str(dex)] + [str(p) for p in classes.rglob('*.class')], check=True, env=env)
    manifest = base / 'AndroidManifest.xml'
    manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.telegram.extensiontests"><uses-sdk android:minSdkVersion="21" android:targetSdkVersion="35"/><application android:label="Extension tests"/><instrumentation android:name="org.telegram.extensiontests.ExtensionSmokeInstrumentation" android:targetPackage="app.mango.im" android:functionalTest="true"/></manifest>''')
    apk = base / 'extension-tests.apk'
    subprocess.run([str(BT / 'aapt2'), 'link', '-I', str(SDK / 'platforms/android-35/android.jar'), '--manifest', str(manifest), '-o', str(apk)], check=True, env=env)
    with zipfile.ZipFile(apk, 'a') as archive:
        for p in dex.glob('*.dex'):
            archive.write(p, p.name)
    # Uses this repository's checked-in development signing configuration.
    subprocess.run([str(BT / 'apksigner'), 'sign', '--ks', str(ROOT / 'TMessagesProj/config/release.keystore'), '--ks-key-alias', 'androidkey', '--ks-pass', 'pass:android', '--key-pass', 'pass:android', str(apk)], check=True, env=env)
    shutil.copy2(apk, OUTPUT / apk.name)
    print(OUTPUT / apk.name)
