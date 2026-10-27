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
 * The criteria trigger {@code realmforge:dungeon_completed} (architecture 16, Q1-2): fires for every party member online
 * when a dungeon run's goal is done, with the run's dungeon type (a run opened through a dungeon portal has one; a QA
 * command's run none), so an advancement (and through it an FTB Quests task) can wait for a dungeon. A criterion names
 * the dungeon type it accepts, any when left out.
 *
 * <pre>{@code "criteria": {"done": {"trigger": "realmforge:dungeon_completed",
 *         "conditions": {"dungeon": "realmforge:test_dungeon"}}}}</pre>
 */
public final class DungeonCompletedTrigger extends SimpleCriterionTrigger<DungeonCompletedTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    /** Tells the player's advancements that they completed a run of {@code dungeon}, or of no type when empty. */
    public void trigger(ServerPlayer player, Optional<Identifier> dungeon) {
        trigger(player, instance -> instance.matches(dungeon));
    }

    /**
     * One criterion's conditions: optionally which players count, and the dungeon type it accepts.
     *
     * @param player the players it applies to, every player when empty
     * @param dungeon the dungeon type, an id of {@code realmforge:dungeon_type}; any run when empty
     */
    public record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<Identifier> dungeon)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                        Identifier.CODEC.optionalFieldOf("dungeon").forGetter(TriggerInstance::dungeon))
                .apply(instance, TriggerInstance::new));

        public boolean matches(Optional<Identifier> completed) {
            return dungeon.isEmpty() || dungeon.equals(completed);
        }
    }
}
