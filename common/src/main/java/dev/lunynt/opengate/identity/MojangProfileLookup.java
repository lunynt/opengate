package dev.lunynt.opengate.identity;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public final class MojangProfileLookup implements ProfileLookup {
    private static final URI ENDPOINT =
            URI.create("https://api.minecraftservices.com/minecraft/profile/lookup/name/");
    private static final int MAXIMUM_RESPONSE_LENGTH = 4_096;
    private static final int MAXIMUM_CACHE_ENTRIES = 4_096;
    private static final Duration RESULT_LIFETIME = Duration.ofMinutes(10);
    private static final Duration FAILURE_LIFETIME = Duration.ofSeconds(15);
    private static final JsonFactory JSON = new JsonFactory();

    private final HttpClient client;
    private final Duration timeout;
    private final Clock clock;
    private final Semaphore requests = new Semaphore(8);
    private final ConcurrentHashMap<String, CachedResult> cache = new ConcurrentHashMap<>();

    public MojangProfileLookup(Duration timeout) {
        this(HttpClient.newBuilder().connectTimeout(timeout).build(), timeout, Clock.systemUTC());
    }

    MojangProfileLookup(HttpClient client, Duration timeout) {
        this(client, timeout, Clock.systemUTC());
    }

    MojangProfileLookup(HttpClient client, Duration timeout, Clock clock) {
        this.client = client;
        this.timeout = timeout;
        this.clock = clock;
    }

    @Override
    public ProfileLookupResult find(String username) {
        var key = username.toLowerCase(Locale.ROOT);
        var cached = cache.get(key);
        var now = clock.instant();
        if (cached != null && cached.expiresAt().isAfter(now)) return cached.result();
        if (!requests.tryAcquire()) return ProfileLookupResult.unavailable();
        try {
            var result = request(username);
            cache(key, result, now.plus(result.status() == ProfileLookupResult.Status.UNAVAILABLE
                    ? FAILURE_LIFETIME
                    : RESULT_LIFETIME));
            return result;
        } finally {
            requests.release();
        }
    }

    private ProfileLookupResult request(String username) {
        var request = HttpRequest.newBuilder(ENDPOINT.resolve(username))
                .timeout(timeout)
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404 || response.statusCode() == 204) return ProfileLookupResult.notFound();
            if (response.statusCode() != 200 || response.body().length() > MAXIMUM_RESPONSE_LENGTH) {
                return ProfileLookupResult.unavailable();
            }
            return parse(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return ProfileLookupResult.unavailable();
        } catch (IOException | RuntimeException exception) {
            return ProfileLookupResult.unavailable();
        }
    }

    private void cache(String key, ProfileLookupResult result, Instant expiresAt) {
        if (!cache.containsKey(key) && cache.size() >= MAXIMUM_CACHE_ENTRIES) {
            cache.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(clock.instant()));
            if (cache.size() >= MAXIMUM_CACHE_ENTRIES) cache.remove(cache.keySet().iterator().next());
        }
        cache.put(key, new CachedResult(result, expiresAt));
    }

    static ProfileLookupResult parse(String json) {
        try (var parser = JSON.createParser(json)) {
            String id = null;
            String name = null;
            if (parser.nextToken() != JsonToken.START_OBJECT) return ProfileLookupResult.unavailable();
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                var field = parser.currentName();
                var token = parser.nextToken();
                if (token == JsonToken.VALUE_STRING && "id".equals(field)) id = parser.getValueAsString();
                else if (token == JsonToken.VALUE_STRING && "name".equals(field)) name = parser.getValueAsString();
                else parser.skipChildren();
            }
            if (id == null || name == null || !id.matches("[0-9a-fA-F]{32}")
                    || !name.matches("[A-Za-z0-9_]{1,16}")) return ProfileLookupResult.unavailable();
            var uuid = UUID.fromString(id.substring(0, 8)
                    + "-" + id.substring(8, 12)
                    + "-" + id.substring(12, 16)
                    + "-" + id.substring(16, 20)
                    + "-" + id.substring(20));
            return ProfileLookupResult.found(new PremiumProfile(uuid, name));
        } catch (IOException | RuntimeException exception) {
            return ProfileLookupResult.unavailable();
        }
    }

    private record CachedResult(ProfileLookupResult result, Instant expiresAt) {}
}
