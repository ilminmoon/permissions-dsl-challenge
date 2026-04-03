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
import com.example.authz.engine.AuthorizationJson;
import com.example.authz.engine.ExpressionEvaluator;
import com.example.authz.explain.AuthorizationDecision;
import com.example.authz.policy.DefaultPolicies;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizationHttpServerTest {
    private final ObjectMapper objectMapper = AuthorizationJson.newObjectMapper();

    @Test
    void authorizeReturnsBadRequestForClientInputErrors() throws Exception {
        try (AuthorizationHttpServer server = new AuthorizationHttpServer(0)) {
            server.start();

            HttpResponse<String> response = postJson(
                    server.port(),
                    """
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
                    """
            );

            assertEquals(400, response.statusCode());
            assertTrue(response.body().contains("Unsupported permission: can_publish"));
        }
    }

    @Test
    void authorizeReturnsInternalServerErrorForUnexpectedRuntimeFailures() throws Exception {
        try (AuthorizationHttpServer server = new AuthorizationHttpServer(
                0,
                new FailingDecisionObjectMapper(),
                DefaultPolicies.allPolicies(),
                new ExpressionEvaluator()
        )) {
            server.start();

            HttpResponse<String> response = postJson(
                    server.port(),
                    objectMapper.writeValueAsString(validRequestBody())
            );

            assertEquals(500, response.statusCode());
            assertTrue(response.body().contains("Internal server error"));
        }
    }

    private HttpResponse<String> postJson(int port, String jsonBody) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + AuthorizationHttpPaths.PERMISSION_CHECKS))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private AuthorizationRequestBody validRequestBody() {
        return new AuthorizationRequestBody(
                new User("u1", "user@example.com", "User"),
                new Team("t1", "Team t1", TeamPlan.PRO),
                new Project("p1", "Project p1", "t1", ProjectVisibility.PRIVATE),
                new Document("d1", "Document d1", "p1", "u2", null, false),
                new TeamMembership("u1", "t1", MembershipRole.VIEWER),
                new ProjectMembership("u1", "p1", MembershipRole.EDITOR),
                com.example.authz.policy.Permission.CAN_VIEW,
                Instant.parse("2026-03-31T00:00:00Z")
        );
    }

    private static final class FailingDecisionObjectMapper extends ObjectMapper {
        private FailingDecisionObjectMapper() {
            registerModule(new JavaTimeModule());
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        }

        @Override
        public byte[] writeValueAsBytes(Object value) throws JsonProcessingException {
            if (value instanceof AuthorizationDecision) {
                throw new RuntimeException("Synthetic serialization failure");
            }
            return super.writeValueAsBytes(value);
        }
    }
}
