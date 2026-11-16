<div align="center">

# SimpleAI

> 一款支持 **Android 4.4** 的开源 AI 聊天客户端 —— 让老旧设备也能顺畅地与大模型对话。

<br>

<a href="#功能">功能</a> ｜
<a href="#快速开始">快速开始</a> ｜
<a href="#技术栈">技术栈</a> ｜
<a href="#贡献">贡献</a>

<br>

[![Android 4.4+](https://img.shields.io/badge/Android-4.4%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/about/versions/kitkat)
[![minSdk 19](https://img.shields.io/badge/minSdk-19-orange)](app/build.gradle)
[![targetSdk 34](https://img.shields.io/badge/targetSdk-34-blue)](app/build.gradle)
[![Java 8](https://img.shields.io/badge/Java-8-007396?logo=openjdk&logoColor=white)](app/build.gradle)
[![Gradle](https://img.shields.io/badge/build-Gradle%209-02303A?logo=gradle&logoColor=white)](gradle/wrapper/gradle-wrapper.properties)
[![OkHttp 3.12](https://img.shields.io/badge/OkHttp-3.12.x-512BD4)](gradle/libs.versions.toml)
[![License: Open Source](https://img.shields.io/badge/license-Open%20Source-green)](.)

</div>

SimpleAI 是一个运行在 Android 上的 AI 聊天客户端，可接入 DeepSeek、智谱 GLM、Kimi、OpenAI 等主流大模型服务，并提供自定义服务商能力。它专为兼容 **Android 4.4（API 19）及更高版本** 而设计：从网络库、加密方式到语法特性都采用低版本友好的实现，让手里的旧手机、车机或平板也能成为称手的 AI 助手。

> [!NOTE]
> 核心亮点：**支持 Android 4.4（KitKat）**。`minSdk 19`，同时兼容到最新 Android 14（`targetSdk 34`）。

## 主要功能

1. **支持 Android 4.4+**：`minSdk 19`，兼容 KitKat 老设备；仅依赖 JDK / Gson 原生 API 与 OkHttp 3.12（最后一个支持 Android 4.x 的版本），无高版本专属特性。
2. **免费 & 开源**：本地 SQLite 存储，数据掌握在自己手中。
3. **多服务商**：内置 DeepSeek、智谱 GLM、Kimi、OpenAI 预设，并支持任意 OpenAI 兼容端点（Claude、Gemini、豆包、书生、SiliconFlow、阿里云百炼等按 host 自动适配）。
4. **流式输出**：SSE 实时打印，思考过程独立展示，支持随时停止与一键重试。
5. **推理深度可调**：关闭 / 自动 / 浅 / 中 / 深五档，按服务商自动注入 `thinking`、`enable_thinking`、`reasoning_effort` 等参数。
6. **模型能力自动识别**：移植自 rikkahub 的 `ModelRegistry`，按模型 ID 推断输入/输出模态与工具调用、推理能力。
7. **会话管理**：历史会话持久化、搜索、批量选择与删除；支持 JSON / Markdown / TXT 三种格式导出。
8. **国际化与主题**：中英双语、浅色 / 深色 / 跟随系统主题、打字机效果，以及平板宽屏渲染。

## 快速开始

### 环境要求

- JDK 17 及以上
- Android SDK Platform 34 及 Build Tools
- Android Studio（推荐）或命令行 Gradle

### 构建安装

```bash
git clone <repository-url>
cd SimpleAI
./gradlew assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`，安装到 Android 4.4 及以上设备即可。

> [!NOTE]
> 项目已启用 MultiDex，并针对 Android 4.x 做了兼容处理（如使用 `URI` 解析 host、`Random` 替代 `ThreadLocalRandom`、正则解析替代 Jsoup、`javax.crypto` 实现 AES）。

### 首次使用

1. 打开应用，进入 **设置 → API 配置**。
2. 选择一个内置预设（DeepSeek / 智谱 GLM / Kimi / OpenAI），或新增自定义配置，填入 API Key。
3. 点击 **测试连接** 校验配置，并将其设为 **当前使用**。
4. 返回聊天页，选择模型，即可开始对话。

## 技术栈

- **语言**：Java 8
- **构建**：Gradle 9 + Android Gradle Plugin
- **UI**：AppCompat / ConstraintLayout / RecyclerView / Fragment
- **网络**：OkHttp 3.12.x + Gson，SSE 流式解析
- **Markdown**：Markwon（表格 / 删除线 / 代码高亮 / LaTeX / 图片）
- **存储**：SQLite + SharedPreferences，MultiDex
- **加密**：AES/CBC/PKCS5Padding（256 位密钥）

## 贡献

欢迎任何 Issues / Pull Requests！只需要将你的更改提交到此项目。

对于新功能的添加，建议先通过 Issue 讨论。提交前请确保：

```bash
./gradlew assembleDebug
```

能够正常构建通过。

## Star History

> [!TIP]
> 如果本项目对你有帮助，请给项目一个 Star，这是持续维护的动力。

<!--
<div align="center">
  <img src="https://api.star-history.com/svg?repos=<owner>/SimpleAI&type=Date" alt="Star History Chart">
</div>
-->

<div align="center">

_让每一台旧设备，也能拥有与大模型对话的能力。_

</div>
