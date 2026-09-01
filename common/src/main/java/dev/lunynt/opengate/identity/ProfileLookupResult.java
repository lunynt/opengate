package dev.lunynt.opengate.identity;

import java.util.Optional;

public record ProfileLookupResult(Status status, PremiumProfile profile) {
    public enum Status {
        FOUND,
        NOT_FOUND,
        UNAVAILABLE
    }

    public static ProfileLookupResult found(PremiumProfile profile) {
        return new ProfileLookupResult(Status.FOUND, profile);
    }

    public static ProfileLookupResult notFound() {
        return new ProfileLookupResult(Status.NOT_FOUND, null);
    }

    public static ProfileLookupResult unavailable() {
        return new ProfileLookupResult(Status.UNAVAILABLE, null);
    }

    public Optional<PremiumProfile> optionalProfile() {
        return Optional.ofNullable(profile);
    }
}
