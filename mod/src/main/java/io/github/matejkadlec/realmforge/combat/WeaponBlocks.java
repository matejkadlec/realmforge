package io.github.matejkadlec.realmforge.combat;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Blocking with a two-handed weapon (RLM-297, architecture 9.3; Matěj, 2026-10-22). Sword Blocking Mechanics raises
 * the weapon while the player holds right click (its use action, poses and animations) for the items in its tag
 * {@link #RAISABLE}, which {@code compat/bettercombat} fills with Better Combat's two-handed weapons; the pack's config
 * switches its own parry and damage reduction off, and these rules ({@link CombatConfig}) decide instead:
 *
 * <ul>
 *   <li>a hit from the covered arc in front of the player within the first {@code fullBlockTicks} after raising the
 *   weapon is blocked in full, a later one by {@code blockedShare} (before armor and resistances, which take their
 *   share of the rest);</li>
 *   <li>after any blocked hit the weapon goes down at the end of the tick, and it cannot be raised again for
 *   {@code cooldownTicks}, as after lowering it by hand: an item cooldown, which vanilla shows on the hotbar and which
 *   refuses the right click on both sides, so holding or spamming right click never chains full blocks.</li>
 * </ul>
 *
 * Like Sword Blocking Mechanics, a raised weapon never blocks damage that bypasses armor or has no source position
 * (magic, falls, drowning) or a piercing arrow. Nor does it spend itself on a hit that the player's hurt cooldown
 * drops anyway (RLM-387). Logical server only.
 */
public final class WeaponBlocks {
    /** Sword Blocking Mechanics' item tag: the items a player raises with right click to block. */
    public static final TagKey<Item> RAISABLE = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath("swordblockingmechanics", "can_perform_sword_blocking"));

    /** The weapon each player had raised at the end of their last tick. */
    private static final Map<Player, ItemStack> RAISED = new WeakHashMap<>();
    /**
     * The use duration each player's raised weapon started with: Sword Blocking Mechanics sets it when the use starts,
     * and the item's own (0, which {@code LivingEntity#getTicksUsingItem} reads) does not know it.
     */
    private static final Map<Player, Integer> RAISE_DURATION = new WeakHashMap<>();
    /** The players whose weapon goes down at the end of this server tick, after a blocked hit. */
    private static final Set<Player> TO_LOWER = Collections.newSetFromMap(new WeakHashMap<>());

    private WeaponBlocks() {
    }

    /**
     * The rules of a block.
     *
     * @param fullBlockTicks how long after raising the weapon a hit is blocked in full
     * @param blockedShare the share of a later hit the weapon blocks
     * @param cooldownTicks how long the weapon cannot be raised again after a blocked hit or after lowering it
     * @param arcDegrees the arc in front of the player the weapon covers
     */
    public record Rules(int fullBlockTicks, double blockedShare, int cooldownTicks, double arcDegrees) {
        /** What a hit at {@code amount} deals to a player who raised the weapon {@code raisedTicks} ago. */
        public float blocked(float amount, int raisedTicks) {
            return raisedTicks < fullBlockTicks ? 0 : (float) (amount * (1 - blockedShare));
        }
    }

    /** Whether the entity is a player holding up a weapon of {@link #RAISABLE}. */
    public static boolean isRaised(LivingEntity entity) {
        return entity instanceof Player && entity.isUsingItem() && entity.getUseItem().is(RAISABLE);
    }

    /**
     * Blocks a hit on a player with a raised weapon, from {@link DamagePipeline} after the dodge: cancels a hit
     * blocked in full, lowers a partly blocked one, and puts the weapon on its cooldown. A hit that the player's open
     * hurt cooldown window drops anyway ({@link DamagePipeline#droppedByCooldown}) is left to it (RLM-387): the
     * weapon stays raised and off its cooldown, without a sound, for the next real hit. Whether it cancelled the hit.
     */
    static boolean block(LivingIncomingDamageEvent event, ServerLevel level) {
        if (!(event.getEntity() instanceof Player player) || !isRaised(player) || event.getAmount() <= 0
                || DamagePipeline.droppedByCooldown(player, event.getSource(), event.getAmount())) {
            return false;
        }
        Rules rules = CombatConfig.blockingRules();
        if (!blockable(player, event.getSource(), rules.arcDegrees())) {
            return false;
        }
        float left = rules.blocked(event.getAmount(), raisedTicks(player));
        player.getCooldowns().addCooldown(player.getUseItem(), rules.cooldownTicks());
        TO_LOWER.add(player);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK,
                player.getSoundSource(), 1.0F, 0.8F + level.getRandom().nextFloat() * 0.4F);
        if (left <= 0) {
            event.setCanceled(true);
            return true;
        }
        event.setAmount(left);
        return false;
    }

    /** How many of the player's ticks ago the raised weapon went up. */
    static int raisedTicks(Player player) {
        int remaining = player.getUseItemRemainingTicks();
        return RAISE_DURATION.getOrDefault(player, remaining) - remaining;
    }

    /** Whether a raised weapon can block the hit: one from the covered arc, with a source position, not magic. */
    static boolean blockable(Player player, DamageSource source, double arcDegrees) {
        if (source.is(DamageTypeTags.BYPASSES_ARMOR)
                || source.getDirectEntity() instanceof AbstractArrow arrow && arrow.getPierceLevel() > 0) {
            return false;
        }
        Vec3 from = source.getSourcePosition();
        if (from == null) {
            return false;
        }
        Vec3 look = player.getViewVector(1.0F);
        return covers(look.x, look.z, from.x - player.getX(), from.z - player.getZ(), arcDegrees);
    }

    /**
     * Whether the horizontal direction {@code (towardX, towardZ)} lies within {@code arcDegrees} centred on the
     * horizontal look direction {@code (lookX, lookZ)}; a hit from straight above or below, or a player looking
     * straight up or down, has no horizontal direction, and only the full circle covers it. An arc of 0 covers
     * nothing.
     */
    public static boolean covers(double lookX, double lookZ, double towardX, double towardZ, double arcDegrees) {
        if (arcDegrees >= 360) {
            return true;
        }
        if (arcDegrees <= 0) {
            return false;
        }
        double lookLength = Math.hypot(lookX, lookZ);
        double towardLength = Math.hypot(towardX, towardZ);
        if (lookLength < 1e-6 || towardLength < 1e-6) {
            return false;
        }
        double cos = (lookX * towardX + lookZ * towardZ) / (lookLength * towardLength);
        return cos >= Math.cos(Math.toRadians(arcDegrees / 2)) - 1e-9;
    }

    /** End of a server tick: lowers the weapons that blocked a hit during it. */
    static void onServerTick(ServerTickEvent.Post event) {
        for (Player player : TO_LOWER) {
            if (isRaised(player)) {
                player.stopUsingItem();
            }
        }
        TO_LOWER.clear();
    }

    /**
     * End of a player's tick: a weapon lowered since the last one, by a blocked hit, by hand or by switching items,
     * goes on its cooldown, so the next block always starts after it.
     */
    static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        ItemStack before = RAISED.remove(player);
        if (isRaised(player)) {
            RAISED.put(player, player.getUseItem());
        } else if (before != null) {
            cooldown(player, before);
        }
    }

    /** Last of the listeners, after Sword Blocking Mechanics set the use's duration: remembers it for a raise. */
    static void onUseStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide()
                && event.getItem().is(RAISABLE)) {
            RAISE_DURATION.put(player, event.getDuration());
        }
    }

    /** A player lowers a raised weapon by hand: its cooldown starts at once, before a new right click can arrive. */
    static void onUseStop(LivingEntityUseItemEvent.Stop event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide()
                && event.getItem().is(RAISABLE)) {
            cooldown(player, event.getItem());
        }
    }

    private static void cooldown(Player player, ItemStack weapon) {
        player.getCooldowns().addCooldown(weapon, CombatConfig.blockingRules().cooldownTicks());
    }
}
