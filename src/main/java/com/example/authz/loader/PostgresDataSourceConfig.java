package com.example.authz.loader;

import java.util.Map;

public record PostgresDataSourceConfig(
        String host,
        int port,
        String database,
        String user,
        String password,
        String applicationName,
        int connectTimeoutSeconds,
        int socketTimeoutSeconds,
        int statementTimeoutMillis,
        int idleInTransactionSessionTimeoutMillis,
        int maxPoolSize,
        int minIdle,
        long connectionAcquisitionTimeoutMillis,
        long idleTimeoutMillis,
        long maxLifetimeMillis,
        boolean rewriteBatchedInserts
) {
    public PostgresDataSourceConfig {
        requireNonBlank(host, "host");
        requireNonBlank(database, "database");
        requireNonBlank(user, "user");
        requireNonBlank(password, "password");
        requireNonBlank(applicationName, "applicationName");
        requirePositive(port, "port");
        requirePositive(connectTimeoutSeconds, "connectTimeoutSeconds");
        requirePositive(socketTimeoutSeconds, "socketTimeoutSeconds");
        requirePositive(statementTimeoutMillis, "statementTimeoutMillis");
        requirePositive(idleInTransactionSessionTimeoutMillis, "idleInTransactionSessionTimeoutMillis");
        requirePositive(maxPoolSize, "maxPoolSize");
        requireNonNegative(minIdle, "minIdle");
        requirePositive(connectionAcquisitionTimeoutMillis, "connectionAcquisitionTimeoutMillis");
        requirePositive(idleTimeoutMillis, "idleTimeoutMillis");
        requirePositive(maxLifetimeMillis, "maxLifetimeMillis");
        if (minIdle > maxPoolSize) {
            throw new IllegalArgumentException("minIdle must not exceed maxPoolSize");
        }
    }

    public static PostgresDataSourceConfig fromEnv(Map<String, String> env) {
        return new PostgresDataSourceConfig(
                env.getOrDefault("AUTHZ_DB_HOST", "127.0.0.1"),
                parseInt(env.getOrDefault("AUTHZ_DB_PORT", "5432"), "AUTHZ_DB_PORT"),
                env.getOrDefault("AUTHZ_DB_NAME", "authz"),
                env.getOrDefault("AUTHZ_DB_USER", "authz"),
                env.getOrDefault("AUTHZ_DB_PASSWORD", "authz"),
                env.getOrDefault("AUTHZ_DB_APPLICATION_NAME", "authz-policy-engine"),
                parseInt(env.getOrDefault("AUTHZ_DB_CONNECT_TIMEOUT_SECONDS", "5"), "AUTHZ_DB_CONNECT_TIMEOUT_SECONDS"),
                parseInt(env.getOrDefault("AUTHZ_DB_SOCKET_TIMEOUT_SECONDS", "30"), "AUTHZ_DB_SOCKET_TIMEOUT_SECONDS"),
                parseInt(env.getOrDefault("AUTHZ_DB_STATEMENT_TIMEOUT_MILLIS", "5000"), "AUTHZ_DB_STATEMENT_TIMEOUT_MILLIS"),
                parseInt(
                        env.getOrDefault("AUTHZ_DB_IDLE_IN_TRANSACTION_TIMEOUT_MILLIS", "15000"),
                        "AUTHZ_DB_IDLE_IN_TRANSACTION_TIMEOUT_MILLIS"
                ),
                parseInt(env.getOrDefault("AUTHZ_DB_MAX_POOL_SIZE", "10"), "AUTHZ_DB_MAX_POOL_SIZE"),
                parseInt(env.getOrDefault("AUTHZ_DB_MIN_IDLE", "1"), "AUTHZ_DB_MIN_IDLE"),
                parseLong(
                        env.getOrDefault("AUTHZ_DB_CONNECTION_ACQUISITION_TIMEOUT_MILLIS", "3000"),
                        "AUTHZ_DB_CONNECTION_ACQUISITION_TIMEOUT_MILLIS"
                ),
                parseLong(env.getOrDefault("AUTHZ_DB_IDLE_TIMEOUT_MILLIS", "600000"), "AUTHZ_DB_IDLE_TIMEOUT_MILLIS"),
                parseLong(env.getOrDefault("AUTHZ_DB_MAX_LIFETIME_MILLIS", "1800000"), "AUTHZ_DB_MAX_LIFETIME_MILLIS"),
                parseBoolean(env.getOrDefault("AUTHZ_DB_REWRITE_BATCHED_INSERTS", "true"), "AUTHZ_DB_REWRITE_BATCHED_INSERTS")
        );
    }

    private static int parseInt(String rawValue, String key) {
        try {
            return Integer.parseInt(rawValue);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be a valid integer", exception);
        }
    }

    private static long parseLong(String rawValue, String key) {
        try {
            return Long.parseLong(rawValue);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be a valid integer", exception);
        }
    }

    private static boolean parseBoolean(String rawValue, String key) {
        if ("true".equalsIgnoreCase(rawValue)) {
            return true;
        }
        if ("false".equalsIgnoreCase(rawValue)) {
            return false;
        }
        throw new IllegalArgumentException(key + " must be true or false");
    }

    private static void requirePositive(long value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private static void requireNonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
