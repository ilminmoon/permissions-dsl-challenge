package com.example.authz.policy;

import com.example.authz.dsl.AndExpression;
import com.example.authz.dsl.ComparisonExpression;
import com.example.authz.dsl.Expression;
import com.example.authz.dsl.FieldRefOperand;
import com.example.authz.dsl.NotExpression;
import com.example.authz.dsl.Operand;
import com.example.authz.dsl.OrExpression;

import java.util.LinkedHashSet;
import java.util.Set;

final class PolicyFactPathValidator {
    private static final Set<String> SUPPORTED_FACT_PATHS = Set.of(
            "user.id",
            "user.email",
            "user.name",
            "team.id",
            "team.name",
            "team.plan",
            "project.id",
            "project.name",
            "project.teamId",
            "project.visibility",
            "document.id",
            "document.title",
            "document.projectId",
            "document.creatorId",
            "document.deletedAt",
            "document.publicLinkEnabled",
            "teamMembership.exists",
            "teamMembership.role",
            "projectMembership.exists",
            "projectMembership.role",
            "request.permission"
    );

    private PolicyFactPathValidator() {
    }

    static void validate(Expression condition, Set<String> requiredFacts) {
        LinkedHashSet<String> referencedPaths = referencedPaths(condition);

        for (String path : referencedPaths) {
            validateSupportedPath(path, "condition");
        }
        for (String path : requiredFacts) {
            validateSupportedPath(path, "requiredFacts");
        }

        LinkedHashSet<String> missingRequiredFacts = new LinkedHashSet<>(referencedPaths);
        missingRequiredFacts.removeAll(requiredFacts);
        if (!missingRequiredFacts.isEmpty()) {
            throw new IllegalArgumentException(
                    "requiredFacts must include every fact path used by the condition. Missing: " + missingRequiredFacts
            );
        }
    }

    private static void validateSupportedPath(String path, String source) {
        if (!SUPPORTED_FACT_PATHS.contains(path)) {
            throw new IllegalArgumentException(source + " contains unsupported fact path: " + path);
        }
    }

    private static LinkedHashSet<String> referencedPaths(Expression expression) {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        collectPaths(expression, paths);
        return paths;
    }

    private static void collectPaths(Expression expression, Set<String> paths) {
        switch (expression) {
            case ComparisonExpression comparisonExpression -> {
                paths.add(comparisonExpression.leftField());
                collectOperandPath(comparisonExpression.rightOperand(), paths);
            }
            case AndExpression andExpression -> andExpression.expressions().forEach(child -> collectPaths(child, paths));
            case OrExpression orExpression -> orExpression.expressions().forEach(child -> collectPaths(child, paths));
            case NotExpression notExpression -> collectPaths(notExpression.expression(), paths);
        }
    }

    private static void collectOperandPath(Operand operand, Set<String> paths) {
        if (operand instanceof FieldRefOperand fieldRefOperand) {
            paths.add(fieldRefOperand.ref());
        }
    }
}
