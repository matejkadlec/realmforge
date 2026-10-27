package io.github.matejkadlec.realmforge.dungeon.instance;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.mob.MobLevels;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.common.Tags;

/**
 * The mob levels of dungeon runs (RLM-459, architecture 14.1 and 15.5), the {@link MobLevels.Source} that
 * {@code dungeon} gives {@code mob}: a mob that gets its level inside a plot whose run has a level takes that level,
 * less its {@link RunLevels.Rank}'s offset. So spawner mobs, mobs placed with the rooms and any mob summoned there
 * follow the run, which the instance dimension's missing mob level rule would leave at level 1.
 */
public final class InstanceMobLevels {
    /** Minibosses: one level below their run's. W5 and W6 put theirs here; bosses are NeoForge's {@code c:bosses}. */
    public static final TagKey<EntityType<?>> MINIBOSSES =
            TagKey.create(Registries.ENTITY_TYPE, Realmforge.id("minibosses"));

    private InstanceMobLevels() {
    }

    /** The level of {@code mob} at a position of the instance dimension, empty elsewhere or outside a leveled run. */
    public static OptionalInt levelFor(ServerLevel level, Mob mob, double x, double y, double z) {
        if (!InstanceDimension.isInstance(level)) {
            return OptionalInt.empty();
        }
        InstanceManager manager = InstanceManager.get(level.getServer());
        if (manager == null) {
            return OptionalInt.empty();
        }
        Optional<Plot> plot = manager.plot(PlotGrid.plotAt(Mth.floor(x), Mth.floor(z)));
        OptionalInt runLevel = plot.map(Plot::level).orElse(OptionalInt.empty());
        if (runLevel.isEmpty()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(RunLevels.mobLevel(runLevel.getAsInt(), rank(mob), mob.getRandom()));
    }

    /** A mob's rank in a dungeon, from its entity type's tags. */
    public static RunLevels.Rank rank(Entity mob) {
        if (mob.is(Tags.EntityTypes.BOSSES)) {
            return RunLevels.Rank.BOSS;
        }
        return mob.is(MINIBOSSES) ? RunLevels.Rank.MINIBOSS : RunLevels.Rank.OTHER;
    }
}
