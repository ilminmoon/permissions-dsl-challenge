package com.example.authz.dsl;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public record NotExpression(@JsonProperty("not") Expression expression) implements Expression {
    public NotExpression {
        Objects.requireNonNull(expression, "expression must not be null");
    }
}
