package dev.lunynt.opengate.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

public final class OpenGateDataSource implements DataSource, AutoCloseable {
    private final DatabaseType type;
    private final HikariDataSource delegate;

    public OpenGateDataSource(DatabaseConfig database) {
        type = database.type();
        var config = new HikariConfig();
        config.setPoolName("opengate-database");
        config.setJdbcUrl(database.jdbcUrl());
        if (!database.username().isEmpty()) config.setUsername(database.username());
        if (!database.password().isEmpty()) config.setPassword(database.password());
        config.setMaximumPoolSize(database.maximumPoolSize());
        config.setMinimumIdle(type == DatabaseType.SQLITE ? 1 : Math.min(2, database.maximumPoolSize()));
        config.setConnectionTimeout(database.connectionTimeout().toMillis());
        config.setValidationTimeout(Math.min(5_000, database.connectionTimeout().toMillis()));
        config.setAutoCommit(true);
        delegate = new HikariDataSource(config);
    }

    public DatabaseType type() { return type; }

    @Override
    public Connection getConnection() throws SQLException {
        var connection = delegate.getConnection();
        if (type == DatabaseType.SQLITE) {
            try (var statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys=ON");
                statement.execute("PRAGMA busy_timeout=5000");
            } catch (SQLException exception) {
                connection.close();
                throw exception;
            }
        }
        return connection;
    }

    @Override public Connection getConnection(String username, String password) throws SQLException {
        return delegate.getConnection(username, password);
    }
    @Override public java.io.PrintWriter getLogWriter() throws SQLException { return delegate.getLogWriter(); }
    @Override public void setLogWriter(java.io.PrintWriter out) throws SQLException { delegate.setLogWriter(out); }
    @Override public void setLoginTimeout(int seconds) throws SQLException { delegate.setLoginTimeout(seconds); }
    @Override public int getLoginTimeout() throws SQLException { return delegate.getLoginTimeout(); }
    @Override public java.util.logging.Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }
    @Override public <T> T unwrap(Class<T> iface) throws SQLException { return delegate.unwrap(iface); }
    @Override public boolean isWrapperFor(Class<?> iface) throws SQLException { return delegate.isWrapperFor(iface); }
    @Override public void close() { delegate.close(); }
}
