package io.github.matejkadlec.realmforge.client.compat.apotheosis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class PotionCharmTooltipTest {
    /** Matěj, 2026-10-29 (RLM-272): a potion's tooltip no longer names the potion charm, which no longer exists. */
    @Test
    void onlyThePotionCharmLineIsDropped() {
        Component name = Component.literal("Potion of the Turtle Master");
        Component effect = Component.translatable("effect.minecraft.slowness");
        Component charm = Component.translatable(PotionCharmTooltip.BLACKLISTED_POTION)
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
        Component other = Component.translatable("misc.apotheosis.something_else");
        List<Component> lines = new ArrayList<>(List.of(name, effect, charm, other));

        PotionCharmTooltip.removeCharmLine(lines);

        assertEquals(List.of(name, effect, other), lines);
    }
}
