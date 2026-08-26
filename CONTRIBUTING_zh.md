# 贡献指南 [English](CONTRIBUTING.md)

感谢你有意贡献。这是一个使用 Jetpack Compose 和 Gradle 构建的 Kotlin Android 项目。

## 快速开始

1. Fork 本仓库。
2. 克隆你的 Fork 并创建分支：`git checkout -b my-change`
3. 构建和测试：`./gradlew assembleDebug`
4. 提交、推送并创建指向 `master` 的 PR。

## 项目结构

```
app/src/main/java/at/creepervm1000/mobileclaw/
  agent/       — AgentEngine：提示词构建、对话循环、工具调度
  core/        — Prefs、AgentFiles、CronStore、Notifier
  llm/         — LLM 客户端抽象、HTTP 层
  tools/       — 工具定义和实现
  ui/          — Compose 界面（Chat、Settings）、ViewModel、主题
  service/     — 前台服务、开机接收器、电池监控
```

## 添加工具

1. 在 `tools/` 下对应文件中创建函数（或新建文件）。
2. 在 `ToolRegistry.kt` 中注册，提供名称、描述和参数 schema。
3. 代理引擎会自动将其包含在系统提示词的工具列表中。

工具接收 JSON 参数并返回字符串。保持返回值简洁——它们会占用上下文窗口。

## 代码风格

- 遵循现有代码风格。使用 Kotlin 惯例，避免不必要的冗余。
- Compose UI 放在 `ui/` 目录。保持 ViewModel 轻量。
- 如果添加了依赖，请更新 `gradle/libs.versions.toml`。

## 测试

目前没有自动化测试套件。请通过构建、安装到设备/模拟器，并配合实际的 LLM 后端进行端到端验证。

## PR 要求

- 每个 PR 只做一个逻辑改动。
- 包含清晰的改动说明和原因。
- 如果添加了工具，解释其功能并展示示例输出。
- 如果修复了 Bug，请包含复现步骤。