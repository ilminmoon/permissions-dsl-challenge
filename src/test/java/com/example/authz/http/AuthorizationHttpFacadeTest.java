package com.example.authz.http;

import com.example.authz.domain.MembershipRole;
import com.example.authz.domain.Project;
import com.example.authz.domain.ProjectVisibility;
import com.example.authz.domain.Team;
import com.example.authz.domain.TeamPlan;
import com.example.authz.domain.User;
import com.example.authz.explain.AuthorizationDecision;
import com.example.authz.loader.AuthorizationMutationService;
import com.example.authz.loader.DocumentCreationResult;
import com.example.authz.policy.Permission;
import com.example.authz.support.AuthorizationTestSupport;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizationHttpFacadeTest {
    private final RecordingMutationService mutationService = new RecordingMutationService();
    private final AuthorizationHttpFacade facade = new AuthorizationHttpFacade(
            AuthorizationTestSupport.newEngine(
                    AuthorizationTestSupport.loader(
                            new AuthorizationTestSupport.ScenarioData(
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
    void rootDocumentListsAvailableEndpoints() {
        Map<String, Object> rootDocument = facade.rootDocument();

        assertEquals("authz-policy-engine", rootDocument.get("service"));
        assertEquals(AuthorizationHttpPaths.PERMISSION_CHECK_TEMPLATE, rootDocument.get("permissionCheckPathTemplate"));
        assertEquals(AuthorizationHttpPaths.TEAMS, rootDocument.get("teamCreatePath"));
        assertEquals(AuthorizationHttpPaths.PROJECTS, rootDocument.get("projectCreatePath"));
        assertEquals(AuthorizationHttpPaths.USERS, rootDocument.get("userCreatePath"));
        assertEquals(AuthorizationHttpPaths.DOCUMENTS, rootDocument.get("documentCreatePath"));
        assertEquals(AuthorizationHttpPaths.HEALTH, rootDocument.get("healthPath"));
        assertEquals("Authorization: Bearer <JWT>", rootDocument.get("authentication"));
    }

    @Test
    void healthDocumentReturnsOk() {
        assertEquals(Map.of("status", "ok"), facade.healthDocument());
    }

    @Test
    void authorizePathAndJwtEvaluatePermission() {
        AuthorizationDecision decision = facade.authorize(
                JwtTestSupport.bearerToken("u1"),
                "d1",
                "can_share"
        );

        assertTrue(decision.allowed());
        assertEquals("allow_project_editor_or_admin_share", decision.decisivePolicyId());
        assertEquals(Permission.CAN_SHARE, decision.permission());
    }

    @Test
    void createTeamDelegatesToMutationService() {
        CreateTeamResponse response = facade.createTeam(
                new CreateTeamRequest("t7", "Team Seven", TeamPlan.ENTERPRISE)
        );

        assertEquals("t7", response.id());
        assertEquals("Team Seven", mutationService.createdTeam.name());
        assertEquals(TeamPlan.ENTERPRISE, mutationService.createdTeam.plan());
    }

    @Test
    void createProjectDelegatesToMutationService() {
        CreateProjectResponse response = facade.createProject(
                new CreateProjectRequest("p7", "Project Seven", "t7", ProjectVisibility.PUBLIC)
        );

        assertEquals("p7", response.id());
        assertEquals("t7", mutationService.createdProject.teamId());
        assertEquals(ProjectVisibility.PUBLIC, mutationService.createdProject.visibility());
    }

    @Test
    void createUserDelegatesNormalizedMembershipsToMutationService() {
        CreateUserResponse response = facade.createUser(
                new CreateUserRequest(
                        "u3",
                        "u3@example.com",
                        "User Three",
                        List.of(new CreateUserRequest.TeamMembershipInput("t1", MembershipRole.VIEWER)),
                        List.of(new CreateUserRequest.ProjectMembershipInput("p1", MembershipRole.EDITOR))
                )
        );

        assertEquals("u3", response.id());
        assertEquals("u3@example.com", mutationService.createdUser.email());
        assertEquals("t1", mutationService.teamMemberships.getFirst().teamId());
        assertEquals("p1", mutationService.projectMemberships.getFirst().projectId());
    }

    @Test
    void createDocumentReturnsCreatorCreatedFlagFromMutationService() {
        mutationService.documentCreationResult = new DocumentCreationResult(true);

        CreateDocumentResponse response = facade.createDocument(
                new CreateDocumentRequest(
                        "d7",
                        "Doc Seven",
                        "p1",
                        new CreateDocumentRequest.CreatorInput("u3", "u3@example.com", "User Three"),
                        Instant.parse("2026-04-03T00:00:00Z"),
                        true
                )
        );

        assertEquals("d7", response.id());
        assertTrue(response.creatorCreated());
        assertEquals("u3", mutationService.createdCreator.id());
        assertEquals("p1", mutationService.createdDocument.projectId());
    }

    private static final class RecordingMutationService implements AuthorizationMutationService {
        private Team createdTeam;
        private Project createdProject;
        private User createdUser;
        private User createdCreator;
        private com.example.authz.domain.Document createdDocument;
        private List<com.example.authz.domain.TeamMembership> teamMemberships = new ArrayList<>();
        private List<com.example.authz.domain.ProjectMembership> projectMemberships = new ArrayList<>();
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
        public void createUser(
                User user,
                List<com.example.authz.domain.TeamMembership> teamMemberships,
                List<com.example.authz.domain.ProjectMembership> projectMemberships
        ) {
            this.createdUser = user;
            this.teamMemberships = List.copyOf(teamMemberships);
            this.projectMemberships = List.copyOf(projectMemberships);
        }

        @Override
        public DocumentCreationResult createDocument(com.example.authz.domain.Document document, User creator) {
            this.createdDocument = document;
            this.createdCreator = creator;
            return documentCreationResult;
        }
    }
}
