package io.github.matejkadlec.realmforge.mob.custom;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The entity classes of the archetypes in {@code content/mobs/} that a feature above {@code mob} owns (architecture
 * 4.4, 14.2): {@code boss}, the body of a boss, is {@code boss/BossMob}. That feature provides its class here when the
 * mod is constructed, and {@code GeneratedMobs} creates the archetype's mobs through {@link #create}, as
 * {@code world/BlockKinds} does for block kinds. {@code melee} and {@code caster} live in this package, and the
 * registrations name their classes directly.
 */
public final class Archetypes {
    /** Creates a mob of an archetype: its entity type, the level it joins and its spec. */
    @FunctionalInterface
    public interface Factory {
        CustomMob create(EntityType<? extends CustomMob> type, Level level, CustomMobSpec spec);
    }

    private static final Map<String, Factory> FACTORIES = new ConcurrentHashMap<>();

    private Archetypes() {
    }

    /** Provides the class of {@code archetype}; called once per archetype, before entity types register. */
    public static void provide(String archetype, Factory factory) {
        if (FACTORIES.putIfAbsent(archetype, factory) != null) {
            throw new IllegalStateException("mob archetype " + archetype + " is provided twice");
        }
    }

    /**
     * A new mob of {@code archetype}.
     *
     * @throws IllegalStateException when no feature provides the archetype
     */
    public static CustomMob create(String archetype, EntityType<? extends CustomMob> type, Level level,
            CustomMobSpec spec) {
        Factory factory = FACTORIES.get(archetype);
        if (factory == null) {
            throw new IllegalStateException("no feature provides mob archetype " + archetype);
        }
        return factory.create(type, level, spec);
    }
}
