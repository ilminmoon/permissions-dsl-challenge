package com.example.authz.loader;

import com.example.authz.domain.Document;
import com.example.authz.domain.Project;
import com.example.authz.domain.ProjectMembership;
import com.example.authz.domain.Team;
import com.example.authz.domain.TeamMembership;
import com.example.authz.domain.User;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Objects;

public final class JdbcAuthorizationMutationService implements AuthorizationMutationService {
    private static final String USER_SQL = """
            select id, email, name
            from users
            where id = ?
            """;

    private static final String TEAM_EXISTS_SQL = """
            select 1
            from teams
            where id = ?
            """;

    private static final String PROJECT_EXISTS_SQL = """
            select 1
            from projects
            where id = ?
            """;

    private static final String DOCUMENT_EXISTS_SQL = """
            select 1
            from documents
            where id = ?
            """;

    private static final String INSERT_USER_SQL = """
            insert into users (id, email, name)
            values (?, ?, ?)
            """;

    private static final String INSERT_TEAM_SQL = """
            insert into teams (id, name, plan)
            values (?, ?, ?)
            """;

    private static final String INSERT_PROJECT_SQL = """
            insert into projects (id, name, team_id, visibility)
            values (?, ?, ?, ?)
            """;

    private static final String INSERT_TEAM_MEMBERSHIP_SQL = """
            insert into team_memberships (user_id, team_id, role)
            values (?, ?, ?)
            """;

    private static final String INSERT_PROJECT_MEMBERSHIP_SQL = """
            insert into project_memberships (user_id, project_id, role)
            values (?, ?, ?)
            """;

    private static final String INSERT_DOCUMENT_SQL = """
            insert into documents (id, title, project_id, creator_id, deleted_at, public_link_enabled)
            values (?, ?, ?, ?, ?, ?)
            """;

    private final DataSource dataSource;

    public JdbcAuthorizationMutationService(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    @Override
    public void createTeam(Team team) {
        Objects.requireNonNull(team, "team must not be null");

        withTransactionVoid(connection -> {
            if (exists(connection, TEAM_EXISTS_SQL, team.id())) {
                throw new ConflictException("Team already exists: " + team.id());
            }

            insertTeam(connection, team);
        });
    }

    @Override
    public void createProject(Project project) {
        Objects.requireNonNull(project, "project must not be null");

        withTransactionVoid(connection -> {
            ensureExists(connection, TEAM_EXISTS_SQL, project.teamId(), "Team not found: ");

            if (exists(connection, PROJECT_EXISTS_SQL, project.id())) {
                throw new ConflictException("Project already exists: " + project.id());
            }

            insertProject(connection, project);
        });
    }

    @Override
    public void createUser(User user, List<TeamMembership> teamMemberships, List<ProjectMembership> projectMemberships) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(teamMemberships, "teamMemberships must not be null");
        Objects.requireNonNull(projectMemberships, "projectMemberships must not be null");

        withTransactionVoid(connection -> {
            if (loadUser(connection, user.id()) != null) {
                throw new ConflictException("User already exists: " + user.id());
            }

            for (TeamMembership teamMembership : teamMemberships) {
                ensureExists(connection, TEAM_EXISTS_SQL, teamMembership.teamId(), "Team not found: ");
            }
            for (ProjectMembership projectMembership : projectMemberships) {
                ensureExists(connection, PROJECT_EXISTS_SQL, projectMembership.projectId(), "Project not found: ");
            }

            insertUser(connection, user);
            insertTeamMemberships(connection, teamMemberships);
            insertProjectMemberships(connection, projectMemberships);
        });
    }

    @Override
    public DocumentCreationResult createDocument(Document document, User creator) {
        Objects.requireNonNull(document, "document must not be null");
        Objects.requireNonNull(creator, "creator must not be null");

        return withTransaction(connection -> {
            ensureExists(connection, PROJECT_EXISTS_SQL, document.projectId(), "Project not found: ");

            if (exists(connection, DOCUMENT_EXISTS_SQL, document.id())) {
                throw new ConflictException("Document already exists: " + document.id());
            }

            boolean creatorCreated = ensureCreator(connection, creator);
            insertDocument(connection, document);
            return new DocumentCreationResult(creatorCreated);
        });
    }

    private boolean ensureCreator(Connection connection, User creator) throws SQLException {
        User existingCreator = loadUser(connection, creator.id());
        if (existingCreator == null) {
            insertUser(connection, creator);
            return true;
        }

        if (!Objects.equals(existingCreator.email(), creator.email())
                || !Objects.equals(existingCreator.name(), creator.name())) {
            throw new ConflictException("Creator already exists with different profile: " + creator.id());
        }

        return false;
    }

    private User loadUser(Connection connection, String userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(USER_SQL)) {
            statement.setString(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new User(
                        resultSet.getString("id"),
                        resultSet.getString("email"),
                        resultSet.getString("name")
                );
            }
        }
    }

    private boolean exists(Connection connection, String sql, String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void ensureExists(Connection connection, String sql, String id, String messagePrefix) throws SQLException {
        if (!exists(connection, sql, id)) {
            throw new ResourceNotFoundException(messagePrefix + id);
        }
    }

    private void insertUser(Connection connection, User user) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_USER_SQL)) {
            statement.setString(1, user.id());
            statement.setString(2, user.email());
            statement.setString(3, user.name());
            statement.executeUpdate();
        }
    }

    private void insertTeam(Connection connection, Team team) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_TEAM_SQL)) {
            statement.setString(1, team.id());
            statement.setString(2, team.name());
            statement.setString(3, team.plan().jsonValue());
            statement.executeUpdate();
        }
    }

    private void insertProject(Connection connection, Project project) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_PROJECT_SQL)) {
            statement.setString(1, project.id());
            statement.setString(2, project.name());
            statement.setString(3, project.teamId());
            statement.setString(4, project.visibility().jsonValue());
            statement.executeUpdate();
        }
    }

    private void insertTeamMemberships(Connection connection, List<TeamMembership> teamMemberships) throws SQLException {
        if (teamMemberships.isEmpty()) {
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement(INSERT_TEAM_MEMBERSHIP_SQL)) {
            for (TeamMembership teamMembership : teamMemberships) {
                statement.setString(1, teamMembership.userId());
                statement.setString(2, teamMembership.teamId());
                statement.setString(3, teamMembership.role().jsonValue());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertProjectMemberships(Connection connection, List<ProjectMembership> projectMemberships) throws SQLException {
        if (projectMemberships.isEmpty()) {
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement(INSERT_PROJECT_MEMBERSHIP_SQL)) {
            for (ProjectMembership projectMembership : projectMemberships) {
                statement.setString(1, projectMembership.userId());
                statement.setString(2, projectMembership.projectId());
                statement.setString(3, projectMembership.role().jsonValue());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertDocument(Connection connection, Document document) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_DOCUMENT_SQL)) {
            statement.setString(1, document.id());
            statement.setString(2, document.title());
            statement.setString(3, document.projectId());
            statement.setString(4, document.creatorId());
            if (document.deletedAt() == null) {
                statement.setNull(5, Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                statement.setTimestamp(5, Timestamp.from(document.deletedAt()));
            }
            statement.setBoolean(6, document.publicLinkEnabled());
            statement.executeUpdate();
        }
    }

    private <T> T withTransaction(SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (RuntimeException exception) {
                rollbackQuietly(connection);
                throw exception;
            } catch (SQLException exception) {
                rollbackQuietly(connection);
                throw mapSqlFailure(exception);
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (ConflictException | ResourceNotFoundException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to persist authorization data to PostgreSQL", exception);
        }
    }

    private void withTransactionVoid(SqlVoidWork work) {
        withTransaction(connection -> {
            work.execute(connection);
            return null;
        });
    }

    private RuntimeException mapSqlFailure(SQLException exception) {
        if ("23505".equals(exception.getSQLState())) {
            return new ConflictException("Resource already exists");
        }
        if ("23503".equals(exception.getSQLState())) {
            return new ResourceNotFoundException("Referenced resource does not exist");
        }
        if ("23514".equals(exception.getSQLState())) {
            return new IllegalArgumentException("Request violates database constraints");
        }
        return new IllegalStateException("Failed to persist authorization data to PostgreSQL", exception);
    }

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Best effort rollback before surfacing the original failure.
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T execute(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    private interface SqlVoidWork {
        void execute(Connection connection) throws SQLException;
    }
}
