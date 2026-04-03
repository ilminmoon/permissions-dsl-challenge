package com.example.authz.http;

import com.example.authz.domain.Document;
import com.example.authz.domain.MembershipRole;
import com.example.authz.domain.Project;
import com.example.authz.domain.ProjectMembership;
import com.example.authz.domain.ProjectVisibility;
import com.example.authz.domain.Team;
import com.example.authz.domain.TeamMembership;
import com.example.authz.domain.TeamPlan;
import com.example.authz.domain.User;
import com.example.authz.engine.AuthorizationRequest;
import com.example.authz.engine.EvaluationContext;
import com.example.authz.loader.AuthorizationSnapshot;
import com.example.authz.loader.DataRequirement;
import com.example.authz.policy.Permission;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestContextDataLoaderTest {
    @Test
    void requestScopedLoaderMaterializesOnlyRequiredFacts() {
        AuthorizationRequestContext context = new AuthorizationRequestContext(
                new User("u1", "user@example.com", "User"),
                new Team("t1", "Team t1", TeamPlan.PRO),
                new Project("p1", "Project p1", "t1", ProjectVisibility.PRIVATE),
                new Document("d1", "Document d1", "p1", "u2", null, false),
                new TeamMembership("u1", "t1", MembershipRole.VIEWER),
                new ProjectMembership("u1", "p1", MembershipRole.EDITOR)
        );
        RequestContextDataLoader loader = new RequestContextDataLoader(context);

        AuthorizationSnapshot snapshot = loader.load(
                new AuthorizationRequest("u1", "d1", Permission.CAN_VIEW, Instant.parse("2026-03-31T00:00:00Z")),
                new DataRequirement(Set.of("project.visibility", "projectMembership.exists", "document.publicLinkEnabled"))
        );

        EvaluationContext evaluationContext = new EvaluationContext(snapshot.facts());

        assertEquals("private", evaluationContext.lookup("project.visibility").value());
        assertEquals(Boolean.TRUE, evaluationContext.lookup("projectMembership.exists").value());
        assertEquals(Boolean.FALSE, evaluationContext.lookup("document.publicLinkEnabled").value());
        assertFalse(evaluationContext.lookup("document.creatorId").found());
        assertFalse(evaluationContext.lookup("team.plan").found());
        assertTrue(snapshot.facts().containsKey("project"));
        assertFalse(snapshot.facts().containsKey("team"));
    }
}
