# Contributing [中文版](CONTRIBUTING_zh.md)

Thanks for wanting to contribute. This is a Kotlin Android project using Jetpack Compose and Gradle.

## Getting started

1. Fork the repo.
2. Clone your fork and create a branch: `git checkout -b my-change`
3. Build and test: `./gradlew assembleDebug`
4. Commit, push, and open a PR targeting `master`.

## Project structure

```
app/src/main/java/at/creepervm1000/mobileclaw/
  agent/       — AgentEngine: prompt construction, turn loop, tool dispatch
  core/        — Prefs, AgentFiles, CronStore, Notifier
  llm/         — LLM client abstraction, HTTP layer
  tools/       — Tool definitions and implementations
  ui/          — Compose screens (Chat, Settings), ViewModel, theme
  service/     — Foreground service, boot receiver, battery monitor
```

## Adding a tool

1. Create a function in the appropriate file under `tools/` (or a new file).
2. Register it in `ToolRegistry.kt` with a name, description, and parameter schema.
3. The agent engine will automatically include it in the system prompt's tool list.

Tools receive JSON parameters and return a string. Keep return values concise — they eat into context.

## Style

- Follow existing code style. Kotlin conventions, no unnecessary verbosity.
- Compose UI goes in `ui/`. Keep ViewModels thin.
- If you add a dependency, update `gradle/libs.versions.toml`.

## Testing

There's no automated test suite yet. Test by building, installing on a device/emulator, and verifying the tool or UI change works end-to-end with an actual LLM backend.

## PR expectations

- One logical change per PR.
- Include a clear description of what changed and why.
- If you're adding a tool, explain what it does and show example output.
- If you're fixing a bug, include reproduction steps.