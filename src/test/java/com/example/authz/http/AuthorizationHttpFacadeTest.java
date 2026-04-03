package com.example.authz.http;

import com.example.authz.domain.Document;
import com.example.authz.domain.Project;
import com.example.authz.domain.ProjectMembership;
import com.example.authz.domain.ProjectVisibility;
import com.example.authz.domain.Team;
import com.example.authz.domain.TeamMembership;
import com.example.authz.domain.TeamPlan;
import com.example.authz.domain.User;
import com.example.authz.domain.MembershipRole;
import com.example.authz.engine.ExpressionEvaluator;
import com.example.authz.explain.AuthorizationDecision;
import com.example.authz.policy.DefaultPolicies;
import com.example.authz.policy.Permission;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthorizationHttpFacadeTest {
    private final AuthorizationHttpFacade facade = new AuthorizationHttpFacade(
            DefaultPolicies.allPolicies(),
            new ExpressionEvaluator()
    );

    @Test
    void rootDocumentListsAvailableEndpoints() {
        Map<String, Object> rootDocument = facade.rootDocument();

        assertEquals("authz-policy-engine", rootDocument.get("service"));
        assertEquals(AuthorizationHttpPaths.PERMISSION_CHECKS, rootDocument.get("permissionChecksPath"));
        assertEquals(AuthorizationHttpPaths.HEALTH, rootDocument.get("healthPath"));
    }

    @Test
    void healthDocumentReturnsOk() {
        assertEquals(Map.of("status", "ok"), facade.healthDocument());
    }

    @Test
    void authorizePayloadEvaluatesScenarioStyleContext() {
        AuthorizationRequestBody payload = new AuthorizationRequestBody(
                new User("u1", "user@example.com", "User"),
                new Team("t1", "Team t1", TeamPlan.PRO),
                new Project("p1", "Project p1", "t1", ProjectVisibility.PRIVATE),
                new Document("d1", "Document d1", "p1", "u2", null, false),
                new TeamMembership("u1", "t1", MembershipRole.VIEWER),
                new ProjectMembership("u1", "p1", MembershipRole.EDITOR),
                Permission.CAN_SHARE,
                Instant.parse("2026-03-31T00:00:00Z")
        );

        AuthorizationDecision decision = facade.authorize(payload);

        assertEquals(true, decision.allowed());
        assertEquals("allow_project_editor_or_admin_share", decision.decisivePolicyId());
        assertEquals(Permission.CAN_SHARE, decision.permission());
    }
}
