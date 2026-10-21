package io.github.matejkadlec.realmforge.client.compat;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import io.github.matejkadlec.realmforge.mob.MobLevels;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import java.util.Optional;
import java.util.OptionalInt;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * The level before a mob's name, in Neat's card above the mob and in Jade's tooltip (RLM-338, Matěj, 2026-10-20):
 * {@code Lv. 20 Zombie}, one function for both. The whole {@code Lv. 20} takes its colour from the mob's level against
 * the viewing player's, as WoW colours a level ({@link #color}); a mob {@value #SKULL_DIFFERENCE} or more levels above
 * shows our skull glyph instead of the number ({@link #SKULL}, RLM-340), in blood red and bold. The name stays white
 * and upright, a custom name too (Neat draws those in italics). {@code Lv. %1$s} is the XP bar's text
 * ({@link GeneratedTexts#GUI_XP_BAR_LEVEL}), so a mob's level reads like the player's own above the hotbar. Only a mob
 * that has a level shows one, read from its synced {@code realmforge:level} ({@link MobLevels#level}); the level is
 * never written into a name, so death messages, name tags and other mods see the mob's own.
 */
public final class MobLevelLabel {
    /** From this many levels above the player, the label shows the skull instead of the number. */
    static final int SKULL_DIFFERENCE = 5;
    static final int GRAY = 0xAAAAAA;
    static final int GREEN = 0x55FF55;
    static final int YELLOW = 0xFFFF55;
    /** Orange of our own, not Minecraft's gold ({@code #FFAA00}). */
    static final int ORANGE = 0xFF9933;
    static final int RED = 0xFF5555;
    /** More saturated than {@link #RED}, and bright enough on Neat's dark card. */
    static final int SKULL_RED = 0xE02020;
    /** Our symbols font, which {@code rf gen} writes ({@code tools/realmforge_tools/generate/fonts.py}). */
    public static final FontDescription SYMBOLS = new FontDescription.Resource(Realmforge.id("symbols"));
    /**
     * The skull, a white skull with red eyes on the default font's grid (RLM-340), at a private-use code point of
     * {@link #SYMBOLS}. Drawn in white, as a bitmap glyph takes the text colour as a tint.
     */
    public static final String SKULL = "";

    private MobLevelLabel() {
    }

    /**
     * The colour of {@code Lv. N} for a mob {@code difference} levels above the player (below when negative), as
     * {@code 0xRRGGBB}: gray from 4 below, green at 3 below, yellow within 2, orange at 3 above, red at 4 above, and
     * the skull's blood red from {@value #SKULL_DIFFERENCE} above.
     */
    static int color(int difference) {
        if (difference <= -4) {
            return GRAY;
        }
        if (difference == -3) {
            return GREEN;
        }
        if (difference <= 2) {
            return YELLOW;
        }
        if (difference == 3) {
            return ORANGE;
        }
        return difference == 4 ? RED : SKULL_RED;
    }

    /** Whether a mob {@code difference} levels above the player shows the skull instead of its level. */
    static boolean skull(int difference) {
        return difference >= SKULL_DIFFERENCE;
    }

    /**
     * {@code Lv. N}, or {@code Lv. } and the skull, for a mob of {@code mobLevel} seen by a player of
     * {@code playerLevel}.
     */
    public static MutableComponent level(int mobLevel, int playerLevel) {
        int difference = mobLevel - playerLevel;
        boolean skull = skull(difference);
        Object shown = skull
                ? Component.literal(SKULL).withStyle(style -> style.withFont(SYMBOLS).withColor(ChatFormatting.WHITE)
                        .withBold(false))
                : mobLevel;
        return Component.translatable(GeneratedTexts.GUI_XP_BAR_LEVEL, shown)
                .withStyle(style -> style.withColor(TextColor.fromRgb(color(difference))).withBold(skull));
    }

    /** {@code Lv. N Name}: the level, a space, and {@code name} in white, upright and not bold. */
    public static MutableComponent line(int mobLevel, int playerLevel, Component name) {
        return Component.empty()
                .append(level(mobLevel, playerLevel))
                .append(" ")
                .append(name.copy().withStyle(style -> style.withColor(ChatFormatting.WHITE).withItalic(false)
                        .withBold(false)));
    }

    /**
     * The line {@code viewer} sees for {@code entity}: empty unless it is a mob with a level. A viewer without a level
     * counts as level {@value EntityLevel#MIN}.
     */
    public static Optional<Component> forMob(Entity entity, @Nullable Player viewer) {
        if (!(entity instanceof Mob mob)) {
            return Optional.empty();
        }
        OptionalInt level = MobLevels.level(mob);
        if (level.isEmpty()) {
            return Optional.empty();
        }
        int playerLevel = viewer != null ? MobLevels.level(viewer).orElse(EntityLevel.MIN) : EntityLevel.MIN;
        return Optional.of(line(level.getAsInt(), playerLevel, mob.getName()));
    }
}
