package io.github.matejkadlec.realmforge.world.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.matejkadlec.realmforge.testing.RoundTrip;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class RealmRefusedPayloadTest {
    @Test
    void streamCodecRoundTripsTheHint() {
        RealmRefusedPayload payload =
                new RealmRefusedPayload(Component.translatable("realm.realmforge.stone.locked_hint"));
        assertEquals(payload, RoundTrip.stream(RealmRefusedPayload.STREAM_CODEC, payload));
    }

    /** A level requirement's hint carries its level (RLM-306): "Requires level 6." */
    @Test
    void streamCodecRoundTripsAHintWithItsLevel() {
        RealmRefusedPayload payload =
                new RealmRefusedPayload(Component.translatable("message.realmforge.requires_level", 6));
        assertEquals(payload, RoundTrip.stream(RealmRefusedPayload.STREAM_CODEC, payload));
    }

    @Test
    void aDimensionThatIsNoRealmSendsNoHint() {
        RealmRefusedPayload payload = new RealmRefusedPayload(Component.empty());
        assertEquals(payload, RoundTrip.stream(RealmRefusedPayload.STREAM_CODEC, payload));
    }
}
