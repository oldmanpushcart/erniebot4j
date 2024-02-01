package io.github.ompc.erniebot4j.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;

import java.lang.reflect.Type;
import java.util.LinkedHashSet;

/**
 * Jackson工具类
 */
public class JacksonUtils {

    private static final ObjectMapper _mapper = new ObjectMapper()
            .setPropertyNamingStrategy(new PropertyNamingStrategies.SnakeCaseStrategy())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static ObjectMapper mapper() {
        return _mapper.copy();
    }

    public static JsonNode toNode(ObjectMapper mapper, String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException cause) {
            throw new RuntimeException(
                    "parse json to node failed!",
                    cause
            );
        }
    }

    public static <T> T toObject(ObjectMapper mapper, Type type, String json) {
        try {
            final var jType = mapper.constructType(type);
            return mapper.readValue(json, jType);
        } catch (JsonProcessingException cause) {
            throw new IllegalArgumentException(
                    "parse json to object failed!",
                    cause
            );
        }
    }

    public static <T> T toObject(ObjectMapper mapper, Class<T> type, JsonNode node) {
        try {
            return mapper.treeToValue(node, type);
        } catch (JsonProcessingException cause) {
            throw new IllegalArgumentException(
                    "parse json to object failed!",
                    cause
            );
        }
    }

    public static String toJson(ObjectMapper mapper, Object object) {
        try {
            return mapper.writer().writeValueAsString(object);
        } catch (JsonProcessingException cause) {
            throw new IllegalArgumentException(
                    "parse object to json failed!",
                    cause
            );
        }
    }

    public static String compact(ObjectMapper mapper, String json) {
        return toJson(mapper, toNode(mapper, json));
    }

    public static JsonNode schema(ObjectMapper mapper, Type type) {
        final var target = mapper.constructType(type);
        final var schemaGen = new JsonSchemaGenerator(mapper);

        try {

            final var schema = schemaGen.generateSchema(target);
            final var schemaJson = mapper.writer().writeValueAsString(schema);
            final var schemaNode = mapper.reader().readTree(schemaJson);

            correct$field$id(schemaNode);
            correct$field$required(schemaNode);
            correct$field$empty_properties(schemaNode);

            return schemaNode;
        } catch (JsonProcessingException cause) {
            throw new IllegalArgumentException(
                    "failed to generate schema for class: %s".formatted(
                            type.getTypeName()
                    ),
                    cause
            );
        }
    }

    /**
     * 移除json-schema描述对象中的id字段
     *
     * @param node json-schema描述对象
     */
    private static void correct$field$id(JsonNode node) {

        // 不是Json对象不需要修复
        if (!node.isObject()) {
            return;
        }

        // 移除json-schema描述对象中的id字段
        if (node.has("type") && node.get("type").asText().equals("object")) {
            if (node.has("id")) {
                ((ObjectNode) node).remove("id");
            }
            if (node.has("properties")) {
                node.get("properties").fields().forEachRemaining(entry -> correct$field$id(entry.getValue()));
            }
        }

        // 修复json-schema描述数组中的items字段，移除items字段中的对象中的id字段
        else if (node.has("type") && node.get("type").asText().equals("array")) {
            if (node.has("items")) {
                correct$field$id(node.get("items"));
            }
        }

    }

    /**
     * 修复json-schema描述对象中的required字段
     *
     * @param node json-schema描述对象
     */
    private static void correct$field$required(JsonNode node) {

        // 不是Json对象不需要修复
        if (!node.isObject()) {
            return;
        }

        // 修复json-schema描述对象中的required字段
        if (node.has("type") && node.get("type").asText().equals("object")) {
            if (node.has("properties")) {
                final var propertiesNode = node.get("properties");
                final var requiredSet = new LinkedHashSet<JsonNode>();
                propertiesNode.fields().forEachRemaining(entry -> {
                    final var name = entry.getKey();
                    final var propertyNode = entry.getValue();
                    if (propertyNode.has("required") && propertyNode.get("required").asBoolean()) {
                        requiredSet.add(new TextNode(name));
                        ((ObjectNode) propertyNode).remove("required");
                    }
                    if (propertyNode.isObject()) {
                        correct$field$required(propertyNode);
                    }
                });
                if (!requiredSet.isEmpty()) {
                    ((ObjectNode) node).putArray("required").addAll(requiredSet);
                }
            }
        }

        // 修复json-schema描述数组中的items字段，处理items字段中的对象中的required字段
        else if (node.has("type") && node.get("type").asText().equals("array")) {
            if (node.has("items")) {
                correct$field$required(node.get("items"));
            }
        }

    }

    private static void correct$field$empty_properties(JsonNode node) {

        // 不是Json对象不需要修复
        if (!node.isObject()) {
            return;
        }

        // 修复json-schema描述对象中的required字段
        if (node.has("type") && node.get("type").asText().equals("object")) {
            if (node.has("properties")) {
                final var propertiesNode = node.get("properties");
                propertiesNode.fields().forEachRemaining(entry -> {
                    final var propertyNode = entry.getValue();
                    if (propertyNode.isObject()) {
                        correct$field$empty_properties(propertyNode);
                    }
                });
            } else {
                ((ObjectNode) node).putObject("properties");
            }
        }

        // 修复json-schema描述数组中的items字段，处理items字段中的对象中的required字段
        else if (node.has("type") && node.get("type").asText().equals("array")) {
            if (node.has("items")) {
                correct$field$empty_properties(node.get("items"));
            }
        }

    }

}
