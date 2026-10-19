package io.github.matejkadlec.realmforge.world.realm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.testing.RoundTrip;
import io.github.matejkadlec.realmforge.world.WorldRegistries;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

class RealmTest {
    private static final Realm STONE = new Realm(ResourceKey.create(Registries.DIMENSION, Realmforge.id("stone")),
            "realm.realmforge.stone.name", "realm.realmforge.stone.subtitle", "realm.realmforge.stone.locked_hint",
            "realm.realmforge.stone.flavour", Realmforge.id("vaultstone"));

    @Test
    void codecRoundTrips() {
        assertEquals(STONE, RoundTrip.json(Realm.CODEC, STONE));
        assertEquals(STONE, RoundTrip.nbt(Realm.CODEC, STONE));
    }

    @Test
    void theUnlockIsTheRealmsAdvancement() {
        assertEquals(Realmforge.id("realm/stone"),
                Realms.unlockAdvancement(ResourceKey.create(WorldRegistries.REALM, Realmforge.id("stone"))));
    }

    /** The file {@code rf gen} writes for {@code content/realms/stone.yaml} decodes. */
    @Test
    void generatedStoneRealmDecodes() throws IOException {
        String path = "/data/realmforge/realmforge/realm/stone.json";
        try (InputStream stream = RealmTest.class.getResourceAsStream(path)) {
            assertTrue(stream != null, path + " is missing; run `uv run rf gen`");
            JsonElement json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            assertEquals(STONE, Realm.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        }
    }
}
