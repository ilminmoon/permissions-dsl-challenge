package com.example.authz.http;

import com.example.authz.domain.ProjectVisibility;

public record CreateProjectRequest(
        String id,
        String name,
        String teamId,
        ProjectVisibility visibility
) {
}
