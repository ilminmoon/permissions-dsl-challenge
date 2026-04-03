package com.example.authz.http;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

final class JwtTestSupport {
    static final String TEST_JWT_SECRET = "test-jwt-secret";
    static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-03-31T00:00:00Z"), ZoneOffset.UTC);

    private JwtTestSupport() {
    }

    static String bearerToken(String userId) {
        String token = HmacJwtAuthenticator.issueToken(
                userId,
                TEST_JWT_SECRET,
                Instant.parse("2026-03-30T23:00:00Z"),
                Instant.parse("2026-03-31T01:00:00Z")
        );
        return "Bearer " + token;
    }
}
