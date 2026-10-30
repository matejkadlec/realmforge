package io.github.matejkadlec.realmforge.compat.bettercombat;

import com.mojang.logging.LogUtils;
import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.combat.WeaponBlocks;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.DefaultDataComponentsBoundEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

/**
 * Puts every weapon Better Combat calls two-handed into Sword Blocking Mechanics' tag {@link WeaponBlocks#RAISABLE},
 * the weapons a player raises with right click to block (RLM-297, architecture 9.3), so a new two-handed weapon blocks
 * with no list to keep: ours (from their Better Combat weapon files), other mods' (their own files, or Better Combat's
 * fallback by item id) and vanilla's. A weapon whose right click already does something stays out, because its use
 * animation is not {@code NONE}: a spear keeps its charge (Matěj, 2026-10-22), a bow its draw. Every two-handed weapon,
 * spears included, also goes into {@link #TWO_HANDED_WEAPONS}, the weapons of Two-Handed Weapon Specialization
 * (RLM-541), bound the same way.
 *
 * <p>Better Combat decides what is two-handed at run time, after the tags have loaded, so the tag cannot be data: the
 * core mod's data replaces Sword Blocking Mechanics' default (every sword) with an empty tag, and this class binds the
 * tag into the items' holders once Better Combat has its weapons: on the server when it starts (Better Combat loads
 * them as it is about to start) and after every reload of the data, which binds the tags anew and then the items'
 * default components ({@code DefaultDataComponentsBoundEvent}; a stack needs its item's components to be read). A
 * client of another server binds it when its configuration binds the components, when it joins, and after every tag
 * update its server sends ({@link #onTagsReceived}): a {@code /reload} there re-applies the server's tags, without
 * this binding, and posts no {@code DefaultDataComponentsBoundEvent} on the client. Before the first binding nothing
 * is bound. {@code ItemStack#is} reads a holder's own set of tags; the tag's list of items stays empty. An access
 * transformer opens {@code Holder.Reference#bindTags}. A Better Combat preset set on a single stack
 * ({@code bettercombat:weapon_preset_id}) does not count: the tag is per item. Runs only while Better Combat is loaded
 * (architecture 19.4); the core mod compiles against the pack's build.
 */
public final class TwoHandedWeapons {
    public static final String MOD_ID = "bettercombat";
    /**
     * Every weapon Better Combat calls two-handed, spears included (RLM-541): Two-Handed Weapon Specialization applies
     * while one is in the main hand. Bound with {@link WeaponBlocks#RAISABLE}, the same way; the core mod's data
     * writes it empty.
     */
    public static final TagKey<Item> TWO_HANDED_WEAPONS = TagKey.create(Registries.ITEM,
            Realmforge.id("two_handed_weapons"));
    private static final Logger LOGGER = LogUtils.getLogger();

    private TwoHandedWeapons() {
    }

    /** Called once from {@code CompatFeature} while Better Combat is loaded. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(ServerStartingEvent.class, event -> logged(bind()));
        NeoForge.EVENT_BUS.addListener(DefaultDataComponentsBoundEvent.class, event -> {
            if (event.shouldUpdateStaticData()) {
                bind();
            }
        });
    }

    /**
     * A client's tag update from a server it is connected to over the network, such as after a {@code /reload} there:
     * the server's tags replace the items' own, so the tag is bound again. Over an integrated server's connection the
     * client shares the server's items, and the server binds them. Registered by the client
     * ({@code client/compat/bettercombat/TwoHandedWeaponsClient}).
     */
    public static void onTagsReceived(TagsUpdatedEvent.ClientPacketReceived event) {
        if (event.shouldUpdateStaticData()) {
            bind();
        }
    }

    /**
     * Binds {@link WeaponBlocks#RAISABLE} to exactly the raisable items ({@link #raisable}) and
     * {@link #TWO_HANDED_WEAPONS} to every two-handed one ({@link #twoHanded}), keeping every other tag of every item;
     * the raisable items, sorted. Does nothing while the items' default components are not bound yet.
     */
    public static List<Identifier> bind() {
        if (BuiltInRegistries.ITEM.listElements().anyMatch(holder -> !holder.areComponentsBound())) {
            return List.of();
        }
        bind(TWO_HANDED_WEAPONS, TwoHandedWeapons::twoHanded);
        return bind(WeaponBlocks.RAISABLE, TwoHandedWeapons::raisable);
    }

    /** Binds {@code tag} to exactly the items {@code member} accepts; those items, sorted. */
    private static List<Identifier> bind(TagKey<Item> tag, Predicate<Item> member) {
        List<Holder.Reference<Item>> members = BuiltInRegistries.ITEM.listElements()
                .filter(holder -> member.test(holder.value()))
                .toList();
        Set<Holder.Reference<Item>> wanted = new HashSet<>(members);
        BuiltInRegistries.ITEM.listElements().forEach(holder -> {
            boolean want = wanted.contains(holder);
            if (holder.is(tag) != want) {
                Set<TagKey<Item>> tags = new HashSet<>();
                holder.tags().filter(other -> !other.equals(tag)).forEach(tags::add);
                if (want) {
                    tags.add(tag);
                }
                holder.bindTags(tags);
            }
        });
        return members.stream().map(holder -> holder.key().identifier()).sorted().toList();
    }

    /** Whether Better Combat calls the item two-handed (RLM-541: Two-Handed Weapon Specialization's weapons). */
    public static boolean twoHanded(Item item) {
        ItemStack stack = new ItemStack(item);
        if (stack.isEmpty()) {
            return false;
        }
        WeaponAttributes attributes = WeaponRegistry.getAttributes(stack);
        return attributes != null && attributes.isTwoHanded();
    }

    /**
     * Whether a player raises the item to block: Better Combat calls it two-handed, and its right click does nothing
     * else (its use animation is {@code NONE}).
     */
    public static boolean raisable(Item item) {
        ItemStack stack = new ItemStack(item);
        if (stack.isEmpty()) {
            return false;
        }
        return twoHanded(item) && stack.getUseAnimation() == ItemUseAnimation.NONE;
    }

    private static void logged(List<Identifier> raisable) {
        LOGGER.info("Two-handed weapons raised to block ({}): {}", raisable.size(), raisable);
    }
}
