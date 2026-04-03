package com.example.authz.loader;

import com.example.authz.domain.Document;
import com.example.authz.domain.Project;
import com.example.authz.domain.Team;
import com.example.authz.domain.User;
import com.example.authz.engine.AuthorizationRequest;

import java.util.Map;

public record AuthorizationSnapshot(
        AuthorizationRequest request,
        User user,
        Team team,
        Project project,
        Document document,
        MembershipFact teamMembership,
        MembershipFact projectMembership,
        Map<String, Object> facts
) {
    public AuthorizationSnapshot {
        facts = facts == null ? Map.of() : Map.copyOf(facts);
    }
}
