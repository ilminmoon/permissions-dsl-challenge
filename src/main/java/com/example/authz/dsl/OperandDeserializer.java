package com.example.authz.dsl;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonMappingException;

import java.io.IOException;

public final class OperandDeserializer extends JsonDeserializer<Operand> {
    @Override
    public Operand deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        ObjectCodec codec = parser.getCodec();
        JsonNode node = codec.readTree(parser);

        if (node.isObject()) {
            if (node.size() == 1 && node.has("ref") && node.get("ref").isTextual()) {
                return new FieldRefOperand(node.get("ref").asText());
            }
            throw JsonMappingException.from(parser, "Operand objects must use the form {\"ref\":\"field.name\"}.");
        }

        if (node.isArray()) {
            throw JsonMappingException.from(parser, "Operands do not support array literals.");
        }

        Object value = codec.treeToValue(node, Object.class);
        return new LiteralOperand(value);
    }
}
