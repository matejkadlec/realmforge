package io.github.matejkadlec.realmforge.combat;

import io.github.matejkadlec.realmforge.combat.net.CombatTextPayload;
import io.github.matejkadlec.realmforge.combat.net.HitDirectionPayload;
import io.github.matejkadlec.realmforge.generated.GeneratedSpellSchools;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sends the floating combat text of a hit, a dodge or a heal, and the hit direction (architecture 9.1, 17.2; Matěj,
 * 2026-10-13). A hit's number and a miss go to the player who dealt it, shown over the entity: a magic school's hit in
 * the school's colour, every other hit (melee, ranged, the Physical school) in the Physical school's orange, red on a
 * crit. The player who was attacked sees no number, the health bar shows the damage, but an arc points at the
 * attacker, and a dodge shows "Miss!" beside the crosshair. A tick of a damaging effect (poison, wither) has no
 * attacker: it goes to the player who applied the effect, in the effect's colour ({@link DamageOverTime}). A heal
 * shows in green over the healed entity, to the players near it. Hits between mobs send nothing.
 */
final class CombatText {
    /** Hits that are not magic, when no Physical school is in the data (it is orange too). */
    static final int BASE = 0xFFFFAA00;
    /** A critical hit that is not magic. */
    static final int CRIT = 0xFFFF3B3B;
    static final int MISS = 0xFFFFFF55;
    static final int HEAL = 0xFF55FF55;
    /** Smaller heals, such as food regeneration's trickle with little saturation, show no number. */
    static final float MIN_HEAL = 0.5F;

    private CombatText() {
    }

    static void hit(LivingEntity defender, DamageSource source, Optional<Holder<SpellSchool>> school, boolean crit,
            float amount) {
        if (amount <= 0) {
            return;
        }
        DamageOverTime.Tick tick = DamageOverTime.tick(defender, source);
        if (tick != null) {
            if (tick.applier() != defender) {
                PacketDistributor.sendToPlayer(tick.applier(),
                        new CombatTextPayload(defender.getId(), amount, tick.color(), (byte) 0));
            }
            return;
        }
        SpellSchool hitSchool = school.map(Holder::value).orElse(null);
        int color = hitSchool != null && hitSchool.magic() ? hitSchool.color() : crit ? CRIT : physical(defender);
        toAttacker(defender, source, new CombatTextPayload(defender.getId(), amount, color,
                crit ? CombatTextPayload.CRIT : 0));
        hitDirection(defender, source);
    }

    static void dodge(LivingEntity defender, DamageSource source) {
        toAttacker(defender, source, new CombatTextPayload(defender.getId(), 0, MISS, CombatTextPayload.MISS));
        if (defender instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new CombatTextPayload(defender.getId(), 0, MISS,
                    (byte) (CombatTextPayload.MISS | CombatTextPayload.INCOMING)));
        }
        hitDirection(defender, source);
    }

    static void heal(LivingEntity entity, float amount) {
        PacketDistributor.sendToPlayersTrackingEntity(entity,
                new CombatTextPayload(entity.getId(), amount, HEAL, CombatTextPayload.HEAL));
    }

    private static void toAttacker(LivingEntity defender, DamageSource source, CombatTextPayload payload) {
        if (source.getEntity() instanceof ServerPlayer attacker && attacker != defender) {
            PacketDistributor.sendToPlayer(attacker, payload);
        }
    }

    /** Points the attacked player at the attacker (the shooter of an arrow), or at where the attack came from. */
    private static void hitDirection(LivingEntity defender, DamageSource source) {
        if (!(defender instanceof ServerPlayer player)) {
            return;
        }
        Entity attacker = source.getEntity();
        if (attacker == defender) {
            return;
        }
        Vec3 from = attacker != null ? attacker.position() : source.getSourcePosition();
        if (from != null) {
            PacketDistributor.sendToPlayer(player,
                    new HitDirectionPayload(attacker != null ? attacker.getId() : -1, from.x, from.z));
        }
    }

    private static int physical(LivingEntity defender) {
        SpellSchool physical = defender.registryAccess().lookupOrThrow(CombatRegistries.SPELL_SCHOOL)
                .getValue(GeneratedSpellSchools.PHYSICAL);
        return physical != null ? physical.color() : BASE;
    }
}
