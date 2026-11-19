芒果客户端 5.1.0（7011）源码包
导出时间：2026-11-12 18:35:06（Asia/Shanghai）

本包从当前工作区直接导出，包含当前修改及新增源码。
构建环境以本说明及项目 Gradle 配置为准；原 README.md 中较早版本的环境说明仅供参考。

一、导入项目
1. 解压整个源码包到可写目录。
2. 使用支持 Android Gradle Plugin 8.13.2 的 Android Studio，选择 Open，打开包含 settings.gradle 的项目根目录。
3. Gradle JDK 选择 JDK 17 或 21，可使用兼容版本的 Android Studio 内置 JBR。
4. 在 SDK Manager 安装 Android SDK Platform 35、Build Tools 35.0.0、NDK 21.4.7075529、CMake 3.10.2。
5. 由 Android Studio 配置本机 SDK 路径并生成 local.properties，等待 Gradle Sync 完成。
首次同步和构建需要联网下载 Gradle 与 Maven 依赖。

二、构建
项目附带 Gradle Wrapper 8.13，使用 Android Gradle Plugin 8.13.2、Kotlin Gradle Plugin 1.9.20。
Android Studio 的 Build Variants 中，应用模块选择 TMessagesProj_App，变体选择 afatDebug 或 afatRelease，再构建 APK。

macOS/Linux 可在项目根目录执行：
  chmod +x gradlew
  ./gradlew :TMessagesProj_App:assembleAfatDebug --console=plain

发布版本构建命令：
  ./gradlew :TMessagesProj_App:assembleAfatRelease --console=plain

Windows 可通过 Android Studio 的 Gradle 面板执行上述 assemble 任务。

三、包含内容
三个 Gradle 模块、Java/JNI 源码、资源、工具及测试脚本、文档、Gradle Wrapper 和现有项目配置。
libs 下的 AAR 与 JNI 目录内的第三方静态库属于构建输入，已保留。
现有 gradle.properties、两个 google-services.json、签名配置及其 keystore 一并保留，以匹配项目当前构建配置。
本次更新说明见 docs/2026-11-12-update.md。

四、排除内容与检查范围
已排除 APK/AAB、Git 历史、IDE 配置、Gradle/NDK 构建缓存、各模块 build 目录、本机 local.properties 及 Python 缓存。
本次只导出源码，检查 ZIP 完整性、关键文件及源码与当前工作区的一致性。APK 由接收方配置环境后自行构建。
