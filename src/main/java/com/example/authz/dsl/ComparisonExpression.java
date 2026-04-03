package com.example.authz.dsl;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonFormat(shape = JsonFormat.Shape.ARRAY)
public record ComparisonExpression(
        @JsonProperty(index = 0)
        String leftField,
        @JsonProperty(index = 1)
        ComparisonOperator operator,
        @JsonProperty(index = 2)
        Operand rightOperand
) implements Expression {
    public ComparisonExpression {
        Objects.requireNonNull(leftField, "leftField must not be null");
        Objects.requireNonNull(operator, "operator must not be null");
        Objects.requireNonNull(rightOperand, "rightOperand must not be null");
    }
}
