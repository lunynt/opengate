package dev.lunynt.opengate.integration;

import java.util.Objects;
import java.util.UUID;
import us.ajg0702.queue.api.AjQueueAPI;

public final class AjQueueIntegration {
    private final AjQueueAPI api;

    public AjQueueIntegration() {
        api = Objects.requireNonNull(AjQueueAPI.getInstance(), "ajQueue API is not initialized");
    }

    public boolean enqueue(UUID playerId, String target) {
        var player = api.getPlatformMethods().getPlayer(playerId);
        return player != null && api.getQueueManager().addToQueue(player, target);
    }
}
