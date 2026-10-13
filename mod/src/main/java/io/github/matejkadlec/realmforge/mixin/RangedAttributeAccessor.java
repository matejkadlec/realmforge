package io.github.matejkadlec.realmforge.mixin;

import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets {@code stats/AttributeCaps} raise vanilla's attribute caps (architecture 8.4). A {@link RangedAttribute}'s
 * upper limit is a private final field set in its constructor, and neither vanilla nor NeoForge offers a way to change
 * it, hence a mixin (architecture 19.3); {@link Mutable} lifts the final. Tested by {@code AttributeCapsTest} and the
 * QA mod's {@code StatsGameTests}, which fail at vanilla's caps without it.
 */
@Mixin(RangedAttribute.class)
public interface RangedAttributeAccessor {
    /** Sets the attribute's upper limit, to which its value is clamped ({@code RangedAttribute#sanitizeValue}). */
    @Accessor("maxValue")
    @Mutable
    void realmforge$setMaxValue(double maxValue);
}
