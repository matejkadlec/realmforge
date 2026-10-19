package io.github.matejkadlec.realmforge.world.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.matejkadlec.realmforge.testing.RoundTrip;
import org.junit.jupiter.api.Test;

class RealmRefusedPayloadTest {
    @Test
    void streamCodecRoundTripsTheHint() {
        RealmRefusedPayload payload = new RealmRefusedPayload("realm.realmforge.stone.locked_hint");
        assertEquals(payload, RoundTrip.stream(RealmRefusedPayload.STREAM_CODEC, payload));
    }

    @Test
    void aDimensionThatIsNoRealmSendsNoHint() {
        RealmRefusedPayload payload = new RealmRefusedPayload("");
        assertEquals(payload, RoundTrip.stream(RealmRefusedPayload.STREAM_CODEC, payload));
    }
}
