package io.github.matejkadlec.realmforge.compat.apotheosis;

import dev.shadowsoffire.apotheosis.Apoth;
import dev.shadowsoffire.apotheosis.loot.LootCategory;
import dev.shadowsoffire.apothic_attributes.api.ALObjects;
import dev.shadowsoffire.apothic_attributes.modifiers.EntitySlotGroup;
import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.spell.SpellCaster;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Apotheosis's loot categories of ours (RLM-273, RLM-546, architecture 19.4, ADR 0038). Registered only when
 * Apotheosis is loaded ({@code CompatFeature}); the core mod compiles against the pack's Apotheosis, Apothic Attributes
 * and Placebo.
 * <p>
 * Apotheosis files an item under the first category, by priority, whose test it passes, unless its data map
 * {@code apotheosis:loot_category_overrides} names one ({@code LootCategory#forItem}). Its {@code melee_weapon} takes
 * a sword or anything whose default modifiers give more than 1 attack damage in the main hand, our wands too (2 to 5),
 * so a wand would roll melee affixes. {@link #WAND} takes {@code #realmforge:wands} first: its priority is
 * Apotheosis's default, 1000, and {@code melee_weapon}'s is 2000. Its stats count in the main hand, as a wand's own
 * do.
 * <p>
 * Its armor categories take any item whose {@code EQUIPPABLE} slot is an armor slot, so our robes would roll armor
 * affixes and take armor gem bonuses, against the caster rule of no armor. The four robe categories, one per armor
 * slot, take every armor piece of ours (our armor is the caster's) through the data map, which the pipeline writes
 * from each piece's gear kind, so their own test passes nothing and the order of the tests never matters.
 * <p>
 * No affix lists our categories yet (the caster affixes, RLM-557 and RLM-562), and their rarity overrides drop every
 * affix slot ({@code content/apotheosis_rarity_overrides/}), so a wand or a robe reforged now gets a rarity, sockets
 * and durability only, and no gem bonus of Apotheosis's names a robe category, so no gem fits a robe until RLM-548.
 */
public final class ApotheosisCompat {
    public static final String MOD_ID = "apotheosis";

    private static final DeferredRegister<LootCategory> LOOT_CATEGORIES =
            DeferredRegister.create(Apoth.BuiltInRegs.LOOT_CATEGORY.key(), Realmforge.MOD_ID);

    /** {@code realmforge:wand}: our wands, never Apotheosis's melee weapons. */
    public static final DeferredHolder<LootCategory, LootCategory> WAND = LOOT_CATEGORIES.register("wand",
            () -> new LootCategory(stack -> stack.is(SpellCaster.WANDS), ALObjects.EquipmentSlotGroups.MAINHAND));
    /** {@code realmforge:robe_head}: a caster's cap or hat, never Apotheosis's helmet. */
    public static final DeferredHolder<LootCategory, LootCategory> ROBE_HEAD =
            robe("head", ALObjects.EquipmentSlotGroups.HEAD);
    /** {@code realmforge:robe_chest}: a caster's robes, never Apotheosis's chestplate. */
    public static final DeferredHolder<LootCategory, LootCategory> ROBE_CHEST =
            robe("chest", ALObjects.EquipmentSlotGroups.CHEST);
    /** {@code realmforge:robe_legs}: a caster's pants, never Apotheosis's leggings. */
    public static final DeferredHolder<LootCategory, LootCategory> ROBE_LEGS =
            robe("legs", ALObjects.EquipmentSlotGroups.LEGS);
    /** {@code realmforge:robe_feet}: a caster's sandals or boots, never Apotheosis's boots. */
    public static final DeferredHolder<LootCategory, LootCategory> ROBE_FEET =
            robe("feet", ALObjects.EquipmentSlotGroups.FEET);

    private ApotheosisCompat() {
    }

    /** Registers the categories. Called once from {@code CompatFeature} when Apotheosis is loaded. */
    public static void register(IEventBus modBus) {
        LOOT_CATEGORIES.register(modBus);
    }

    /** A robe category of the armor slot {@code kind}: only the data map files an item under it. */
    private static DeferredHolder<LootCategory, LootCategory> robe(String kind, EntitySlotGroup slots) {
        return LOOT_CATEGORIES.register("robe_" + kind, () -> new LootCategory(stack -> false, slots));
    }
}
