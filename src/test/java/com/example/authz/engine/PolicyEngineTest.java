package com.example.authz.engine;

import com.example.authz.explain.AuthorizationDecision;
import com.example.authz.explain.ExpressionTrace;
import com.example.authz.explain.PolicyTrace;
import com.example.authz.loader.AuthorizationSnapshot;
import com.example.authz.loader.MembershipFact;
import com.example.authz.policy.Permission;
import com.example.authz.support.AuthorizationTestSupport;
import com.example.authz.support.AuthorizationTestSupport.FakeAuthorizationDataLoader;
import com.example.authz.support.AuthorizationTestSupport.ScenarioData;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyEngineTest {
    @Test
    void rejectsBlankUserIdBeforeLoadingData() {
        FakeAuthorizationDataLoader loader = AuthorizationTestSupport.loader(validScenario());
        PolicyEngine engine = AuthorizationTestSupport.newEngine(loader);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> engine.authorize(new AuthorizationRequest("   ", "d1", Permission.CAN_VIEW, AuthorizationTestSupport.FIXED_TIME))
        );

        assertEquals("request.userId must not be blank", exception.getMessage());
        assertEquals(0, loader.loadCount());
    }

    @Test
    void rejectsNullPermissionBeforeLoadingData() {
        FakeAuthorizationDataLoader loader = AuthorizationTestSupport.loader(validScenario());
        PolicyEngine engine = AuthorizationTestSupport.newEngine(loader);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> engine.authorize(new AuthorizationRequest("u1", "d1", null, AuthorizationTestSupport.FIXED_TIME))
        );

        assertEquals("request.permission must not be null", exception.getMessage());
        assertEquals(0, loader.loadCount());
    }

    @Test
    void rejectsNullRequestedAtBeforeLoadingData() {
        FakeAuthorizationDataLoader loader = AuthorizationTestSupport.loader(validScenario());
        PolicyEngine engine = AuthorizationTestSupport.newEngine(loader);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> engine.authorize(new AuthorizationRequest("u1", "d1", Permission.CAN_VIEW, null))
        );

        assertEquals("request.requestedAt must not be null", exception.getMessage());
        assertEquals(0, loader.loadCount());
    }

    @Test
    void returnsDefaultDenyWithoutLoadingWhenNoPolicyApplies() {
        FakeAuthorizationDataLoader loader = AuthorizationTestSupport.loader(validScenario());
        PolicyEngine engine = new PolicyEngine(List.of(), loader, new ExpressionEvaluator());

        AuthorizationDecision decision = engine.authorize(
                AuthorizationTestSupport.request("u1", "d1", Permission.CAN_VIEW)
        );

        assertEquals(false, decision.allowed());
        assertNull(decision.decisivePolicyId());
        assertEquals("Denied by default because no allow policy matched.", decision.finalReason());
        assertTrue(decision.trace().policyTraces().isEmpty());
        assertEquals(0, loader.loadCount());
    }

    @Test
    void loadsOnlyFactsNeededForTheRequestedPermission() {
        ScenarioData scenario = new ScenarioData(
                "u1",
                "d1",
                "u2",
                com.example.authz.domain.TeamPlan.PRO,
                com.example.authz.domain.ProjectVisibility.PRIVATE,
                null,
                false,
                com.example.authz.domain.MembershipRole.VIEWER,
                com.example.authz.domain.MembershipRole.EDITOR
        );
        FakeAuthorizationDataLoader loader = AuthorizationTestSupport.loader(scenario);
        PolicyEngine engine = AuthorizationTestSupport.newEngine(loader);

        AuthorizationDecision decision = engine.authorize(
                AuthorizationTestSupport.request("u1", "d1", Permission.CAN_DELETE)
        );

        assertEquals(false, decision.allowed());
        assertEquals(
                Set.of(
                        "document.deletedAt",
                        "document.creatorId",
                        "user.id",
                        "project.visibility",
                        "projectMembership.exists",
                        "teamMembership.exists",
                        "teamMembership.role",
                        "document.publicLinkEnabled"
                ),
                loader.lastRequirement().factPaths()
        );
        assertEquals(1, loader.loadCount());
    }

    @Test
    void creatorAllowProducesAUsableDecisionTrace() {
        ScenarioData scenario = new ScenarioData(
                "u1",
                "d1",
                "u1",
                com.example.authz.domain.TeamPlan.PRO,
                com.example.authz.domain.ProjectVisibility.PRIVATE,
                null,
                false,
                com.example.authz.domain.MembershipRole.VIEWER,
                com.example.authz.domain.MembershipRole.VIEWER
        );
        FakeAuthorizationDataLoader loader = AuthorizationTestSupport.loader(scenario);
        PolicyEngine engine = AuthorizationTestSupport.newEngine(loader);

        AuthorizationDecision decision = engine.authorize(
                AuthorizationTestSupport.request("u1", "d1", Permission.CAN_VIEW)
        );

        assertEquals(true, decision.allowed());
        assertEquals("allow_document_creator_all", decision.decisivePolicyId());
        assertTrue(decision.finalReason().contains("allow_document_creator_all"));
        assertNotNull(decision.trace());
        assertEquals(Permission.CAN_VIEW, decision.trace().request().permission());

        List<PolicyTrace> traces = decision.trace().policyTraces();
        assertEquals(
                List.of("deny_private_project_non_member_access", "allow_document_creator_all"),
                traces.stream().map(PolicyTrace::policyId).toList()
        );
        assertEquals(EvaluationResult.FALSE, traces.get(0).result());
        assertEquals(EvaluationResult.TRUE, traces.get(1).result());
        assertEquals(1, traces.get(1).expressionTraces().size());
        assertTrue(traces.get(1).expressionTraces().get(0).detail().contains("document.creatorId"));
    }

    @Test
    void denyOverridesAllowAndShortCircuitsOnceTheDecisionIsKnown() {
        ScenarioData scenario = new ScenarioData(
                "u1",
                "d1",
                "u1",
                com.example.authz.domain.TeamPlan.PRO,
                com.example.authz.domain.ProjectVisibility.PRIVATE,
                Instant.parse("2026-03-31T12:00:00Z"),
                false,
                com.example.authz.domain.MembershipRole.ADMIN,
                com.example.authz.domain.MembershipRole.ADMIN
        );
        FakeAuthorizationDataLoader loader = AuthorizationTestSupport.loader(scenario);
        PolicyEngine engine = AuthorizationTestSupport.newEngine(loader);

        AuthorizationDecision decision = engine.authorize(
                AuthorizationTestSupport.request("u1", "d1", Permission.CAN_SHARE)
        );

        assertEquals(false, decision.allowed());
        assertEquals("deny_deleted_document_mutations", decision.decisivePolicyId());
        assertTrue(decision.finalReason().contains("Deleted documents cannot be edited, deleted, or shared."));
        assertEquals(1, decision.trace().policyTraces().size());
        assertEquals("deny_deleted_document_mutations", decision.trace().policyTraces().getFirst().policyId());
        assertEquals(EvaluationResult.TRUE, decision.trace().policyTraces().getFirst().result());
    }

    @Test
    void returnsDefaultDenyWhenNoAllowPolicyMatches() {
        ScenarioData scenario = new ScenarioData(
                "u1",
                "d1",
                "u2",
                com.example.authz.domain.TeamPlan.PRO,
                com.example.authz.domain.ProjectVisibility.PUBLIC,
                null,
                false,
                null,
                null
        );
        FakeAuthorizationDataLoader loader = AuthorizationTestSupport.loader(scenario);
        PolicyEngine engine = AuthorizationTestSupport.newEngine(loader);

        AuthorizationDecision decision = engine.authorize(
                AuthorizationTestSupport.request("u1", "d1", Permission.CAN_DELETE)
        );

        assertEquals(false, decision.allowed());
        assertNull(decision.decisivePolicyId());
        assertEquals("Denied by default because no allow policy matched.", decision.finalReason());
        assertEquals(
                List.of(
                        "deny_deleted_document_mutations",
                        "deny_private_project_non_member_access",
                        "allow_document_creator_all"
                ),
                decision.trace().policyTraces().stream().map(PolicyTrace::policyId).toList()
        );
        assertEquals(
                List.of(EvaluationResult.FALSE, EvaluationResult.FALSE, EvaluationResult.FALSE),
                decision.trace().policyTraces().stream().map(PolicyTrace::result).toList()
        );
    }

    @Test
    void freePlanShareDenyOverridesCreatorAllow() {
        ScenarioData scenario = new ScenarioData(
                "u1",
                "d1",
                "u1",
                com.example.authz.domain.TeamPlan.FREE,
                com.example.authz.domain.ProjectVisibility.PRIVATE,
                null,
                false,
                com.example.authz.domain.MembershipRole.ADMIN,
                com.example.authz.domain.MembershipRole.ADMIN
        );
        FakeAuthorizationDataLoader loader = AuthorizationTestSupport.loader(scenario);
        PolicyEngine engine = AuthorizationTestSupport.newEngine(loader);

        AuthorizationDecision decision = engine.authorize(
                AuthorizationTestSupport.request("u1", "d1", Permission.CAN_SHARE)
        );

        assertEquals(false, decision.allowed());
        assertEquals("deny_free_plan_share", decision.decisivePolicyId());
        assertEquals(
                List.of("deny_deleted_document_mutations", "deny_private_project_non_member_access", "deny_free_plan_share"),
                decision.trace().policyTraces().stream().map(PolicyTrace::policyId).toList()
        );
        assertEquals(
                List.of(EvaluationResult.FALSE, EvaluationResult.FALSE, EvaluationResult.TRUE),
                decision.trace().policyTraces().stream().map(PolicyTrace::result).toList()
        );
    }

    @Test
    void freePlanShareDenyOverridesTeamAdminAllow() {
        ScenarioData scenario = new ScenarioData(
                "u1",
                "d1",
                "u2",
                com.example.authz.domain.TeamPlan.FREE,
                com.example.authz.domain.ProjectVisibility.PRIVATE,
                null,
                false,
                com.example.authz.domain.MembershipRole.ADMIN,
                null
        );
        FakeAuthorizationDataLoader loader = AuthorizationTestSupport.loader(scenario);
        PolicyEngine engine = AuthorizationTestSupport.newEngine(loader);

        AuthorizationDecision decision = engine.authorize(
                AuthorizationTestSupport.request("u1", "d1", Permission.CAN_SHARE)
        );

        assertEquals(false, decision.allowed());
        assertEquals("deny_free_plan_share", decision.decisivePolicyId());
        assertTrue(decision.finalReason().contains("Free-plan teams cannot change document sharing settings."));
        assertEquals(
                List.of(EvaluationResult.FALSE, EvaluationResult.FALSE, EvaluationResult.TRUE),
                decision.trace().policyTraces().stream().map(PolicyTrace::result).toList()
        );
    }

    @Test
    void partialDataLeavesUnknownResultsInTheDecisionTrace() {
        FakeAuthorizationDataLoader loader = AuthorizationTestSupport.loader(request -> {
            Map<String, Object> facts = new LinkedHashMap<>();
            facts.put("user", Map.of("id", "u1"));
            facts.put("project", Map.of("visibility", "private"));
            facts.put("document", Map.of("publicLinkEnabled", false));

            return new AuthorizationSnapshot(
                    request,
                    new com.example.authz.domain.User("u1", "u1@example.com", "User"),
                    new com.example.authz.domain.Team("t1", "Team", com.example.authz.domain.TeamPlan.PRO),
                    new com.example.authz.domain.Project("p1", "Project", "t1", com.example.authz.domain.ProjectVisibility.PRIVATE),
                    new com.example.authz.domain.Document("d1", "Doc", "p1", "u2", null, false),
                    new MembershipFact(false, null),
                    new MembershipFact(false, null),
                    facts
            );
        });
        PolicyEngine engine = AuthorizationTestSupport.newEngine(loader);

        AuthorizationDecision decision = engine.authorize(
                AuthorizationTestSupport.request("u1", "d1", Permission.CAN_VIEW)
        );

        assertEquals(false, decision.allowed());
        assertNull(decision.decisivePolicyId());
        assertEquals(
                List.of("deny_private_project_non_member_access", "allow_document_creator_all", "allow_team_admin_view_edit_share", "allow_public_link_view", "allow_project_member_view"),
                decision.trace().policyTraces().stream().map(PolicyTrace::policyId).toList()
        );
        assertEquals(
                List.of(EvaluationResult.UNKNOWN, EvaluationResult.UNKNOWN, EvaluationResult.UNKNOWN, EvaluationResult.FALSE, EvaluationResult.UNKNOWN),
                decision.trace().policyTraces().stream().map(PolicyTrace::result).toList()
        );

        ExpressionTrace privateProjectTrace = decision.trace().policyTraces().getFirst().expressionTraces().getFirst();
        assertEquals(EvaluationResult.UNKNOWN, privateProjectTrace.result());
        assertTrue(privateProjectTrace.detail().contains("projectMembership.exists"));
        assertTrue(privateProjectTrace.detail().contains("teamMembership.role"));
    }

    private ScenarioData validScenario() {
        return new ScenarioData(
                "u1",
                "d1",
                "u2",
                com.example.authz.domain.TeamPlan.PRO,
                com.example.authz.domain.ProjectVisibility.PRIVATE,
                null,
                false,
                com.example.authz.domain.MembershipRole.VIEWER,
                com.example.authz.domain.MembershipRole.EDITOR
        );
    }
}
