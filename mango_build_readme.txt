芒果客户端 4.8 (7008) 源码包
===============================
包含内容：完整 Gradle 工程（TMessagesProj / TMessagesProj_App / TMessagesProj_AppTests）、
JNI 与第三方源码、gradle wrapper、签名配置与 release.keystore。
不包含：.git、build/、.cxx/、.gradle/、.idea/、local.properties、afat 产物、APK。

导入与构建：
1. Android Studio 直接 Open 本目录（不要只打开 TMessagesProj 子目录）。
2. 首次打开会自动生成 local.properties；若未生成，手动添加：
   sdk.dir=/你的/Android/Sdk
3. JDK 使用 Android Studio 自带 JBR（JDK 17/21 均可）。
4. 等待 Gradle Sync 完成（首次会下载依赖与 NDK/CMake，需要联网）。
5. 命令行构建 Debug：
   ./gradlew :TMessagesProj_App:assembleDebug
   产物：TMessagesProj_App/build/outputs/apk/debug/
6. 构建 Release（使用包内 keystore 配置）：
   ./gradlew :TMessagesProj_App:assembleRelease
   签名参数见 gradle.properties（RELEASE_STORE_PASSWORD / RELEASE_KEY_ALIAS 等）。

注意：
- NDK/CMake 版本需与 TMessagesProj/build.gradle 内 ndkVersion 一致，SDK Manager 中安装对应版本。
- 首次编译 JNI 需要较长时间与足够内存（建议 16GB 以上）。
- 应用包名：app.mango.im；版本：4.8 (7008)。
