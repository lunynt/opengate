package dev.lunynt.opengate.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MojangProfileLookupTest {
    @Test
    void parsesProfileResponse() {
        var result = MojangProfileLookup.parse(
                "{\"id\":\"853c80ef3c3749fdaa49938b674adae6\",\"name\":\"Example\"}");

        assertEquals(ProfileLookupResult.Status.FOUND, result.status());
        assertEquals(UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6"), result.profile().playerId());
        assertEquals("Example", result.profile().username());
    }

    @Test
    void rejectsMalformedSuccessfulResponse() {
        assertEquals(ProfileLookupResult.Status.UNAVAILABLE, MojangProfileLookup.parse("{}").status());
    }
}
