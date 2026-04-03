package com.example.authz.http;

import com.example.authz.domain.Document;
import com.example.authz.domain.Project;
import com.example.authz.domain.ProjectMembership;
import com.example.authz.domain.Team;
import com.example.authz.domain.TeamMembership;
import com.example.authz.domain.User;

import java.util.Objects;

record AuthorizationRequestContext(
        User user,
        Team team,
        Project project,
        Document document,
        TeamMembership teamMembership,
        ProjectMembership projectMembership
) {
    AuthorizationRequestContext {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(team, "team must not be null");
        Objects.requireNonNull(project, "project must not be null");
        Objects.requireNonNull(document, "document must not be null");
    }
}
