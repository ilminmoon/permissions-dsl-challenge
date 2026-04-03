package com.example.authz.http;

import com.example.authz.engine.AuthorizationRequest;
import com.example.authz.engine.ExpressionEvaluator;
import com.example.authz.engine.PolicyEngine;
import com.example.authz.explain.AuthorizationDecision;
import com.example.authz.policy.PolicyDefinition;

import java.util.List;
import java.util.Map;
import java.util.Objects;

final class AuthorizationHttpFacade {
    private final List<PolicyDefinition> policies;
    private final ExpressionEvaluator expressionEvaluator;
    private final AuthorizationRequestBodyValidator validator;

    AuthorizationHttpFacade(List<PolicyDefinition> policies, ExpressionEvaluator expressionEvaluator) {
        this.policies = List.copyOf(Objects.requireNonNull(policies, "policies must not be null"));
        this.expressionEvaluator = Objects.requireNonNull(expressionEvaluator, "expressionEvaluator must not be null");
        this.validator = new AuthorizationRequestBodyValidator();
    }

    Map<String, Object> rootDocument() {
        return Map.of(
                "service", "authz-policy-engine",
                "status", "ok",
                "permissionChecksPath", AuthorizationHttpPaths.PERMISSION_CHECKS,
                "healthPath", AuthorizationHttpPaths.HEALTH
        );
    }

    Map<String, String> healthDocument() {
        return Map.of("status", "ok");
    }

    AuthorizationDecision authorize(AuthorizationRequestBody payload) {
        validator.validate(payload);
        AuthorizationRequest request = new AuthorizationRequest(
                payload.user().id(),
                payload.document().id(),
                payload.permission(),
                payload.requestedAt()
        );
        AuthorizationRequestContext context = new AuthorizationRequestContext(
                payload.user(),
                payload.team(),
                payload.project(),
                payload.document(),
                payload.teamMembership(),
                payload.projectMembership()
        );
        PolicyEngine engine = new PolicyEngine(policies, new RequestContextDataLoader(context), expressionEvaluator);
        return engine.authorize(request);
    }
}
