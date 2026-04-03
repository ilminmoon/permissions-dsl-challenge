package com.example.authz.domain;

public record TeamMembership(
        String userId,
        String teamId,
        MembershipRole role
) {
}
