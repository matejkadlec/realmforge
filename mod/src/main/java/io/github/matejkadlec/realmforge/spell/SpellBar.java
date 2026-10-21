package io.github.matejkadlec.realmforge.spell;

import io.github.matejkadlec.realmforge.spell.net.CastRefusedPayload;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The server side of the spell bar (architecture 11.2 step 1; C3b, RLM-25): a key's intent, {@code CastSpellPayload},
 * becomes a cast through {@link SpellCaster}, which checks everything; a refusal goes back to the player with its
 * reason ({@link CastRefusedPayload}), for the text over the bar (research SP-3).
 */
public final class SpellBar {
    private SpellBar() {
    }

    /**
     * {@code player} casts {@code spell} from the bar at the entity with id {@code targetId} in their level, if it is
     * a living one, else at nothing; with {@code replaceCast}, another spell's cast in progress gives way.
     */
    public static CastResult cast(ServerPlayer player, ResourceKey<Spell> spell, Optional<Integer> targetId,
            boolean replaceCast) {
        LivingEntity target = targetId.map(id -> player.level().getEntity(id)).filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast).orElse(null);
        CastResult result = SpellCaster.cast(player, spell, SpellAim.at(target), replaceCast);
        result.failure().ifPresent(failure -> PacketDistributor.sendToPlayer(player,
                new CastRefusedPayload(spell, failure)));
        return result;
    }
}
