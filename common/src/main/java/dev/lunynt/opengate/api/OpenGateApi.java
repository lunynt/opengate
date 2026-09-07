package dev.lunynt.opengate.api;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface OpenGateApi {
    CompletionStage<Optional<OpenGateUser>> findUser(UUID accountId);

    CompletionStage<Optional<OpenGateUser>> findUser(String username);

    Optional<OpenGateSession> findSession(UUID connectionId);

    boolean isAuthenticated(UUID connectionId);

    CompletionStage<Boolean> revokeSession(UUID connectionId);
}
