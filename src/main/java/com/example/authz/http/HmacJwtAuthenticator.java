package com.example.authz.http;

import com.example.authz.engine.AuthorizationJson;
import com.fasterxml.jackson.databind.JsonNode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

final class HmacJwtAuthenticator implements TokenAuthenticator {
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final byte[] secret;
    private final Clock clock;

    HmacJwtAuthenticator(String secret, Clock clock) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret must not be blank");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public AuthenticatedPrincipal authenticate(String authorizationHeader) {
        String token = bearerToken(authorizationHeader);
        String[] segments = token.split("\\.");
        if (segments.length != 3) {
            throw new UnauthorizedException("JWT must have exactly three segments");
        }

        JsonNode header = readJson(decodeSegment(segments[0]), "JWT header");
        JsonNode payload = readJson(decodeSegment(segments[1]), "JWT payload");
        validateHeader(header);
        validateSignature(segments[0], segments[1], segments[2]);
        validateExpiry(payload);

        JsonNode subject = payload.get("sub");
        if (subject == null || subject.asText().isBlank()) {
            throw new UnauthorizedException("JWT subject (sub) must not be blank");
        }
        return new AuthenticatedPrincipal(subject.asText());
    }

    private String bearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new UnauthorizedException("Authorization header must contain a Bearer token");
        }
        String prefix = "Bearer ";
        if (!authorizationHeader.regionMatches(true, 0, prefix, 0, prefix.length())) {
            throw new UnauthorizedException("Authorization header must contain a Bearer token");
        }
        String token = authorizationHeader.substring(prefix.length()).trim();
        if (token.isBlank()) {
            throw new UnauthorizedException("Authorization header must contain a Bearer token");
        }
        return token;
    }

    private JsonNode readJson(byte[] bytes, String label) {
        try {
            return AuthorizationJson.newObjectMapper().readTree(bytes);
        } catch (Exception exception) {
            throw new UnauthorizedException(label + " must be valid JSON");
        }
    }

    private byte[] decodeSegment(String segment) {
        try {
            return URL_DECODER.decode(segment);
        } catch (IllegalArgumentException exception) {
            throw new UnauthorizedException("JWT segments must be valid base64url");
        }
    }

    private void validateHeader(JsonNode header) {
        String algorithm = header.path("alg").asText();
        if (!"HS256".equals(algorithm)) {
            throw new UnauthorizedException("JWT alg must be HS256");
        }
    }

    private void validateSignature(String headerSegment, String payloadSegment, String signatureSegment) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] expected = mac.doFinal((headerSegment + "." + payloadSegment).getBytes(StandardCharsets.UTF_8));
            byte[] actual = URL_DECODER.decode(signatureSegment);
            if (!MessageDigest.isEqual(expected, actual)) {
                throw new UnauthorizedException("JWT signature is invalid");
            }
        } catch (UnauthorizedException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to validate JWT signature", exception);
        }
    }

    private void validateExpiry(JsonNode payload) {
        JsonNode exp = payload.get("exp");
        if (exp == null || exp.isNull()) {
            return;
        }
        Instant expiresAt = Instant.ofEpochSecond(exp.asLong());
        if (!expiresAt.isAfter(clock.instant())) {
            throw new UnauthorizedException("JWT has expired");
        }
    }

    static String issueToken(String subject, String secret, Instant issuedAt, Instant expiresAt) {
        try {
            String headerSegment = URL_ENCODER.encodeToString("""
                    {"alg":"HS256","typ":"JWT"}
                    """.strip().getBytes(StandardCharsets.UTF_8));
            String payloadSegment = URL_ENCODER.encodeToString(("""
                    {"sub":"%s","iat":%d,"exp":%d}
                    """.formatted(subject, issuedAt.getEpochSecond(), expiresAt.getEpochSecond()))
                    .getBytes(StandardCharsets.UTF_8));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal((headerSegment + "." + payloadSegment).getBytes(StandardCharsets.UTF_8));
            return headerSegment + "." + payloadSegment + "." + URL_ENCODER.encodeToString(signature);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to issue JWT", exception);
        }
    }
}
