package dev.lunynt.opengate.identity;

import dev.lunynt.opengate.database.DatabaseType;
import dev.lunynt.opengate.database.OpenGateDataSource;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

public final class IdentityIdTranslator {
    private final OpenGateDataSource dataSource;
    private final UuidV7Generator generator;
    private final Clock clock;
    private final boolean enabled;

    public IdentityIdTranslator(OpenGateDataSource dataSource, Clock clock, boolean enabled) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.generator = new UuidV7Generator(clock, new java.security.SecureRandom());
        this.clock = Objects.requireNonNull(clock, "clock");
        this.enabled = enabled;
    }

    public UUID translate(UUID source) {
        Objects.requireNonNull(source, "source");
        if (!enabled || source.version() != 4) return source;
        var existing = find(source);
        if (existing != null) return existing;
        for (var attempt = 0; attempt < 3; attempt++) {
            var translated = generator.generate();
            try {
                insert(source, translated);
                return translated;
            } catch (SQLException exception) {
                existing = find(source);
                if (existing != null) return existing;
                if (!isConstraintViolation(exception)) {
                    throw new IllegalStateException("could not persist UUID translation", exception);
                }
            }
        }
        throw new IllegalStateException("could not allocate a unique UUIDv7 identity");
    }

    private UUID find(UUID source) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(
                        "SELECT translated_id FROM identity_mappings WHERE source_id = ?")) {
            statement.setString(1, source.toString());
            try (var results = statement.executeQuery()) {
                return results.next() ? UUID.fromString(results.getString(1)) : null;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("could not read UUID translation", exception);
        }
    }

    private void insert(UUID source, UUID translated) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(
                        "INSERT INTO identity_mappings(source_id, translated_id, created_at) VALUES(?, ?, ?)")) {
            statement.setString(1, source.toString());
            statement.setString(2, translated.toString());
            statement.setLong(3, clock.millis());
            statement.executeUpdate();
        }
    }

    private boolean isConstraintViolation(SQLException exception) {
        var state = exception.getSQLState();
        return "23505".equals(state) || "23000".equals(state)
                || dataSource.type() == DatabaseType.SQLITE && exception.getErrorCode() == 19;
    }
}
