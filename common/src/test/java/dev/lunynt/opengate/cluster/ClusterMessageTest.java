package dev.lunynt.opengate.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClusterMessageTest {
    private static final UUID INSTANCE = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID ACCOUNT = UUID.fromString("00000000-0000-4000-8000-000000000002");

    @Test
    void acceptsAuthenticationAndRevocation() {
        for (var type : new String[] {"AUTH", "REVOKE"}) {
            assertEquals(new ClusterMessage(type, 42, INSTANCE, ACCOUNT),
                    ClusterMessage.parse(type + "|42|" + INSTANCE + "|" + ACCOUNT).orElseThrow());
        }
    }

    @Test
    void rejectsUnknownTypesBeforeTheyCanAdvanceSequenceState() {
        assertTrue(ClusterMessage.parse("UNKNOWN|9223372036854775807|" + INSTANCE + "|" + ACCOUNT).isEmpty());
    }

    @Test
    void rejectsMalformedOrUnboundedInput() {
        for (var sequence : new String[] {"0", "-1", "+1", "01", "9223372036854775808", "NaN"}) {
            assertTrue(ClusterMessage.parse("AUTH|" + sequence + "|" + INSTANCE + "|" + ACCOUNT).isEmpty());
        }
        assertTrue(ClusterMessage.parse(null).isEmpty());
        assertTrue(ClusterMessage.parse("x".repeat(129)).isEmpty());
        assertTrue(ClusterMessage.parse("AUTH|1|invalid|" + ACCOUNT).isEmpty());
        assertTrue(ClusterMessage.parse("AUTH|1|" + INSTANCE + "|1-1-1-1-1").isEmpty());
        assertTrue(ClusterMessage.parse("AUTH|1|" + INSTANCE + "|" + ACCOUNT + "|extra").isEmpty());
    }
}
