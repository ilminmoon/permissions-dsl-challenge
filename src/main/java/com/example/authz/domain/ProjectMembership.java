package com.example.authz.domain;

public record ProjectMembership(
        String userId,
        String projectId,
        MembershipRole role
) {
}
