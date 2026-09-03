package dev.lunynt.opengate.identity;

import java.util.UUID;

public interface FloodgateIdentity {
    boolean isPlayer(UUID playerId);

    boolean isUsername(String username);

    static FloodgateIdentity unavailable() {
        return new FloodgateIdentity() {
            @Override
            public boolean isPlayer(UUID playerId) {
                return false;
            }

            @Override
            public boolean isUsername(String username) {
                return false;
            }
        };
    }
}
