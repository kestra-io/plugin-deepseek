# Kestra DeepSeek Plugin

## What

- Provides plugin components under `io.kestra.plugin.deepseek`.
- Includes classes such as `DeepseekResponseNormalizer`, `ChatCompletion`.

## Why

- This plugin integrates Kestra with DeepSeek.
- It provides tasks that call DeepSeek chat models for conversational or schema-guided JSON responses.

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin`:

- `deepseek`

Infrastructure dependencies (Docker Compose services):

- `app`

### Key Plugin Classes

- `io.kestra.plugin.deepseek.ChatCompletion`

### Project Structure

```
plugin-deepseek/
├── src/main/java/io/kestra/plugin/deepseek/
├── src/test/java/io/kestra/plugin/deepseek/
├── build.gradle
└── README.md
```

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
