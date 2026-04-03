package com.example.authz.http;

import com.example.authz.domain.TeamPlan;

public record CreateTeamRequest(
        String id,
        String name,
        TeamPlan plan
) {
}
