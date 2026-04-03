package com.example.authz.http;

import java.time.Instant;

public record CreateDocumentResponse(
        String id,
        String title,
        String projectId,
        CreateDocumentRequest.CreatorInput creator,
        Instant deletedAt,
        boolean publicLinkEnabled,
        boolean creatorCreated
) {
}
