package com.example.authz.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthorizationHttpServerAppTest {
    @Test
    void resolvePortRejectsInvalidCommandLinePort() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> AuthorizationHttpServerApp.resolvePort(new String[]{"--port", "not-a-port"}, null)
        );

        assertEquals("--port must be a valid integer port", exception.getMessage());
    }

    @Test
    void resolvePortRejectsInvalidEnvironmentPort() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> AuthorizationHttpServerApp.resolvePort(new String[0], "oops")
        );

        assertEquals("PORT must be a valid integer port", exception.getMessage());
    }

    @Test
    void resolvePortFallsBackToDefaultWhenNoOverrideIsPresent() {
        assertEquals(8080, AuthorizationHttpServerApp.resolvePort(new String[0], null));
    }
}
