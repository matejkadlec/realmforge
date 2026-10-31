package io.github.matejkadlec.realmforge.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;

/**
 * The value of the {@code realmforge:effect_stacks} attachment (RLM-539; architecture 7, 11.4): the stacks of damage
 * over time ({@link StackedDamage}) the holder carries, by status effect. Each caster's spell keeps one stack: another
 * spell or caster adds one, the same spell from the same caster replaces its own. A stack's damage per tick was fixed
 * when it was applied. Mutable, as it counts down every tick; saved with the holder, as its effects are, never synced.
 */
public final class EffectStacks {
    public static final Codec<EffectStacks> CODEC = Codec.unboundedMap(ResourceKey.codec(Registries.MOB_EFFECT),
            Stack.CODEC.listOf()).xmap(EffectStacks::new, EffectStacks::stacks);

    private final Map<ResourceKey<MobEffect>, List<Stack>> stacks = new HashMap<>();

    public EffectStacks() {
    }

    private EffectStacks(Map<ResourceKey<MobEffect>, List<Stack>> saved) {
        saved.forEach((effect, list) -> stacks.put(effect, new ArrayList<>(list)));
    }

    /** Effect to its stacks; a copy. */
    public Map<ResourceKey<MobEffect>, List<Stack>> stacks() {
        Map<ResourceKey<MobEffect>, List<Stack>> copy = new HashMap<>();
        stacks.forEach((effect, list) -> copy.put(effect, List.copyOf(list)));
        return copy;
    }

    /** The stacks of {@code effect}; a copy, empty when it has none. */
    public List<Stack> of(ResourceKey<MobEffect> effect) {
        return List.copyOf(stacks.getOrDefault(effect, List.of()));
    }

    /** Adds {@code stack} to {@code effect}'s, replacing the stack its caster's spell had. */
    public void add(ResourceKey<MobEffect> effect, Stack stack) {
        List<Stack> list = stacks.computeIfAbsent(effect, key -> new ArrayList<>());
        list.removeIf(other -> other.sameSource(stack));
        list.add(stack);
    }

    /** Replaces {@code effect}'s stacks with {@code remaining}, dropping the effect's entry when none is left. */
    void set(ResourceKey<MobEffect> effect, List<Stack> remaining) {
        if (remaining.isEmpty()) {
            stacks.remove(effect);
        } else {
            stacks.put(effect, new ArrayList<>(remaining));
        }
    }

    /** Forgets {@code effect}'s stacks; whether it had any. */
    public boolean clear(ResourceKey<MobEffect> effect) {
        return stacks.remove(effect) != null;
    }

    public boolean isEmpty() {
        return stacks.isEmpty();
    }

    /**
     * One caster's stack of damage over time.
     *
     * @param caster who applied it
     * @param spell the spell that applied it, if a spell did: its source with the caster
     * @param procOrigin the proc whose spell applied it, if one did; its ticks then trigger no proc
     * @param perTick the damage of one tick, fixed when it was applied
     * @param remainingTicks how long it still lasts, in ticks
     */
    public record Stack(UUID caster, Optional<Identifier> spell, Optional<Identifier> procOrigin, double perTick,
            int remainingTicks) {
        public static final Codec<Stack> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        UUIDUtil.CODEC.fieldOf("caster").forGetter(Stack::caster),
                        Identifier.CODEC.optionalFieldOf("spell").forGetter(Stack::spell),
                        Identifier.CODEC.optionalFieldOf("proc_origin").forGetter(Stack::procOrigin),
                        Codec.DOUBLE.fieldOf("per_tick").forGetter(Stack::perTick),
                        Codec.INT.fieldOf("remaining_ticks").forGetter(Stack::remainingTicks))
                .apply(instance, Stack::new));

        public Stack {
            Objects.requireNonNull(caster, "caster");
            Objects.requireNonNull(spell, "spell");
            Objects.requireNonNull(procOrigin, "procOrigin");
            if (!(perTick >= 0) || !Double.isFinite(perTick)) {
                throw new IllegalArgumentException("A tick deals 0 or more: " + perTick);
            }
        }

        /** Whether {@code other} comes from the same caster's same spell: it refreshes this stack. */
        public boolean sameSource(Stack other) {
            return caster.equals(other.caster) && spell.equals(other.spell);
        }

        /** This stack a tick later. */
        public Stack counted() {
            return new Stack(caster, spell, procOrigin, perTick, remainingTicks - 1);
        }
    }
}
