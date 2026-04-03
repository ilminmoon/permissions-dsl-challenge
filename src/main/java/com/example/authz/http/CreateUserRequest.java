package com.example.authz.http;

import com.example.authz.domain.MembershipRole;

import java.util.List;

public record CreateUserRequest(
        String id,
        String email,
        String name,
        List<TeamMembershipInput> teamMemberships,
        List<ProjectMembershipInput> projectMemberships
) {
    public CreateUserRequest {
        teamMemberships = teamMemberships == null ? List.of() : List.copyOf(teamMemberships);
        projectMemberships = projectMemberships == null ? List.of() : List.copyOf(projectMemberships);
    }

    public record TeamMembershipInput(
            String teamId,
            MembershipRole role
    ) {
    }

    public record ProjectMembershipInput(
            String projectId,
            MembershipRole role
    ) {
    }
}
