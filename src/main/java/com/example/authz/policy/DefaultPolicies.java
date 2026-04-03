package com.example.authz.policy;

import com.example.authz.dsl.AndExpression;
import com.example.authz.dsl.ComparisonExpression;
import com.example.authz.dsl.ComparisonOperator;
import com.example.authz.dsl.Expression;
import com.example.authz.dsl.FieldRefOperand;
import com.example.authz.dsl.LiteralOperand;
import com.example.authz.dsl.OrExpression;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

public final class DefaultPolicies {
    private static final String ROLE_ADMIN = "admin";
    private static final String ROLE_EDITOR = "editor";
    private static final String PLAN_FREE = "free";
    private static final String VISIBILITY_PRIVATE = "private";

    private static final Set<Permission> ALL_PERMISSIONS = Set.of(
            Permission.CAN_VIEW,
            Permission.CAN_EDIT,
            Permission.CAN_DELETE,
            Permission.CAN_SHARE
    );
    private static final Set<Permission> MUTATING_PERMISSIONS = Set.of(
            Permission.CAN_EDIT,
            Permission.CAN_DELETE,
            Permission.CAN_SHARE
    );
    private static final Set<Permission> TEAM_ADMIN_PERMISSIONS = Set.of(
            Permission.CAN_VIEW,
            Permission.CAN_EDIT,
            Permission.CAN_SHARE
    );

    private static final List<PolicyDefinition> MANDATORY_POLICIES = List.of(
            policy(
                    "deny_deleted_document_mutations",
                    "Deleted documents cannot be edited, deleted, or shared.",
                    PolicyEffect.DENY,
                    MUTATING_PERMISSIONS,
                    ne("document.deletedAt", null),
                    factPaths("document.deletedAt")
            ),
            policy(
                    "allow_document_creator_all",
                    "The document creator has all permissions.",
                    PolicyEffect.ALLOW,
                    ALL_PERMISSIONS,
                    eqRef("document.creatorId", "user.id"),
                    factPaths("document.creatorId", "user.id")
            ),
            policy(
                    "allow_project_editor_or_admin_edit",
                    "Project editors and admins may edit documents in that project.",
                    PolicyEffect.ALLOW,
                    Set.of(Permission.CAN_EDIT),
                    projectRoleIsEditorOrAdmin(),
                    factPaths("projectMembership.role")
            ),
            policy(
                    "allow_team_admin_view_edit_share",
                    "Team admins may view, edit, and share all documents in their team.",
                    PolicyEffect.ALLOW,
                    TEAM_ADMIN_PERMISSIONS,
                    teamRoleIsAdmin(),
                    factPaths("teamMembership.role")
            ),
            policy(
                    "deny_private_project_non_member_access",
                    "Private project documents are denied to non-project-members who are not team admins.",
                    PolicyEffect.DENY,
                    ALL_PERMISSIONS,
                    privateProjectAccessDeniedForNonMember(),
                    factPaths(
                            "project.visibility",
                            "projectMembership.exists",
                            "teamMembership.exists",
                            "teamMembership.role",
                            "document.publicLinkEnabled",
                            "request.permission"
                    )
            ),
            policy(
                    "deny_free_plan_share",
                    "Free-plan teams cannot change document sharing settings.",
                    PolicyEffect.DENY,
                    Set.of(Permission.CAN_SHARE),
                    eq("team.plan", PLAN_FREE),
                    factPaths("team.plan")
            ),
            policy(
                    "allow_public_link_view",
                    "Anyone may view a document when its public link is enabled.",
                    PolicyEffect.ALLOW,
                    Set.of(Permission.CAN_VIEW),
                    eq("document.publicLinkEnabled", true),
                    factPaths("document.publicLinkEnabled")
            )
    );

    private static final List<PolicyDefinition> SUPPLEMENTAL_POLICIES = List.of(
            policy(
                    "allow_project_member_view",
                    "Project members may view documents in their project unless another deny policy matches.",
                    PolicyEffect.ALLOW,
                    Set.of(Permission.CAN_VIEW),
                    eq("projectMembership.exists", true),
                    factPaths("projectMembership.exists")
            ),
            policy(
                    "allow_project_editor_or_admin_share",
                    "Project editors and admins may change document sharing settings unless another deny policy matches.",
                    PolicyEffect.ALLOW,
                    Set.of(Permission.CAN_SHARE),
                    projectRoleIsEditorOrAdmin(),
                    factPaths("projectMembership.role")
            )
    );

    private static final List<PolicyDefinition> ALL_POLICIES = buildAllPolicies();

    private DefaultPolicies() {
    }

    public static List<PolicyDefinition> mandatoryPolicies() {
        return MANDATORY_POLICIES;
    }

    public static List<PolicyDefinition> supplementalPolicies() {
        return SUPPLEMENTAL_POLICIES;
    }

    public static List<PolicyDefinition> allPolicies() {
        return ALL_POLICIES;
    }

    private static List<PolicyDefinition> buildAllPolicies() {
        return Stream.concat(MANDATORY_POLICIES.stream(), SUPPLEMENTAL_POLICIES.stream()).toList();
    }

    private static Expression projectRoleIsEditorOrAdmin() {
        return or(
                eq("projectMembership.role", ROLE_EDITOR),
                eq("projectMembership.role", ROLE_ADMIN)
        );
    }

    private static Expression teamRoleIsAdmin() {
        return eq("teamMembership.role", ROLE_ADMIN);
    }

    private static Expression privateProjectAccessDeniedForNonMember() {
        return and(
                eq("project.visibility", VISIBILITY_PRIVATE),
                eq("projectMembership.exists", false),
                userIsNotTeamAdmin(),
                publicLinkViewCarveOutDoesNotApply()
        );
    }

    private static Expression userIsNotTeamAdmin() {
        return or(
                eq("teamMembership.exists", false),
                ne("teamMembership.role", ROLE_ADMIN)
        );
    }

    private static Expression publicLinkViewCarveOutDoesNotApply() {
        return or(
                ne("request.permission", Permission.CAN_VIEW.dslValue()),
                eq("document.publicLinkEnabled", false)
        );
    }

    private static Set<String> factPaths(String... paths) {
        return new LinkedHashSet<>(List.of(paths));
    }

    private static PolicyDefinition policy(
            String id,
            String description,
            PolicyEffect effect,
            Set<Permission> permissions,
            Expression condition,
            Set<String> requiredFacts
    ) {
        return new PolicyDefinition(id, description, effect, permissions, condition, requiredFacts);
    }

    private static ComparisonExpression eq(String field, Object value) {
        return new ComparisonExpression(field, ComparisonOperator.EQ, new LiteralOperand(value));
    }

    private static ComparisonExpression ne(String field, Object value) {
        return new ComparisonExpression(field, ComparisonOperator.NE, new LiteralOperand(value));
    }

    private static ComparisonExpression eqRef(String field, String ref) {
        return new ComparisonExpression(field, ComparisonOperator.EQ, new FieldRefOperand(ref));
    }

    private static AndExpression and(Expression... expressions) {
        return new AndExpression(List.of(expressions));
    }

    private static OrExpression or(Expression... expressions) {
        return new OrExpression(List.of(expressions));
    }
}
