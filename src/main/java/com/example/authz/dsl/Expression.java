package com.example.authz.dsl;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = ExpressionDeserializer.class)
public sealed interface Expression
        permits ComparisonExpression, AndExpression, OrExpression, NotExpression {
}
