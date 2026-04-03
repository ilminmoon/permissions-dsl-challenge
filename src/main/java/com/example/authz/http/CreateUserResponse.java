package com.example.authz.http;

import java.util.List;

public record CreateUserResponse(
        String id,
        String email,
        String name,
        List<CreateUserRequest.TeamMembershipInput> teamMemberships,
        List<CreateUserRequest.ProjectMembershipInput> projectMemberships
) {
}
