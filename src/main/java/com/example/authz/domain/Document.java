package com.example.authz.domain;

import java.time.Instant;

public record Document(
        String id,
        String title,
        String projectId,
        String creatorId,
        Instant deletedAt,
        boolean publicLinkEnabled
) {
}
