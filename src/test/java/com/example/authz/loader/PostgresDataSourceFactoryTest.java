package com.example.authz.loader;

import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostgresDataSourceFactoryTest {
    @Test
    void buildsPooledDataSourceConfigWithConfiguredLimits() {
        PostgresDataSourceConfig config = new PostgresDataSourceConfig(
                "postgres.internal",
                5432,
                "authz",
                "authz",
                "secret",
                "authz-reviewer",
                5,
                30,
                7000,
                20000,
                12,
                2,
                4000L,
                120000L,
                900000L,
                true
        );

        HikariConfig hikariConfig = PostgresDataSourceFactory.hikariConfig(config);

        assertEquals("authz-reviewer-pool", hikariConfig.getPoolName());
        assertEquals(12, hikariConfig.getMaximumPoolSize());
        assertEquals(2, hikariConfig.getMinimumIdle());
        assertEquals(4000L, hikariConfig.getConnectionTimeout());
        assertEquals(120000L, hikariConfig.getIdleTimeout());
        assertEquals(900000L, hikariConfig.getMaxLifetime());
        assertEquals(4000L, hikariConfig.getValidationTimeout());
        assertEquals(4000L, hikariConfig.getInitializationFailTimeout());
    }
}
