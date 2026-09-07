package dev.lunynt.opengate.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import dev.lunynt.opengate.database.DatabaseConfig;
import dev.lunynt.opengate.database.DatabaseSchema;
import dev.lunynt.opengate.database.DatabaseType;
import dev.lunynt.opengate.database.OpenGateDataSource;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentityIdTranslatorTest {
    @Test
    void persistsStableUuidV7Mapping() {
        var config = new DatabaseConfig(
                DatabaseType.H2, "jdbc:h2:mem:uuid-map;DB_CLOSE_DELAY=-1", "", "", 2, Duration.ofSeconds(5));
        try (var dataSource = new OpenGateDataSource(config)) {
            DatabaseSchema.migrate(dataSource);
            var translator = new IdentityIdTranslator(dataSource, Clock.systemUTC(), true);
            var source = UUID.randomUUID();

            var translated = translator.translate(source);

            assertNotEquals(source, translated);
            assertEquals(7, translated.version());
            assertEquals(translated, translator.translate(source));
        }
    }

    @Test
    void leavesIdsUntouchedWhenDisabled() {
        var config = new DatabaseConfig(
                DatabaseType.H2, "jdbc:h2:mem:uuid-disabled;DB_CLOSE_DELAY=-1", "", "", 1, Duration.ofSeconds(5));
        try (var dataSource = new OpenGateDataSource(config)) {
            DatabaseSchema.migrate(dataSource);
            var source = UUID.randomUUID();
            assertEquals(source, new IdentityIdTranslator(dataSource, Clock.systemUTC(), false).translate(source));
        }
    }
}
