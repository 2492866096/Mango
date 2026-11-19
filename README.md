<div align="center">

# 🥭 Mango Client · 芒果客户端

**面向 Android 的 Telegram 第三方增强客户端**  
**An independent, enhanced Telegram client for Android**

[![License: GPL v2](https://img.shields.io/badge/License-GPL--2.0-blue.svg)](LICENSE)
![Platform](https://img.shields.io/badge/Platform-Android-brightgreen)
![Version](https://img.shields.io/badge/Version-5.0%20(7010)-orange)

[简体中文](#-简体中文) · [English](#-english) · [源码 / Source](https://github.com/2492866096/Mango) · [版本发布 / Releases](https://github.com/2492866096/Mango/releases) · [官方频道 / Official Channel](https://t.me/hhhhp) · [修改建议 / Bug 反馈](https://t.me/zsq888)

</div>

---

## 🇨🇳 简体中文

### 项目简介

**芒果客户端（Mango Client）** 是基于 Telegram Android 源码开发的独立第三方客户端，重点改善消息处理效率、聊天管理、媒体体验、界面定制和隐私控制。项目开放源代码，方便用户了解实现方式，也欢迎开发者审查、反馈与贡献。

> **非官方声明：** 芒果客户端不是 Telegram 官方应用，也不隶属于 Telegram。Telegram 名称及相关商标归其相应权利人所有。

### ✨ 核心功能

| 分类 | 主要能力 |
| --- | --- |
| 💬 消息与聊天 | 消息管理、历史定位、关键词提醒、发送前确认、快捷操作 |
| 📁 文件与媒体 | 下载体验优化、媒体筛选、文件清理与保存辅助工具 |
| 🔐 隐私与安全 | 隐私设置增强、风险提醒、可自定义的交互行为 |
| 🌐 网络与代理 | 代理与连接管理、网络体验相关增强 |
| 🎨 个性与效率 | 界面定制、扩展中心、设置搜索及多种效率工具 |

功能随版本变化，具体以客户端内设置、版本更新记录及源码实现为准；部分功能可能受 Telegram 服务端规则、系统权限和网络环境限制。
🥭 芒果客户端 5.1.0｜更新及开源说明

🦖 版本更新

- 自动表情回应默认关闭，开启需二次确认，支持频道、群组、私聊独立设置。
- 新增在线图标开关，默认开启。
- 新增禁止点按切换头像开关，默认关闭。
- 翻译消息时自动忽略蓝色链接，优化翻译速度。
- 移除「已读乱回」功能。

⚠️ 关于自动加入频道功能

芒果客户端 5.1.0 源码仍保留历史自动加入频道逻辑，二次开发者请自行删除相关代码后再编译使用。

1. 实际加入范围

开发过程中，该功能实际仅配置过芒果客户端官方频道 "@hhhhp"，未配置其他频道、群组或机器人。

固定直链核查地址：

https://1812652903.cdn.123clouddisk.com/1812652903/GY/MG.txt#

该链接地址承诺保持不变，欢迎用户自行核查。

2. 关于任务队列和限流机制

早期开发时，曾考虑扩展机器人及交流群的自动加入功能，因此预先设计了加入任务队列和 Telegram 限流等待机制，用于控制请求频率。

相关扩展方案并未实际启用，代码中存在这些机制，不代表实际执行过其他频道、群组或机器人的自动加入操作。📌  关于直链更新 APP
直链更新是早期开发的功能，主要用于方便用户获取最新版本安装包。
该功能需要用户主动操作才能获取更新，不存在用户不知情的情况下自动安装 APP 的行为。
此外，相关云盘不支持TG类的软件包括第三方 更新链接早已因审核被屏蔽，目前已无法正常获取更新。
7月份已违规 https://t.me/hhhhp/17539
直链云盘地址 https://1812652903.v.123pan.cn/1812652903/GY/芒果客户端.apk
考虑到该功能已失去作用，后续更新将同步取消直链更新功能

3. 关于历史版本

5.1.0 及此前版本的源码仍保留自动加入频道的相关逻辑。

请二次开发者在编译或分发前自行删除相关代码，并检查关联调用。

该功能已在 5.1 之后的开发版本中取消。

4. 关于用户知情权

我们承认，早期未经用户明确确认便自动加入官方频道的设计存在不足。

对此我们接受用户的批评与监督，并在后续版本中取消了该功能。

📢 关于后续停止开源

开源的初衷是提高透明度、接受用户监督，但部分争议经过多次解释仍未能消除误解，也消耗了大量开发精力。

经过慎重考虑，5.1.0 将作为芒果客户端最后一个公开源码版本，后续版本转为闭源开发。

停止开源不代表停止更新。芒果客户端仍将持续维护、优化，并接受合理的意见与监督。

📦 下载与反馈

- 5.1.0 正式版： https://github.com/2492866096/Mango/releases/tag/v5.1.0
- 官方频道： https://t.me/hhhhp
- 交流群： https://t.me/AKUOD
- 问题反馈： https://t.me/zsq888

> Releases 页面中的安装包需要由项目维护者单独发布；仓库公开源码不代表已经发布 APK。请优先从可信的项目发布渠道获取安装包，并核对版本及签名信息。

### 🛠️ 源码构建

本仓库包含 Gradle 工程、`TMessagesProj`、`TMessagesProj_App`、JNI 与所需的第三方源码。主要步骤如下：

1. 使用 Android Studio **Open** 仓库根目录（不要只打开 `TMessagesProj`）。
2. 配置 Android SDK；根据 `TMessagesProj/build.gradle` 安装匹配的 NDK / CMake 版本。
3. 使用项目支持的 JDK（源码包构建说明提及 JDK 17 / 21），完成 Gradle Sync。
4. 按需配置自己的 Telegram API 凭据、Google 服务配置及签名信息；不要把真实密钥提交到公开仓库。
5. 在满足项目依赖与配置的环境下构建 Debug：

```bash
./gradlew :TMessagesProj_App:assembleDebug
```

Debug 产物通常位于：

```text
TMessagesProj_App/build/outputs/apk/debug/
```

Release 构建命令：

```bash
./gradlew :TMessagesProj_App:assembleRelease
```

Release 构建还需要你自己的签名文件与相应 Gradle 配置。仓库中用于开源展示的 Google API Key 已替换为占位符，相关服务在正确配置前可能无法工作。**这里提供的是构建入口说明，不代表已验证任意开发环境均能直接编译通过。**

更多细节见 [`mango_build_readme.txt`](mango_build_readme.txt)，但请勿照搬其中涉及发布签名密钥的历史打包说明，应以你自己的安全签名配置为准。

### 🔏 隐私与安全

芒果客户端的部分增强功能设计为在设备本地运行。公开源码是为了提高可审查性，但不等于已经完成第三方安全审计。

- Telegram 消息与媒体通信仍需要连接 Telegram 服务及你所选择的网络路径或代理。
- 使用翻译、外部链接、更新检查或其他联网功能时，可能发生相应网络请求，具体行为请以源码和功能设置为准。
- 不要在 Issues、日志或提交记录中公开手机号、会话、API Key、Bot Token、代理凭据、签名文件或其他私人数据。
- 欢迎通过源码审查与 Issues 提出可复现的安全或隐私问题。

### 📜 开源许可与致谢

本仓库基于 [Telegram Android](https://github.com/DrKLO/Telegram) 的开放源码开发，保留了上游及第三方组件的版权和许可证文件。仓库根目录附有 [`LICENSE`](LICENSE)（GNU GPL v2）；第三方组件可能适用不同许可证，以其各自文件为准。

重新分发、修改或编译本项目时，请遵守适用许可证、保留必要的版权声明，并按许可证要求提供对应源码。项目使用自己的应用名称和视觉标识，不代表 Telegram 官方认可或背书。

### 🤝 参与项目

欢迎提出 Bug、修改建议和 Pull Request：

- 📢 **官方频道：** [芒果客户端官方频道](https://t.me/hhhhp)
- 🐛 **修改建议 / Bug 反馈：** [@zsq888](https://t.me/zsq888)
- 💬 **交流群：** [Telegram 交流群](https://t.me/AKUOD)
- [GitHub Issues：公开问题追踪](https://github.com/2492866096/Mango/issues)
- [Pull Requests：提交代码改进](https://github.com/2492866096/Mango/pulls)

提交问题前请先检查是否已有相同反馈，并尽量提供系统版本、客户端版本、复现步骤及已脱敏的日志。

---

## 🌍 English

### About

**Mango Client** is an independent, third-party Android client built on Telegram's open-source Android codebase. It focuses on more efficient messaging, chat management, media handling, interface customization, and privacy controls.

The source code is published for transparency, review, and community contributions. **Mango Client is not an official Telegram app and is not affiliated with Telegram.**

### ✨ Highlights

| Area | Features |
| --- | --- |
| 💬 Messaging | Chat and message management, history navigation, keyword alerts, send confirmations, quick actions |
| 📁 Media & Files | Download-related improvements, media filtering, file organization and cleanup tools |
| 🔐 Privacy & Safety | Additional privacy settings, risk reminders, customizable interaction behavior |
| 🌐 Connectivity | Proxy and network management enhancements |
| 🎨 Customization | UI customization, an extension center, settings search, and productivity tools |

Availability varies by release, Android version, server-side behavior, and user settings.

### 📦 Download & Versions

- **Source version:** `5.0 (7010)`
- **Android application ID:** `app.mango.im`
- **Source code:** [GitHub repository](https://github.com/2492866096/Mango)
- **APK releases:** [GitHub Releases](https://github.com/2492866096/Mango/releases)

> APK files are published separately by the maintainers. An open-source repository does not necessarily contain downloadable release binaries.

### 🛠️ Building from Source

1. Open the **repository root** in Android Studio.
2. Install the required Android SDK, NDK, and CMake versions as specified by the Gradle project.
3. Configure a compatible JDK and allow Gradle to sync dependencies.
4. Provide your **own** API credentials, service configuration, and signing materials where required. Do not commit secrets.
5. Build a Debug APK:

```bash
./gradlew :TMessagesProj_App:assembleDebug
```

For a release build, configure your own signing credentials and use:

```bash
./gradlew :TMessagesProj_App:assembleRelease
```

See [`mango_build_readme.txt`](mango_build_readme.txt) for additional project-specific notes. The public source has placeholder Google API keys, so integrations that rely on those keys may not function until configured. These commands are provided as build entry points, **not as a guarantee of reproducible or successful builds on every environment**.

### 🔏 Privacy & Security

Some enhancements are designed to run locally on the device. Source availability makes the code reviewable, but is not a substitute for an independent security audit.

Telegram messaging still depends on Telegram's services and the network route or proxy you choose. Features that access external services may make additional network requests. Never include personal data, tokens, signing keys, or authentication credentials in public issues or commits.

### 📜 Licensing & Attribution

This project is based on [Telegram for Android](https://github.com/DrKLO/Telegram). The repository retains upstream and third-party notices and license files. The repository root includes [`LICENSE`](LICENSE) (GNU GPL v2); individual dependencies may be governed by their own licenses.

When modifying or redistributing the project, follow all applicable license obligations and preserve required attributions. This independent project is not endorsed by Telegram.

### 🤝 Contributing & Community

Bug reports, improvement suggestions, and pull requests are welcome:

- 📢 **Official channel:** [Mango Client](https://t.me/hhhhp)
- 🐛 **Suggestions & Bug Reports:** [@zsq888](https://t.me/zsq888)
- 💬 **Discussion group:** [Telegram community](https://t.me/AKUOD)
- [GitHub Issues](https://github.com/2492866096/Mango/issues)
- [Pull Requests](https://github.com/2492866096/Mango/pulls)

Please include a clear reproduction procedure and redact any private data before sharing logs.

---

<div align="center">

**🥭 Mango Client · Open Source · Built for a better Telegram experience**

</div>
