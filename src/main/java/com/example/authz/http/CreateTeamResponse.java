package com.example.authz.http;

import com.example.authz.domain.TeamPlan;

public record CreateTeamResponse(
        String id,
        String name,
        TeamPlan plan
) {
}
