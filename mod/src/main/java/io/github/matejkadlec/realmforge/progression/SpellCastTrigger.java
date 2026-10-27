package io.github.matejkadlec.realmforge.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.criterion.ContextAwarePredicate;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * The criteria trigger {@code realmforge:spell_cast} (architecture 16, Q1-2): fires when a player's cast goes off, an
 * instant spell when it is cast and a cast time or channel when it completes (an interrupted one never), so an
 * advancement (and through it an FTB Quests task) can wait for a spell. A proc's spell is no cast of the player's and
 * fires nothing. A criterion names the spell it accepts, any when left out.
 *
 * <pre>{@code "criteria": {"done": {"trigger": "realmforge:spell_cast", "conditions": {"spell": "realmforge:firebolt"}}}}</pre>
 */
public final class SpellCastTrigger extends SimpleCriterionTrigger<SpellCastTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    /** Tells the player's advancements that their cast of {@code spell} went off. */
    public void trigger(ServerPlayer player, Identifier spell) {
        trigger(player, instance -> instance.matches(spell));
    }

    /**
     * One criterion's conditions: optionally which players count, and the spell it accepts.
     *
     * @param player the players it applies to, every player when empty
     * @param spell the spell, an id of {@code realmforge:spell}; any spell when empty
     */
    public record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<Identifier> spell)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                        Identifier.CODEC.optionalFieldOf("spell").forGetter(TriggerInstance::spell))
                .apply(instance, TriggerInstance::new));

        public boolean matches(Identifier cast) {
            return spell.isEmpty() || spell.get().equals(cast);
        }
    }
}
