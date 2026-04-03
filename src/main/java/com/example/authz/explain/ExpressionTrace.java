package com.example.authz.explain;

import com.example.authz.dsl.Expression;
import com.example.authz.engine.EvaluationResult;

public record ExpressionTrace(
        Expression expression,
        EvaluationResult result,
        String detail
) {
}
