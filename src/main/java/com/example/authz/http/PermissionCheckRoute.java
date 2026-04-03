package com.example.authz.http;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

record PermissionCheckRoute(String documentId, String permissionToken) {
    static Optional<PermissionCheckRoute> tryParse(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return Optional.empty();
        }

        String[] segments = Arrays.stream(rawPath.split("/"))
                .filter(segment -> !segment.isBlank())
                .toArray(String[]::new);
        if (segments.length != 5) {
            return Optional.empty();
        }
        if (!"v1".equals(segments[0]) || !"documents".equals(segments[1]) || !"permissions".equals(segments[3])) {
            return Optional.empty();
        }

        String documentId = decode(segments[2]);
        String permissionToken = decode(segments[4]);
        if (documentId.isBlank() || permissionToken.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new PermissionCheckRoute(documentId, permissionToken));
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
