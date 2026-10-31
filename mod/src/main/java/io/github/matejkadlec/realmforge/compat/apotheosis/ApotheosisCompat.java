package io.github.matejkadlec.realmforge.compat.apotheosis;

import dev.shadowsoffire.apotheosis.Apoth;
import dev.shadowsoffire.apotheosis.loot.LootCategory;
import dev.shadowsoffire.apothic_attributes.api.ALObjects;
import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.spell.SpellCaster;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Apotheosis's loot categories of ours (RLM-273, architecture 19.4, ADR 0038). Registered only when Apotheosis is
 * loaded ({@code CompatFeature}); the core mod compiles against the pack's Apotheosis, Apothic Attributes and Placebo.
 * <p>
 * Apotheosis files an item under the first category, by priority, whose test it passes, unless its data map
 * {@code apotheosis:loot_category_overrides} names one ({@code LootCategory#forItem}). Its {@code melee_weapon} takes
 * a sword or anything whose default modifiers give more than 1 attack damage in the main hand, our wands too (2 to 5),
 * so a wand would roll melee affixes. {@link #WAND} takes {@code #realmforge:wands} first: its priority is
 * Apotheosis's default, 1000, and {@code melee_weapon}'s is 2000. Its stats count in the main hand, as a wand's own
 * do. No affix lists the category yet (the caster affixes, RLM-557), so a wand never rolls a melee affix, and a wand
 * reforged now gets a rarity, sockets and durability only.
 */
public final class ApotheosisCompat {
    public static final String MOD_ID = "apotheosis";

    private static final DeferredRegister<LootCategory> LOOT_CATEGORIES =
            DeferredRegister.create(Apoth.BuiltInRegs.LOOT_CATEGORY.key(), Realmforge.MOD_ID);

    /** {@code realmforge:wand}: our wands, never Apotheosis's melee weapons. */
    public static final DeferredHolder<LootCategory, LootCategory> WAND = LOOT_CATEGORIES.register("wand",
            () -> new LootCategory(stack -> stack.is(SpellCaster.WANDS), ALObjects.EquipmentSlotGroups.MAINHAND));

    private ApotheosisCompat() {
    }

    /** Registers the categories. Called once from {@code CompatFeature} when Apotheosis is loaded. */
    public static void register(IEventBus modBus) {
        LOOT_CATEGORIES.register(modBus);
    }
}
