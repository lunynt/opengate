package dev.lunynt.opengate.identity;

import dev.lunynt.opengate.account.AccountRepository;
import dev.lunynt.opengate.auth.IdentityType;
import java.util.Objects;
import java.util.regex.Pattern;

public final class IdentityResolver {
    private static final Pattern VALID_USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private final AccountRepository accounts;
    private final ProfileLookup profiles;
    private final boolean premiumLookupEnabled;
    private final java.util.function.Predicate<String> offlineWhitelist;

    public IdentityResolver(
            AccountRepository accounts, ProfileLookup profiles, boolean premiumLookupEnabled) {
        this(accounts, profiles, premiumLookupEnabled, ignored -> true);
    }

    public IdentityResolver(
            AccountRepository accounts,
            ProfileLookup profiles,
            boolean premiumLookupEnabled,
            java.util.function.Predicate<String> offlineWhitelist) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.premiumLookupEnabled = premiumLookupEnabled;
        this.offlineWhitelist = Objects.requireNonNull(offlineWhitelist, "offlineWhitelist");
    }

    public IdentityDecision resolve(String username) {
        if (!VALID_USERNAME.matcher(username).matches()) {
            return IdentityDecision.DENY_INVALID_USERNAME;
        }

        var account = accounts.findByUsername(username);
        if (account.isPresent()) {
            var existing = account.orElseThrow();
            if (!existing.username().equals(username)) {
                return IdentityDecision.DENY_CASE_MISMATCH;
            }
            if (existing.identityType() == IdentityType.PREMIUM) return IdentityDecision.ONLINE;
            return offlineWhitelist.test(username)
                    ? IdentityDecision.OFFLINE
                    : IdentityDecision.DENY_OFFLINE_NOT_WHITELISTED;
        }

        if (!premiumLookupEnabled) {
            return offlineDecision(username);
        }

        var result = profiles.find(username);
        if (result.status() == ProfileLookupResult.Status.UNAVAILABLE) {
            return IdentityDecision.DENY_LOOKUP_UNAVAILABLE;
        }
        if (result.status() == ProfileLookupResult.Status.NOT_FOUND) {
            return offlineDecision(username);
        }
        return result.profile().username().equals(username)
                ? IdentityDecision.ONLINE
                : IdentityDecision.DENY_CASE_MISMATCH;
    }

    private IdentityDecision offlineDecision(String username) {
        return offlineWhitelist.test(username)
                ? IdentityDecision.OFFLINE
                : IdentityDecision.DENY_OFFLINE_NOT_WHITELISTED;
    }
}
