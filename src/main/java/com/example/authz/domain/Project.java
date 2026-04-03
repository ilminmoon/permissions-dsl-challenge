package com.example.authz.domain;

public record Project(
        String id,
        String name,
        String teamId,
        ProjectVisibility visibility
) {
}
