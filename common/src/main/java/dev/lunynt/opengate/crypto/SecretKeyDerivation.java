package dev.lunynt.opengate.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public final class SecretKeyDerivation {
    private SecretKeyDerivation() {}

    public static SecretKey derive(SecretKey masterKey, String purpose, String algorithm) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(masterKey.getEncoded(), "HmacSHA256"));
            var bytes = mac.doFinal(("opengate/" + purpose).getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(bytes, algorithm);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("could not derive OpenGate secret key", exception);
        }
    }
}
