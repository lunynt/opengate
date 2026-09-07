package dev.lunynt.opengate.cluster;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public interface ClusterCoordinator extends AutoCloseable {
    CompletionStage<Void> authenticated(UUID accountId);

    CompletionStage<Void> revoked(UUID accountId);

    @Override
    void close();

    static ClusterCoordinator disabled() {
        return new ClusterCoordinator() {
            @Override public CompletionStage<Void> authenticated(UUID accountId) {
                return CompletableFuture.completedFuture(null);
            }
            @Override public CompletionStage<Void> revoked(UUID accountId) {
                return CompletableFuture.completedFuture(null);
            }
            @Override public void close() {}
        };
    }
}
