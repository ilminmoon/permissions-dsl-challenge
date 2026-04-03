package com.example.authz.policy;

import com.example.authz.dsl.ComparisonExpression;
import com.example.authz.dsl.ComparisonOperator;
import com.example.authz.dsl.LiteralOperand;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyDefinitionTest {
    @Test
    void preservesImmutablePolicyMetadata() {
        PolicyDefinition policy = new PolicyDefinition(
                "deny_free_plan_share",
                "Free-plan teams cannot change document sharing settings.",
                PolicyEffect.DENY,
                Set.of(Permission.CAN_SHARE),
                new ComparisonExpression("team.plan", ComparisonOperator.EQ, new LiteralOperand("free")),
                Set.of("team.plan")
        );

        assertEquals(PolicyEffect.DENY, policy.effect());
        assertEquals(Set.of(Permission.CAN_SHARE), policy.permissions());
    }

    @Test
    void preservesRequiredFactOrderForReadableTraceOutput() {
        PolicyDefinition policy = new PolicyDefinition(
                "ordered_policy",
                "Preserves required fact order.",
                PolicyEffect.ALLOW,
                Set.of(Permission.CAN_VIEW),
                new ComparisonExpression("project.visibility", ComparisonOperator.EQ, new LiteralOperand("private")),
                new LinkedHashSet<>(List.of("project.visibility", "teamMembership.role", "projectMembership.exists"))
        );

        assertEquals(
                List.of("project.visibility", "teamMembership.role", "projectMembership.exists"),
                policy.requiredFacts().stream().toList()
        );
    }

    @Test
    void defaultPoliciesExposeMandatoryAndSupplementalDefinitions() {
        assertEquals(7, DefaultPolicies.mandatoryPolicies().size());
        assertEquals(2, DefaultPolicies.supplementalPolicies().size());
        assertEquals(9, DefaultPolicies.allPolicies().size());
        assertEquals(
                Set.of(
                        "deny_deleted_document_mutations",
                        "allow_document_creator_all",
                        "allow_project_editor_or_admin_edit",
                        "allow_team_admin_view_edit_share",
                        "deny_private_project_non_member_access",
                        "deny_free_plan_share",
                        "allow_public_link_view",
                        "allow_project_member_view",
                        "allow_project_editor_or_admin_share"
                ),
                DefaultPolicies.allPolicies().stream().map(PolicyDefinition::id).collect(java.util.stream.Collectors.toSet())
        );
        assertTrue(DefaultPolicies.allPolicies().stream().allMatch(policy -> !policy.requiredFacts().isEmpty()));
        PolicyDefinition privateProjectDeny = DefaultPolicies.allPolicies().stream()
                .filter(policy -> policy.id().equals("deny_private_project_non_member_access"))
                .findFirst()
                .orElseThrow();
        assertEquals(
                List.of(
                        "project.visibility",
                        "projectMembership.exists",
                        "teamMembership.exists",
                        "teamMembership.role",
                        "document.publicLinkEnabled",
                        "request.permission"
                ),
                privateProjectDeny.requiredFacts().stream().toList()
        );
    }

    @Test
    void rejectsUnsupportedFactPathInsideCondition() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PolicyDefinition(
                        "invalid_policy",
                        "Contains an unsupported expression path.",
                        PolicyEffect.ALLOW,
                        Set.of(Permission.CAN_VIEW),
                        new ComparisonExpression("document.ownerId", ComparisonOperator.EQ, new LiteralOperand("u1")),
                        Set.of("document.ownerId")
                )
        );

        assertEquals("condition contains unsupported fact path: document.ownerId", exception.getMessage());
    }

    @Test
    void rejectsRequiredFactsThatDoNotCoverConditionPaths() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PolicyDefinition(
                        "missing_required_fact",
                        "Missing required facts for the condition.",
                        PolicyEffect.ALLOW,
                        Set.of(Permission.CAN_VIEW),
                        new ComparisonExpression("user.id", ComparisonOperator.EQ, new LiteralOperand("u1")),
                        Set.of("team.id")
                )
        );

        assertEquals(
                "requiredFacts must include every fact path used by the condition. Missing: [user.id]",
                exception.getMessage()
        );
    }
}
