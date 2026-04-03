package com.example.authz.http;

import com.example.authz.domain.Document;
import com.example.authz.domain.Project;
import com.example.authz.domain.ProjectMembership;
import com.example.authz.domain.Team;
import com.example.authz.domain.TeamMembership;
import com.example.authz.domain.User;
import com.example.authz.policy.Permission;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.Objects;

public record AuthorizationRequestBody(
        User user,
        Team team,
        Project project,
        Document document,
        TeamMembership teamMembership,
        ProjectMembership projectMembership,
        Permission permission,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant requestedAt
) {
    public AuthorizationRequestBody {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(team, "team must not be null");
        Objects.requireNonNull(project, "project must not be null");
        Objects.requireNonNull(document, "document must not be null");
        Objects.requireNonNull(permission, "permission must not be null");
        Objects.requireNonNull(requestedAt, "requestedAt must not be null");
    }
}
