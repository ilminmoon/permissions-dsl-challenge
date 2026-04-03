package com.example.authz.http;

import com.example.authz.engine.AuthorizationRequest;
import com.example.authz.explain.AuthorizationDecision;
import com.example.authz.loader.AuthorizationDataLoader;
import com.example.authz.loader.AuthorizationMutationService;
import com.example.authz.loader.DocumentCreationResult;
import com.example.authz.loader.JdbcAuthorizationDataLoader;
import com.example.authz.loader.JdbcAuthorizationMutationService;
import com.example.authz.loader.PostgresDataSourceConfig;
import com.example.authz.loader.PostgresDataSourceFactory;
import com.example.authz.policy.DefaultPolicies;
import com.example.authz.policy.Permission;
import com.example.authz.engine.ExpressionEvaluator;
import com.example.authz.engine.PolicyEngine;
import com.example.authz.domain.Document;
import com.example.authz.domain.Project;
import com.example.authz.domain.ProjectMembership;
import com.example.authz.domain.ProjectVisibility;
import com.example.authz.domain.Team;
import com.example.authz.domain.TeamPlan;
import com.example.authz.domain.TeamMembership;
import com.example.authz.domain.User;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class AuthorizationHttpFacade implements AutoCloseable {
    private static final String DEFAULT_JWT_SECRET = "local-dev-jwt-secret";

    private final PolicyEngine engine;
    private final TokenAuthenticator tokenAuthenticator;
    private final AuthorizationMutationService mutationService;
    private final Clock clock;
    private final AutoCloseable closeableDependency;

    AuthorizationHttpFacade(
            PolicyEngine engine,
            TokenAuthenticator tokenAuthenticator,
            AuthorizationMutationService mutationService,
            Clock clock
    ) {
        this(engine, tokenAuthenticator, mutationService, clock, () -> {
        });
    }

    AuthorizationHttpFacade(
            PolicyEngine engine,
            TokenAuthenticator tokenAuthenticator,
            AuthorizationMutationService mutationService,
            Clock clock,
            AutoCloseable closeableDependency
    ) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        this.tokenAuthenticator = Objects.requireNonNull(tokenAuthenticator, "tokenAuthenticator must not be null");
        this.mutationService = Objects.requireNonNull(mutationService, "mutationService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.closeableDependency = Objects.requireNonNull(closeableDependency, "closeableDependency must not be null");
    }

    static AuthorizationHttpFacade createDefault(Map<String, String> env, Clock clock) {
        var dataSource = PostgresDataSourceFactory.create(PostgresDataSourceConfig.fromEnv(env));
        AuthorizationDataLoader dataLoader = new JdbcAuthorizationDataLoader(dataSource);
        AuthorizationMutationService mutationService = new JdbcAuthorizationMutationService(dataSource);
        PolicyEngine engine = new PolicyEngine(
                DefaultPolicies.allPolicies(),
                dataLoader,
                new ExpressionEvaluator()
        );
        String secret = env.getOrDefault("AUTHZ_JWT_SECRET", DEFAULT_JWT_SECRET);
        return new AuthorizationHttpFacade(engine, new HmacJwtAuthenticator(secret, clock), mutationService, clock, dataSource);
    }

    Map<String, Object> rootDocument() {
        return Map.of(
                "service", "authz-policy-engine",
                "status", "ok",
                "permissionCheckPathTemplate", AuthorizationHttpPaths.PERMISSION_CHECK_TEMPLATE,
                "teamCreatePath", AuthorizationHttpPaths.TEAMS,
                "projectCreatePath", AuthorizationHttpPaths.PROJECTS,
                "userCreatePath", AuthorizationHttpPaths.USERS,
                "documentCreatePath", AuthorizationHttpPaths.DOCUMENTS,
                "healthPath", AuthorizationHttpPaths.HEALTH,
                "authentication", "Authorization: Bearer <JWT>"
        );
    }

    Map<String, String> healthDocument() {
        return Map.of("status", "ok");
    }

    AuthorizationDecision authorize(String authorizationHeader, String documentId, String permissionToken) {
        AuthenticatedPrincipal principal = tokenAuthenticator.authenticate(authorizationHeader);
        Permission permission = Permission.fromJson(permissionToken);
        AuthorizationRequest request = new AuthorizationRequest(
                principal.userId(),
                documentId,
                permission,
                Instant.now(clock)
        );
        return engine.authorize(request);
    }

    CreateTeamResponse createTeam(CreateTeamRequest request) {
        WriteRequestValidator.validate(request);

        Team team = new Team(request.id(), request.name(), request.plan());
        mutationService.createTeam(team);
        return new CreateTeamResponse(request.id(), request.name(), request.plan());
    }

    CreateProjectResponse createProject(CreateProjectRequest request) {
        WriteRequestValidator.validate(request);

        Project project = new Project(request.id(), request.name(), request.teamId(), request.visibility());
        mutationService.createProject(project);
        return new CreateProjectResponse(request.id(), request.name(), request.teamId(), request.visibility());
    }

    CreateUserResponse createUser(CreateUserRequest request) {
        WriteRequestValidator.validate(request);

        User user = toUser(request);
        List<TeamMembership> teamMemberships = toTeamMemberships(request, user.id());
        List<ProjectMembership> projectMemberships = toProjectMemberships(request, user.id());

        mutationService.createUser(user, teamMemberships, projectMemberships);
        return new CreateUserResponse(
                request.id(),
                request.email(),
                request.name(),
                request.teamMemberships(),
                request.projectMemberships()
        );
    }

    CreateDocumentResponse createDocument(CreateDocumentRequest request) {
        WriteRequestValidator.validate(request);

        User creator = toCreator(request);
        Document document = toDocument(request, creator.id());

        DocumentCreationResult result = mutationService.createDocument(document, creator);
        return new CreateDocumentResponse(
                request.id(),
                request.title(),
                request.projectId(),
                request.creator(),
                request.deletedAt(),
                request.publicLinkEnabled(),
                result.creatorCreated()
        );
    }

    private User toUser(CreateUserRequest request) {
        return new User(request.id(), request.email(), request.name());
    }

    private List<TeamMembership> toTeamMemberships(CreateUserRequest request, String userId) {
        return request.teamMemberships().stream()
                .map(teamMembership -> new TeamMembership(userId, teamMembership.teamId(), teamMembership.role()))
                .toList();
    }

    private List<ProjectMembership> toProjectMemberships(CreateUserRequest request, String userId) {
        return request.projectMemberships().stream()
                .map(projectMembership -> new ProjectMembership(userId, projectMembership.projectId(), projectMembership.role()))
                .toList();
    }

    private User toCreator(CreateDocumentRequest request) {
        return new User(
                request.creator().id(),
                request.creator().email(),
                request.creator().name()
        );
    }

    private Document toDocument(CreateDocumentRequest request, String creatorId) {
        return new Document(
                request.id(),
                request.title(),
                request.projectId(),
                creatorId,
                request.deletedAt(),
                request.publicLinkEnabled()
        );
    }

    @Override
    public void close() {
        try {
            closeableDependency.close();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to close HTTP facade dependency", exception);
        }
    }
}
