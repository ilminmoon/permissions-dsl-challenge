package com.example.authz.explain;

import com.example.authz.policy.Permission;

public record AuthorizationDecision(
        boolean allowed,
        Permission permission,
        String decisivePolicyId,
        String finalReason,
        DecisionTrace trace
) {
}
