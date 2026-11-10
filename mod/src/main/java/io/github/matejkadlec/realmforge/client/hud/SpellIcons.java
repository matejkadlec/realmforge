package io.github.matejkadlec.realmforge.client.hud;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.generated.GeneratedSpellIcons;
import io.github.matejkadlec.realmforge.spell.Spell;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;

/**
 * A spell's icon (A1, RLM-642): the GUI sprite {@code realmforge:spell/<path>}, 16 pixels square, which the pipeline
 * writes from the spell's approved art ({@code art/textures/spell/}, {@code generate/spells.py}). A spell without art
 * has none ({@link GeneratedSpellIcons}): a test spell, a mob's spell, a spell another data pack adds. Its slot shows
 * its initials instead, as every slot did before the icons.
 */
public final class SpellIcons {
    /** An icon's width and height, a texture's pixel for a pixel of the GUI. */
    public static final int SIZE = 16;
    private static final String SPRITE_PREFIX = "spell/";

    private SpellIcons() {
    }

    /** The sprite of {@code spell}'s icon, if it has one. */
    public static Optional<Identifier> sprite(ResourceKey<Spell> spell) {
        Identifier id = spell.identifier();
        return Realmforge.MOD_ID.equals(id.getNamespace()) && GeneratedSpellIcons.SPELLS.contains(id.getPath())
                ? Optional.of(id.withPrefix(SPRITE_PREFIX)) : Optional.empty();
    }

    /** The sprite of the icon of the spell {@code key} names, if it names one that has an icon. */
    public static Optional<Identifier> sprite(Optional<ResourceKey<Spell>> key) {
        return key.flatMap(SpellIcons::sprite);
    }

    /** Draws {@code sprite} with its top left corner at x, y, at {@code alpha} (0 to 255). */
    public static void draw(GuiGraphicsExtractor graphics, Identifier sprite, int x, int y, int alpha) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, SIZE, SIZE, ARGB.white(alpha));
    }
}
