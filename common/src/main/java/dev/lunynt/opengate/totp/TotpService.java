package dev.lunynt.opengate.totp;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Locale;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.util.encoders.Base32;

public final class TotpService {
    private static final long STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final String SHA256_PREFIX = "sha256:";

    private final Clock clock;
    private final SecureRandom random;

    public TotpService(Clock clock) {
        this(clock, new SecureRandom());
    }

    TotpService(Clock clock, SecureRandom random) {
        this.clock = clock;
        this.random = random;
    }

    public String createSecret() {
        var bytes = new byte[32];
        random.nextBytes(bytes);
        return SHA256_PREFIX + Base32.toBase32String(bytes).replace("=", "");
    }

    public boolean verify(String secret, String code) {
        return matchingStep(secret, code).isPresent();
    }

    public OptionalLong matchingStep(String secret, String code) {
        if (code == null || !code.matches("\\d{6}")) {
            return OptionalLong.empty();
        }
        var step = clock.instant().getEpochSecond() / STEP_SECONDS;
        for (long offset = -1; offset <= 1; offset++) {
            if (MessageDigest.isEqual(
                    generate(secret, step + offset).getBytes(StandardCharsets.US_ASCII),
                    code.getBytes(StandardCharsets.US_ASCII))) {
                return OptionalLong.of(step + offset);
            }
        }
        return OptionalLong.empty();
    }

    public String provisioningUri(String issuer, String username, String secret) {
        var algorithm = algorithm(secret);
        return "otpauth://totp/" + encode(issuer + ":" + username)
                + "?secret=" + encodedSecret(secret)
                + "&issuer=" + encode(issuer)
                + "&algorithm=" + algorithm.provisioningName
                + "&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    String generate(String secret, long step) {
        try {
            var algorithm = algorithm(secret);
            var encoded = encodedSecret(secret).toUpperCase(Locale.ROOT);
            var padding = (8 - encoded.length() % 8) % 8;
            var key = Base32.decode(encoded + "=".repeat(padding));
            var mac = Mac.getInstance(algorithm.macName);
            mac.init(new SecretKeySpec(key, algorithm.macName));
            var digest = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            var offset = digest[digest.length - 1] & 0x0f;
            var binary = ((digest[offset] & 0x7f) << 24)
                    | ((digest[offset + 1] & 0xff) << 16)
                    | ((digest[offset + 2] & 0xff) << 8)
                    | (digest[offset + 3] & 0xff);
            return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
        } catch (GeneralSecurityException | RuntimeException exception) {
            throw new IllegalArgumentException("invalid TOTP secret", exception);
        }
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static Algorithm algorithm(String credential) {
        return credential.startsWith(SHA256_PREFIX) ? Algorithm.SHA256 : Algorithm.SHA1;
    }

    private static String encodedSecret(String credential) {
        return credential.startsWith(SHA256_PREFIX) ? credential.substring(SHA256_PREFIX.length()) : credential;
    }

    private enum Algorithm {
        SHA1("HmacSHA1", "SHA1"),
        SHA256("HmacSHA256", "SHA256");

        private final String macName;
        private final String provisioningName;

        Algorithm(String macName, String provisioningName) {
            this.macName = macName;
            this.provisioningName = provisioningName;
        }
    }
}
