package io.kestra.plugin.deepseek;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
public class ChatCompletionTest {

    private final String DEEPSEEK_API_KEY = System.getenv("DEEPSEEK_API_KEY");

    @Inject
    private RunContextFactory runContextFactory;

    @EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".*")
    @Test
    void shouldGetResultsWithChatCompletion() throws Exception {
        var runContext = runContextFactory.of(Map.of(
            "apiKey", DEEPSEEK_API_KEY,
            "modelName", "deepseek-chat",
            "messages", List.of(
                ChatCompletion.ChatMessage.builder()
                    .type(ChatCompletion.ChatMessageType.USER)
                    .content("What is the capital of France? Answer just the name.")
                    .build()
            )
        ));

        var task = ChatCompletion.builder()
            .apiKey(Property.ofExpression("{{ apiKey }}"))
            .modelName(Property.ofExpression("{{ modelName }}"))
            .messages(Property.ofExpression("{{ messages }}"))
            .build();

        var output = task.run(runContext);

        assertThat(output.getResponse(), notNullValue());
        assertThat(output.getResponse(), containsString("Paris"));
    }

    @EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".*")
    @Test
    void shouldGetStructuredJsonWithSchema() throws Exception {
        var schema = """
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
            """;

        var runContext = runContextFactory.of(Map.of(
            "apiKey", DEEPSEEK_API_KEY,
            "modelName", "deepseek-chat",
            "messages", List.of(
                ChatCompletion.ChatMessage.builder()
                    .type(ChatCompletion.ChatMessageType.USER)
                    .content("I recently read 'To Kill a Mockingbird' by Harper Lee. Return JSON only.")
                    .build()
            ),
            "jsonResponseSchema", schema
        ));

        var task = ChatCompletion.builder()
            .apiKey(Property.ofExpression("{{ apiKey }}"))
            .modelName(Property.ofExpression("{{ modelName }}"))
            .messages(Property.ofExpression("{{ messages }}"))
            .jsonResponseSchema(Property.ofExpression("{{ jsonResponseSchema }}"))
            .build();

        var output = task.run(runContext);

        assertThat(output.getResponse(), notNullValue());
        assertThat(output.getRaw(), notNullValue());

        // Parse the JSON string in the response
        var mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(output.getResponse());

        // Basic schema checks
        assertThat(node.path("name").isTextual(), is(true));
        assertThat(node.path("authors").isArray(), is(true));

        // Sanity check on values
        assertThat(node.path("name").asText(), containsString("Mockingbird"));
        assertThat(node.path("authors").get(0).asText(), containsString("Harper Lee"));
    }
}
