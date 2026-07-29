package org.apache.seata.metadata;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;

/**
 * Utility for merging GraalVM native image reachability metadata JSON files.
 *
 * <p>The merge is source-into-target with target-preserving semantics:
 * existing keys in the target are never overwritten; only missing keys
 * are added from the source. This ensures that manually curated metadata
 * is not lost when merging automatically generated entries.
 *
 * <p>Typical usage involves running the application with the GraalVM native
 * image agent, which produces a {@code reachability-metadata.json} file
 * under {@code target/native-image-config/}. That file is merged into the
 * canonical metadata file under
 * {@code src/main/resources/META-INF/native-image/} via
 * {@link #mergeObjectNodes(JsonNode, JsonNode)}.
 */
public class MergeNativeImageMetadata {

    /** Shared ObjectMapper instance. */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Recursively merges fields from {@code source} into {@code target}
     * without overwriting existing {@code target} fields. A key is only
     * written from {@code source} when it does not already exist in
     * {@code target}.
     *
     * <h3>Merge rules</h3>
     * <ul>
     *   <li><b>Key missing in target</b> — copied from source as-is.</li>
     *   <li><b>Both values are objects</b> — merged recursively.</li>
     *   <li><b>Both values are arrays</b> — elements at matching indices
     *       are merged recursively; extra source elements are appended.</li>
     *   <li><b>Otherwise</b> — target value is kept unchanged (source
     *       value is ignored).</li>
     * </ul>
     *
     * @param source the generated metadata to merge from
     * @param target the canonical metadata to merge into (mutated in place)
     */
    public static void mergeObjectNodes(JsonNode source, JsonNode target) {
        if (!source.isObject() || !target.isObject()) {
            return;
        }
        for (Map.Entry<String, JsonNode> entry : source.properties()) {
            String key = entry.getKey();
            JsonNode sourceValue = entry.getValue();
            JsonNode targetValue = target.get(key);
            if (targetValue == null) {
                // Key is absent in target — copy from source directly.
                ((ObjectNode) target).set(key, sourceValue);
            } else if (sourceValue.isObject() && targetValue.isObject()) {
                // Both are objects — merge recursively.
                mergeObjectNodes(sourceValue, targetValue);
            } else if (sourceValue.isArray() && targetValue.isArray()) {
                // Both are arrays — merge object elements by index,
                // then append any extra source elements.
                for (int i = 0; i < sourceValue.size() && i < targetValue.size(); i++) {
                    mergeObjectNodes(sourceValue.get(i), targetValue.get(i));
                }
                // Append remaining source elements not present in target.
                for (int i = targetValue.size(); i < sourceValue.size(); i++) {
                    ((ArrayNode) targetValue).add(sourceValue.get(i));
                }
            }
            // Otherwise the target already has a value and the types
            // differ — keep the target value unchanged.
        }
    }
}
