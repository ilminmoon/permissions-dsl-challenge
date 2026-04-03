package com.example.authz.dsl;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonMappingException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class ExpressionDeserializer extends JsonDeserializer<Expression> {
    @Override
    public Expression deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        ObjectCodec codec = parser.getCodec();
        JsonNode node = codec.readTree(parser);
        return parseExpression(node, codec, parser);
    }

    private Expression parseExpression(JsonNode node, ObjectCodec codec, JsonParser parser) throws IOException {
        if (node.isArray()) {
            return parseComparison(node, codec, parser);
        }

        if (node.isObject()) {
            return parseLogicalExpression(node, codec, parser);
        }

        throw JsonMappingException.from(parser, "Expression must be a comparison array or logical object.");
    }

    private Expression parseComparison(JsonNode node, ObjectCodec codec, JsonParser parser) throws IOException {
        if (node.size() != 3) {
            throw JsonMappingException.from(parser, "Comparison expressions must contain exactly three items.");
        }

        JsonNode leftNode = node.get(0);
        if (!leftNode.isTextual()) {
            throw JsonMappingException.from(parser, "Comparison left operand must be a field path string.");
        }

        ComparisonOperator operator = codec.treeToValue(node.get(1), ComparisonOperator.class);
        Operand rightOperand = parseRightOperand(node.get(2), codec);
        return new ComparisonExpression(leftNode.asText(), operator, rightOperand);
    }

    private Operand parseRightOperand(JsonNode node, ObjectCodec codec) throws IOException {
        return node.isNull()
                ? new LiteralOperand(null)
                : codec.treeToValue(node, Operand.class);
    }

    private Expression parseLogicalExpression(JsonNode node, ObjectCodec codec, JsonParser parser) throws IOException {
        Iterator<String> fieldNames = node.fieldNames();
        if (!fieldNames.hasNext()) {
            throw JsonMappingException.from(parser, "Expression object must contain one of: and, or, not.");
        }

        String operator = fieldNames.next();
        if (fieldNames.hasNext()) {
            throw JsonMappingException.from(parser, "Expression object must contain exactly one top-level operator.");
        }

        JsonNode operandNode = node.get(operator);
        return switch (operator) {
            case "and" -> new AndExpression(parseExpressionArray(operandNode, codec, parser));
            case "or" -> new OrExpression(parseExpressionArray(operandNode, codec, parser));
            case "not" -> new NotExpression(parseExpression(operandNode, codec, parser));
            default -> throw JsonMappingException.from(parser, "Unsupported expression operator: " + operator);
        };
    }

    private List<Expression> parseExpressionArray(JsonNode node, ObjectCodec codec, JsonParser parser) throws IOException {
        if (node == null || !node.isArray()) {
            throw JsonMappingException.from(parser, "Logical expressions must contain an array of child expressions.");
        }
        if (node.isEmpty()) {
            throw JsonMappingException.from(parser, "Logical expressions must contain at least one child expression.");
        }

        List<Expression> expressions = new ArrayList<>(node.size());
        for (JsonNode child : node) {
            expressions.add(parseExpression(child, codec, parser));
        }
        return expressions;
    }
}
