package io.github.matejkadlec.realmforge.compat.bettercombat;

import com.mojang.logging.LogUtils;
import io.github.matejkadlec.realmforge.combat.WeaponBlocks;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.DefaultDataComponentsBoundEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

/**
 * Puts every weapon Better Combat calls two-handed into Sword Blocking Mechanics' tag {@link WeaponBlocks#RAISABLE},
 * the weapons a player raises with right click to block (RLM-297, architecture 9.3), so a new two-handed weapon blocks
 * with no list to keep: ours (from their Better Combat weapon files), other mods' (their own files, or Better Combat's
 * fallback by item id) and vanilla's. A weapon whose right click already does something stays out, because its use
 * animation is not {@code NONE}: a spear keeps its charge (Matěj, 2026-10-22), a bow its draw.
 *
 * <p>Better Combat decides what is two-handed at run time, after the tags have loaded, so the tag cannot be data: the
 * core mod's data replaces Sword Blocking Mechanics' default (every sword) with an empty tag, and this class binds the
 * tag into the items' holders once Better Combat has its weapons: on the server when it starts (Better Combat loads
 * them as it is about to start) and after every reload of the data, which binds the tags anew, and on a client of
 * another server when it joins and after every data update: each reload binds the items' default components after
 * their tags and then posts {@code DefaultDataComponentsBoundEvent}, and a stack needs its item's components to be
 * read. Before the first binding nothing is bound. {@code ItemStack#is} reads a holder's own set of tags; the tag's
 * list of items stays empty. An access transformer opens {@code Holder.Reference#bindTags}. A Better Combat preset
 * set on a single stack ({@code bettercombat:weapon_preset_id}) does not count: the tag is per item. Runs only while
 * Better Combat is loaded (architecture 19.4); the core mod compiles against the pack's build.
 */
public final class TwoHandedWeapons {
    public static final String MOD_ID = "bettercombat";
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
     * Binds {@link WeaponBlocks#RAISABLE} to exactly the raisable items ({@link #raisable}), keeping every other tag
     * of every item; the items it holds, sorted. Does nothing while the items' default components are not bound yet.
     */
    public static List<Identifier> bind() {
        if (BuiltInRegistries.ITEM.listElements().anyMatch(holder -> !holder.areComponentsBound())) {
            return List.of();
        }
        TagKey<Item> tag = WeaponBlocks.RAISABLE;
        List<Holder.Reference<Item>> raisable = BuiltInRegistries.ITEM.listElements()
                .filter(holder -> raisable(holder.value()))
                .toList();
        Set<Holder.Reference<Item>> wanted = new HashSet<>(raisable);
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
        return raisable.stream().map(holder -> holder.key().identifier()).sorted().toList();
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
        WeaponAttributes attributes = WeaponRegistry.getAttributes(stack);
        return attributes != null && attributes.isTwoHanded() && stack.getUseAnimation() == ItemUseAnimation.NONE;
    }

    private static void logged(List<Identifier> raisable) {
        LOGGER.info("Two-handed weapons raised to block ({}): {}", raisable.size(), raisable);
    }
}
