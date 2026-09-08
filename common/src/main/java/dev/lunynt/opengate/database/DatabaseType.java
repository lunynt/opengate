package dev.lunynt.opengate.database;

import java.util.Locale;

public enum DatabaseType {
    SQLITE,
    POSTGRESQL,
    MYSQL,
    MARIADB,
    H2;

    public String driverClassName() {
        return switch (this) {
            case SQLITE -> "org.sqlite.JDBC";
            case POSTGRESQL -> "org.postgresql.Driver";
            case MYSQL -> "com.mysql.cj.jdbc.Driver";
            case MARIADB -> "org.mariadb.jdbc.Driver";
            case H2 -> "org.h2.Driver";
        };
    }

    public static DatabaseType parse(String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "database-type must be one of sqlite, postgresql, mysql, mariadb, h2", exception);
        }
    }
}
