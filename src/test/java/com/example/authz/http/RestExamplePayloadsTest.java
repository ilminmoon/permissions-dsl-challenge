package com.example.authz.http;

import com.example.authz.domain.MembershipRole;
import com.example.authz.domain.Project;
import com.example.authz.domain.ProjectVisibility;
import com.example.authz.domain.Team;
import com.example.authz.domain.TeamPlan;
import com.example.authz.engine.AuthorizationRequest;
import com.example.authz.explain.AuthorizationDecision;
import com.example.authz.loader.AuthorizationMutationService;
import com.example.authz.loader.DocumentCreationResult;
import com.example.authz.loader.AuthorizationSnapshot;
import com.example.authz.support.AuthorizationTestSupport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RestExamplePayloadsTest {
    private static final Path EXAMPLES_DIR = Path.of("examples", "rest");

    private final AuthorizationHttpFacade facade = new AuthorizationHttpFacade(
            AuthorizationTestSupport.newEngine(
                    (request, requirement) -> snapshotFor(request.documentId(), request)
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
                public void createUser(
                        com.example.authz.domain.User user,
                        List<com.example.authz.domain.TeamMembership> teamMemberships,
                        List<com.example.authz.domain.ProjectMembership> projectMemberships
                ) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public DocumentCreationResult createDocument(com.example.authz.domain.Document document, com.example.authz.domain.User creator) {
                    throw new UnsupportedOperationException();
                }
            },
            JwtTestSupport.FIXED_CLOCK
    );

    @Test
    void allRestExamplesDeserializeAndMatchScenarioExpectations() throws IOException {
        List<Path> files;
        try (Stream<Path> stream = Files.list(EXAMPLES_DIR)) {
            files = stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }

        assertEquals(24, files.size());

        for (Path file : files) {
            RestExampleCase example = com.example.authz.engine.AuthorizationJson.newObjectMapper()
                    .readValue(file.toFile(), RestExampleCase.class);
            assertEquals("GET", example.method(), file.getFileName().toString());
            PermissionCheckRoute route = PermissionCheckRoute.tryParse(example.path())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid permission-check path: " + example.path()));

            AuthorizationDecision decision = facade.authorize(
                    JwtTestSupport.bearerToken(example.jwtSubject()),
                    route.documentId(),
                    route.permissionToken()
            );
            String fileName = file.getFileName().toString();

            assertEquals(example.expectedAllowed(), decision.allowed(), fileName);
            if (example.expectedDecisivePolicyId() != null) {
                assertEquals(example.expectedDecisivePolicyId(), decision.decisivePolicyId(), fileName);
            }
        }
    }

    private record RestExampleCase(
            String method,
            String path,
            String jwtSubject,
            boolean expectedAllowed,
            String expectedDecisivePolicyId
    ) {
    }

    private AuthorizationSnapshot snapshotFor(String documentId, AuthorizationRequest request) {
        return AuthorizationTestSupport.snapshot(request, switch (documentId) {
            case "d1" -> new AuthorizationTestSupport.ScenarioData("u1", "d1", "u2", TeamPlan.PRO, ProjectVisibility.PRIVATE, null, false, MembershipRole.VIEWER, MembershipRole.EDITOR);
            case "d2" -> new AuthorizationTestSupport.ScenarioData("u1", "d2", "u1", TeamPlan.PRO, ProjectVisibility.PRIVATE, Instant.parse("2026-03-31T12:00:00Z"), false, MembershipRole.ADMIN, MembershipRole.ADMIN);
            case "d3" -> new AuthorizationTestSupport.ScenarioData("u1", "d3", "u2", TeamPlan.FREE, ProjectVisibility.PUBLIC, null, false, MembershipRole.VIEWER, MembershipRole.ADMIN);
            case "d4" -> new AuthorizationTestSupport.ScenarioData("u1", "d4", "u2", TeamPlan.PRO, ProjectVisibility.PRIVATE, null, false, MembershipRole.ADMIN, null);
            case "d5" -> new AuthorizationTestSupport.ScenarioData("u1", "d5", "u2", TeamPlan.PRO, ProjectVisibility.PRIVATE, null, false, MembershipRole.EDITOR, null);
            case "d6" -> new AuthorizationTestSupport.ScenarioData("u1", "d6", "u2", TeamPlan.PRO, ProjectVisibility.PRIVATE, null, true, null, null);
            default -> throw new IllegalArgumentException("Unknown example document: " + documentId);
        });
    }
}
