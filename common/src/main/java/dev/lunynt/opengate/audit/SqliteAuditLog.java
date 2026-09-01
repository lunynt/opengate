package dev.lunynt.opengate.audit;

import dev.lunynt.opengate.database.SqliteSchema;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SqliteAuditLog implements AuditLog {
    private static final int MAXIMUM_DETAIL_LENGTH = 256;

    private final String jdbcUrl;
    private final Clock clock;
    private final AddressFingerprint fingerprints;

    public SqliteAuditLog(Path databaseFile, Clock clock, AddressFingerprint fingerprints) {
        SqliteSchema.migrate(databaseFile);
        this.jdbcUrl = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
        this.clock = clock;
        this.fingerprints = fingerprints;
    }

    @Override
    public void record(
            AuditEventType type,
            UUID playerId,
            String username,
            String address,
            String detail) {
        if (detail != null && detail.length() > MAXIMUM_DETAIL_LENGTH) {
            throw new IllegalArgumentException("audit detail exceeds " + MAXIMUM_DETAIL_LENGTH + " characters");
        }
        var sql = """
                INSERT INTO audit_events(
                    occurred_at, event_type, player_id, username, address_fingerprint, detail
                ) VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (var connection = DriverManager.getConnection(jdbcUrl);
                var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, clock.instant().toEpochMilli());
            statement.setString(2, type.name());
            statement.setString(3, playerId == null ? null : playerId.toString());
            statement.setString(4, username);
            statement.setString(5, fingerprints.create(address));
            statement.setString(6, detail);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("could not write OpenGate audit event", exception);
        }
    }

    @Override
    public List<AuditRecord> recent(UUID playerId, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("audit limit must be between 1 and 100");
        var sql = """
                SELECT id, occurred_at, event_type, player_id, username, address_fingerprint, detail
                FROM audit_events WHERE player_id = ? ORDER BY occurred_at DESC, id DESC LIMIT ?
                """;
        try (var connection = DriverManager.getConnection(jdbcUrl);
                var statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setInt(2, limit);
            try (var results = statement.executeQuery()) {
                var records = new ArrayList<AuditRecord>();
                while (results.next()) {
                    records.add(new AuditRecord(
                            results.getLong("id"),
                            Instant.ofEpochMilli(results.getLong("occurred_at")),
                            AuditEventType.valueOf(results.getString("event_type")),
                            UUID.fromString(results.getString("player_id")),
                            results.getString("username"),
                            results.getString("address_fingerprint"),
                            results.getString("detail")));
                }
                return List.copyOf(records);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("could not read OpenGate audit events", exception);
        }
    }
}
