package com.example.authz.support;

import com.example.authz.domain.Document;
import com.example.authz.domain.MembershipRole;
import com.example.authz.domain.Project;
import com.example.authz.domain.ProjectVisibility;
import com.example.authz.domain.Team;
import com.example.authz.domain.TeamPlan;
import com.example.authz.domain.User;
import com.example.authz.engine.AuthorizationRequest;
import com.example.authz.engine.ExpressionEvaluator;
import com.example.authz.engine.PolicyEngine;
import com.example.authz.loader.AuthorizationDataLoader;
import com.example.authz.loader.AuthorizationSnapshot;
import com.example.authz.loader.DataRequirement;
import com.example.authz.loader.MembershipFact;
import com.example.authz.policy.DefaultPolicies;
import com.example.authz.policy.Permission;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public final class AuthorizationTestSupport {
    public static final Instant FIXED_TIME = Instant.parse("2026-03-31T00:00:00Z");

    private AuthorizationTestSupport() {
    }

    public static PolicyEngine newEngine(AuthorizationDataLoader loader) {
        return new PolicyEngine(DefaultPolicies.allPolicies(), loader, new ExpressionEvaluator());
    }

    public static AuthorizationRequest request(String userId, String documentId, Permission permission) {
        return new AuthorizationRequest(userId, documentId, permission, FIXED_TIME);
    }

    public static FakeAuthorizationDataLoader loader(ScenarioData scenario) {
        return new FakeAuthorizationDataLoader(request -> snapshot(request, scenario));
    }

    public static FakeAuthorizationDataLoader loader(Function<AuthorizationRequest, AuthorizationSnapshot> snapshotFactory) {
        return new FakeAuthorizationDataLoader(snapshotFactory);
    }

    public static AuthorizationSnapshot snapshot(AuthorizationRequest request, ScenarioData scenario) {
        User user = new User(scenario.userId(), scenario.userId() + "@example.com", "Test User");
        Team team = new Team("t1", "Team", scenario.teamPlan());
        Project project = new Project("p1", "Project", team.id(), scenario.projectVisibility());
        Document document = new Document(
                scenario.documentId(),
                "Document",
                project.id(),
                scenario.creatorId(),
                scenario.deletedAt(),
                scenario.publicLinkEnabled()
        );
        MembershipFact teamMembership = membership(scenario.teamRole());
        MembershipFact projectMembership = membership(scenario.projectRole());

        return new AuthorizationSnapshot(
                request,
                user,
                team,
                project,
                document,
                teamMembership,
                projectMembership,
                facts(scenario)
        );
    }

    private static MembershipFact membership(MembershipRole role) {
        return new MembershipFact(role != null, role);
    }

    private static Map<String, Object> facts(ScenarioData scenario) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("user", mapOf("id", scenario.userId()));
        facts.put("team", mapOf("plan", dslValue(scenario.teamPlan())));
        facts.put("project", mapOf("visibility", dslValue(scenario.projectVisibility())));
        facts.put(
                "document",
                mapOf(
                        "creatorId", scenario.creatorId(),
                        "deletedAt", scenario.deletedAt(),
                        "publicLinkEnabled", scenario.publicLinkEnabled()
                )
        );
        facts.put(
                "teamMembership",
                mapOf(
                        "exists", scenario.teamRole() != null,
                        "role", dslValue(scenario.teamRole())
                )
        );
        facts.put(
                "projectMembership",
                mapOf(
                        "exists", scenario.projectRole() != null,
                        "role", dslValue(scenario.projectRole())
                )
        );
        return Collections.unmodifiableMap(facts);
    }

    private static Map<String, Object> mapOf(Object... entries) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            map.put((String) entries[index], entries[index + 1]);
        }
        return Collections.unmodifiableMap(map);
    }

    private static String dslValue(Enum<?> value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT);
    }

    public record ScenarioData(
            String userId,
            String documentId,
            String creatorId,
            TeamPlan teamPlan,
            ProjectVisibility projectVisibility,
            Instant deletedAt,
            boolean publicLinkEnabled,
            MembershipRole teamRole,
            MembershipRole projectRole
    ) {
        public ScenarioData {
            Objects.requireNonNull(userId, "userId must not be null");
            Objects.requireNonNull(documentId, "documentId must not be null");
            Objects.requireNonNull(creatorId, "creatorId must not be null");
            Objects.requireNonNull(teamPlan, "teamPlan must not be null");
            Objects.requireNonNull(projectVisibility, "projectVisibility must not be null");
        }
    }

    public static final class FakeAuthorizationDataLoader implements AuthorizationDataLoader {
        private final Function<AuthorizationRequest, AuthorizationSnapshot> snapshotFactory;
        private AuthorizationRequest lastRequest;
        private DataRequirement lastRequirement;
        private int loadCount;

        private FakeAuthorizationDataLoader(Function<AuthorizationRequest, AuthorizationSnapshot> snapshotFactory) {
            this.snapshotFactory = snapshotFactory;
        }

        @Override
        public AuthorizationSnapshot load(AuthorizationRequest request, DataRequirement requirement) {
            this.lastRequest = request;
            this.lastRequirement = requirement;
            this.loadCount++;
            return snapshotFactory.apply(request);
        }

        public AuthorizationRequest lastRequest() {
            return lastRequest;
        }

        public DataRequirement lastRequirement() {
            return lastRequirement;
        }

        public int loadCount() {
            return loadCount;
        }
    }
}
