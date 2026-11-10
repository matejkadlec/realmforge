package io.github.matejkadlec.realmforge.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.matejkadlec.realmforge.spell.SpellLook;
import net.minecraft.util.LightCoordsUtil;
import org.junit.jupiter.api.Test;

/** How a look lights its models (RLM-640; Matěj, 2026-10-29: Fireball lights up, Frostbolt glows a little). */
class SpellModelsTest {
    @Test
    void aWorldLookTakesTheLightWhereTheModelIs() {
        int cave = LightCoordsUtil.pack(3, 0);
        assertEquals(cave, SpellModels.lit(SpellLook.Light.WORLD, cave));
    }

    @Test
    void aGlowingLookIsAFewBlockLightLevelsBrighterAndKeepsTheSky() {
        int lit = SpellModels.lit(SpellLook.Light.GLOW, LightCoordsUtil.pack(3, 12));
        assertEquals(3 + SpellModels.GLOW_LEVELS, LightCoordsUtil.block(lit));
        assertEquals(12, LightCoordsUtil.sky(lit), "the sky's light is the world's");
        assertEquals(15, LightCoordsUtil.block(SpellModels.lit(SpellLook.Light.GLOW, LightCoordsUtil.pack(13, 0))),
                "never above full block light");
    }

    @Test
    void anEmittingLookIsDrawnAtFullBrightnessInTheDark() {
        assertEquals(LightCoordsUtil.FULL_BRIGHT, SpellModels.lit(SpellLook.Light.EMIT, LightCoordsUtil.pack(0, 0)));
    }
}
