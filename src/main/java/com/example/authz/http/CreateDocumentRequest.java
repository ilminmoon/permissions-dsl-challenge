package com.example.authz.http;

import java.time.Instant;

public record CreateDocumentRequest(
        String id,
        String title,
        String projectId,
        CreatorInput creator,
        Instant deletedAt,
        boolean publicLinkEnabled
) {
    public record CreatorInput(
            String id,
            String email,
            String name
    ) {
    }
}
