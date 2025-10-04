package io.kestra.plugin.deepseek;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Small utility to normalize DeepSeek structured JSON outputs when users requested an array.
 * This keeps parsing/repair logic in one place and makes it easy to unit-test.
 */
public final class DeepseekResponseNormalizer {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DeepseekResponseNormalizer() {
    }

    /**
     * Normalize a raw model response when the provided schema expects a JSON array.
     *
     * @param content     raw string returned by DeepSeek (may be malformed)
     * @param maybeSchema JSON schema string provided by the user (may be null)
     * @return normalized content (unchanged if no normalization applied)
     */
    public static String normalize(String content, String maybeSchema) {
        if (content == null) {
            return null;
        }

        boolean expectArray = maybeSchema != null &&
            maybeSchema.replaceAll("\\s+", "").toLowerCase().contains("\"type\":\"array\"");

        if (!expectArray) {
            return content;
        }

        String trimmed = content.trim();

        // already a valid array — nothing to do
        if (trimmed.startsWith("[")) {
            return content;
        }

        // If it's a single JSON object -> wrap it
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return "[" + content + "]";
        }

        // Try to parse: if it's parseable as a JSON object, wrap it
        try {
            JsonNode node = MAPPER.readTree(trimmed);
            if (node != null && node.isObject()) {
                return "[" + content + "]";
            }
        } catch (Exception ignored) {
            // ignore — we'll attempt repair heuristics below
        }

        // If it ends with ']' but missing opening '[' -> add it
        if (trimmed.endsWith("]") && !trimmed.startsWith("[")) {
            return "[" + content;
        }

        // Fallback: ensure both brackets exist (best-effort)
        String repaired = content;
        if (!trimmed.startsWith("[")) {
            repaired = "[" + repaired;
        }
        if (!repaired.trim().endsWith("]")) {
            repaired = repaired + "]";
        }
        return repaired;
    }
}
