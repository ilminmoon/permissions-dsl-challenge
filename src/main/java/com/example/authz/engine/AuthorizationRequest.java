package com.example.authz.engine;

import com.example.authz.policy.Permission;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

public record AuthorizationRequest(
        String userId,
        String documentId,
        Permission permission,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant requestedAt
) {
}
