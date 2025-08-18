package io.kestra.plugin.deepseek;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@SuperBuilder
@Getter
@NoArgsConstructor
@ToString
@EqualsAndHashCode
@Plugin(
    examples = {
        @Example(
            title = "Chat completion with DeepSeek",
            full = true,
            code = """
                id: deepseek_chat
                namespace: company.team

                tasks:
                  - id: chat_completion
                    type: io.kestra.plugin.deepseek.ChatCompletion
                    apiKey: "{{ secret('DEEPSEEK_API_KEY') }}"
                    modelName: deepseek-chat
                    messages:
                      - type: SYSTEM
                        content: You are a helpful assistant.
                      - type: USER
                        content: What is the capital of Germany? Return only the name.
                """
        ),
        @Example(
            title = "DeepSeek chat with JSON Mode (schema guidance)",
            full = true,
            code = """
                id: deepseek_chat_json_mode
                namespace: company.team

                tasks:
                  - id: chat_completion_json
                    type: io.kestra.plugin.deepseek.ChatCompletion
                    apiKey: "{{ secret('DEEPSEEK_API_KEY') }}"
                    modelName: deepseek-chat
                    messages:
                      - type: USER
                        content: Extract the book information from: "I recently read 'To Kill a Mockingbird' by Harper Lee." Return JSON only.
                    jsonResponseSchema: |
                      {
                        "type": "object",
                        "title": "Book",
                        "additionalProperties": false,
                        "required": ["name", "authors"],
                        "properties": {
                          "name": { "type": "string" },
                          "authors": { "type": "array", "items": { "type": "string" } }
                        }
                      }
                """
        )
    }
)
public class ChatCompletion extends Task implements RunnableTask<ChatCompletion.Output> {

    @Schema(title = "API Key", description = "The DeepSeek API key used for authentication")
    @NotNull
    private Property<String> apiKey;

    @Schema(title = "Model name", description = "The name of the DeepSeek model to use, e.g. `deepseek-chat` or `deepseek-coder`")
    @NotNull
    private Property<String> modelName;

    @Schema(title = "Base URL", description = "The base URL of the DeepSeek API. Using the /v1 URL allows to be compatible with OpenAI.")
    @Builder.Default
    private Property<String> baseUrl = Property.ofValue("https://api.deepseek.com/v1");

    @Schema(title = "Messages", description = "The list of messages in the conversation history")
    @NotNull
    private Property<List<ChatMessage>> messages;

    @Schema(
        title = "JSON Response Schema",
        description = "JSON schema (as string) to guide JSON-only output when using DeepSeek JSON Mode. " +
            "If provided, the request sets response_format = {\"type\":\"json_object\"} and prepends a system message instructing the model to output valid JSON following the given schema. " +
            "DeepSeek does not currently enforce server-side schema validation; the schema is used as guidance."
    )
    private Property<String> jsonResponseSchema;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var resolvedApiKey = runContext.render(apiKey).as(String.class).orElseThrow();
        var resolvedModelName = runContext.render(modelName).as(String.class).orElseThrow();
        var resolvedBaseUrl = runContext.render(baseUrl).as(String.class).orElse("https://api.deepseek.com/v1");
        var resolvedMessages = runContext.render(messages).asList(ChatMessage.class);
        var maybeSchema = runContext.render(jsonResponseSchema).as(String.class);

        // Build messages, optionally prepend a schema-guidance system message for JSON Mode.
        List<Map<String, String>> formattedMessages = new ArrayList<>();

        // If a schema is provided, add a system instruction to produce JSON only and follow the schema.
        if (maybeSchema.isPresent() && !maybeSchema.get().isBlank()) {
            // NOTE: JSON Mode is enabled via response_format = {"type":"json_object"}.
            // The provided schema is used as guidance in the prompt since DeepSeek's API does not accept a server-enforced JSON Schema.
            formattedMessages.add(Map.of(
                "role", ChatMessageType.SYSTEM.role(),
                "content", "You must output valid JSON only (no extra text). " +
                    "Follow this JSON Schema strictly when formatting your response. " +
                    "If a field is unknown, output a sensible empty value of the correct type. " +
                    "JSON Schema:\n" + maybeSchema.get()
            ));
        }

        // Add user/system messages from input
        formattedMessages.addAll(
            resolvedMessages.stream()
                .map(msg -> Map.of(
                    "role", msg.type().role(),
                    "content", Objects.toString(msg.content(), "")
                ))
                .toList()
        );

        // Build request body; enable JSON Mode if schema provided.
        var requestBodyBuilder = new java.util.LinkedHashMap<String, Object>();
        requestBodyBuilder.put("model", resolvedModelName);
        requestBodyBuilder.put("messages", formattedMessages);

        if (maybeSchema.isPresent() && !maybeSchema.get().isBlank()) {
            // Enable DeepSeek JSON Mode to force valid JSON output
            // See: https://api-docs.deepseek.com/guides/json_mode/
            requestBodyBuilder.put("response_format", Map.of("type", "json_object"));
        }

        var requestBody = requestBodyBuilder;

        try (var client = new HttpClient(runContext, HttpConfiguration.builder().build())) {
            var request = HttpRequest.builder()
                .uri(URI.create(resolvedBaseUrl + "/chat/completions"))
                .addHeader("Authorization", "Bearer " + resolvedApiKey)
                .addHeader("Content-Type", "application/json")
                .method("POST")
                .body(HttpRequest.JsonRequestBody.builder().content(requestBody).build())
                .build();

            var response = client.request(request, ObjectNode.class);

            if (response.getStatus().getCode() >= 400) {
                throw new IOException("DeepSeek API error: " + response.getBody());
            }

            var content = response.getBody()
                .get("choices")
                .get(0)
                .get("message")
                .get("content")
                .asText();

            return Output.builder()
                .response(content)
                .raw(response.getBody().toString())
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private final String response;
        private final String raw;
    }

    @Builder
    public record ChatMessage(ChatMessageType type, String content) {
    }

    public enum ChatMessageType {
        SYSTEM("system"),
        ASSISTANT("assistant"),
        USER("user");

        private final String role;

        ChatMessageType(String role) {
            this.role = role;
        }

        public String role() {
            return role;
        }
    }
}
