package com.example.authz.engine;

import com.example.authz.dsl.AndExpression;
import com.example.authz.dsl.ComparisonExpression;
import com.example.authz.dsl.ComparisonOperator;
import com.example.authz.dsl.Expression;
import com.example.authz.dsl.FieldRefOperand;
import com.example.authz.dsl.LiteralOperand;
import com.example.authz.dsl.NotExpression;
import com.example.authz.dsl.OrExpression;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExpressionEvaluatorTest {
    private final ExpressionEvaluator evaluator = new ExpressionEvaluator();

    @Test
    void assignmentExampleSimpleEquality() {
        Expression expression = new ComparisonExpression("user.id", ComparisonOperator.EQ, new LiteralOperand("123"));

        EvaluationResult result = evaluator.evaluate(expression, nestedFacts(Map.of(
                "user", Map.of("id", "123")
        )));

        assertEquals(EvaluationResult.TRUE, result);
    }

    @Test
    void assignmentExampleAndOperation() {
        Expression expression = new AndExpression(List.of(
                new ComparisonExpression("document.deletedAt", ComparisonOperator.EQ, new LiteralOperand(null)),
                new ComparisonExpression("user.role", ComparisonOperator.EQ, new LiteralOperand("admin"))
        ));

        EvaluationResult result = evaluator.evaluate(expression, nestedFacts(Map.of(
                "document", mapWithNull("deletedAt"),
                "user", Map.of("role", "admin")
        )));

        assertEquals(EvaluationResult.TRUE, result);
    }

    @Test
    void assignmentExampleMissingDataProducesUnknown() {
        Expression expression = new ComparisonExpression("document.title", ComparisonOperator.EQ, new LiteralOperand("Test"));

        EvaluationResult result = evaluator.evaluate(expression, nestedFacts(Map.of(
                "user", Map.of("id", "123")
        )));

        assertEquals(EvaluationResult.UNKNOWN, result);
    }

    @Test
    void supportsRefToRefComparison() {
        Expression expression = new ComparisonExpression("document.creatorId", ComparisonOperator.EQ, new FieldRefOperand("user.id"));

        EvaluationResult result = evaluator.evaluate(expression, nestedFacts(Map.of(
                "document", Map.of("creatorId", "u1"),
                "user", Map.of("id", "u1")
        )));

        assertEquals(EvaluationResult.TRUE, result);
    }

    @Test
    void supportsNumericComparison() {
        Expression expression = new ComparisonExpression("document.version", ComparisonOperator.GT, new LiteralOperand(2));

        EvaluationResult result = evaluator.evaluate(expression, nestedFacts(Map.of(
                "document", Map.of("version", 3)
        )));

        assertEquals(EvaluationResult.TRUE, result);
    }

    @Test
    void supportsBooleanComparison() {
        Expression expression = new ComparisonExpression("document.publicLinkEnabled", ComparisonOperator.EQ, new LiteralOperand(true));

        EvaluationResult result = evaluator.evaluate(expression, nestedFacts(Map.of(
                "document", Map.of("publicLinkEnabled", true)
        )));

        assertEquals(EvaluationResult.TRUE, result);
    }

    @Test
    void supportsNullComparison() {
        Expression expression = new ComparisonExpression("document.deletedAt", ComparisonOperator.EQ, new LiteralOperand(null));

        EvaluationResult result = evaluator.evaluate(expression, nestedFacts(Map.of(
                "document", mapWithNull("deletedAt")
        )));

        assertEquals(EvaluationResult.TRUE, result);
    }

    @Test
    void supportsDateComparisonForIsoStrings() {
        Expression expression = new ComparisonExpression(
                "document.deletedAt",
                ComparisonOperator.GT,
                new LiteralOperand("2026-03-30T00:00:00Z")
        );

        EvaluationResult result = evaluator.evaluate(expression, nestedFacts(Map.of(
                "document", Map.of("deletedAt", "2026-03-31T00:00:00Z")
        )));

        assertEquals(EvaluationResult.TRUE, result);
    }

    @Test
    void supportsDateComparisonForInstantValues() {
        Expression expression = new ComparisonExpression(
                "document.deletedAt",
                ComparisonOperator.LTE,
                new LiteralOperand("2026-03-31T00:00:00Z")
        );

        EvaluationResult result = evaluator.evaluate(expression, nestedFacts(Map.of(
                "document", Map.of("deletedAt", Instant.parse("2026-03-31T00:00:00Z"))
        )));

        assertEquals(EvaluationResult.TRUE, result);
    }

    @Test
    void supportsNestedLogic() {
        Expression expression = new OrExpression(List.of(
                new AndExpression(List.of(
                        new ComparisonExpression("project.visibility", ComparisonOperator.EQ, new LiteralOperand("private")),
                        new ComparisonExpression("projectMembership.exists", ComparisonOperator.EQ, new LiteralOperand(true))
                )),
                new NotExpression(new ComparisonExpression("document.publicLinkEnabled", ComparisonOperator.EQ, new LiteralOperand(true)))
        ));

        EvaluationResult result = evaluator.evaluate(expression, nestedFacts(Map.of(
                "project", Map.of("visibility", "private"),
                "projectMembership", Map.of("exists", true),
                "document", Map.of("publicLinkEnabled", false)
        )));

        assertEquals(EvaluationResult.TRUE, result);
    }

    @Test
    void falseAndUnknownResolvesToFalse() {
        Expression expression = new AndExpression(List.of(
                new ComparisonExpression("document.publicLinkEnabled", ComparisonOperator.EQ, new LiteralOperand(false)),
                new ComparisonExpression("missing.value", ComparisonOperator.EQ, new LiteralOperand("x"))
        ));

        EvaluationResult result = evaluator.evaluate(expression, nestedFacts(Map.of(
                "document", Map.of("publicLinkEnabled", true)
        )));

        assertEquals(EvaluationResult.FALSE, result);
    }

    @Test
    void trueAndUnknownResolvesToUnknown() {
        Expression expression = new AndExpression(List.of(
                new ComparisonExpression("document.publicLinkEnabled", ComparisonOperator.EQ, new LiteralOperand(true)),
                new ComparisonExpression("missing.value", ComparisonOperator.EQ, new LiteralOperand("x"))
        ));

        EvaluationResult result = evaluator.evaluate(expression, nestedFacts(Map.of(
                "document", Map.of("publicLinkEnabled", true)
        )));

        assertEquals(EvaluationResult.UNKNOWN, result);
    }

    @Test
    void trueOrUnknownResolvesToTrue() {
        Expression expression = new OrExpression(List.of(
                new ComparisonExpression("document.publicLinkEnabled", ComparisonOperator.EQ, new LiteralOperand(true)),
                new ComparisonExpression("missing.value", ComparisonOperator.EQ, new LiteralOperand("x"))
        ));

        EvaluationResult result = evaluator.evaluate(expression, nestedFacts(Map.of(
                "document", Map.of("publicLinkEnabled", true)
        )));

        assertEquals(EvaluationResult.TRUE, result);
    }

    @Test
    void falseOrUnknownResolvesToUnknown() {
        Expression expression = new OrExpression(List.of(
                new ComparisonExpression("document.publicLinkEnabled", ComparisonOperator.EQ, new LiteralOperand(false)),
                new ComparisonExpression("missing.value", ComparisonOperator.EQ, new LiteralOperand("x"))
        ));

        EvaluationResult result = evaluator.evaluate(expression, nestedFacts(Map.of(
                "document", Map.of("publicLinkEnabled", true)
        )));

        assertEquals(EvaluationResult.UNKNOWN, result);
    }

    @Test
    void notUnknownResolvesToUnknown() {
        Expression expression = new NotExpression(
                new ComparisonExpression("missing.value", ComparisonOperator.EQ, new LiteralOperand("x"))
        );

        EvaluationResult result = evaluator.evaluate(expression, nestedFacts(Map.of()));

        assertEquals(EvaluationResult.UNKNOWN, result);
    }

    @Test
    void supportsFlatDottedFactKeysAsConvenience() {
        Expression expression = new ComparisonExpression("user.id", ComparisonOperator.EQ, new LiteralOperand("u1"));

        EvaluationResult result = evaluator.evaluate(expression, Map.of("user.id", "u1"));

        assertEquals(EvaluationResult.TRUE, result);
    }

    @Test
    void evaluationContextDeepCopiesNestedFacts() {
        Map<String, Object> user = new HashMap<>();
        user.put("id", "u1");

        Map<String, Object> facts = new HashMap<>();
        facts.put("user", user);

        EvaluationContext context = new EvaluationContext(facts);
        user.put("id", "u2");

        Expression expression = new ComparisonExpression("user.id", ComparisonOperator.EQ, new LiteralOperand("u1"));
        EvaluationResult result = evaluator.evaluate(expression, context);

        assertEquals(EvaluationResult.TRUE, result);
    }

    private static Map<String, Object> nestedFacts(Map<String, Object> value) {
        return value;
    }

    private static Map<String, Object> mapWithNull(String key) {
        Map<String, Object> value = new HashMap<>();
        value.put(key, null);
        return value;
    }
}
