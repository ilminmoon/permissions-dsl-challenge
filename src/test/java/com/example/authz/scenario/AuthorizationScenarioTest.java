package com.example.authz.scenario;

import com.example.authz.explain.AuthorizationDecision;
import com.example.authz.policy.Permission;
import com.example.authz.support.AuthorizationTestSupport;
import com.example.authz.support.AuthorizationTestSupport.ScenarioData;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizationScenarioTest {
    @Test
    void scenario1GeneralProjectMember() {
        ScenarioData scenario = new ScenarioData(
                "u1",
                "d1",
                "u2",
                com.example.authz.domain.TeamPlan.PRO,
                com.example.authz.domain.ProjectVisibility.PRIVATE,
                null,
                false,
                com.example.authz.domain.MembershipRole.VIEWER,
                com.example.authz.domain.MembershipRole.EDITOR
        );

        assertDecision(scenario, Permission.CAN_VIEW, true, "allow_project_member_view");
        assertDecision(scenario, Permission.CAN_EDIT, true, "allow_project_editor_or_admin_edit");
        assertDecision(scenario, Permission.CAN_DELETE, false, null);
        assertDecision(scenario, Permission.CAN_SHARE, true, "allow_project_editor_or_admin_share");
    }

    @Test
    void scenario2DeletedDocument() {
        ScenarioData scenario = new ScenarioData(
                "u1",
                "d1",
                "u1",
                com.example.authz.domain.TeamPlan.PRO,
                com.example.authz.domain.ProjectVisibility.PRIVATE,
                Instant.parse("2026-03-31T09:00:00Z"),
                false,
                com.example.authz.domain.MembershipRole.ADMIN,
                com.example.authz.domain.MembershipRole.ADMIN
        );

        assertDecision(scenario, Permission.CAN_VIEW, true, "allow_document_creator_all");
        assertDecision(scenario, Permission.CAN_EDIT, false, "deny_deleted_document_mutations");
        assertDecision(scenario, Permission.CAN_DELETE, false, "deny_deleted_document_mutations");
        assertDecision(scenario, Permission.CAN_SHARE, false, "deny_deleted_document_mutations");
    }

    @Test
    void scenario3FreePlanRestrictions() {
        ScenarioData scenario = new ScenarioData(
                "u1",
                "d1",
                "u2",
                com.example.authz.domain.TeamPlan.FREE,
                com.example.authz.domain.ProjectVisibility.PUBLIC,
                null,
                false,
                com.example.authz.domain.MembershipRole.VIEWER,
                com.example.authz.domain.MembershipRole.ADMIN
        );

        assertDecision(scenario, Permission.CAN_VIEW, true, "allow_project_member_view");
        assertDecision(scenario, Permission.CAN_EDIT, true, "allow_project_editor_or_admin_edit");
        assertDecision(scenario, Permission.CAN_DELETE, false, null);
        assertDecision(scenario, Permission.CAN_SHARE, false, "deny_free_plan_share");
    }

    @Test
    void scenario4TeamAdminCanAccessPrivateProjectDocuments() {
        ScenarioData scenario = new ScenarioData(
                "u1",
                "d1",
                "u2",
                com.example.authz.domain.TeamPlan.PRO,
                com.example.authz.domain.ProjectVisibility.PRIVATE,
                null,
                false,
                com.example.authz.domain.MembershipRole.ADMIN,
                null
        );

        assertDecision(scenario, Permission.CAN_VIEW, true, "allow_team_admin_view_edit_share");
        assertDecision(scenario, Permission.CAN_EDIT, true, "allow_team_admin_view_edit_share");
        assertDecision(scenario, Permission.CAN_DELETE, false, null);
        assertDecision(scenario, Permission.CAN_SHARE, true, "allow_team_admin_view_edit_share");
    }

    @Test
    void scenario5NonMemberTeamEditorIsDeniedForPrivateProjectDocuments() {
        ScenarioData scenario = new ScenarioData(
                "u1",
                "d1",
                "u2",
                com.example.authz.domain.TeamPlan.PRO,
                com.example.authz.domain.ProjectVisibility.PRIVATE,
                null,
                false,
                com.example.authz.domain.MembershipRole.EDITOR,
                null
        );

        assertDecision(scenario, Permission.CAN_VIEW, false, "deny_private_project_non_member_access");
        assertDecision(scenario, Permission.CAN_EDIT, false, "deny_private_project_non_member_access");
        assertDecision(scenario, Permission.CAN_DELETE, false, "deny_private_project_non_member_access");
        assertDecision(scenario, Permission.CAN_SHARE, false, "deny_private_project_non_member_access");
    }

    @Test
    void scenario6PublicLinkAllowsViewOnly() {
        ScenarioData scenario = new ScenarioData(
                "u1",
                "d1",
                "u2",
                com.example.authz.domain.TeamPlan.PRO,
                com.example.authz.domain.ProjectVisibility.PRIVATE,
                null,
                true,
                null,
                null
        );

        assertDecision(scenario, Permission.CAN_VIEW, true, "allow_public_link_view");
        assertDecision(scenario, Permission.CAN_EDIT, false, "deny_private_project_non_member_access");
        assertDecision(scenario, Permission.CAN_DELETE, false, "deny_private_project_non_member_access");
        assertDecision(scenario, Permission.CAN_SHARE, false, "deny_private_project_non_member_access");
    }

    @Test
    void publicProjectGuestWithoutPublicLinkFallsBackToDefaultDenyForView() {
        ScenarioData scenario = new ScenarioData(
                "u1",
                "d1",
                "u2",
                com.example.authz.domain.TeamPlan.PRO,
                com.example.authz.domain.ProjectVisibility.PUBLIC,
                null,
                false,
                null,
                null
        );

        assertDecision(scenario, Permission.CAN_VIEW, false, null);
    }

    private void assertDecision(
            ScenarioData scenario,
            Permission permission,
            boolean expectedAllowed,
            String expectedPolicyId
    ) {
        AuthorizationDecision decision = AuthorizationTestSupport.newEngine(AuthorizationTestSupport.loader(scenario))
                .authorize(AuthorizationTestSupport.request(scenario.userId(), scenario.documentId(), permission));

        assertEquals(expectedAllowed, decision.allowed());
        if (expectedPolicyId == null) {
            assertNull(decision.decisivePolicyId());
            assertTrue(decision.finalReason().contains("Denied by default"));
        } else {
            assertEquals(expectedPolicyId, decision.decisivePolicyId());
            assertTrue(decision.finalReason().contains(expectedPolicyId));
        }
        assertEquals(permission, decision.permission());
    }
}
