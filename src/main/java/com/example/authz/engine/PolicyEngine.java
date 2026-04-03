package com.example.authz.engine;

import com.example.authz.explain.DecisionTrace;
import com.example.authz.explain.ExpressionTrace;
import com.example.authz.explain.AuthorizationDecision;
import com.example.authz.explain.PolicyTrace;
import com.example.authz.loader.AuthorizationDataLoader;
import com.example.authz.loader.AuthorizationSnapshot;
import com.example.authz.loader.DataRequirement;
import com.example.authz.policy.PolicyDefinition;
import com.example.authz.policy.PolicyEffect;
import com.example.authz.policy.Permission;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class PolicyEngine {
    private final List<PolicyDefinition> policies;
    private final AuthorizationDataLoader dataLoader;
    private final ExpressionEvaluator expressionEvaluator;

    public PolicyEngine(List<PolicyDefinition> policies, AuthorizationDataLoader dataLoader) {
        this(policies, dataLoader, new ExpressionEvaluator());
    }

    public PolicyEngine(List<PolicyDefinition> policies, AuthorizationDataLoader dataLoader, ExpressionEvaluator expressionEvaluator) {
        this.policies = List.copyOf(Objects.requireNonNull(policies, "policies must not be null"));
        this.dataLoader = Objects.requireNonNull(dataLoader, "dataLoader must not be null");
        this.expressionEvaluator = Objects.requireNonNull(expressionEvaluator, "expressionEvaluator must not be null");
    }

    public List<PolicyDefinition> policies() {
        return policies;
    }

    public AuthorizationDataLoader dataLoader() {
        return dataLoader;
    }

    public AuthorizationDecision authorize(AuthorizationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        validateRequest(request);

        List<PolicyDefinition> applicablePolicies = applicablePolicies(request.permission());
        List<PolicyTrace> traces = new ArrayList<>();
        if (applicablePolicies.isEmpty()) {
            return defaultDenyDecision(request, traces);
        }

        DataRequirement requirement = requiredDataFor(applicablePolicies);
        AuthorizationSnapshot snapshot = dataLoader.load(request, requirement);
        EvaluationContext context = new EvaluationContext(evaluationFactsFor(request, snapshot));

        Optional<AuthorizationDecision> denyDecision = firstMatchingDecision(
                request,
                applicablePolicies,
                PolicyEffect.DENY,
                context,
                traces
        );
        if (denyDecision.isPresent()) {
            return denyDecision.get();
        }

        Optional<AuthorizationDecision> allowDecision = firstMatchingDecision(
                request,
                applicablePolicies,
                PolicyEffect.ALLOW,
                context,
                traces
        );
        if (allowDecision.isPresent()) {
            return allowDecision.get();
        }

        return defaultDenyDecision(request, traces);
    }

    private AuthorizationDecision defaultDenyDecision(AuthorizationRequest request, List<PolicyTrace> traces) {
        return new AuthorizationDecision(
                false,
                request.permission(),
                null,
                "Denied by default because no allow policy matched.",
                new DecisionTrace(request, traces)
        );
    }

    private Optional<AuthorizationDecision> firstMatchingDecision(
            AuthorizationRequest request,
            List<PolicyDefinition> applicablePolicies,
            PolicyEffect effect,
            EvaluationContext context,
            List<PolicyTrace> traces
    ) {
        for (PolicyDefinition policy : applicablePolicies) {
            if (policy.effect() != effect) {
                continue;
            }

            EvaluationResult result = expressionEvaluator.evaluate(policy.condition(), context);
            traces.add(traceFor(policy, result));

            if (result == EvaluationResult.TRUE) {
                return Optional.of(decisionFor(request, effect, policy, traces));
            }
        }
        return Optional.empty();
    }

    private AuthorizationDecision decisionFor(
            AuthorizationRequest request,
            PolicyEffect effect,
            PolicyDefinition policy,
            List<PolicyTrace> traces
    ) {
        return new AuthorizationDecision(
                effect == PolicyEffect.ALLOW,
                request.permission(),
                policy.id(),
                finalReasonFor(policy),
                new DecisionTrace(request, traces)
        );
    }

    private PolicyTrace traceFor(PolicyDefinition policy, EvaluationResult result) {
        String summary = "Policy " + policy.id() + " evaluated to " + result + ".";
        ExpressionTrace expressionTrace = new ExpressionTrace(
                policy.condition(),
                result,
                "Required facts: " + policy.requiredFacts()
        );
        return new PolicyTrace(policy.id(), policy.effect(), result, summary, List.of(expressionTrace));
    }

    private String finalReasonFor(PolicyDefinition policy) {
        return switch (policy.effect()) {
            case DENY -> "Denied by policy " + policy.id() + ": " + policy.description();
            case ALLOW -> "Allowed by policy " + policy.id() + ": " + policy.description();
        };
    }

    private List<PolicyDefinition> applicablePolicies(Permission permission) {
        return policies.stream()
                .filter(policy -> policy.appliesTo(permission))
                .toList();
    }

    private DataRequirement requiredDataFor(List<PolicyDefinition> applicablePolicies) {
        Set<String> factPaths = new LinkedHashSet<>();
        for (PolicyDefinition policy : applicablePolicies) {
            for (String factPath : policy.requiredFacts()) {
                if (!factPath.startsWith("request.")) {
                    factPaths.add(factPath);
                }
            }
        }
        return new DataRequirement(factPaths);
    }

    private Map<String, Object> evaluationFactsFor(AuthorizationRequest request, AuthorizationSnapshot snapshot) {
        Map<String, Object> facts = new LinkedHashMap<>(snapshot.facts());
        facts.put("request", Map.of("permission", request.permission().dslValue()));
        return facts;
    }

    private void validateRequest(AuthorizationRequest request) {
        requireNonBlank(request.userId(), "request.userId");
        requireNonBlank(request.documentId(), "request.documentId");
        requireNonNull(request.permission(), "request.permission");
        requireNonNull(request.requestedAt(), "request.requestedAt");
    }

    private void requireNonBlank(String value, String path) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(path + " must not be blank");
        }
    }

    private void requireNonNull(Object value, String path) {
        if (value == null) {
            throw new IllegalArgumentException(path + " must not be null");
        }
    }
}
