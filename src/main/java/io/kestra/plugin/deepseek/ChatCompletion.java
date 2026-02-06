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
@Schema(
    title = "Send chat to DeepSeek",
    description = "Calls the DeepSeek Chat Completions API with role-based messages and optional JSON Mode guidance. Uses `baseUrl` default `https://api.deepseek.com/v1` for OpenAI-compatible routing; add `jsonResponseSchema` to request JSON-only output—schema is advisory and not server-validated."
)
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

    @Schema(title = "API key", description = "DeepSeek API key used for Bearer authentication")
    @NotNull
    private Property<String> apiKey;

    @Schema(title = "Model name", description = "DeepSeek model identifier such as `deepseek-chat` or `deepseek-coder`")
    @NotNull
    private Property<String> modelName;

    @Schema(title = "Base URL", description = "DeepSeek API endpoint; defaults to `https://api.deepseek.com/v1` for OpenAI-compatible clients and can point to a proxy.")
    @Builder.Default
    private Property<String> baseUrl = Property.ofValue("https://api.deepseek.com/v1");

    @Schema(title = "Messages", description = "Ordered chat history with roles SYSTEM, USER, or ASSISTANT; rendered before sending.")
    @NotNull
    private Property<List<ChatMessage>> messages;

    @Schema(
        title = "JSON Response Schema",
        description = "Optional JSON Schema string to enable DeepSeek JSON Mode. Sets `response_format` to `json_object` and prepends a schema reminder; DeepSeek treats the schema as guidance and does not validate server-side."
    )
    private Property<String> jsonResponseSchema;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var resolvedApiKey = runContext.render(apiKey).as(String.class).orElseThrow();
        var resolvedModelName = runContext.render(modelName).as(String.class).orElseThrow();
        var resolvedBaseUrl = runContext.render(baseUrl).as(String.class).orElse("https://api.deepseek.com/v1");
        var resolvedMessages = runContext.render(messages).asList(ChatMessage.class);
        var rSchema = runContext.render(jsonResponseSchema).as(String.class).orElse(null);

        List<Map<String, String>> formattedMessages = new ArrayList<>();

        if (rSchema != null && !rSchema.isBlank()) {
            formattedMessages.add(Map.of(
                "role", ChatMessageType.SYSTEM.role(),
                "content", "You must output valid JSON only (no extra text). " +
                    "Follow this JSON Schema strictly when formatting your response. " +
                    "If a field is unknown, output a sensible empty value of the correct type. " +
                    "JSON Schema:\n" + rSchema
            ));
        }

        formattedMessages.addAll(
            resolvedMessages.stream()
                .map(msg -> Map.of(
                    "role", msg.type().role(),
                    "content", Objects.toString(msg.content(), "")
                ))
                .toList()
        );

        var requestBody = new java.util.LinkedHashMap<String, Object>();
        requestBody.put("model", resolvedModelName);
        requestBody.put("messages", formattedMessages);

        if (rSchema != null && !rSchema.isBlank()) {
            // Enable DeepSeek JSON Mode to force valid JSON output
            requestBody.put("response_format", Map.of("type", "json_object"));
        }

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

            // Normalize if schema expects an array
            content = DeepseekResponseNormalizer.normalize(content, rSchema);

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
