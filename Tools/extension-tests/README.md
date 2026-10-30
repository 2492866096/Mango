# Extension regression checks

Run from the repository root:

```sh
python3 Tools/extension-tests/run_media_proxy_tests.py
```

Requires Python 3, a JDK, and Android SDK Sources (the tests execute the SDK's JSON implementation). The runner accepts `--java-home` and `--android-sdk`; it also reads `JAVA_HOME` and `ANDROID_HOME`. Defaults match Android Studio on macOS. If Android build tools are installed, both new media resource files are compiled with AAPT2.

The tests compile the actual proxy controller, media preference helper, and wallpaper store against desktop fixtures. All generated files live in temporary directories. Proxy subscription requests reach only a loopback HTTP server, never Telegram or the bundled subscription. No app dependencies are added.

Coverage includes deleted proxy recovery while offline, joining a refresh during fetching or application polling, callback delivery, subscription source isolation, media preference persistence, wallpaper import content and deduplication, durable copies, invalid paths, empty imports, and removal.

These are helper regressions, not Android UI or playback tests. The following still require a device: audio and round-video completion, manual playback controls, gallery cancellation, wallpaper preview/apply, and deleting a library wallpaper during its upload.

## Android smoke checks

After building the app, build the dependency-free instrumentation APK:

```sh
python3 Tools/extension-tests/build_smoke_instrumentation.py
```

Install both APKs into a disposable, signed-out emulator (the instrumentation checks that no account is signed in), then run:

```sh
adb -s emulator-5554 install -r TMessagesProj_App/build/outputs/apk/afat/debug/app.apk
adb -s emulator-5554 install -r build/extension-tests/extension-tests.apk
adb -s emulator-5554 shell am instrument -w org.telegram.extensiontests/org.telegram.extensiontests.ExtensionSmokeInstrumentation
```

This checks actual Android preferences, every menu permutation, local tag combinations and account isolation, durable wallpaper files, and renders the extension screens and tag editor. Screenshots are saved in the app's external files directory under `extension-smoke`. The test does not send chat messages or start presence simulation. Use a fresh disposable installation so the synthetic local tag fixtures do not mix with your data.

The builder reads the app's compile classpath from Gradle and uses the repository's development keystore; override `JAVA_HOME` and `ANDROID_HOME` for your machine. It adds no dependencies to the application.

Add `-e locale zh` to `am instrument` to exercise the bundled Chinese resources. This selects the locale only inside the disposable test process.
