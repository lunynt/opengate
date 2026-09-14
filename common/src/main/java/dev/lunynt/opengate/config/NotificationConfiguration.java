package dev.lunynt.opengate.config;

import java.time.Duration;

public record NotificationConfiguration(
        boolean chatEnabled,
        boolean titlesEnabled,
        boolean actionBarEnabled,
        Duration reminderInterval,
        Duration titleFadeIn,
        Duration titleStay,
        Duration titleFadeOut) {

    public NotificationConfiguration {
        requireNonNegative(titleFadeIn, "title fade-in");
        requirePositive(titleStay, "title stay");
        requireNonNegative(titleFadeOut, "title fade-out");
        requirePositive(reminderInterval, "reminder interval");
    }

    private static void requirePositive(Duration value, String name) {
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
    }

    private static void requireNonNegative(Duration value, String name) {
        if (value.isNegative()) throw new IllegalArgumentException(name + " must not be negative");
    }
}
