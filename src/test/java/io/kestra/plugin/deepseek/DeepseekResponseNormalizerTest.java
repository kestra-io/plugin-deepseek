package io.kestra.plugin.deepseek;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DeepseekResponseNormalizerTest {
    private final ObjectMapper mapper = new ObjectMapper();

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
