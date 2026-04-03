package com.example.authz.domain;

public record Team(
        String id,
        String name,
        TeamPlan plan
) {
}
