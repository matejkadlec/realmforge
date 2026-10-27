package io.github.matejkadlec.realmforge.item;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import java.util.HashSet;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Two-handed gear (RLM-172; Matěj, 2026-10-26): a wand takes both hands, so a caster never holds a shield with it.
 * While an item of {@link #TWO_HANDED} is in a player's main hand, every shield ({@code c:tools/shield}) is locked, and
 * it stays locked for {@link #LOCK_TICKS} after the wand leaves the hand, even when a shield takes its place. The lock
 * shows as vanilla's item cooldown on every shield, which also refuses blocking with one. A shield in either hand while
 * the lock lasts drops to the ground, with "You can't use this now" in the action bar: one in the off hand when a wand
 * comes into the main hand, and one taken in hand during the lock.
 *
 * <p>The lock is this class's own, per player, not the shields' cooldown: an axe that disables a shield puts it on the
 * same vanilla cooldown, and such a shield stays in hand. Item cooldowns are not saved, and neither is the lock: it
 * ends with a logout or a death, as theirs do. This is not Better Combat's two-handed weapons, which a player raises with
 * right click to block ({@code combat/WeaponBlocks}). Logical server only.
 */
public final class TwoHandedGear {
    /** The item tag of two-handed gear, which the pipeline writes from {@code content/items/} ({@code gear.two_handed}). */
    public static final TagKey<Item> TWO_HANDED = TagKey.create(Registries.ITEM, Realmforge.id("two_handed"));
    /** How long shields stay locked after a two-handed item leaves the main hand: 3 minutes (Matěj, 2026-10-26). */
    public static final int LOCK_TICKS = 3 * 60 * 20;
    /** While a two-handed item stays in the main hand, the lock starts again once a second, so it never runs out. */
    private static final int REFRESH_TICKS = 20;

    /** The game time each locked player's lock ends at. */
    private static final Map<Player, Long> LOCKED_UNTIL = new WeakHashMap<>();

    private TwoHandedGear() {
    }

    /**
     * The game time the player's shield lock ends at, or empty when their shields are not locked: by a two-handed item
     * in the main hand (the end moves on every second) or by one that left it less than {@link #LOCK_TICKS} ago.
     */
    public static OptionalLong lockEnd(Player player) {
        Long until = LOCKED_UNTIL.get(player);
        return until != null && player.level().getGameTime() < until ? OptionalLong.of(until) : OptionalLong.empty();
    }

    /**
     * End of a player's tick: a two-handed item in the main hand (re)starts the lock, and while it lasts a shield in
     * either hand drops.
     */
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive()) {
            return;
        }
        long now = player.level().getGameTime();
        if (player.getMainHandItem().is(TWO_HANDED)) {
            lock(player, now);
        }
        Long until = LOCKED_UNTIL.get(player);
        if (until == null) {
            return;
        }
        if (now >= until) {
            LOCKED_UNTIL.remove(player);
            return;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.is(Tags.Items.TOOLS_SHIELD)) {
                player.setItemInHand(hand, ItemStack.EMPTY);
                player.drop(held, false, true);
                player.sendOverlayMessage(Component.translatable(GeneratedTexts.MESSAGE_SHIELD_LOCKED)
                        .withStyle(ChatFormatting.RED));
            }
        }
    }

    /**
     * Locks the player's shields for {@link #LOCK_TICKS} from now, unless a lock started less than a second ago: the
     * cooldown of every shield item, and of any shield in hand with a cooldown group of its own, starts again.
     */
    private static void lock(ServerPlayer player, long now) {
        Long until = LOCKED_UNTIL.get(player);
        if (until != null && until - now > LOCK_TICKS - REFRESH_TICKS) {
            return;
        }
        LOCKED_UNTIL.put(player, now + LOCK_TICKS);
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
            cooldowns.addCooldown(group, LOCK_TICKS);
        }
    }
}
