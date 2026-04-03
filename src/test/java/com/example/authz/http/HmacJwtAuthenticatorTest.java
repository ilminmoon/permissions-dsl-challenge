package com.example.authz.http;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HmacJwtAuthenticatorTest {
    private final HmacJwtAuthenticator authenticator = new HmacJwtAuthenticator(
            JwtTestSupport.TEST_JWT_SECRET,
            JwtTestSupport.FIXED_CLOCK
    );

    @Test
    void authenticateAcceptsValidBearerToken() {
        AuthenticatedPrincipal principal = authenticator.authenticate(JwtTestSupport.bearerToken("u1"));

        assertEquals("u1", principal.userId());
    }

    @Test
    void authenticateRejectsMissingBearerHeader() {
        UnauthorizedException exception = assertThrows(
                UnauthorizedException.class,
                () -> authenticator.authenticate(null)
        );

        assertEquals("Authorization header must contain a Bearer token", exception.getMessage());
    }

    @Test
    void authenticateRejectsInvalidSignature() {
        String token = HmacJwtAuthenticator.issueToken(
                "u1",
                "different-secret",
                Instant.parse("2026-03-30T23:00:00Z"),
                Instant.parse("2026-03-31T01:00:00Z")
        );

        UnauthorizedException exception = assertThrows(
                UnauthorizedException.class,
                () -> authenticator.authenticate("Bearer " + token)
        );

        assertEquals("JWT signature is invalid", exception.getMessage());
    }

    @Test
    void authenticateRejectsExpiredToken() {
        String token = HmacJwtAuthenticator.issueToken(
                "u1",
                JwtTestSupport.TEST_JWT_SECRET,
                Instant.parse("2026-03-30T20:00:00Z"),
                Instant.parse("2026-03-30T21:00:00Z")
        );

        UnauthorizedException exception = assertThrows(
                UnauthorizedException.class,
                () -> authenticator.authenticate("Bearer " + token)
        );

        assertEquals("JWT has expired", exception.getMessage());
    }
}
