package dev.lunynt.opengate.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.lunynt.opengate.account.Account;
import dev.lunynt.opengate.account.AccountRepository;
import dev.lunynt.opengate.auth.IdentityType;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentityResolverTest {
    private final MemoryAccounts accounts = new MemoryAccounts();

    @Test
    void registeredOfflineAccountKeepsOfflineIdentity() {
        accounts.save(account("Player", IdentityType.OFFLINE));
        var resolver = new IdentityResolver(accounts, ignored -> ProfileLookupResult.unavailable(), true);

        assertEquals(IdentityDecision.OFFLINE, resolver.resolve("Player"));
        assertEquals(IdentityDecision.DENY_CASE_MISMATCH, resolver.resolve("player"));
    }

    @Test
    void unknownPremiumNameRequiresOnlineAuthentication() {
        var profile = new PremiumProfile(UUID.randomUUID(), "Player");
        var resolver = new IdentityResolver(accounts, ignored -> ProfileLookupResult.found(profile), true);

        assertEquals(IdentityDecision.ONLINE, resolver.resolve("Player"));
    }

    @Test
    void explicitPremiumModeLetsUnknownNamesRegisterOffline() {
        var profile = new PremiumProfile(UUID.randomUUID(), "Pipis");
        var resolver = new IdentityResolver(
                accounts, ignored -> ProfileLookupResult.found(profile), true, false, false, ignored -> true);

        assertEquals(IdentityDecision.OFFLINE, resolver.resolve("pipis"));
    }

    @Test
    void registeredPremiumAccountStillRequiresOnlineAuthentication() {
        accounts.save(account("Player", IdentityType.PREMIUM));
        var resolver = new IdentityResolver(
                accounts, ignored -> ProfileLookupResult.unavailable(), true, false, true, ignored -> true);

        assertEquals(IdentityDecision.ONLINE, resolver.resolve("Player"));
    }

    @Test
    void premiumReservationProtectsAnUnregisteredName() {
        var profile = new PremiumProfile(UUID.randomUUID(), "Player");
        var resolver = new IdentityResolver(
                accounts, ignored -> ProfileLookupResult.found(profile), true, false, true, ignored -> true);

        assertEquals(IdentityDecision.ONLINE, resolver.resolve("Player"));
        assertEquals(IdentityDecision.DENY_CASE_MISMATCH, resolver.resolve("player"));
    }

    @Test
    void lookupFailureDeniesInsteadOfDowngradingIdentity() {
        var resolver = new IdentityResolver(accounts, ignored -> ProfileLookupResult.unavailable(), true);

        assertEquals(IdentityDecision.DENY_LOOKUP_UNAVAILABLE, resolver.resolve("Player"));
    }

    @Test
    void offlineWhitelistAppliesToKnownAndUnknownOfflineAccounts() {
        accounts.save(account("Blocked", IdentityType.OFFLINE));
        var resolver = new IdentityResolver(
                accounts,
                ignored -> ProfileLookupResult.notFound(),
                true,
                "Allowed"::equals);

        assertEquals(IdentityDecision.OFFLINE, resolver.resolve("Allowed"));
        assertEquals(IdentityDecision.DENY_OFFLINE_NOT_WHITELISTED, resolver.resolve("Blocked"));
    }

    private static Account account(String username, IdentityType type) {
        return new Account(UUID.randomUUID(), username, type, "hash", null, Instant.EPOCH);
    }

    private static final class MemoryAccounts implements AccountRepository {
        private final HashMap<String, Account> values = new HashMap<>();

        @Override
        public Optional<Account> findByPlayerId(UUID playerId) {
            return values.values().stream().filter(value -> value.playerId().equals(playerId)).findFirst();
        }

        @Override
        public Optional<Account> findByUsername(String username) {
            return Optional.ofNullable(values.get(username.toLowerCase(Locale.ROOT)));
        }

        @Override
        public void save(Account account) {
            values.put(account.normalizedUsername(), account);
        }

        @Override
        public boolean claimTotpStep(UUID playerId, long step) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(UUID playerId) {
            values.values().removeIf(account -> account.playerId().equals(playerId));
        }
    }
}
