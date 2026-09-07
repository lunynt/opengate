package dev.lunynt.opengate.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.lunynt.opengate.database.DatabaseConfig;
import dev.lunynt.opengate.database.DatabaseSchema;
import dev.lunynt.opengate.database.DatabaseType;
import dev.lunynt.opengate.database.OpenGateDataSource;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcAuditLogTest {
    @TempDir
    Path directory;

    @Test
    void storesQueryableEventWithoutRawAddress() throws Exception {
        var file = directory.resolve("audit.db");
        var playerId = UUID.randomUUID();
        var config = new DatabaseConfig(
                DatabaseType.SQLITE, "jdbc:sqlite:" + file, "", "", 1, Duration.ofSeconds(5));
        try (var dataSource = new OpenGateDataSource(config)) {
            DatabaseSchema.migrate(dataSource);
            var log = new JdbcAuditLog(
                    dataSource,
                    Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                    new AddressFingerprint(new SecretKeySpec(new byte[32], "AES")));

            log.record(AuditEventType.LOGIN_FAILURE, playerId, "Player", "203.0.113.42", null);

            var record = log.recent(playerId, 10).getFirst();
            assertEquals(AuditEventType.LOGIN_FAILURE, record.type());
            assertEquals("Player", record.username());
            assertFalse(record.addressFingerprint().contains("203.0.113.42"));
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file);
                    var statement = connection.createStatement();
                    var results = statement.executeQuery("SELECT address_fingerprint FROM audit_events")) {
                assertFalse(results.getString(1).contains("203.0.113.42"));
            }
        }
    }
}
