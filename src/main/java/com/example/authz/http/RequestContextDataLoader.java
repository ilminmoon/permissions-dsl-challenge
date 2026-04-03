package com.example.authz.http;

import com.example.authz.domain.MembershipRole;
import com.example.authz.domain.ProjectMembership;
import com.example.authz.domain.TeamMembership;
import com.example.authz.engine.AuthorizationRequest;
import com.example.authz.loader.AuthorizationDataLoader;
import com.example.authz.loader.AuthorizationSnapshot;
import com.example.authz.loader.DataRequirement;
import com.example.authz.loader.MembershipFact;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

final class RequestContextDataLoader implements AuthorizationDataLoader {
    private final AuthorizationRequestContext context;

    RequestContextDataLoader(AuthorizationRequestContext context) {
        this.context = context;
    }

    @Override
    public AuthorizationSnapshot load(AuthorizationRequest request, DataRequirement requirement) {
        MembershipFact teamMembership = membershipFact(context.teamMembership());
        MembershipFact projectMembership = membershipFact(context.projectMembership());

        return new AuthorizationSnapshot(
                request,
                context.user(),
                context.team(),
                context.project(),
                context.document(),
                teamMembership,
                projectMembership,
                buildFacts(requirement, teamMembership, projectMembership)
        );
    }

    private MembershipFact membershipFact(TeamMembership membership) {
        return new MembershipFact(membership != null, membership == null ? null : membership.role());
    }

    private MembershipFact membershipFact(ProjectMembership membership) {
        return new MembershipFact(membership != null, membership == null ? null : membership.role());
    }

    private Map<String, Object> buildFacts(
            DataRequirement requirement,
            MembershipFact teamMembership,
            MembershipFact projectMembership
    ) {
        LinkedHashMap<String, Object> facts = new LinkedHashMap<>();
        requirement.factPaths().stream()
                .sorted(Comparator.naturalOrder())
                .forEach(path -> putPathValue(facts, path, factValue(path, teamMembership, projectMembership)));
        return deepUnmodifiableCopy(facts);
    }

    private String roleValue(MembershipRole role) {
        return role == null ? null : role.jsonValue();
    }

    private Object factValue(String path, MembershipFact teamMembership, MembershipFact projectMembership) {
        return switch (path) {
            case "user.id" -> context.user().id();
            case "user.email" -> context.user().email();
            case "user.name" -> context.user().name();
            case "team.id" -> context.team().id();
            case "team.name" -> context.team().name();
            case "team.plan" -> context.team().plan().jsonValue();
            case "project.id" -> context.project().id();
            case "project.name" -> context.project().name();
            case "project.teamId" -> context.project().teamId();
            case "project.visibility" -> context.project().visibility().jsonValue();
            case "document.id" -> context.document().id();
            case "document.title" -> context.document().title();
            case "document.projectId" -> context.document().projectId();
            case "document.creatorId" -> context.document().creatorId();
            case "document.deletedAt" -> context.document().deletedAt();
            case "document.publicLinkEnabled" -> context.document().publicLinkEnabled();
            case "teamMembership.exists" -> teamMembership.exists();
            case "teamMembership.role" -> roleValue(teamMembership.role());
            case "projectMembership.exists" -> projectMembership.exists();
            case "projectMembership.role" -> roleValue(projectMembership.role());
            default -> throw new IllegalArgumentException("Unsupported fact path: " + path);
        };
    }

    private void putPathValue(Map<String, Object> facts, String path, Object value) {
        String[] segments = path.split("\\.");
        Map<String, Object> current = facts;
        for (int index = 0; index < segments.length - 1; index++) {
            String segment = segments[index];
            @SuppressWarnings("unchecked")
            Map<String, Object> next = (Map<String, Object>) current.computeIfAbsent(segment, ignored -> new LinkedHashMap<>());
            current = next;
        }
        current.put(segments[segments.length - 1], value);
    }

    private Map<String, Object> deepUnmodifiableCopy(Map<String, Object> source) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) nested;
                copy.put(entry.getKey(), deepUnmodifiableCopy(nestedMap));
            } else {
                copy.put(entry.getKey(), value);
            }
        }
        return Collections.unmodifiableMap(copy);
    }
}
