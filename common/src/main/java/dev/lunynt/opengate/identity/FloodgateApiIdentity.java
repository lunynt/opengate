package dev.lunynt.opengate.identity;

import java.util.UUID;
import org.geysermc.floodgate.api.FloodgateApi;

public final class FloodgateApiIdentity implements FloodgateIdentity {
    private final FloodgateApi api;

    public FloodgateApiIdentity() {
        api = FloodgateApi.getInstance();
    }

    @Override
    public boolean isPlayer(UUID playerId) {
        return api.isFloodgatePlayer(playerId);
    }

    @Override
    public boolean isUsername(String username) {
        return api.getPlayers().stream()
                .anyMatch(player -> player.getCorrectUsername().equals(username));
    }
}
