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
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class RealmTest {
    private static final Realm STONE = new Realm(ResourceKey.create(Registries.DIMENSION, Realmforge.id("stone")),
            "realm.realmforge.stone.name", "realm.realmforge.stone.subtitle", "realm.realmforge.stone.locked_hint",
            "realm.realmforge.stone.flavour", Optional.of(Realmforge.id("vaultstone")),
            Optional.of(Realmforge.id("undervault_signet")), 0xFF7A8FB0, Optional.of(Identifier.withDefaultNamespace("ash")),
            0.8F, new Realm.Arrival(20, 90, Realmforge.id("vaultstone"), 3));
    private static final Realm OVERWORLD = new Realm(Level.OVERWORLD, "realm.realmforge.overworld.name", "", "", "",
            Optional.empty(), Optional.empty(), 0xFF5DBB63, Optional.empty(), 1.0F,
            new Realm.Arrival(64, 310, Identifier.withDefaultNamespace("stone_bricks"), 3));

    @Test
    void codecRoundTrips() {
        assertEquals(STONE, RoundTrip.json(Realm.CODEC, STONE));
        assertEquals(STONE, RoundTrip.nbt(Realm.CODEC, STONE));
        assertEquals(OVERWORLD, RoundTrip.json(Realm.CODEC, OVERWORLD));
    }

    @Test
    void anArrivalClampsHeightsIntoItsRange() {
        assertEquals(20, STONE.arrival().clampY(-5));
        assertEquals(64, STONE.arrival().clampY(64));
        assertEquals(90, STONE.arrival().clampY(200));
    }

    @Test
    void theUnlockIsTheRealmsAdvancement() {
        assertEquals(Realmforge.id("realm/stone"),
                Realms.unlockAdvancement(ResourceKey.create(WorldRegistries.REALM, Realmforge.id("stone"))));
    }

    /** The files {@code rf gen} writes for {@code content/realms/} decode. */
    @Test
    void generatedRealmsDecode() throws IOException {
        assertEquals(STONE, generated("stone"));
        assertEquals(OVERWORLD, generated("overworld"));
    }

    private static Realm generated(String id) throws IOException {
        String path = "/data/realmforge/realmforge/realm/" + id + ".json";
        try (InputStream stream = RealmTest.class.getResourceAsStream(path)) {
            assertTrue(stream != null, path + " is missing; run `uv run rf gen`");
            JsonElement json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return Realm.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        }
    }
}
