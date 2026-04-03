package com.example.authz.loader;

import com.example.authz.domain.Document;
import com.example.authz.domain.MembershipRole;
import com.example.authz.domain.Project;
import com.example.authz.domain.ProjectVisibility;
import com.example.authz.domain.Team;
import com.example.authz.domain.TeamPlan;
import com.example.authz.domain.User;
import com.example.authz.engine.AuthorizationRequest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class JdbcAuthorizationDataLoader implements AuthorizationDataLoader {
    private static final String DOCUMENT_CONTEXT_WITH_MEMBERSHIPS_SQL = """
            select
                d.id as document_id,
                d.title as document_title,
                d.project_id as document_project_id,
                d.creator_id as document_creator_id,
                d.deleted_at as document_deleted_at,
                d.public_link_enabled as document_public_link_enabled,
                p.id as project_id,
                p.name as project_name,
                p.team_id as project_team_id,
                p.visibility as project_visibility,
                t.id as team_id,
                t.name as team_name,
                t.plan as team_plan,
                tm.role as team_membership_role,
                pm.role as project_membership_role
            from documents d
            join projects p on p.id = d.project_id
            join teams t on t.id = p.team_id
            left join team_memberships tm
                on tm.user_id = ?
               and tm.team_id = t.id
            left join project_memberships pm
                on pm.user_id = ?
               and pm.project_id = p.id
            where d.id = ?
            """;

    private static final String DOCUMENT_CONTEXT_SQL = """
            select
                d.id as document_id,
                d.title as document_title,
                d.project_id as document_project_id,
                d.creator_id as document_creator_id,
                d.deleted_at as document_deleted_at,
                d.public_link_enabled as document_public_link_enabled,
                p.id as project_id,
                p.name as project_name,
                p.team_id as project_team_id,
                p.visibility as project_visibility,
                t.id as team_id,
                t.name as team_name,
                t.plan as team_plan
            from documents d
            join projects p on p.id = d.project_id
            join teams t on t.id = p.team_id
            where d.id = ?
            """;

    private static final String USER_SQL = """
            select id, email, name
            from users
            where id = ?
            """;

    private final DataSource dataSource;

    public JdbcAuthorizationDataLoader(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    @Override
    public AuthorizationSnapshot load(AuthorizationRequest request, DataRequirement requirement) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            DocumentContext documentContext = loadDocumentContext(connection, request, requirement);
            User user = loadUser(connection, request.userId(), requirement);

            return new AuthorizationSnapshot(
                    request,
                    user,
                    documentContext.team(),
                    documentContext.project(),
                    documentContext.document(),
                    documentContext.teamMembership(),
                    documentContext.projectMembership(),
                    buildFacts(
                            requirement,
                            user,
                            documentContext.team(),
                            documentContext.project(),
                            documentContext.document(),
                            documentContext.teamMembership(),
                            documentContext.projectMembership()
                    )
            );
        } catch (ResourceNotFoundException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load authorization context from PostgreSQL", exception);
        }
    }

    private DocumentContext loadDocumentContext(
            Connection connection,
            AuthorizationRequest request,
            DataRequirement requirement
    ) throws Exception {
        ContextQueryPlan queryPlan = contextQueryPlan(requirement);
        try (PreparedStatement statement = connection.prepareStatement(queryPlan.sql())) {
            statement.setFetchSize(1);
            statement.setMaxRows(1);
            int parameterIndex = 1;
            if (queryPlan.includesMemberships()) {
                statement.setString(parameterIndex++, request.userId());
                statement.setString(parameterIndex++, request.userId());
            }
            statement.setString(parameterIndex, request.documentId());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new ResourceNotFoundException("Document not found: " + request.documentId());
                }

                Team team = new Team(
                        resultSet.getString("team_id"),
                        resultSet.getString("team_name"),
                        TeamPlan.fromJson(resultSet.getString("team_plan"))
                );
                Project project = new Project(
                        resultSet.getString("project_id"),
                        resultSet.getString("project_name"),
                        resultSet.getString("project_team_id"),
                        ProjectVisibility.fromJson(resultSet.getString("project_visibility"))
                );
                Document document = new Document(
                        resultSet.getString("document_id"),
                        resultSet.getString("document_title"),
                        resultSet.getString("document_project_id"),
                        resultSet.getString("document_creator_id"),
                        toInstant(resultSet.getTimestamp("document_deleted_at")),
                        resultSet.getBoolean("document_public_link_enabled")
                );
                return new DocumentContext(
                        team,
                        project,
                        document,
                        membershipFact(resultSet, "team_membership_role"),
                        membershipFact(resultSet, "project_membership_role")
                );
            }
        }
    }

    private User loadUser(Connection connection, String userId, DataRequirement requirement) throws Exception {
        boolean needsDatabaseUser = requirement.factPaths().contains("user.email") || requirement.factPaths().contains("user.name");
        if (!needsDatabaseUser) {
            return new User(userId, null, null);
        }

        try (PreparedStatement statement = connection.prepareStatement(USER_SQL)) {
            statement.setFetchSize(1);
            statement.setMaxRows(1);
            statement.setString(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return new User(userId, null, null);
                }
                return new User(
                        resultSet.getString("id"),
                        resultSet.getString("email"),
                        resultSet.getString("name")
                );
            }
        }
    }

    static ContextQueryPlan contextQueryPlan(DataRequirement requirement) {
        boolean needsMemberships = requirement.factPaths().stream()
                .anyMatch(path -> path.startsWith("teamMembership.") || path.startsWith("projectMembership."));
        return needsMemberships ? ContextQueryPlan.WITH_MEMBERSHIPS : ContextQueryPlan.BASE_CONTEXT;
    }

    private Map<String, Object> buildFacts(
            DataRequirement requirement,
            User user,
            Team team,
            Project project,
            Document document,
            MembershipFact teamMembership,
            MembershipFact projectMembership
    ) {
        LinkedHashMap<String, Object> facts = new LinkedHashMap<>();
        requirement.factPaths().stream()
                .sorted(Comparator.naturalOrder())
                .forEach(path -> putPathValue(facts, path, factValue(path, user, team, project, document, teamMembership, projectMembership)));
        return deepUnmodifiableCopy(facts);
    }

    private Object factValue(
            String path,
            User user,
            Team team,
            Project project,
            Document document,
            MembershipFact teamMembership,
            MembershipFact projectMembership
    ) {
        return switch (path) {
            case "user.id" -> user.id();
            case "user.email" -> user.email();
            case "user.name" -> user.name();
            case "team.id" -> team.id();
            case "team.name" -> team.name();
            case "team.plan" -> team.plan().jsonValue();
            case "project.id" -> project.id();
            case "project.name" -> project.name();
            case "project.teamId" -> project.teamId();
            case "project.visibility" -> project.visibility().jsonValue();
            case "document.id" -> document.id();
            case "document.title" -> document.title();
            case "document.projectId" -> document.projectId();
            case "document.creatorId" -> document.creatorId();
            case "document.deletedAt" -> document.deletedAt();
            case "document.publicLinkEnabled" -> document.publicLinkEnabled();
            case "teamMembership.exists" -> teamMembership.exists();
            case "teamMembership.role" -> roleValue(teamMembership.role());
            case "projectMembership.exists" -> projectMembership.exists();
            case "projectMembership.role" -> roleValue(projectMembership.role());
            default -> throw new IllegalArgumentException("Unsupported fact path: " + path);
        };
    }

    private String roleValue(MembershipRole role) {
        return role == null ? null : role.jsonValue();
    }

    private void putPathValue(Map<String, Object> facts, String path, Object value) {
        String[] segments = path.split("\\.");
        Map<String, Object> current = facts;
        for (int index = 0; index < segments.length - 1; index++) {
            String segment = segments[index];
            @SuppressWarnings("unchecked")
            Map<String, Object> next = (Map<String, Object>) current.computeIfAbsent(segment, ignored -> new LinkedHashMap<>());
            current = next;
        }
        current.put(segments[segments.length - 1], value);
    }

    private Map<String, Object> deepUnmodifiableCopy(Map<String, Object> source) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) nested;
                copy.put(entry.getKey(), deepUnmodifiableCopy(nestedMap));
            } else {
                copy.put(entry.getKey(), value);
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private MembershipFact membershipFact(ResultSet resultSet, String columnName) throws Exception {
        try {
            String roleValue = resultSet.getString(columnName);
            return roleValue == null
                    ? new MembershipFact(false, null)
                    : new MembershipFact(true, MembershipRole.fromJson(roleValue));
        } catch (java.sql.SQLException exception) {
            return new MembershipFact(false, null);
        }
    }

    enum ContextQueryPlan {
        BASE_CONTEXT(DOCUMENT_CONTEXT_SQL, false),
        WITH_MEMBERSHIPS(DOCUMENT_CONTEXT_WITH_MEMBERSHIPS_SQL, true);

        private final String sql;
        private final boolean includesMemberships;

        ContextQueryPlan(String sql, boolean includesMemberships) {
            this.sql = sql;
            this.includesMemberships = includesMemberships;
        }

        String sql() {
            return sql;
        }

        boolean includesMemberships() {
            return includesMemberships;
        }
    }

    private record DocumentContext(
            Team team,
            Project project,
            Document document,
            MembershipFact teamMembership,
            MembershipFact projectMembership
    ) {
    }
}
