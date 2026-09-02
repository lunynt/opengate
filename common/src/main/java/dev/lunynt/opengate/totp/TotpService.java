package dev.lunynt.opengate.totp;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.util.encoders.Base32;

public final class TotpService {
    private static final long STEP_SECONDS = 30;
    private static final int DIGITS = 6;

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
        var bytes = new byte[20];
        random.nextBytes(bytes);
        return Base32.toBase32String(bytes).replace("=", "");
    }

    public boolean verify(String secret, String code) {
        if (code == null || !code.matches("\\d{6}")) {
            return false;
        }
        var step = clock.instant().getEpochSecond() / STEP_SECONDS;
        for (long offset = -1; offset <= 1; offset++) {
            if (MessageDigest.isEqual(
                    generate(secret, step + offset).getBytes(StandardCharsets.US_ASCII),
                    code.getBytes(StandardCharsets.US_ASCII))) {
                return true;
            }
        }
        return false;
    }

    public String provisioningUri(String issuer, String username, String secret) {
        return "otpauth://totp/" + encode(issuer + ":" + username)
                + "?secret=" + secret
                + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    String generate(String secret, long step) {
        try {
            var key = Base32.decode(secret.toUpperCase(Locale.ROOT));
            var mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
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
}
