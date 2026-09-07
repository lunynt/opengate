package dev.lunynt.opengate.database;

import java.util.Locale;

public enum DatabaseType {
    SQLITE,
    POSTGRESQL,
    MYSQL,
    MARIADB,
    H2;

    public static DatabaseType parse(String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "database-type must be one of sqlite, postgresql, mysql, mariadb, h2", exception);
        }
    }
}
