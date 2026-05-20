# How to use the DeepSeek plugin

Call DeepSeek chat models for conversational completions and structured JSON responses.

## Authentication

Set `apiKey` to your DeepSeek API key. Store it in a [secret](https://kestra.io/docs/concepts/secret). The `baseUrl` defaults to `https://api.deepseek.com/v1` — override it if you are using a self-hosted or OpenAI-compatible proxy.

## Tasks

`ChatCompletion` sends a list of messages to a DeepSeek model and returns the response. Set `jsonResponseSchema` to enable JSON mode — the model will return a structured JSON response guided by the schema you provide. Note that DeepSeek treats the schema as a prompt-level hint rather than performing server-side validation, so downstream parsing should still handle unexpected output shapes.
