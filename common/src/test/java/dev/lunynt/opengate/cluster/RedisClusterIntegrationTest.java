package dev.lunynt.opengate.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.lunynt.opengate.auth.IdentityType;
import dev.lunynt.opengate.auth.ResolvedIdentity;
import dev.lunynt.opengate.auth.SessionRegistry;
import dev.lunynt.opengate.config.RedisConfiguration;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class RedisClusterIntegrationTest {
    @Test
    @Timeout(30)
    void synchronizesRevocationAndRecoversAfterRedisRestart() throws Exception {
        var executable = System.getenv("OPENGATE_TEST_REDIS_SERVER");
        assumeTrue(executable != null, "Set OPENGATE_TEST_REDIS_SERVER to run live Redis coverage");
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        var server = start(executable, port);
        try {
            await(() -> listening(port));
            var configuration = new RedisConfiguration(true, "redis://127.0.0.1:" + port,
                    "test:" + UUID.randomUUID(), Duration.ofSeconds(1));
            var firstSessions = new SessionRegistry(Clock.systemUTC());
            var secondSessions = new SessionRegistry(Clock.systemUTC());
            try (var first = new RedisClusterCoordinator(configuration, firstSessions);
                    var second = new RedisClusterCoordinator(configuration, secondSessions)) {
                var account = UUID.randomUUID();
                open(firstSessions, account);
                second.authenticated(account).toCompletableFuture().join();
                await(() -> firstSessions.size() == 0);

                open(firstSessions, account);
                second.revoked(account).toCompletableFuture().join();
                await(() -> firstSessions.size() == 0);

                for (var i = 0; i < 20; i++) {
                    second.revoked(account).toCompletableFuture().join();
                }
                open(firstSessions, account);
                open(secondSessions, account);
                server.destroy();
                server.waitFor();
                await(() -> firstSessions.size() == 0 && secondSessions.size() == 0);
                assertThrows(CompletionException.class,
                        () -> first.authenticated(account).toCompletableFuture().join());

                server = start(executable, port);
                await(() -> {
                    try {
                        second.authenticated(account).toCompletableFuture().join();
                        return true;
                    } catch (CompletionException exception) {
                        return false;
                    }
                });
                await(() -> {
                    try {
                        first.authenticated(account).toCompletableFuture().join();
                        return true;
                    } catch (CompletionException exception) {
                        return false;
                    }
                });
                open(firstSessions, account);
                second.revoked(account).toCompletableFuture().join();
                await(() -> firstSessions.size() == 0);
                assertEquals(0, secondSessions.size());
            }
        } finally {
            server.destroyForcibly();
            server.waitFor();
        }
    }

    private static Process start(String executable, int port) throws Exception {
        return new ProcessBuilder(executable, "--bind", "127.0.0.1", "--port", Integer.toString(port),
                "--save", "", "--appendonly", "no")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }

    private static void open(SessionRegistry sessions, UUID account) {
        sessions.open(UUID.randomUUID()).resolve(
                new ResolvedIdentity("Player", account, IdentityType.OFFLINE, true, true, false));
    }

    private static boolean listening(int port) {
        try (var socket = new Socket("127.0.0.1", port)) {
            return socket.isConnected();
        } catch (java.io.IOException exception) {
            return false;
        }
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        var deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) throw new AssertionError("Redis state did not converge");
            Thread.sleep(20);
        }
    }
}
