package dev.lunynt.opengate.database;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class DatabaseTypeTest {
    @Test
    void mapsEveryDatabaseToItsBundledDriver() {
        assertEquals("org.sqlite.JDBC", DatabaseType.SQLITE.driverClassName());
        assertEquals("org.postgresql.Driver", DatabaseType.POSTGRESQL.driverClassName());
        assertEquals("com.mysql.cj.jdbc.Driver", DatabaseType.MYSQL.driverClassName());
        assertEquals("org.mariadb.jdbc.Driver", DatabaseType.MARIADB.driverClassName());
        assertEquals("org.h2.Driver", DatabaseType.H2.driverClassName());
    }
}
