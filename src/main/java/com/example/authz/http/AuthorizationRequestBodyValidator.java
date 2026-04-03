package com.example.authz.http;

import com.example.authz.domain.ProjectMembership;
import com.example.authz.domain.TeamMembership;

final class AuthorizationRequestBodyValidator {
    void validate(AuthorizationRequestBody payload) {
        requireNonNull(payload.user(), "user");
        requireNonNull(payload.team(), "team");
        requireNonNull(payload.project(), "project");
        requireNonNull(payload.document(), "document");

        requireNonBlank(payload.user().id(), "user.id");
        requireNonBlank(payload.team().id(), "team.id");
        requireNonNull(payload.team().plan(), "team.plan");
        requireNonBlank(payload.project().id(), "project.id");
        requireNonBlank(payload.project().teamId(), "project.teamId");
        requireNonNull(payload.project().visibility(), "project.visibility");
        requireNonBlank(payload.document().id(), "document.id");
        requireNonBlank(payload.document().projectId(), "document.projectId");
        requireNonBlank(payload.document().creatorId(), "document.creatorId");
        requireNonNull(payload.permission(), "permission");
        requireNonNull(payload.requestedAt(), "requestedAt");

        requireEquals(payload.project().teamId(), payload.team().id(), "project.teamId", "team.id");
        requireEquals(payload.document().projectId(), payload.project().id(), "document.projectId", "project.id");
        validateTeamMembership(payload.teamMembership(), payload.user().id(), payload.team().id());
        validateProjectMembership(payload.projectMembership(), payload.user().id(), payload.project().id());
    }

    private void requireNonBlank(String value, String path) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(path + " must not be blank");
        }
    }

    private void requireNonNull(Object value, String path) {
        if (value == null) {
            throw new IllegalArgumentException(path + " must not be null");
        }
    }

    private void requireEquals(String left, String right, String leftPath, String rightPath) {
        if (!left.equals(right)) {
            throw new IllegalArgumentException(leftPath + " must match " + rightPath);
        }
    }

    private void validateTeamMembership(TeamMembership membership, String userId, String teamId) {
        if (membership == null) {
            return;
        }

        requireNonBlank(membership.userId(), "teamMembership.userId");
        requireNonBlank(membership.teamId(), "teamMembership.teamId");
        requireNonNull(membership.role(), "teamMembership.role");
        requireEquals(membership.userId(), userId, "teamMembership.userId", "user.id");
        requireEquals(membership.teamId(), teamId, "teamMembership.teamId", "team.id");
    }

    private void validateProjectMembership(ProjectMembership membership, String userId, String projectId) {
        if (membership == null) {
            return;
        }

        requireNonBlank(membership.userId(), "projectMembership.userId");
        requireNonBlank(membership.projectId(), "projectMembership.projectId");
        requireNonNull(membership.role(), "projectMembership.role");
        requireEquals(membership.userId(), userId, "projectMembership.userId", "user.id");
        requireEquals(membership.projectId(), projectId, "projectMembership.projectId", "project.id");
    }
}
