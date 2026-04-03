package com.example.authz.dsl;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

public record AndExpression(@JsonProperty("and") List<Expression> expressions) implements Expression {
    public AndExpression {
        Objects.requireNonNull(expressions, "expressions must not be null");
        if (expressions.isEmpty()) {
            throw new IllegalArgumentException("and expressions must contain at least one child expression");
        }
        expressions = List.copyOf(expressions);
    }
}
