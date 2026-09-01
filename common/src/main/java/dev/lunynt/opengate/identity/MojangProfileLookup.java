package dev.lunynt.opengate.identity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;

public final class MojangProfileLookup implements ProfileLookup {
    private static final URI ENDPOINT =
            URI.create("https://api.minecraftservices.com/minecraft/profile/lookup/name/");
    private static final Pattern PROFILE_PATTERN = Pattern.compile(
            "\\{[^}]*\"id\"\\s*:\\s*\"([0-9a-fA-F]{32})\"[^}]*\"name\"\\s*:\\s*\"([A-Za-z0-9_]{1,16})\"[^}]*}");

    private final HttpClient client;
    private final Duration timeout;

    public MojangProfileLookup(Duration timeout) {
        this(HttpClient.newBuilder().connectTimeout(timeout).build(), timeout);
    }

    MojangProfileLookup(HttpClient client, Duration timeout) {
        this.client = client;
        this.timeout = timeout;
    }

    @Override
    public ProfileLookupResult find(String username) {
        var request = HttpRequest.newBuilder(ENDPOINT.resolve(username))
                .timeout(timeout)
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404 || response.statusCode() == 204) {
                return ProfileLookupResult.notFound();
            }
            if (response.statusCode() != 200) {
                return ProfileLookupResult.unavailable();
            }
            return parse(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return ProfileLookupResult.unavailable();
        } catch (Exception exception) {
            return ProfileLookupResult.unavailable();
        }
    }

    static ProfileLookupResult parse(String json) {
        var matcher = PROFILE_PATTERN.matcher(json);
        if (!matcher.find()) {
            return ProfileLookupResult.unavailable();
        }
        var id = matcher.group(1);
        var uuid = UUID.fromString(id.substring(0, 8)
                + "-" + id.substring(8, 12)
                + "-" + id.substring(12, 16)
                + "-" + id.substring(16, 20)
                + "-" + id.substring(20));
        return ProfileLookupResult.found(new PremiumProfile(uuid, matcher.group(2)));
    }
}
