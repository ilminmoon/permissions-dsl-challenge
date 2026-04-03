package com.example.authz.loader;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.postgresql.ds.PGSimpleDataSource;

public final class PostgresDataSourceFactory {
    private PostgresDataSourceFactory() {
    }

    public static HikariDataSource create(PostgresDataSourceConfig config) {
        return new HikariDataSource(hikariConfig(config));
    }

    static HikariConfig hikariConfig(PostgresDataSourceConfig config) {
        PGSimpleDataSource delegate = new PGSimpleDataSource();
        delegate.setServerNames(new String[]{config.host()});
        delegate.setPortNumbers(new int[]{config.port()});
        delegate.setDatabaseName(config.database());
        delegate.setUser(config.user());
        delegate.setPassword(config.password());
        delegate.setApplicationName(config.applicationName());
        delegate.setConnectTimeout(config.connectTimeoutSeconds());
        delegate.setSocketTimeout(config.socketTimeoutSeconds());
        delegate.setTcpKeepAlive(true);
        delegate.setReWriteBatchedInserts(config.rewriteBatchedInserts());
        delegate.setOptions(sessionOptions(config));

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDataSource(delegate);
        hikariConfig.setPoolName(config.applicationName() + "-pool");
        hikariConfig.setMaximumPoolSize(config.maxPoolSize());
        hikariConfig.setMinimumIdle(config.minIdle());
        hikariConfig.setConnectionTimeout(config.connectionAcquisitionTimeoutMillis());
        hikariConfig.setIdleTimeout(config.idleTimeoutMillis());
        hikariConfig.setMaxLifetime(config.maxLifetimeMillis());
        hikariConfig.setValidationTimeout(Math.min(config.connectionAcquisitionTimeoutMillis(), 5000L));
        hikariConfig.setInitializationFailTimeout(config.connectionAcquisitionTimeoutMillis());
        return hikariConfig;
    }

    private static String sessionOptions(PostgresDataSourceConfig config) {
        return "-c statement_timeout=" + config.statementTimeoutMillis()
                + " -c idle_in_transaction_session_timeout=" + config.idleInTransactionSessionTimeoutMillis();
    }
}
