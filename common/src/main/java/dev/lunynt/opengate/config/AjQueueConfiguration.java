package dev.lunynt.opengate.config;

import java.util.Properties;

public record AjQueueConfiguration(boolean enabled, String target) {
    public AjQueueConfiguration {
        if (enabled && (target == null || target.isBlank())) {
            throw new IllegalArgumentException("ajqueue-target must not be blank when integration is enabled");
        }
        target = target == null ? "" : target.trim();
    }

    static AjQueueConfiguration from(Properties properties) {
        return new AjQueueConfiguration(
                OpenGateConfig.bool(properties, "ajqueue-enabled", false),
                properties.getProperty("ajqueue-target", "lobby"));
    }
}
