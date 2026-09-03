package dev.lunynt.opengate.account;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HexFormat;
import java.util.Locale;

public final class NetworkAddress {
    private NetworkAddress() {}

    public static String rateLimitKey(String address) {
        try {
            var bytes = InetAddress.getByName(address).getAddress();
            if (bytes.length == 16) {
                for (var index = 8; index < bytes.length; index++) bytes[index] = 0;
            }
            return HexFormat.of().formatHex(bytes);
        } catch (UnknownHostException exception) {
            return address.toLowerCase(Locale.ROOT);
        }
    }
}
