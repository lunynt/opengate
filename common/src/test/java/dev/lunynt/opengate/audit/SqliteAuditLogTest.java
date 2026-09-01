package dev.lunynt.opengate.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.lunynt.opengate.database.SqliteSchema;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.sql.DriverManager;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteAuditLogTest {
    @TempDir
    Path directory;

    @Test
    void storesQueryableEventWithoutRawAddress() throws Exception {
        var file = directory.resolve("audit.db");
        SqliteSchema.migrate(file);
        var playerId = UUID.randomUUID();
        var log = new SqliteAuditLog(
                file,
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
