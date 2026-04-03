package com.example.authz.engine;

import com.example.authz.dsl.AndExpression;
import com.example.authz.dsl.ComparisonExpression;
import com.example.authz.dsl.ComparisonOperator;
import com.example.authz.dsl.Expression;
import com.example.authz.dsl.FieldRefOperand;
import com.example.authz.dsl.LiteralOperand;
import com.example.authz.dsl.NotExpression;
import com.example.authz.dsl.Operand;
import com.example.authz.dsl.OrExpression;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ExpressionEvaluator {
    public EvaluationResult evaluate(Expression expression, Map<String, Object> facts) {
        return evaluate(expression, new EvaluationContext(facts));
    }

    public EvaluationResult evaluate(Expression expression, EvaluationContext context) {
        Objects.requireNonNull(expression, "expression must not be null");
        Objects.requireNonNull(context, "context must not be null");

        return switch (expression) {
            case ComparisonExpression comparisonExpression -> evaluateComparison(comparisonExpression, context);
            case AndExpression andExpression -> evaluateAnd(andExpression, context);
            case OrExpression orExpression -> evaluateOr(orExpression, context);
            case NotExpression notExpression -> negate(evaluate(notExpression.expression(), context));
        };
    }

    private EvaluationResult evaluateComparison(ComparisonExpression expression, EvaluationContext context) {
        EvaluationContext.LookupResult leftLookup = context.lookup(expression.leftField());
        if (!leftLookup.found()) {
            return EvaluationResult.UNKNOWN;
        }

        ResolvedOperand rightOperand = resolveOperand(expression.rightOperand(), context);
        if (!rightOperand.known()) {
            return EvaluationResult.UNKNOWN;
        }

        return compareValues(leftLookup.value(), rightOperand.value(), expression.operator());
    }

    private ResolvedOperand resolveOperand(Operand operand, EvaluationContext context) {
        return switch (operand) {
            case LiteralOperand literalOperand -> ResolvedOperand.known(literalOperand.value());
            case FieldRefOperand fieldRefOperand -> {
                EvaluationContext.LookupResult lookup = context.lookup(fieldRefOperand.ref());
                yield lookup.found() ? ResolvedOperand.known(lookup.value()) : ResolvedOperand.unknown();
            }
        };
    }

    private EvaluationResult evaluateAnd(AndExpression expression, EvaluationContext context) {
        boolean hasUnknown = false;
        for (Expression child : expression.expressions()) {
            EvaluationResult result = evaluate(child, context);
            if (result == EvaluationResult.FALSE) {
                return EvaluationResult.FALSE;
            }
            if (result == EvaluationResult.UNKNOWN) {
                hasUnknown = true;
            }
        }
        return hasUnknown ? EvaluationResult.UNKNOWN : EvaluationResult.TRUE;
    }

    private EvaluationResult evaluateOr(OrExpression expression, EvaluationContext context) {
        boolean hasUnknown = false;
        for (Expression child : expression.expressions()) {
            EvaluationResult result = evaluate(child, context);
            if (result == EvaluationResult.TRUE) {
                return EvaluationResult.TRUE;
            }
            if (result == EvaluationResult.UNKNOWN) {
                hasUnknown = true;
            }
        }
        return hasUnknown ? EvaluationResult.UNKNOWN : EvaluationResult.FALSE;
    }

    private EvaluationResult negate(EvaluationResult value) {
        return switch (value) {
            case TRUE -> EvaluationResult.FALSE;
            case FALSE -> EvaluationResult.TRUE;
            case UNKNOWN -> EvaluationResult.UNKNOWN;
        };
    }

    private EvaluationResult compareValues(Object left, Object right, ComparisonOperator operator) {
        if (isEqualityOperator(operator)) {
            Boolean equalityResult = equalityResult(left, right);
            if (equalityResult == null) {
                return EvaluationResult.UNKNOWN;
            }
            return operator == ComparisonOperator.EQ
                    ? fromBoolean(equalityResult)
                    : fromBoolean(!equalityResult);
        }

        Integer orderingResult = orderingResult(left, right);
        if (orderingResult == null) {
            return EvaluationResult.UNKNOWN;
        }

        return switch (operator) {
            case GT -> fromBoolean(orderingResult > 0);
            case GTE -> fromBoolean(orderingResult >= 0);
            case LT -> fromBoolean(orderingResult < 0);
            case LTE -> fromBoolean(orderingResult <= 0);
            case EQ, NE -> throw new IllegalStateException("Equality operators should be handled earlier.");
        };
    }

    private boolean isEqualityOperator(ComparisonOperator operator) {
        return operator == ComparisonOperator.EQ || operator == ComparisonOperator.NE;
    }

    private Boolean equalityResult(Object left, Object right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }

        Optional<BigDecimal> leftNumber = decimalValue(left);
        Optional<BigDecimal> rightNumber = decimalValue(right);
        if (leftNumber.isPresent() && rightNumber.isPresent()) {
            return leftNumber.get().compareTo(rightNumber.get()) == 0;
        }

        Optional<Instant> leftInstant = instantValue(left);
        Optional<Instant> rightInstant = instantValue(right);
        if (leftInstant.isPresent() && rightInstant.isPresent()) {
            return leftInstant.get().equals(rightInstant.get());
        }

        if (left instanceof Boolean leftBoolean && right instanceof Boolean rightBoolean) {
            return leftBoolean.equals(rightBoolean);
        }

        if (left instanceof String leftString && right instanceof String rightString) {
            return leftString.equals(rightString);
        }

        return null;
    }

    private Integer orderingResult(Object left, Object right) {
        if (left == null || right == null) {
            return null;
        }

        Optional<BigDecimal> leftNumber = decimalValue(left);
        Optional<BigDecimal> rightNumber = decimalValue(right);
        if (leftNumber.isPresent() && rightNumber.isPresent()) {
            return leftNumber.get().compareTo(rightNumber.get());
        }

        Optional<Instant> leftInstant = instantValue(left);
        Optional<Instant> rightInstant = instantValue(right);
        if (leftInstant.isPresent() && rightInstant.isPresent()) {
            return leftInstant.get().compareTo(rightInstant.get());
        }

        if (left instanceof String leftString && right instanceof String rightString) {
            return leftString.compareTo(rightString);
        }

        return null;
    }

    private Optional<BigDecimal> decimalValue(Object value) {
        if (!(value instanceof Number number)) {
            return Optional.empty();
        }

        try {
            return Optional.of(new BigDecimal(number.toString()));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private Optional<Instant> instantValue(Object value) {
        if (value instanceof Instant instant) {
            return Optional.of(instant);
        }
        if (value instanceof String text) {
            // DSL date literals are plain strings; when both sides parse as ISO-8601 instants, compare as Instants.
            try {
                return Optional.of(Instant.parse(text));
            } catch (DateTimeParseException exception) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private EvaluationResult fromBoolean(boolean value) {
        return value ? EvaluationResult.TRUE : EvaluationResult.FALSE;
    }

    private record ResolvedOperand(boolean known, Object value) {
        private static ResolvedOperand known(Object value) {
            return new ResolvedOperand(true, value);
        }

        private static ResolvedOperand unknown() {
            return new ResolvedOperand(false, null);
        }
    }
}
