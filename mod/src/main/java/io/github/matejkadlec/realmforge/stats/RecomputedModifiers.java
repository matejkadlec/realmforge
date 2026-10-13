package io.github.matejkadlec.realmforge.stats;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * Saved-but-recomputed attribute modifiers (architecture 8.2). A source (stat rules now; race, class and talents
 * later) owns the modifier ids its predicate accepts and rewrites that whole set from its data on every recompute.
 * The modifiers are permanent, so the entity saves them and they are back before its health loads; a recompute
 * touches only what changed and removes the owned ids it no longer produces.
 */
public final class RecomputedModifiers {
    private RecomputedModifiers() {
    }

    /**
     * Makes the permanent modifiers whose id {@code owned} accepts exactly {@code wanted}: removes the others, adds
     * the missing ones and replaces those whose amount or operation changed. A wanted modifier on an attribute the
     * entity does not have is skipped. Returns whether anything changed.
     */
    public static boolean replace(AttributeMap attributes, Predicate<Identifier> owned, List<StatBonus> wanted) {
        Map<Holder<Attribute>, Map<Identifier, AttributeModifier>> wantedByAttribute = new HashMap<>();
        for (StatBonus bonus : wanted) {
            wantedByAttribute.computeIfAbsent(bonus.attribute(), attribute -> new HashMap<>())
                    .put(bonus.modifier().id(), bonus.modifier());
        }

        boolean changed = false;
        for (AttributeInstance.Packed packed : attributes.pack()) {
            Map<Identifier, AttributeModifier> kept = wantedByAttribute.getOrDefault(packed.attribute(), Map.of());
            for (AttributeModifier modifier : packed.modifiers()) {
                if (owned.test(modifier.id()) && !kept.containsKey(modifier.id())) {
                    attributes.getInstance(packed.attribute()).removeModifier(modifier.id());
                    changed = true;
                }
            }
        }

        for (StatBonus bonus : wanted) {
            AttributeInstance instance = attributes.getInstance(bonus.attribute());
            if (instance == null) {
                continue;
            }
            AttributeModifier current = instance.getModifier(bonus.modifier().id());
            if (bonus.modifier().equals(current) && instance.getPermanentModifiers().contains(current)) {
                continue;
            }
            instance.addOrReplacePermanentModifier(bonus.modifier());
            changed = true;
        }
        return changed;
    }
}
