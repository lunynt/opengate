package dev.lunynt.opengate.cluster;

import dev.lunynt.opengate.auth.SessionRegistry;
import dev.lunynt.opengate.config.RedisConfiguration;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisChannelHandler;
import io.lettuce.core.RedisConnectionStateListener;
import io.lettuce.core.RedisURI;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.api.StatefulRedisConnection;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class RedisClusterCoordinator implements ClusterCoordinator {
    private final String instanceId = UUID.randomUUID().toString();
    private final String channel;
    private final String sequenceKey;
    private final SessionRegistry sessions;
    private final RedisClient client;
    private final StatefulRedisConnection<String, String> commands;
    private final StatefulRedisPubSubConnection<String, String> subscription;
    private volatile ConcurrentHashMap<UUID, Long> latestSequences = new ConcurrentHashMap<>();
    private final AtomicLong subscriptionGeneration = new AtomicLong();
    private volatile boolean subscriptionReady;

    public RedisClusterCoordinator(RedisConfiguration configuration, SessionRegistry sessions) {
        this.sessions = java.util.Objects.requireNonNull(sessions, "sessions");
        channel = configuration.channel();
        sequenceKey = channel + ":sequence";
        var uri = RedisURI.create(configuration.uri());
        uri.setTimeout(configuration.timeout());
        client = RedisClient.create(uri);
        try {
            commands = client.connect();
            subscription = client.connectPubSub();
            subscription.addListener(new RedisConnectionStateListener() {
                @Override
                public void onRedisDisconnected(RedisChannelHandler<?, ?> connection) {
                    subscriptionReady = false;
                    subscriptionGeneration.incrementAndGet();
                    latestSequences = new ConcurrentHashMap<>();
                    sessions.invalidateAll();
                }
            });
            subscription.addListener(new RedisPubSubAdapter<>() {
                @Override public void subscribed(String incomingChannel, long count) {
                    if (channel.equals(incomingChannel)) subscriptionReady = true;
                }

                @Override public void message(String incomingChannel, String message) {
                    if (channel.equals(incomingChannel)) receive(message);
                }
            });
            subscription.sync().subscribe(channel);
        } catch (RuntimeException | Error failure) {
            try {
                client.shutdown();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    @Override
    public CompletionStage<Void> authenticated(UUID accountId) {
        return publish("AUTH", accountId);
    }

    @Override
    public CompletionStage<Void> revoked(UUID accountId) {
        return publish("REVOKE", accountId);
    }

    private CompletionStage<Void> publish(String type, UUID accountId) {
        var generation = subscriptionGeneration.get();
        var sequences = latestSequences;
        if (!subscriptionReady) {
            return CompletableFuture.failedFuture(new IllegalStateException("Redis subscription is unavailable"));
        }
        return commands.async().incr(sequenceKey).thenCompose(sequence -> {
            if (!subscriptionReady || generation != subscriptionGeneration.get()) {
                throw new IllegalStateException("Redis subscription changed during publication");
            }
            sequences.merge(accountId, sequence, Math::max);
            var message = type + "|" + sequence + "|" + instanceId + "|" + accountId;
            return commands.async().publish(channel, message).thenApply(ignored -> {
                if (!subscriptionReady || generation != subscriptionGeneration.get()) {
                    throw new IllegalStateException("Redis subscription changed during publication");
                }
                return null;
            });
        });
    }

    private void receive(String message) {
        ClusterMessage.parse(message).ifPresent(parsed -> {
            if (parsed.instanceId().toString().equals(instanceId)) return;
            var previous = latestSequences.merge(parsed.accountId(), parsed.sequence(), Math::max);
            if (parsed.sequence() < previous) return;
            sessions.closeByAccountId(parsed.accountId());
        });
    }

    @Override
    public void close() {
        client.shutdown();
    }
}
