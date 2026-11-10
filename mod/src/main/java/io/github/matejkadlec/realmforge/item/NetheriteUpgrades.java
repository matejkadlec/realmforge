package io.github.matejkadlec.realmforge.item;

import io.github.matejkadlec.realmforge.Realmforge;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;

/**
 * What a netherite upgrade does on its piece (RLM-566; architecture 8.2, 13): while the piece is worn, the bonus of its
 * archetype counts in its armor slot, added to the stack's own modifiers through {@link ItemAttributeModifierEvent}, so
 * vanilla applies and removes it with the equipment and NeoForge's tooltip lists it under the slot's heading; and the
 * piece's tooltip names the upgrade in its colour. The bonus comes from the registry entry the piece names, on the
 * server and on a client alike ({@link CommonHooks#resolveLookup}).
 */
public final class NetheriteUpgrades {
    /** Vanilla's netherite armor, whose pieces get an upgrade's melee bonus. */
    public static final TagKey<Item> MELEE_PIECES = TagKey.create(Registries.ITEM,
            Realmforge.id("netherite_upgradable/melee"));
    /** Our netherite robes, whose pieces get an upgrade's caster bonus. */
    public static final TagKey<Item> CASTER_PIECES = TagKey.create(Registries.ITEM,
            Realmforge.id("netherite_upgradable/caster"));

    private NetheriteUpgrades() {
    }

    /** Whether {@code stack} carries a netherite upgrade. */
    public static boolean isUpgraded(ItemStack stack) {
        return stack.has(ItemComponents.NETHERITE_UPGRADE.get());
    }

    /** The upgrade {@code stack} carries, if it carries one that exists. */
    public static Optional<NetheriteUpgrade> of(ItemStack stack) {
        ResourceKey<NetheriteUpgrade> key = stack.get(ItemComponents.NETHERITE_UPGRADE.get());
        if (key == null) {
            return Optional.empty();
        }
        HolderLookup.RegistryLookup<NetheriteUpgrade> upgrades =
                CommonHooks.resolveLookup(ItemRegistries.NETHERITE_UPGRADE);
        return upgrades == null ? Optional.empty() : upgrades.get(key).map(Holder::value);
    }

    /** The archetype of the piece {@code stack}, if it takes an upgrade. */
    public static Optional<NetheriteUpgrade.Archetype> archetype(ItemStack stack) {
        if (stack.is(MELEE_PIECES)) {
            return Optional.of(NetheriteUpgrade.Archetype.MELEE);
        }
        if (stack.is(CASTER_PIECES)) {
            return Optional.of(NetheriteUpgrade.Archetype.CASTER);
        }
        return Optional.empty();
    }

    /** The id of the {@code n}-th modifier of an upgrade's bonus on a piece in {@code slot}, unique per slot. */
    public static Identifier modifierId(EquipmentSlot slot, int n) {
        return Realmforge.id("netherite_upgrade/" + slot.getName() + "/" + n);
    }

    /** Adds the bonus of an upgraded piece to its modifiers, in the armor slot it is worn in. */
    public static void onItemAttributeModifiers(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        Optional<NetheriteUpgrade.Archetype> archetype = archetype(stack);
        if (equippable == null || archetype.isEmpty()) {
            return;
        }
        of(stack).ifPresent(upgrade -> {
            EquipmentSlot slot = equippable.slot();
            List<NetheriteUpgrade.Bonus> bonus = upgrade.bonus(archetype.get());
            for (int n = 0; n < bonus.size(); n++) {
                NetheriteUpgrade.Bonus modifier = bonus.get(n);
                event.addModifier(modifier.attribute(),
                        new AttributeModifier(modifierId(slot, n), modifier.amount(), modifier.operation()),
                        EquipmentSlotGroup.bySlot(slot));
            }
        });
    }

    /** The tooltip line of an upgraded piece: the upgrade's name in its colour, or none. */
    public static List<Component> tooltip(ItemStack stack) {
        return of(stack).<List<Component>>map(upgrade -> List.of(
                Component.translatable(upgrade.name()).withColor(upgrade.color()))).orElse(List.of());
    }
}
