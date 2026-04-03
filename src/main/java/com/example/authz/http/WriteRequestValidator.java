package com.example.authz.http;

import java.util.List;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

final class WriteRequestValidator {
    private WriteRequestValidator() {
    }

    static void validate(CreateTeamRequest request) {
        Objects.requireNonNull(request, "request body must not be null");
        requireNonBlank(request.id(), "id");
        requireNonBlank(request.name(), "name");
        if (request.plan() == null) {
            throw new IllegalArgumentException("plan must not be null");
        }
    }

    static void validate(CreateProjectRequest request) {
        Objects.requireNonNull(request, "request body must not be null");
        requireNonBlank(request.id(), "id");
        requireNonBlank(request.name(), "name");
        requireNonBlank(request.teamId(), "teamId");
        if (request.visibility() == null) {
            throw new IllegalArgumentException("visibility must not be null");
        }
    }

    static void validate(CreateUserRequest request) {
        Objects.requireNonNull(request, "request body must not be null");
        requireNonBlank(request.id(), "id");
        requireNonBlank(request.email(), "email");
        requireNonBlank(request.name(), "name");
        validateMemberships(
                request.teamMemberships(),
                "teamMemberships",
                CreateUserRequest.TeamMembershipInput::teamId,
                teamMembership -> teamMembership.role(),
                "teamId"
        );
        validateMemberships(
                request.projectMemberships(),
                "projectMemberships",
                CreateUserRequest.ProjectMembershipInput::projectId,
                projectMembership -> projectMembership.role(),
                "projectId"
        );
    }

    static void validate(CreateDocumentRequest request) {
        Objects.requireNonNull(request, "request body must not be null");
        requireNonBlank(request.id(), "id");
        requireNonBlank(request.title(), "title");
        requireNonBlank(request.projectId(), "projectId");
        if (request.creator() == null) {
            throw new IllegalArgumentException("creator must not be null");
        }
        requireNonBlank(request.creator().id(), "creator.id");
        requireNonBlank(request.creator().email(), "creator.email");
        requireNonBlank(request.creator().name(), "creator.name");
    }

    private static <T> void validateMemberships(
            List<T> entries,
            String collectionName,
            Function<T, String> idExtractor,
            Function<T, Object> roleExtractor,
            String idFieldName
    ) {
        Set<String> seenIds = new HashSet<>();
        for (T entry : entries) {
            if (entry == null) {
                throw new IllegalArgumentException(collectionName + " must not contain null entries");
            }

            String targetId = idExtractor.apply(entry);
            requireNonBlank(targetId, collectionName + "." + idFieldName);
            if (roleExtractor.apply(entry) == null) {
                throw new IllegalArgumentException(collectionName + ".role must not be null");
            }
            if (!seenIds.add(targetId)) {
                throw new IllegalArgumentException(collectionName + " must not contain duplicate " + idFieldName + " values");
            }
        }
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
