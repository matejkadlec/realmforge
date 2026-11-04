package io.github.matejkadlec.realmforge.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import org.junit.jupiter.api.Test;

/** RLM-487: the age of each periodic effect since its first application, which its ticks follow. */
class EffectAgesTest {
    private static final ResourceKey<MobEffect> BURNING = ResourceKey.create(Registries.MOB_EFFECT,
            Identifier.parse("realmforge:test_burning"));
    private static final ResourceKey<MobEffect> REGROWTH = ResourceKey.create(Registries.MOB_EFFECT,
            Identifier.parse("realmforge:test_regrowth"));

    @Test
    void eachEffectCountsItsOwnServerTicksFromOne() {
        EffectAges ages = new EffectAges();
        assertEquals(0, ages.age(BURNING), "no tick yet");
        assertEquals(1, ages.advance(BURNING));
        assertEquals(2, ages.advance(BURNING));
        assertEquals(1, ages.advance(REGROWTH), "another effect counts on its own");
        assertEquals(2, ages.age(BURNING));
    }

    @Test
    void aForgottenEffectStartsAgain() {
        EffectAges ages = new EffectAges();
        ages.advance(BURNING);
        ages.advance(BURNING);
        assertTrue(ages.forget(BURNING));
        assertFalse(ages.forget(BURNING), "nothing left to forget");
        assertEquals(1, ages.advance(BURNING), "a new application after it ended starts at 1");
    }

    @Test
    void anEffectWithoutEndStartsCountingAgainAtTheLargestInt() {
        assertEquals(1, loaded(Integer.MAX_VALUE).advance(BURNING));
    }

    /** The ages survive a save and a load, so a reload keeps the pace of the ticks. */
    @Test
    void itSavesAndLoads() {
        EffectAges ages = new EffectAges();
        ages.advance(BURNING);
        ages.advance(BURNING);
        EffectAges reloaded = EffectAges.CODEC.parse(JsonOps.INSTANCE,
                EffectAges.CODEC.encodeStart(JsonOps.INSTANCE, ages).getOrThrow()).getOrThrow();
        assertEquals(2, reloaded.age(BURNING));
        assertEquals(3, reloaded.advance(BURNING));
        assertEquals(38, loaded(37).advance(BURNING));
    }

    /** Ages as a save holds them: test_burning at {@code age}. */
    private static EffectAges loaded(int age) {
        return EffectAges.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"realmforge:test_burning\": " + age + "}")).getOrThrow();
    }
}
