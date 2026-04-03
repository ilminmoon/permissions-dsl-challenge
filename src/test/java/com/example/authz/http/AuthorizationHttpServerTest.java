package com.example.authz.http;

import com.example.authz.domain.ProjectVisibility;
import com.example.authz.domain.TeamPlan;
import com.example.authz.domain.MembershipRole;
import com.example.authz.domain.Project;
import com.example.authz.domain.ProjectMembership;
import com.example.authz.domain.Team;
import com.example.authz.domain.TeamMembership;
import com.example.authz.domain.User;
import com.example.authz.engine.AuthorizationJson;
import com.example.authz.explain.AuthorizationDecision;
import com.example.authz.loader.AuthorizationMutationService;
import com.example.authz.loader.ConflictException;
import com.example.authz.loader.DocumentCreationResult;
import com.example.authz.loader.ResourceNotFoundException;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizationHttpServerTest {
    private final RecordingMutationService mutationService = new RecordingMutationService();
    private final AuthorizationHttpFacade facade = new AuthorizationHttpFacade(
            com.example.authz.support.AuthorizationTestSupport.newEngine(
                    com.example.authz.support.AuthorizationTestSupport.loader(
                            new com.example.authz.support.AuthorizationTestSupport.ScenarioData(
                                    "u1",
                                    "d1",
                                    "u2",
                                    TeamPlan.PRO,
                                    ProjectVisibility.PRIVATE,
                                    null,
                                    false,
                                    MembershipRole.VIEWER,
                                    MembershipRole.EDITOR
                            )
                    )
            ),
            new HmacJwtAuthenticator(JwtTestSupport.TEST_JWT_SECRET, JwtTestSupport.FIXED_CLOCK),
            mutationService,
            JwtTestSupport.FIXED_CLOCK
    );

    @Test
    void permissionCheckReturnsBadRequestForInvalidPermission() throws Exception {
        try (AuthorizationHttpServer server = new AuthorizationHttpServer(0, AuthorizationJson.newObjectMapper(), facade)) {
            server.start();

            HttpResponse<String> response = get(
                    server.port(),
                    "/v1/documents/d1/permissions/can_publish",
                    JwtTestSupport.bearerToken("u1")
            );

            assertEquals(400, response.statusCode());
            assertTrue(response.body().contains("Unsupported permission: can_publish"));
        }
    }

    @Test
    void permissionCheckReturnsUnauthorizedForMissingJwt() throws Exception {
        try (AuthorizationHttpServer server = new AuthorizationHttpServer(0, AuthorizationJson.newObjectMapper(), facade)) {
            server.start();

            HttpResponse<String> response = get(
                    server.port(),
                    "/v1/documents/d1/permissions/can_view",
                    null
            );

            assertEquals(401, response.statusCode());
            assertTrue(response.body().contains("Bearer token"));
        }
    }

    @Test
    void permissionCheckReturnsNotFoundForUnknownDocument() throws Exception {
        AuthorizationHttpFacade missingDocumentFacade = new AuthorizationHttpFacade(
                com.example.authz.support.AuthorizationTestSupport.newEngine(
                        (request, requirement) -> {
                            throw new ResourceNotFoundException("Document not found: " + request.documentId());
                        }
                ),
                new HmacJwtAuthenticator(JwtTestSupport.TEST_JWT_SECRET, JwtTestSupport.FIXED_CLOCK),
                new RecordingMutationService(),
                JwtTestSupport.FIXED_CLOCK
        );

        try (AuthorizationHttpServer server = new AuthorizationHttpServer(0, AuthorizationJson.newObjectMapper(), missingDocumentFacade)) {
            server.start();

            HttpResponse<String> response = get(
                    server.port(),
                    "/v1/documents/missing/permissions/can_view",
                    JwtTestSupport.bearerToken("u1")
            );

            assertEquals(404, response.statusCode());
            assertTrue(response.body().contains("Document not found"));
        }
    }

    @Test
    void permissionCheckReturnsInternalServerErrorForUnexpectedRuntimeFailures() throws Exception {
        try (AuthorizationHttpServer server = new AuthorizationHttpServer(
                0,
                new FailingDecisionObjectMapper(),
                facade
        )) {
            server.start();

            HttpResponse<String> response = get(
                    server.port(),
                    "/v1/documents/d1/permissions/can_view",
                    JwtTestSupport.bearerToken("u1")
            );

            assertEquals(500, response.statusCode());
            assertTrue(response.body().contains("Internal server error"));
        }
    }

    @Test
    void teamCreateReturnsCreatedPayload() throws Exception {
        try (AuthorizationHttpServer server = new AuthorizationHttpServer(0, AuthorizationJson.newObjectMapper(), facade)) {
            server.start();

            HttpResponse<String> response = postJson(
                    server.port(),
                    "/v1/teams",
                    """
                    {
                      "id": "t7",
                      "name": "Team Seven",
                      "plan": "enterprise"
                    }
                    """
            );

            assertEquals(201, response.statusCode());
            assertTrue(response.body().contains("\"id\":\"t7\""));
            assertEquals("t7", mutationService.createdTeam.id());
        }
    }

    @Test
    void projectCreateReturnsCreatedPayload() throws Exception {
        try (AuthorizationHttpServer server = new AuthorizationHttpServer(0, AuthorizationJson.newObjectMapper(), facade)) {
            server.start();

            HttpResponse<String> response = postJson(
                    server.port(),
                    "/v1/projects",
                    """
                    {
                      "id": "p7",
                      "name": "Project Seven",
                      "teamId": "t7",
                      "visibility": "public"
                    }
                    """
            );

            assertEquals(201, response.statusCode());
            assertTrue(response.body().contains("\"id\":\"p7\""));
            assertEquals("p7", mutationService.createdProject.id());
        }
    }

    @Test
    void userCreateReturnsCreatedPayload() throws Exception {
        try (AuthorizationHttpServer server = new AuthorizationHttpServer(0, AuthorizationJson.newObjectMapper(), facade)) {
            server.start();

            HttpResponse<String> response = postJson(
                    server.port(),
                    "/v1/users",
                    """
                    {
                      "id": "u3",
                      "email": "u3@example.com",
                      "name": "User Three",
                      "teamMemberships": [
                        { "teamId": "t1", "role": "viewer" }
                      ],
                      "projectMemberships": [
                        { "projectId": "p1", "role": "editor" }
                      ]
                    }
                    """
            );

            assertEquals(201, response.statusCode());
            assertTrue(response.body().contains("\"id\":\"u3\""));
            assertEquals("u3", mutationService.createdUser.id());
            assertEquals("t1", mutationService.teamMemberships.getFirst().teamId());
        }
    }

    @Test
    void documentCreateReturnsCreatedPayload() throws Exception {
        mutationService.documentCreationResult = new DocumentCreationResult(true);

        try (AuthorizationHttpServer server = new AuthorizationHttpServer(0, AuthorizationJson.newObjectMapper(), facade)) {
            server.start();

            HttpResponse<String> response = postJson(
                    server.port(),
                    "/v1/documents",
                    """
                    {
                      "id": "d7",
                      "title": "Document Seven",
                      "projectId": "p1",
                      "creator": {
                        "id": "u3",
                        "email": "u3@example.com",
                        "name": "User Three"
                      },
                      "deletedAt": null,
                      "publicLinkEnabled": false
                    }
                    """
            );

            assertEquals(201, response.statusCode());
            assertTrue(response.body().contains("\"creatorCreated\":true"));
            assertNotNull(mutationService.createdDocument);
            assertEquals("d7", mutationService.createdDocument.id());
        }
    }

    @Test
    void userCreateReturnsBadRequestForInvalidPayload() throws Exception {
        try (AuthorizationHttpServer server = new AuthorizationHttpServer(0, AuthorizationJson.newObjectMapper(), facade)) {
            server.start();

            HttpResponse<String> response = postJson(
                    server.port(),
                    "/v1/users",
                    """
                    {
                      "id": "",
                      "email": "u3@example.com",
                      "name": "User Three"
                    }
                    """
            );

            assertEquals(400, response.statusCode());
            assertTrue(response.body().contains("id must not be blank"));
        }
    }

    @Test
    void documentCreateReturnsConflictWhenMutationServiceRejectsRequest() throws Exception {
        AuthorizationHttpFacade conflictFacade = new AuthorizationHttpFacade(
                com.example.authz.support.AuthorizationTestSupport.newEngine(
                        com.example.authz.support.AuthorizationTestSupport.loader(
                                new com.example.authz.support.AuthorizationTestSupport.ScenarioData(
                                        "u1",
                                        "d1",
                                        "u2",
                                        TeamPlan.PRO,
                                        ProjectVisibility.PRIVATE,
                                        null,
                                        false,
                                        MembershipRole.VIEWER,
                                        MembershipRole.EDITOR
                                )
                        )
                ),
                new HmacJwtAuthenticator(JwtTestSupport.TEST_JWT_SECRET, JwtTestSupport.FIXED_CLOCK),
                new AuthorizationMutationService() {
                    @Override
                    public void createTeam(Team team) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public void createProject(Project project) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public void createUser(User user, List<TeamMembership> teamMemberships, List<ProjectMembership> projectMemberships) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public DocumentCreationResult createDocument(com.example.authz.domain.Document document, User creator) {
                        throw new ConflictException("Document already exists: " + document.id());
                    }
                },
                JwtTestSupport.FIXED_CLOCK
        );

        try (AuthorizationHttpServer server = new AuthorizationHttpServer(0, AuthorizationJson.newObjectMapper(), conflictFacade)) {
            server.start();

            HttpResponse<String> response = postJson(
                    server.port(),
                    "/v1/documents",
                    """
                    {
                      "id": "d1",
                      "title": "Document One",
                      "projectId": "p1",
                      "creator": {
                        "id": "u2",
                        "email": "creator@example.com",
                        "name": "Document Creator"
                      },
                      "deletedAt": null,
                      "publicLinkEnabled": false
                    }
                    """
            );

            assertEquals(409, response.statusCode());
            assertTrue(response.body().contains("Document already exists"));
        }
    }

    private HttpResponse<String> get(int port, String path, String authorizationHeader) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .GET();
        if (authorizationHeader != null) {
            builder.header("Authorization", authorizationHeader);
        }

        HttpRequest request = builder
                .build();

        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(int port, String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
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

    private static final class RecordingMutationService implements AuthorizationMutationService {
        private Team createdTeam;
        private Project createdProject;
        private User createdUser;
        private com.example.authz.domain.Document createdDocument;
        private List<TeamMembership> teamMemberships = new ArrayList<>();
        private DocumentCreationResult documentCreationResult = new DocumentCreationResult(false);

        @Override
        public void createTeam(Team team) {
            this.createdTeam = team;
        }

        @Override
        public void createProject(Project project) {
            this.createdProject = project;
        }

        @Override
        public void createUser(User user, List<TeamMembership> teamMemberships, List<ProjectMembership> projectMemberships) {
            this.createdUser = user;
            this.teamMemberships = List.copyOf(teamMemberships);
        }

        @Override
        public DocumentCreationResult createDocument(com.example.authz.domain.Document document, User creator) {
            this.createdDocument = document;
            return documentCreationResult;
        }
    }
}
