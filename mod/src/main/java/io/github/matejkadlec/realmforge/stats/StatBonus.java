package io.github.matejkadlec.realmforge.stats;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/** An attribute modifier a stat source gives (a stat rule now; race, class and talents later), and its attribute. */
public record StatBonus(Holder<Attribute> attribute, AttributeModifier modifier) {
}
