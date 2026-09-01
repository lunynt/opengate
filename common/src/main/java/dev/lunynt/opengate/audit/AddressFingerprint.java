package dev.lunynt.opengate.audit;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public final class AddressFingerprint {
    private final byte[] key;

    public AddressFingerprint(SecretKey key) {
        this.key = key.getEncoded().clone();
    }

    public String create(String address) {
        if (address == null) return null;
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(address.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("could not fingerprint address", exception);
        }
    }
}
