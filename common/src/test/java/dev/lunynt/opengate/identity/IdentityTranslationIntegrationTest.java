package dev.lunynt.opengate.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.lunynt.opengate.database.DatabaseConfig;
import dev.lunynt.opengate.database.DatabaseSchema;
import dev.lunynt.opengate.database.DatabaseType;
import dev.lunynt.opengate.database.OpenGateDataSource;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class IdentityTranslationIntegrationTest {
    @TempDir
    Path directory;

    @Test
    @Timeout(60)
    void independentTranslatorsConvergeAndMappingSurvivesReopening() throws Exception {
        for (var type : new DatabaseType[] {DatabaseType.H2, DatabaseType.SQLITE}) {
            var url = type == DatabaseType.H2
                    ? "jdbc:h2:" + directory.resolve("identities-h2")
                    : "jdbc:sqlite:" + directory.resolve("identities.db");
            var config = new DatabaseConfig(type, url, "", "", 4, Duration.ofSeconds(5));
            var source = UUID.randomUUID();
            UUID mapped;
            try (var firstPool = new OpenGateDataSource(config);
                    var secondPool = new OpenGateDataSource(config)) {
                DatabaseSchema.migrate(firstPool);
                var first = new IdentityIdTranslator(firstPool, Clock.systemUTC(), true);
                var second = new IdentityIdTranslator(secondPool, Clock.systemUTC(), true);
                var start = new CountDownLatch(1);
                var results = new HashSet<UUID>();
                try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
                    var futures = new ArrayList<Future<UUID>>();
                    for (var index = 0; index < 32; index++) {
                        var translator = index % 2 == 0 ? first : second;
                        futures.add(workers.submit(() -> {
                            start.await();
                            return translator.translate(source);
                        }));
                    }
                    start.countDown();
                    for (var future : futures) results.add(future.get());
                }
                assertEquals(1, results.size());
                mapped = results.iterator().next();
                assertEquals(7, mapped.version());
                try (var connection = firstPool.getConnection();
                        var statement = connection.createStatement();
                        var rows = statement.executeQuery("SELECT COUNT(*) FROM identity_mappings")) {
                    rows.next();
                    assertEquals(1, rows.getInt(1));
                }
            }
            try (var reopened = new OpenGateDataSource(config)) {
                assertEquals(mapped, new IdentityIdTranslator(reopened, Clock.systemUTC(), true).translate(source));
            }
        }
    }

    @Test
    void unavailableDatabaseNeverReturnsAnUnpersistedIdentity() {
        var config = new DatabaseConfig(DatabaseType.H2, "jdbc:h2:mem:" + UUID.randomUUID(),
                "", "", 1, Duration.ofSeconds(5));
        var pool = new OpenGateDataSource(config);
        var translator = new IdentityIdTranslator(pool, Clock.systemUTC(), true);
        pool.close();
        assertThrows(IllegalStateException.class, () -> translator.translate(UUID.randomUUID()));
    }
}
