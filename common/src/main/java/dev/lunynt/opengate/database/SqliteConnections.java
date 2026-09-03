package dev.lunynt.opengate.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class SqliteConnections {
    private SqliteConnections() {}

    public static Connection open(String jdbcUrl) throws SQLException {
        var connection = DriverManager.getConnection(jdbcUrl);
        try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
            return connection;
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
    }
}
