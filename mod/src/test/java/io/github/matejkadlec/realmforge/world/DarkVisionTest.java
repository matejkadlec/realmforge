package io.github.matejkadlec.realmforge.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matejkadlec.realmforge.world.net.ToggleDarkVisionPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.common.EffectCures;
import org.junit.jupiter.api.Test;

class DarkVisionTest {
    @Test
    void effectIsInfiniteHiddenAndCuredByNothing() {
        MobEffectInstance effect = DarkVision.effect();
        assertTrue(effect.is(MobEffects.NIGHT_VISION));
        assertTrue(effect.isInfiniteDuration());
        assertFalse(effect.isVisible(), "particles");
        assertFalse(effect.showIcon(), "icon");
        assertFalse(effect.isAmbient());
        assertFalse(effect.getCures().contains(EffectCures.MILK));
        assertFalse(effect.getCures().contains(EffectCures.PROTECTED_BY_TOTEM));
        assertTrue(DarkVision.isDarkVision(effect));
    }

    @Test
    void otherNightVisionIsNotDarkVision() {
        assertFalse(DarkVision.isDarkVision(null));
        assertFalse(DarkVision.isDarkVision(new MobEffectInstance(MobEffects.NIGHT_VISION, 3600)), "a potion");
        assertFalse(DarkVision.isDarkVision(new MobEffectInstance(
                MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0, false, false, false)), "milk cures it");
        MobEffectInstance visible = new MobEffectInstance(
                MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0, false, true, true);
        visible.getCures().clear();
        assertFalse(DarkVision.isDarkVision(visible), "particles and icon");
        MobEffectInstance glowing = new MobEffectInstance(
                MobEffects.GLOWING, MobEffectInstance.INFINITE_DURATION, 0, false, false, false);
        glowing.getCures().clear();
        assertFalse(DarkVision.isDarkVision(glowing), "another effect");
    }

    @Test
    void togglePayloadRoundTrips() {
        ByteBuf buffer = Unpooled.buffer();
        ToggleDarkVisionPayload.STREAM_CODEC.encode(buffer, ToggleDarkVisionPayload.INSTANCE);
        assertSame(ToggleDarkVisionPayload.INSTANCE, ToggleDarkVisionPayload.STREAM_CODEC.decode(buffer));
        assertFalse(buffer.isReadable(), "the payload carries no bytes");
    }
}
