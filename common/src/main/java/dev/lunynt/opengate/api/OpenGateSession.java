package dev.lunynt.opengate.api;

import dev.lunynt.opengate.auth.AuthenticationMethod;
import dev.lunynt.opengate.auth.AuthenticationState;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record OpenGateSession(
        UUID connectionId,
        UUID accountId,
        AuthenticationState state,
        Optional<AuthenticationMethod> authenticationMethod,
        Instant createdAt) {}
