package com.example.authz.explain;

import com.example.authz.engine.EvaluationResult;
import com.example.authz.policy.PolicyEffect;

import java.util.List;

public record PolicyTrace(
        String policyId,
        PolicyEffect effect,
        EvaluationResult result,
        String summary,
        List<ExpressionTrace> expressionTraces
) {
    public PolicyTrace {
        expressionTraces = List.copyOf(expressionTraces);
    }
}
