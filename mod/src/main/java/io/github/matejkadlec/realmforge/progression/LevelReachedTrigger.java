package io.github.matejkadlec.realmforge.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.criterion.ContextAwarePredicate;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

/**
 * The criteria trigger {@code realmforge:level_reached} (architecture 16): fires with a player's character level
 * whenever it changes and when they log in, so an advancement (and through it an FTB Quests task) can wait for a
 * level. A criterion names the levels it accepts, {@code "level": {"min": 5}} for "level 5 or more": a player can
 * pass several levels at once when the level cap rises, so an exact level could be skipped.
 *
 * <pre>{@code "criteria": {"level_5": {"trigger": "realmforge:level_reached", "conditions": {"level": {"min": 5}}}}}</pre>
 */
public final class LevelReachedTrigger extends SimpleCriterionTrigger<LevelReachedTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    /** Tells the player's advancements that their character is at {@code level} now. */
    public void trigger(ServerPlayer player, int level) {
        trigger(player, instance -> instance.matches(level));
    }

    /**
     * One criterion's conditions: optionally which players count, and the levels it accepts (any when left out).
     *
     * @param player the players it applies to, every player when empty
     * @param level the levels it accepts
     */
    public record TriggerInstance(Optional<ContextAwarePredicate> player, MinMaxBounds.Ints level)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                        MinMaxBounds.Ints.CODEC.optionalFieldOf("level", MinMaxBounds.Ints.ANY)
                                .forGetter(TriggerInstance::level))
                .apply(instance, TriggerInstance::new));

        public boolean matches(int reached) {
            return level.matches(reached);
        }
    }
}
