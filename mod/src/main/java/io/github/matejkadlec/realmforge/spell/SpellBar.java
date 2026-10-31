package io.github.matejkadlec.realmforge.spell;

import io.github.matejkadlec.realmforge.spell.net.CastRefusedPayload;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The server side of the spell bar (architecture 11.2 step 1; C3b, RLM-25): a key's intent, {@code CastSpellPayload},
 * becomes a cast through {@link SpellCaster}, which checks everything; a refusal goes back to the player with its
 * reason ({@link CastRefusedPayload}), for the text over the bar (research SP-3).
 *
 * <p>Aim assistance (RLM-345, ADR 0055): the client names the entity under its crosshair, if any (direct aim). For an
 * enemy or ally spell that names none, the server picks the target in the cone around the player's look ({@link
 * AimAssist}), up to the spell's range or {@link AimAssist#REACH}, whichever is shorter. A named entity must lie in
 * front of the player, the widest view a client can have, so a modified client cannot cast behind them.
 */
public final class SpellBar {
    /** The widest angle from the look at which a client may name its target: a view of 180 degrees. */
    static final double MAX_VIEW_COS = 0;

    private SpellBar() {
    }

    /**
     * {@code player} casts {@code spell} from the bar at the entity with id {@code targetId} in their level, if it is
     * a living one, else at the entity aim assistance picks for a targeted spell, else at nothing; with {@code
     * replaceCast}, another spell's cast in progress gives way.
     */
    public static CastResult cast(ServerPlayer player, ResourceKey<Spell> spell, Optional<Integer> targetId,
            boolean replaceCast) {
        LivingEntity target = targetId.map(id -> player.level().getEntity(id)).filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast).orElse(null);
        CastResult result = target != null && !inView(player, target)
                ? CastResult.refused(CastFailure.INVALID_TARGET)
                : SpellCaster.cast(player, spell, SpellAim.at(target != null ? target : assisted(player, spell)),
                        replaceCast);
        result.failure().ifPresent(failure -> PacketDistributor.sendToPlayer(player,
                new CastRefusedPayload(spell, failure)));
        return result;
    }

    /** The target aim assistance picks for {@code player}'s enemy or ally spell, or null for any other spell. */
    private static LivingEntity assisted(ServerPlayer player, ResourceKey<Spell> key) {
        return Spells.get(player.registryAccess(), key).map(spell -> spell.value().targeting())
                .flatMap(targeting -> targeting instanceof EnemyTargeting
                        ? AimAssist.enemy(player, Math.min(targeting.reach(player), AimAssist.REACH))
                        : targeting instanceof AllyTargeting
                        ? AimAssist.ally(player, Math.min(targeting.reach(player), AimAssist.REACH))
                        : Optional.empty())
                .orElse(null);
    }

    /** Whether {@code target}'s middle lies in front of {@code player}'s eyes, within {@link #MAX_VIEW_COS}. */
    static boolean inView(ServerPlayer player, LivingEntity target) {
        if (target == player) {
            return true;
        }
        Vec3 offset = target.getBoundingBox().getCenter().subtract(player.getEyePosition());
        return offset.lengthSqr() < 1e-8 || offset.normalize().dot(player.getLookAngle()) > MAX_VIEW_COS;
    }
}
