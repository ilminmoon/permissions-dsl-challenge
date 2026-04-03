package com.example.authz.dsl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DslJacksonRoundTripTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void roundTripsSimpleComparison() throws Exception {
        Expression expression = new ComparisonExpression(
                "user.id",
                ComparisonOperator.EQ,
                new LiteralOperand("123")
        );

        String json = objectMapper.writeValueAsString(expression);
        Expression roundTripped = objectMapper.readValue(json, Expression.class);

        assertEquals("[\"user.id\",\"eq\",\"123\"]", json);
        assertEquals(expression, roundTripped);
    }

    @Test
    void deserializesNestedAndOrNotShape() throws Exception {
        String json = """
                {
                  "and": [
                    ["project.visibility", "=", "private"],
                    {
                      "or": [
                        ["projectMembership.exists", "eq", true],
                        {
                          "not": ["document.publicLinkEnabled", "eq", true]
                        }
                      ]
                    }
                  ]
                }
                """;

        Expression expression = objectMapper.readValue(json, Expression.class);
        String serialized = objectMapper.writeValueAsString(expression);
        JsonNode node = objectMapper.readTree(serialized);

        assertInstanceOf(AndExpression.class, expression);
        assertTrue(node.has("and"));
        assertEquals(2, node.get("and").size());
    }

    @Test
    void roundTripsFieldReferenceOperand() throws Exception {
        String json = """
                ["document.creatorId", "eq", {"ref":"user.id"}]
                """;

        Expression expression = objectMapper.readValue(json, Expression.class);
        String serialized = objectMapper.writeValueAsString(expression);
        JsonNode node = objectMapper.readTree(serialized);

        ComparisonExpression comparison = assertInstanceOf(ComparisonExpression.class, expression);
        FieldRefOperand operand = assertInstanceOf(FieldRefOperand.class, comparison.rightOperand());

        assertEquals("user.id", operand.ref());
        assertEquals("user.id", node.get(2).get("ref").asText());
    }

    @Test
    void roundTripsNullLiteral() throws Exception {
        String json = """
                ["document.deletedAt", "<>", null]
                """;

        Expression expression = objectMapper.readValue(json, Expression.class);
        String serialized = objectMapper.writeValueAsString(expression);
        JsonNode node = objectMapper.readTree(serialized);

        ComparisonExpression comparison = assertInstanceOf(ComparisonExpression.class, expression);
        LiteralOperand operand = assertInstanceOf(LiteralOperand.class, comparison.rightOperand());

        assertNull(operand.value());
        assertTrue(node.get(2).isNull());
    }

    @Test
    void roundTripsDateCompatibleStringLiteral() throws Exception {
        Expression expression = new ComparisonExpression(
                "document.deletedAt",
                ComparisonOperator.GTE,
                new LiteralOperand("2026-03-31T00:00:00Z")
        );

        String json = objectMapper.writeValueAsString(expression);
        Expression roundTripped = objectMapper.readValue(json, Expression.class);
        JsonNode node = objectMapper.readTree(json);

        assertEquals("2026-03-31T00:00:00Z", node.get(2).asText());
        assertEquals(expression, roundTripped);
    }

    @Test
    void createsExpressionTreeInMemory() {
        Expression expression = new AndExpression(List.of(
                new ComparisonExpression("document.creatorId", ComparisonOperator.EQ, new FieldRefOperand("user.id")),
                new NotExpression(new ComparisonExpression("document.deletedAt", ComparisonOperator.NE, new LiteralOperand(null)))
        ));

        assertInstanceOf(AndExpression.class, expression);
        assertEquals(2, ((AndExpression) expression).expressions().size());
    }

    @Test
    void rejectsEmptyLogicalExpressionArrays() {
        IllegalArgumentException inMemoryException = assertThrows(
                IllegalArgumentException.class,
                () -> new AndExpression(List.of())
        );
        assertEquals("and expressions must contain at least one child expression", inMemoryException.getMessage());

        Exception jsonException = assertThrows(
                Exception.class,
                () -> objectMapper.readValue("{\"or\":[]}", Expression.class)
        );
        assertTrue(jsonException.getMessage().contains("at least one child expression"));
    }
}
