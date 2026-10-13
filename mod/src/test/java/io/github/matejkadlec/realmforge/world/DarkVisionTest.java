package io.github.matejkadlec.realmforge.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matejkadlec.realmforge.world.net.ToggleDarkVisionPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.junit.jupiter.api.Test;

class DarkVisionTest {
    @Test
    void effectIsInfiniteAndHidden() {
        MobEffectInstance effect = DarkVision.effect();
        assertTrue(effect.is(MobEffects.NIGHT_VISION));
        assertTrue(effect.isInfiniteDuration());
        assertFalse(effect.isVisible(), "particles");
        assertFalse(effect.showIcon(), "icon");
        assertFalse(effect.isAmbient());
        assertTrue(DarkVision.isDarkVision(effect));
    }

    @Test
    void otherNightVisionIsNotDarkVision() {
        assertFalse(DarkVision.isDarkVision(null));
        assertFalse(DarkVision.isDarkVision(new MobEffectInstance(MobEffects.NIGHT_VISION, 3600)), "a potion");
        assertFalse(DarkVision.isDarkVision(new MobEffectInstance(
                MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 1, false, false, false)), "amplifier");
        assertFalse(DarkVision.isDarkVision(new MobEffectInstance(
                MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0, false, true, true)), "particles and icon");
        assertFalse(DarkVision.isDarkVision(new MobEffectInstance(
                MobEffects.GLOWING, MobEffectInstance.INFINITE_DURATION, 0, false, false, false)), "another effect");
    }

    @Test
    void togglePayloadRoundTrips() {
        ByteBuf buffer = Unpooled.buffer();
        ToggleDarkVisionPayload.STREAM_CODEC.encode(buffer, ToggleDarkVisionPayload.INSTANCE);
        assertSame(ToggleDarkVisionPayload.INSTANCE, ToggleDarkVisionPayload.STREAM_CODEC.decode(buffer));
        assertFalse(buffer.isReadable(), "the payload carries no bytes");
    }
}
