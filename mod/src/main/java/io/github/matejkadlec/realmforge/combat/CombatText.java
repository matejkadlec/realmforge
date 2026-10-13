package io.github.matejkadlec.realmforge.combat;

import io.github.matejkadlec.realmforge.combat.net.CombatTextPayload;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sends the floating combat text of a hit or a dodge (architecture 9.1, 17.2): to the player who dealt it, shown over
 * the hit entity, and to the player who took it, shown at the side of the screen. Hits between mobs send nothing.
 */
final class CombatText {
    /** Melee and other hits without a school are white. */
    static final int PLAIN = 0xFFFFFFFF;

    private CombatText() {
    }

    static void hit(LivingEntity defender, DamageSource source, Optional<Holder<SpellSchool>> school, boolean crit,
            float amount) {
        if (amount > 0) {
            int color = school.map(holder -> holder.value().color()).orElse(PLAIN);
            send(defender, source, amount, color, crit ? CombatTextPayload.CRIT : 0);
        }
    }

    static void dodge(LivingEntity defender, DamageSource source) {
        send(defender, source, 0, PLAIN, CombatTextPayload.DODGE);
    }

    private static void send(LivingEntity defender, DamageSource source, float amount, int color, int flags) {
        if (source.getEntity() instanceof ServerPlayer attacker && attacker != defender) {
            PacketDistributor.sendToPlayer(attacker,
                    new CombatTextPayload(defender.getId(), amount, color, (byte) flags));
        }
        if (defender instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player,
                    new CombatTextPayload(defender.getId(), amount, color, (byte) (flags | CombatTextPayload.INCOMING)));
        }
    }
}
