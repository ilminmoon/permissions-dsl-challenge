package com.example.authz.loader;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresDataSourceConfigTest {
    @Test
    void readsDefaultsForOperationalPostgresSettings() {
        PostgresDataSourceConfig config = PostgresDataSourceConfig.fromEnv(Map.of());

        assertEquals("127.0.0.1", config.host());
        assertEquals(5432, config.port());
        assertEquals("authz-policy-engine", config.applicationName());
        assertEquals(5, config.connectTimeoutSeconds());
        assertEquals(30, config.socketTimeoutSeconds());
        assertEquals(5000, config.statementTimeoutMillis());
        assertEquals(15000, config.idleInTransactionSessionTimeoutMillis());
        assertEquals(10, config.maxPoolSize());
        assertEquals(1, config.minIdle());
        assertEquals(3000L, config.connectionAcquisitionTimeoutMillis());
        assertEquals(600000L, config.idleTimeoutMillis());
        assertEquals(1800000L, config.maxLifetimeMillis());
        assertTrue(config.rewriteBatchedInserts());
    }

    @Test
    void readsOverrideValuesForOperationalPostgresSettings() {
        PostgresDataSourceConfig config = PostgresDataSourceConfig.fromEnv(Map.ofEntries(
                Map.entry("AUTHZ_DB_HOST", "postgres.internal"),
                Map.entry("AUTHZ_DB_PORT", "6432"),
                Map.entry("AUTHZ_DB_APPLICATION_NAME", "authz-reviewer"),
                Map.entry("AUTHZ_DB_CONNECT_TIMEOUT_SECONDS", "9"),
                Map.entry("AUTHZ_DB_SOCKET_TIMEOUT_SECONDS", "45"),
                Map.entry("AUTHZ_DB_STATEMENT_TIMEOUT_MILLIS", "7000"),
                Map.entry("AUTHZ_DB_IDLE_IN_TRANSACTION_TIMEOUT_MILLIS", "20000"),
                Map.entry("AUTHZ_DB_MAX_POOL_SIZE", "16"),
                Map.entry("AUTHZ_DB_MIN_IDLE", "3"),
                Map.entry("AUTHZ_DB_CONNECTION_ACQUISITION_TIMEOUT_MILLIS", "4500"),
                Map.entry("AUTHZ_DB_IDLE_TIMEOUT_MILLIS", "120000"),
                Map.entry("AUTHZ_DB_MAX_LIFETIME_MILLIS", "900000"),
                Map.entry("AUTHZ_DB_REWRITE_BATCHED_INSERTS", "false")
        ));

        assertEquals("postgres.internal", config.host());
        assertEquals(6432, config.port());
        assertEquals("authz-reviewer", config.applicationName());
        assertEquals(9, config.connectTimeoutSeconds());
        assertEquals(45, config.socketTimeoutSeconds());
        assertEquals(7000, config.statementTimeoutMillis());
        assertEquals(20000, config.idleInTransactionSessionTimeoutMillis());
        assertEquals(16, config.maxPoolSize());
        assertEquals(3, config.minIdle());
        assertEquals(4500L, config.connectionAcquisitionTimeoutMillis());
        assertEquals(120000L, config.idleTimeoutMillis());
        assertEquals(900000L, config.maxLifetimeMillis());
        assertFalse(config.rewriteBatchedInserts());
    }

    @Test
    void rejectsInvalidPoolRelationships() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PostgresDataSourceConfig.fromEnv(Map.of(
                        "AUTHZ_DB_MAX_POOL_SIZE", "2",
                        "AUTHZ_DB_MIN_IDLE", "3"
                ))
        );

        assertEquals("minIdle must not exceed maxPoolSize", exception.getMessage());
    }

    @Test
    void rejectsInvalidBooleanFlags() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PostgresDataSourceConfig.fromEnv(Map.of(
                        "AUTHZ_DB_REWRITE_BATCHED_INSERTS", "sometimes"
                ))
        );

        assertEquals("AUTHZ_DB_REWRITE_BATCHED_INSERTS must be true or false", exception.getMessage());
    }
}
