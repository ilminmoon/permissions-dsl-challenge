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
import com.example.authz.engine.AuthorizationJson;
import com.example.authz.engine.ExpressionEvaluator;
import com.example.authz.policy.DefaultPolicies;
import com.example.authz.policy.Permission;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthorizationRequestBodyValidationTest {
    private final AuthorizationHttpFacade facade = new AuthorizationHttpFacade(
            DefaultPolicies.allPolicies(),
            new ExpressionEvaluator()
    );

    private final AuthorizationRequestBodyReader requestBodyReader =
            new AuthorizationRequestBodyReader(AuthorizationJson.newObjectMapper());

    @Test
    void authorizeRejectsBlankUserId() {
        AuthorizationRequestBody payload = validRequestBody(
                new User("   ", "user@example.com", "User"),
                new Team("t1", "Team t1", TeamPlan.PRO),
                new Project("p1", "Project p1", "t1", ProjectVisibility.PRIVATE),
                new Document("d1", "Document d1", "p1", "u2", null, false),
                new TeamMembership("u1", "t1", MembershipRole.VIEWER),
                new ProjectMembership("u1", "p1", MembershipRole.EDITOR),
                Permission.CAN_VIEW
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> facade.authorize(payload));

        assertEquals("user.id must not be blank", exception.getMessage());
    }

    @Test
    void authorizeRejectsProjectTeamMismatch() {
        AuthorizationRequestBody payload = validRequestBody(
                new User("u1", "user@example.com", "User"),
                new Team("t1", "Team t1", TeamPlan.PRO),
                new Project("p1", "Project p1", "t9", ProjectVisibility.PRIVATE),
                new Document("d1", "Document d1", "p1", "u2", null, false),
                new TeamMembership("u1", "t1", MembershipRole.VIEWER),
                new ProjectMembership("u1", "p1", MembershipRole.EDITOR),
                Permission.CAN_VIEW
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> facade.authorize(payload));

        assertEquals("project.teamId must match team.id", exception.getMessage());
    }

    @Test
    void authorizeRejectsTeamMembershipUserMismatch() {
        AuthorizationRequestBody payload = validRequestBody(
                new User("u1", "user@example.com", "User"),
                new Team("t1", "Team t1", TeamPlan.PRO),
                new Project("p1", "Project p1", "t1", ProjectVisibility.PRIVATE),
                new Document("d1", "Document d1", "p1", "u2", null, false),
                new TeamMembership("u9", "t1", MembershipRole.VIEWER),
                new ProjectMembership("u1", "p1", MembershipRole.EDITOR),
                Permission.CAN_VIEW
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> facade.authorize(payload));

        assertEquals("teamMembership.userId must match user.id", exception.getMessage());
    }

    @Test
    void payloadReaderRejectsMissingRequestedAt() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> requestBodyReader.read(json("""
                        {
                          "user": { "id": "u1", "email": "user@example.com", "name": "User" },
                          "team": { "id": "t1", "name": "Team t1", "plan": "pro" },
                          "project": { "id": "p1", "name": "Project p1", "teamId": "t1", "visibility": "private" },
                          "document": {
                            "id": "d1",
                            "title": "Document d1",
                            "projectId": "p1",
                            "creatorId": "u2",
                            "deletedAt": null,
                            "publicLinkEnabled": false
                          },
                          "permission": "can_view"
                        }
                        """))
        );

        assertEquals("Invalid request body at requestedAt: requestedAt must not be null", exception.getMessage());
    }

    @Test
    void payloadReaderRejectsInvalidPermission() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> requestBodyReader.read(json("""
                        {
                          "user": { "id": "u1", "email": "user@example.com", "name": "User" },
                          "team": { "id": "t1", "name": "Team t1", "plan": "pro" },
                          "project": { "id": "p1", "name": "Project p1", "teamId": "t1", "visibility": "private" },
                          "document": {
                            "id": "d1",
                            "title": "Document d1",
                            "projectId": "p1",
                            "creatorId": "u2",
                            "deletedAt": null,
                            "publicLinkEnabled": false
                          },
                          "permission": "can_publish",
                          "requestedAt": "2026-03-31T00:00:00Z"
                        }
                        """))
        );

        assertEquals("Invalid request body at permission: Unsupported permission: can_publish", exception.getMessage());
    }

    @Test
    void payloadReaderRejectsInvalidTimestamp() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> requestBodyReader.read(json("""
                        {
                          "user": { "id": "u1", "email": "user@example.com", "name": "User" },
                          "team": { "id": "t1", "name": "Team t1", "plan": "pro" },
                          "project": { "id": "p1", "name": "Project p1", "teamId": "t1", "visibility": "private" },
                          "document": {
                            "id": "d1",
                            "title": "Document d1",
                            "projectId": "p1",
                            "creatorId": "u2",
                            "deletedAt": null,
                            "publicLinkEnabled": false
                          },
                          "permission": "can_view",
                          "requestedAt": "not-a-timestamp"
                        }
                        """))
        );

        assertEquals("Invalid timestamp at requestedAt. Expected an ISO-8601 instant.", exception.getMessage());
    }

    private AuthorizationRequestBody validRequestBody(
            User user,
            Team team,
            Project project,
            Document document,
            TeamMembership teamMembership,
            ProjectMembership projectMembership,
            Permission permission
    ) {
        return new AuthorizationRequestBody(
                user,
                team,
                project,
                document,
                teamMembership,
                projectMembership,
                permission,
                Instant.parse("2026-03-31T00:00:00Z")
        );
    }

    private ByteArrayInputStream json(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
