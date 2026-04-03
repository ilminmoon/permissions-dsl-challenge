package com.example.authz.http;

import com.example.authz.domain.ProjectVisibility;

public record CreateProjectResponse(
        String id,
        String name,
        String teamId,
        ProjectVisibility visibility
) {
}
