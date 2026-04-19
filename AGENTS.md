# Kestra DeepSeek Plugin

## What

- Provides plugin components under `io.kestra.plugin.deepseek`.
- Includes classes such as `DeepseekResponseNormalizer`, `ChatCompletion`.

## Why

- What user problem does this solve? Teams need to call DeepSeek chat models for conversational or schema-guided JSON responses from orchestrated workflows instead of relying on manual console work, ad hoc scripts, or disconnected schedulers.
- Why would a team adopt this plugin in a workflow? It keeps DeepSeek steps in the same Kestra flow as upstream preparation, approvals, retries, notifications, and downstream systems.
- What operational/business outcome does it enable? It reduces manual handoffs and fragmented tooling while improving reliability, traceability, and delivery speed for processes that depend on DeepSeek.

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
