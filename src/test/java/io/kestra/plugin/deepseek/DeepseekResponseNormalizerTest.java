package io.kestra.plugin.deepseek;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.serializers.JacksonMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DeepseekResponseNormalizerTest {
    // Use Kestra Jackson mapper in tests too
    private final ObjectMapper mapper = JacksonMapper.ofJson();

    @Test
    public void testMissingOpeningBracketMultipleObjects() throws Exception {
        String raw = "{ \"title\": \"Get prescription\" },\n"
            + "{ \"title\": \"Go shopping\" }\n"
            + "]"; // malformed: missing opening '['

        String normalized = DeepseekResponseNormalizer.normalize(raw, "{ \"type\": \"array\" }");
        JsonNode node = mapper.readTree(normalized);
        assertTrue(node.isArray(), "Normalized output should be an array");
        assertEquals(2, node.size());
    }

    @Test
    public void testSingleObjectWrapped() throws Exception {
        String raw = "{ \"title\": \"Only task\" }";

        String normalized = DeepseekResponseNormalizer.normalize(raw, "{ \"type\": \"array\" }");
        JsonNode node = mapper.readTree(normalized);
        assertTrue(node.isArray());
        assertEquals(1, node.size());
        assertEquals("Only task", node.get(0).get("title").asText());
        // reviewer suggested additional assertion
        assertEquals("Only task", node.get(0).get("title").asText());
    }

    @Test
    public void testNoSchemaNoChange() {
        String raw = "{ \"a\": 1 }";
        String normalized = DeepseekResponseNormalizer.normalize(raw, null);
        assertEquals(raw, normalized);
    }

    @Test
    public void testAlreadyArrayNoChange() throws Exception {
        String raw = "[{ \"t\": 1 }, { \"t\": 2 }]";
        String normalized = DeepseekResponseNormalizer.normalize(raw, "{ \"type\": \"array\" }");
        JsonNode node = mapper.readTree(normalized);
        assertTrue(node.isArray());
        assertEquals(2, node.size());
    }
}
