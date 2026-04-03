package com.example.authz.explain;

import com.example.authz.engine.AuthorizationRequest;

import java.util.List;

public record DecisionTrace(
        AuthorizationRequest request,
        List<PolicyTrace> policyTraces
) {
    public DecisionTrace {
        policyTraces = List.copyOf(policyTraces);
    }
}
