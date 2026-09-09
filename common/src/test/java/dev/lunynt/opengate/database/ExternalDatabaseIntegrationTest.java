package dev.lunynt.opengate.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.lunynt.opengate.account.Account;
import dev.lunynt.opengate.account.AccountAlreadyExistsException;
import dev.lunynt.opengate.account.JdbcAccountRepository;
import dev.lunynt.opengate.auth.CookieSessionService;
import dev.lunynt.opengate.auth.IdentityType;
import dev.lunynt.opengate.identity.IdentityIdTranslator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class ExternalDatabaseIntegrationTest {
    @Test
    @Timeout(30)
    void postgresql() throws Exception {
        exercise(DatabaseType.POSTGRESQL, "OPENGATE_TEST_POSTGRESQL_URL",
                "OPENGATE_TEST_POSTGRESQL_USERNAME", "OPENGATE_TEST_POSTGRESQL_PASSWORD");
    }

    @Test
    @Timeout(30)
    void mysql() throws Exception {
        exercise(DatabaseType.MYSQL, "OPENGATE_TEST_MYSQL_URL",
                "OPENGATE_TEST_MYSQL_USERNAME", "OPENGATE_TEST_MYSQL_PASSWORD");
    }

    @Test
    @Timeout(30)
    void mariadb() throws Exception {
        exercise(DatabaseType.MARIADB, "OPENGATE_TEST_MARIADB_URL",
                "OPENGATE_TEST_MARIADB_USERNAME", "OPENGATE_TEST_MARIADB_PASSWORD");
    }

    private static void exercise(DatabaseType type, String urlName, String usernameName, String passwordName)
            throws Exception {
        var url = System.getenv(urlName);
        assumeTrue(url != null && !url.isBlank(), "Set " + urlName + " to run live " + type + " coverage");
        var config = new DatabaseConfig(type, url, environment(usernameName), environment(passwordName),
                4, Duration.ofSeconds(5));
        try (var dataSource = new OpenGateDataSource(config)) {
            DatabaseSchema.migrate(dataSource);
            DatabaseSchema.migrate(dataSource);
            var accounts = new JdbcAccountRepository(dataSource);
            var username = "Db" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
            var account = new Account(UUID.randomUUID(), username, IdentityType.OFFLINE,
                    "initial-hash", null, Instant.EPOCH);
            accounts.save(account);
            assertEquals(account, accounts.findByUsername(username.toLowerCase(java.util.Locale.ROOT)).orElseThrow());
            var replacement = new Account(account.playerId(), username, IdentityType.PREMIUM,
                    "replacement-hash", null, Instant.now());
            assertThrows(AccountAlreadyExistsException.class, () -> accounts.save(replacement));
            assertEquals(account, accounts.findByPlayerId(account.playerId()).orElseThrow());
            assertTrue(accounts.claimTotpStep(account.playerId(), 10));
            assertFalse(accounts.claimTotpStep(account.playerId(), 10));
            assertTrue(accounts.claimTotpStep(account.playerId(), 11));

            var sourceId = UUID.randomUUID();
            var translated = new IdentityIdTranslator(dataSource, Clock.systemUTC(), true).translate(sourceId);
            assertEquals(7, translated.version());
            assertEquals(translated,
                    new IdentityIdTranslator(dataSource, Clock.systemUTC(), true).translate(sourceId));

            try (var cookies = new CookieSessionService(dataSource, Clock.systemUTC(), Duration.ofHours(1), true)) {
                var token = cookies.issue(account.playerId()).get().orElseThrow();
                assertTrue(cookies.verify(account.playerId(), token).get());
                accounts.updatePassword(account.playerId(), "changed-hash");
                assertFalse(cookies.verify(account.playerId(), token).get());
            }
        }
    }

    private static String environment(String name) {
        var value = System.getenv(name);
        return value == null ? "" : value;
    }
}
