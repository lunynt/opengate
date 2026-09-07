package dev.lunynt.opengate.cluster;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.lunynt.opengate.auth.SessionRegistry;
import dev.lunynt.opengate.config.RedisConfiguration;
import java.net.ServerSocket;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class RedisStartupIntegrationTest {
    @Test
    @Timeout(15)
    void failedHandshakeReleasesOwnedEventLoopThreads() throws Exception {
        var existingThreads = lettuceThreads();
        try (var endpoint = new ServerSocket(0)) {
            var configuration = new RedisConfiguration(true,
                    "redis://127.0.0.1:" + endpoint.getLocalPort(), "test:startup", Duration.ofMillis(100));
            assertThrows(RuntimeException.class,
                    () -> new RedisClusterCoordinator(configuration, new SessionRegistry(Clock.systemUTC())));
        }
        var remainingThreads = lettuceThreads();
        remainingThreads.removeAll(existingThreads);
        assertTrue(remainingThreads.isEmpty(), "Failed startup leaked Lettuce event-loop threads");
    }

    private static Set<Long> lettuceThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.isAlive() && thread.getName().startsWith("lettuce-"))
                .map(Thread::threadId)
                .collect(Collectors.toSet());
    }
}
