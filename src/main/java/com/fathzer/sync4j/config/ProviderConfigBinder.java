package com.fathzer.sync4j.config;

import java.io.IOException;
import java.util.Collection;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fathzer.sync4j.FileProviderFactory;

/**
 * Builds the configuration object of a provider from its JSON description.
 * <ul>
 * <li>The environment variables are replaced in the texts (the names of the properties are not).</li>
 * <li>The names of the enumeration values are not case sensitive ("us" is the same as "US").</li>
 * <li>An unknown property is an error (it is probably a typo).</li>
 * <li>A text is converted to the type of the property if it is a number or a boolean ("42" can be the value of an integer),
 * so that environment variables can be used everywhere.</li>
 * </ul>
 */
final class ProviderConfigBinder {
    private static final ObjectMapper MAPPER = JsonMapper.builder().enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS).build();

    private ProviderConfigBinder() {
        // Utility class
    }

    /**
     * Builds the configuration of a provider.
     * @param factory the factory of the provider
     * @param config the JSON configuration (null if there is none)
     * @param wording what is configured, for the error messages (for instance "configuration of photos source")
     * @param <T> the type of the configuration
     * @return the configuration, or null if the provider has no configuration (see {@link FileProviderFactory#configType()})
     * @throws IOException if the configuration is invalid
     */
    static <T> T bind(FileProviderFactory<T> factory, JsonNode config, String wording) throws IOException {
        final Class<T> type = factory.configType();
        final boolean empty = config == null || config.isNull() || (config.isObject() && config.isEmpty());
        if (type == Void.class) {
            if (!empty) {
                throw new IOException("The " + wording + " is not valid: the " + factory.type() + " provider has no configuration");
            }
            return null;
        }
        if (!empty && !config.isObject()) {
            throw new IOException("The " + wording + " should be an object");
        }
        final JsonNode node = empty ? MAPPER.createObjectNode() : replaceEnvironmentVariables(config, wording);
        try {
            return MAPPER.treeToValue(node, type);
        } catch (UnrecognizedPropertyException e) {
            throw new IOException("The " + wording + " is not valid: unknown property '" + e.getPropertyName() + "' (the known properties are "
                    + known(e.getKnownPropertyIds()) + ")", e);
        } catch (JsonProcessingException e) {
            throw new IOException("The " + wording + " is not valid" + where(e) + ": " + e.getOriginalMessage(), e);
        }
    }

    private static String known(Collection<Object> ids) {
        return ids == null ? "?" : ids.stream().map(String::valueOf).sorted().collect(Collectors.joining(", "));
    }

    private static String where(JsonProcessingException e) {
        if (e instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
            return " (property " + mapping.getPath().stream().map(r -> r.getFieldName() == null ? "[" + r.getIndex() + "]" : r.getFieldName()).collect(Collectors.joining(".")) + ")";
        }
        return "";
    }

    private static JsonNode replaceEnvironmentVariables(JsonNode node, String wording) throws IOException {
        if (node.isTextual()) {
            return TextNode.valueOf(EnvVarSubstitutor.replaceStrict(node.asText(), wording));
        } else if (node.isObject()) {
            final ObjectNode result = MAPPER.createObjectNode();
            for (var field : (Iterable<java.util.Map.Entry<String, JsonNode>>) node::fields) {
                result.set(field.getKey(), replaceEnvironmentVariables(field.getValue(), wording));
            }
            return result;
        } else if (node.isArray()) {
            final ArrayNode result = MAPPER.createArrayNode();
            for (JsonNode element : node) {
                result.add(replaceEnvironmentVariables(element, wording));
            }
            return result;
        }
        return node;
    }
}
