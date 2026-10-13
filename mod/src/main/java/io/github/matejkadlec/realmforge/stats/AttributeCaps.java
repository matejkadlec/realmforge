package io.github.matejkadlec.realmforge.stats;

import io.github.matejkadlec.realmforge.mixin.RangedAttributeAccessor;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

/**
 * The upper limits of attributes (architecture 8.4). An attribute's value is clamped to its range
 * ({@code Attribute#sanitizeValue}), and vanilla's caps for armor (30), armor toughness (20), max health (1024) and
 * attack damage (2048) are far below the numbers Realmforge's gear, levels and bosses reach. {@link #raise} lifts them
 * to the caps in {@link #RAISED} through the mixin accessor {@link RangedAttributeAccessor}; our own stats take their
 * caps from {@code content/stats/}. {@link #max} reads the cap of any attribute, so code that needs one (a stat with a
 * hard cap, a tooltip) asks here instead of copying a number.
 */
public final class AttributeCaps {
    /**
     * The cap of every raised attribute: a limit against broken numbers, far above anything the game grants (Vitality
     * gives 1 health a point), not a balance number.
     */
    public static final double RAISED_MAX = 100_000;

    /** A vanilla attribute and the cap {@link #raise} gives it. */
    public record Raised(Holder<Attribute> attribute, double max) {
    }

    /** The vanilla attributes whose caps Realmforge raises, with their caps. */
    public static final List<Raised> RAISED = List.of(
            new Raised(Attributes.ARMOR, RAISED_MAX),
            new Raised(Attributes.ARMOR_TOUGHNESS, RAISED_MAX),
            new Raised(Attributes.MAX_HEALTH, RAISED_MAX),
            new Raised(Attributes.ATTACK_DAMAGE, RAISED_MAX));

    private AttributeCaps() {
    }

    /**
     * Raises the caps in {@link #RAISED}, never lowering one that another mod raised further. Called once from
     * {@code StatsFeature} while the mod is constructed: vanilla registered its attributes before any mod, and no
     * entity exists yet. It runs on the client and on the server alike, as each clamps attribute values itself.
     */
    static void raise() {
        for (Raised raised : RAISED) {
            RangedAttribute attribute = (RangedAttribute) raised.attribute().value();
            if (attribute.getMaxValue() < raised.max()) {
                ((RangedAttributeAccessor) attribute).realmforge$setMaxValue(raised.max());
            }
        }
    }

    /**
     * The highest value the attribute can take, read from the attribute itself, so it is the cap the game applies: a
     * {@code RangedAttribute}'s maximum (vanilla's raised ones, our stats, a share as a fraction), 1 for a NeoForge
     * {@code BooleanAttribute}, infinity for an attribute without a limit.
     */
    public static double max(Holder<Attribute> attribute) {
        return attribute.value().sanitizeValue(Double.POSITIVE_INFINITY);
    }
}
