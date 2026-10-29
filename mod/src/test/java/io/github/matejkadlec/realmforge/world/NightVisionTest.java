package io.github.matejkadlec.realmforge.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matejkadlec.realmforge.world.net.ToggleNightVisionPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.junit.jupiter.api.Test;

class NightVisionTest {
    @Test
    void effectIsInfiniteAndHidden() {
        MobEffectInstance effect = NightVision.effect();
        assertTrue(effect.is(MobEffects.NIGHT_VISION));
        assertTrue(effect.isInfiniteDuration());
        assertFalse(effect.isVisible(), "particles");
        assertFalse(effect.showIcon(), "icon");
        assertFalse(effect.isAmbient());
        assertTrue(NightVision.isOurs(effect));
    }

    @Test
    void otherNightVisionIsNotOurs() {
        assertFalse(NightVision.isOurs(null));
        assertFalse(NightVision.isOurs(new MobEffectInstance(MobEffects.NIGHT_VISION, 3600)), "a potion");
        assertFalse(NightVision.isOurs(new MobEffectInstance(
                MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 1, false, false, false)), "amplifier");
        assertFalse(NightVision.isOurs(new MobEffectInstance(
                MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0, false, true, true)), "particles and icon");
        assertFalse(NightVision.isOurs(new MobEffectInstance(
                MobEffects.GLOWING, MobEffectInstance.INFINITE_DURATION, 0, false, false, false)), "another effect");
    }

    @Test
    void togglePayloadRoundTrips() {
        ByteBuf buffer = Unpooled.buffer();
        ToggleNightVisionPayload.STREAM_CODEC.encode(buffer, ToggleNightVisionPayload.INSTANCE);
        assertSame(ToggleNightVisionPayload.INSTANCE, ToggleNightVisionPayload.STREAM_CODEC.decode(buffer));
        assertFalse(buffer.isReadable(), "the payload carries no bytes");
    }
}
