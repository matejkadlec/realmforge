package io.github.matejkadlec.realmforge.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.matejkadlec.realmforge.character.KillXpMultiplier.SizeMultiplier;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import org.junit.jupiter.api.Test;

class KillXpMultiplierTest {
    /** A slime's: nothing at size 1, a tenth up to size 3, a quarter above. */
    private static final KillXpMultiplier SLIME = new KillXpMultiplier(
            BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(EntityType.SLIME), 0.25,
            List.of(new SizeMultiplier(1, 0), new SizeMultiplier(3, 0.1)));

    @Test
    void aMobWithoutASizeTakesTheMultiplier() {
        assertEquals(0.25, SLIME.at(OptionalInt.empty()));
    }

    @Test
    void theFirstSizeEntryTheMobFitsApplies() {
        assertEquals(0.0, SLIME.at(OptionalInt.of(1)));
        assertEquals(0.1, SLIME.at(OptionalInt.of(2)));
        assertEquals(0.1, SLIME.at(OptionalInt.of(3)));
        assertEquals(0.25, SLIME.at(OptionalInt.of(4)));
        assertEquals(0.25, SLIME.at(OptionalInt.of(127)));
    }

    @Test
    void theCodecReadsTheGeneratedShape() {
        String json = "{\"entity\": \"minecraft:slime\", \"multiplier\": 0.25, "
                + "\"by_size\": [{\"max_size\": 1, \"multiplier\": 0.0}]}";
        KillXpMultiplier slime = KillXpMultiplier.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .getOrThrow();
        assertSame(EntityType.SLIME, slime.entity().value());
        assertEquals(0.25, slime.multiplier());
        assertEquals(List.of(new SizeMultiplier(1, 0)), slime.bySize());
        KillXpMultiplier witch = KillXpMultiplier.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"entity\": \"minecraft:witch\", \"multiplier\": 1.5}")).getOrThrow();
        assertEquals(List.of(), witch.bySize());
    }

    @Test
    void theCodecRefusesBrokenEntries() {
        for (String json : List.of(
                "{\"entity\": \"minecraft:no_such_mob\", \"multiplier\": 1}",
                "{\"entity\": \"minecraft:witch\", \"multiplier\": -1}",
                "{\"entity\": \"minecraft:slime\", \"multiplier\": 1, \"by_size\": [{\"max_size\": 2, \"multiplier\": 0},"
                        + " {\"max_size\": 2, \"multiplier\": 1}]}",
                "{\"entity\": \"minecraft:slime\", \"multiplier\": 1, \"by_size\": [{\"max_size\": 0, \"multiplier\": 0}]}")) {
            assertTrue(KillXpMultiplier.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).isError(), json);
        }
    }
}
