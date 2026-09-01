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

    public IdentityResolver(
            AccountRepository accounts, ProfileLookup profiles, boolean premiumLookupEnabled) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.premiumLookupEnabled = premiumLookupEnabled;
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
            return existing.identityType() == IdentityType.PREMIUM
                    ? IdentityDecision.ONLINE
                    : IdentityDecision.OFFLINE;
        }

        if (!premiumLookupEnabled) {
            return IdentityDecision.OFFLINE;
        }

        var result = profiles.find(username);
        if (result.status() == ProfileLookupResult.Status.UNAVAILABLE) {
            return IdentityDecision.DENY_LOOKUP_UNAVAILABLE;
        }
        if (result.status() == ProfileLookupResult.Status.NOT_FOUND) {
            return IdentityDecision.OFFLINE;
        }
        return result.profile().username().equals(username)
                ? IdentityDecision.ONLINE
                : IdentityDecision.DENY_CASE_MISMATCH;
    }
}
