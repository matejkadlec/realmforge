package io.github.matejkadlec.realmforge.combat;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Overheal (architecture 9.1): healing beyond max health becomes absorption, up to the {@code realmforge:overheal}
 * share of max health, and lasts {@value #DURATION_TICKS} ticks after the last heal that overflowed, like WotLK's
 * absorb shields (Divine Aegis, Val'anyr), so it cannot be stocked up before a fight. Logical server only.
 *
 * <p>Vanilla clamps absorption to the {@code minecraft:max_absorption} attribute, so the overheal's absorption comes
 * with a transient modifier ({@value #MODIFIER_PATH}) of the same size. Damage eats it before other absorption (a
 * golden apple's): each server tick, what the absorption lost since the last look comes off the overheal first, and
 * its modifier shrinks with it. When it expires, it is taken off the absorption with its modifier. Neither is saved: a player who logs in has the absorption above the maximum clamped
 * away.
 */
public final class Overheal {
    /** How long overheal's absorption lasts after the last heal that added to it: 10 seconds. */
    public static final int DURATION_TICKS = 200;
    static final String MODIFIER_PATH = "overheal";
    /** The id of the {@code minecraft:max_absorption} modifier that makes room for the overheal's absorption. */
    public static final Identifier MODIFIER = Realmforge.id(MODIFIER_PATH);

    private static final Map<LivingEntity, Active> ACTIVE = new WeakHashMap<>();

    /**
     * An entity's overheal absorption, the game time it expires at, and the entity's whole absorption when it was
     * last looked at, so that what damage took since then comes off the overheal first.
     */
    private record Active(float amount, long expiresAt, float absorption) {
        /** What is left after the damage the entity's absorption took since the last look. */
        Active follow(LivingEntity entity) {
            float now = entity.getAbsorptionAmount();
            float left = Math.min(now, Math.max(0, amount - Math.max(0, absorption - now)));
            return new Active(left, expiresAt, now);
        }
    }

    private Overheal() {
    }

    /** The absorption the entity has from overheal now; 0 when it has none. */
    public static float amount(LivingEntity entity) {
        Active active = ACTIVE.get(entity);
        return active == null ? 0 : active.follow(entity).amount();
    }

    /**
     * Turns the part of a heal beyond max health into absorption, up to the cap, and restarts the duration. Called by
     * {@link DamagePipeline} with the amount a heal restores, before the health changes.
     */
    static void onHeal(LivingEntity entity, float heal) {
        double share = DamagePipeline.stat(entity, GeneratedStats.OVERHEAL);
        if (share <= 0 || heal <= entity.getMaxHealth() - entity.getHealth()) {
            return;
        }
        float current = amount(entity);
        float next = (float) DamageCalculator.overheal(heal, entity.getHealth(), entity.getMaxHealth(), current, share);
        setModifier(entity, next);
        entity.setAbsorptionAmount(entity.getAbsorptionAmount() + next - current);
        ACTIVE.put(entity, new Active(next, entity.level().getGameTime() + DURATION_TICKS,
                entity.getAbsorptionAmount()));
    }

    /** Follows what damage left of each overheal and removes those that ran out or expired. */
    static void onServerTick(ServerTickEvent.Post event) {
        for (Iterator<Map.Entry<LivingEntity, Active>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<LivingEntity, Active> entry = it.next();
            LivingEntity entity = entry.getKey();
            if (entity.isRemoved() || !entity.isAlive()) {
                it.remove();
                continue;
            }
            Active before = entry.getValue();
            Active active = before.follow(entity);
            if (active.amount() <= 0 || entity.level().getGameTime() >= active.expiresAt()) {
                float other = entity.getAbsorptionAmount() - active.amount();
                removeModifier(entity);
                entity.setAbsorptionAmount(other);
                it.remove();
                continue;
            }
            if (active.amount() < before.amount()) {
                setModifier(entity, active.amount());
            }
            entry.setValue(active);
        }
    }

    /** Clamps a loaded player's absorption to its maximum: the overheal's modifier and its tracking are not saved. */
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        LivingEntity player = event.getEntity();
        if (player.level() instanceof ServerLevel && player.getAbsorptionAmount() > player.getMaxAbsorption()) {
            player.setAbsorptionAmount(player.getAbsorptionAmount());
        }
    }

    private static void setModifier(LivingEntity entity, float amount) {
        AttributeInstance instance = entity.getAttribute(Attributes.MAX_ABSORPTION);
        if (instance != null) {
            instance.addOrUpdateTransientModifier(
                    new AttributeModifier(MODIFIER, amount, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private static void removeModifier(LivingEntity entity) {
        AttributeInstance instance = entity.getAttribute(Attributes.MAX_ABSORPTION);
        if (instance != null) {
            instance.removeModifier(MODIFIER);
        }
    }
}
