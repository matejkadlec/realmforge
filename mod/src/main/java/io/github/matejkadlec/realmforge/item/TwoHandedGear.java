package io.github.matejkadlec.realmforge.item;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Two-handed gear (RLM-172; Matěj, 2026-10-26): a wand takes both hands, so a caster never holds a shield with it.
 * While an item of {@link #TWO_HANDED} is in a player's main hand, every shield ({@code c:tools/shield}) is locked, and
 * it stays locked for {@link #LOCK_TICKS} after the wand leaves the hand, even when a shield takes its place. The lock
 * shows as vanilla's item cooldown on every shield, which also refuses blocking with one. A shield in either hand while
 * the lock lasts goes into the bag, with "You can't use this now" in the action bar: one in the off hand when a wand
 * comes into the main hand, and one taken in hand during the lock. It takes a free slot of the main inventory, never
 * the hotbar, so it does not come back into a hand by itself; with none free it drops at the player's feet, a plain
 * drop rather than a throw (RLM-461; Matěj, 2026-10-28), and while the lock lasts it is not picked up into the
 * selected slot, which would put it in the main hand to drop again.
 *
 * <p>The lock is this class's own, per player, not the shields' cooldown: an axe that disables a shield puts it on the
 * same vanilla cooldown, and such a shield stays in hand. Item cooldowns are not saved, and neither is the lock: it
 * ends with a logout or a death, as theirs do. It is kept by the player's UUID, because a respawn builds a new player;
 * a return from the End, which builds one too, keeps it. A client rebuilds its player at every dimension change and
 * forgets its cooldowns then, so the lock is put on the shields' cooldown again. This is not Better Combat's two-handed
 * weapons, which a player raises with right click to block ({@code combat/WeaponBlocks}). Logical server only.
 */
public final class TwoHandedGear {
    /** The item tag of two-handed gear, which the pipeline writes from {@code content/items/} ({@code gear.two_handed}). */
    public static final TagKey<Item> TWO_HANDED = TagKey.create(Registries.ITEM, Realmforge.id("two_handed"));
    /** How long shields stay locked after a two-handed item leaves the main hand: 3 minutes (Matěj, 2026-10-26). */
    public static final int LOCK_TICKS = 3 * 60 * 20;
    /** While a two-handed item stays in the main hand, the lock starts again once a second, so it never runs out. */
    private static final int REFRESH_TICKS = 20;

    /**
     * The game time each locked player's lock ends at, by UUID: a respawned player is a new object, so a weak map by
     * player would hand the dead one's lock on until the garbage collector ran (RLM-461).
     */
    private static final Map<UUID, Long> LOCKED_UNTIL = new HashMap<>();

    private TwoHandedGear() {
    }

    /**
     * The game time the player's shield lock ends at, or empty when their shields are not locked: by a two-handed item
     * in the main hand (the end moves on every second) or by one that left it less than {@link #LOCK_TICKS} ago.
     */
    public static OptionalLong lockEnd(Player player) {
        Long until = LOCKED_UNTIL.get(player.getUUID());
        return until != null && player.level().getGameTime() < until ? OptionalLong.of(until) : OptionalLong.empty();
    }

    /**
     * End of a player's tick: a two-handed item in the main hand (re)starts the lock, and while it lasts a shield in
     * either hand goes into the bag.
     */
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive()) {
            return;
        }
        long now = player.level().getGameTime();
        if (player.getMainHandItem().is(TWO_HANDED)) {
            lock(player, now);
        }
        Long until = LOCKED_UNTIL.get(player.getUUID());
        if (until == null) {
            return;
        }
        if (now >= until) {
            LOCKED_UNTIL.remove(player.getUUID());
            return;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.is(Tags.Items.TOOLS_SHIELD)) {
                player.setItemInHand(hand, ItemStack.EMPTY);
                stow(player, held);
                player.sendOverlayMessage(Component.translatable(GeneratedTexts.MESSAGE_SHIELD_LOCKED)
                        .withStyle(ChatFormatting.RED));
            }
        }
    }

    /** A death ends the lock; a return from the End, which builds a new player too, keeps it and shows it again. */
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.isEndConquered()) {
            showLock(player);
        } else {
            LOCKED_UNTIL.remove(player.getUUID());
        }
    }

    /** A logout ends the lock. */
    static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LOCKED_UNTIL.remove(event.getEntity().getUUID());
    }

    /**
     * While the lock lasts, a shield on the ground is not picked up into the selected slot: a shield does not stack, so
     * a pickup takes the first free slot ({@code Inventory#getFreeSlot}), and with the bag full that is often the
     * selected one, from which it would drop again at once, every time its pickup delay ran out. Into another free
     * slot it is picked up as usual.
     */
    static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        Player player = event.getPlayer();
        Inventory inventory = player.getInventory();
        if (event.getItemEntity().getItem().is(Tags.Items.TOOLS_SHIELD) && lockEnd(player).isPresent()
                && inventory.getFreeSlot() == inventory.getSelectedSlot()) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    /** The client built a new player, without cooldowns: the lock goes back on its shields. */
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            showLock(player);
        }
    }

    /**
     * Puts {@code shield} into a free slot of the main inventory, never the hotbar, so it does not come back into a
     * hand by itself. With none free it drops at the player's feet, as a plain drop: not counted as the player's drop,
     * and not thrown ahead, which a drop that is not random does along the look, so its motion is taken away.
     */
    private static void stow(ServerPlayer player, ItemStack shield) {
        Inventory inventory = player.getInventory();
        for (int slot = Inventory.SELECTION_SIZE; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                inventory.setItem(slot, shield);
                return;
            }
        }
        ItemEntity dropped = player.drop(shield, false, false);
        if (dropped != null) {
            dropped.setDeltaMovement(Vec3.ZERO);
        }
    }

    /**
     * Locks the player's shields for {@link #LOCK_TICKS} from now, unless a lock started less than a second ago: the
     * cooldown of every shield item, and of any shield in hand with a cooldown group of its own, starts again.
     */
    private static void lock(ServerPlayer player, long now) {
        Long until = LOCKED_UNTIL.get(player.getUUID());
        if (until != null && until - now > LOCK_TICKS - REFRESH_TICKS) {
            return;
        }
        LOCKED_UNTIL.put(player.getUUID(), now + LOCK_TICKS);
        coolDownShields(player, LOCK_TICKS);
    }

    /** Puts what is left of the player's lock on every shield's cooldown again, if it is locked. */
    private static void showLock(ServerPlayer player) {
        OptionalLong until = lockEnd(player);
        if (until.isPresent()) {
            coolDownShields(player, (int) (until.getAsLong() - player.level().getGameTime()));
        }
    }

    /**
     * Starts the cooldown of every shield item, and of any shield in hand with a cooldown group of its own, for
     * {@code ticks}.
     */
    private static void coolDownShields(ServerPlayer player, int ticks) {
        ItemCooldowns cooldowns = player.getCooldowns();
        Set<Identifier> groups = new HashSet<>();
        for (Holder<Item> shield : BuiltInRegistries.ITEM.getTagOrEmpty(Tags.Items.TOOLS_SHIELD)) {
            groups.add(cooldowns.getCooldownGroup(new ItemStack(shield)));
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.is(Tags.Items.TOOLS_SHIELD)) {
                groups.add(cooldowns.getCooldownGroup(held));
            }
        }
        for (Identifier group : groups) {
            cooldowns.addCooldown(group, ticks);
        }
    }
}
