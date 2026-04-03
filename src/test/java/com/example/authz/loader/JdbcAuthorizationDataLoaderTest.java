package com.example.authz.loader;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JdbcAuthorizationDataLoaderTest {
    @Test
    void usesBaseContextQueryWhenMembershipFactsAreNotRequired() {
        DataRequirement requirement = new DataRequirement(Set.of("document.creatorId", "team.plan"));

        assertEquals(
                JdbcAuthorizationDataLoader.ContextQueryPlan.BASE_CONTEXT,
                JdbcAuthorizationDataLoader.contextQueryPlan(requirement)
        );
    }

    @Test
    void usesMembershipJoinQueryWhenMembershipFactsAreRequired() {
        DataRequirement requirement = new DataRequirement(Set.of("projectMembership.exists", "teamMembership.role"));

        assertEquals(
                JdbcAuthorizationDataLoader.ContextQueryPlan.WITH_MEMBERSHIPS,
                JdbcAuthorizationDataLoader.contextQueryPlan(requirement)
        );
    }
}
