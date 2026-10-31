package io.github.matejkadlec.realmforge.effect;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * One of our status effects, from {@code content/effects/} through {@code GeneratedEffects} (architecture 11.4):
 * vanilla's attribute modifiers, damage or healing at the end of every interval ({@link Periodic}), which takes the
 * stats of the caster that applied it ({@link EffectTicks}), damage over time kept per caster's spell
 * ({@link StackedDamage}, RLM-539), whether it stuns ({@link Stuns}, C4) and how clients draw its holder
 * ({@link Look}).
 */
public class StatusEffect extends MobEffect {
    private final Optional<Periodic> periodic;
    private Optional<StackedDamage> stacks = Optional.empty();
    private Optional<Look> look = Optional.empty();
    private boolean stuns;

    public StatusEffect(MobEffectCategory category, int color, Optional<Periodic> periodic) {
        super(category, color);
        this.periodic = Objects.requireNonNull(periodic, "periodic");
    }

    /** Its damage or healing over time, if it has any. */
    public Optional<Periodic> periodic() {
        return periodic;
    }

    /** Its damage over time kept per caster's spell, if it has any ({@link EffectStacks}). */
    public Optional<StackedDamage> stacks() {
        return stacks;
    }

    /**
     * Makes its ticks the stacks that spells keep on its holder (RLM-539): of {@code school} every {@code intervalTicks},
     * ignoring armor or not. The generated registration calls it while it builds the effect.
     */
    public StatusEffect stacks(String school, int intervalTicks, boolean ignoresArmor) {
        stacks = Optional.of(StackedDamage.of(school, intervalTicks, ignoresArmor));
        return this;
    }

    /** How clients draw its holder, if anyhow. */
    public Optional<Look> look() {
        return look;
    }

    /** Makes clients draw its holder with {@code look}; the generated registration calls it. */
    public StatusEffect looks(Look look) {
        this.look = Optional.of(look);
        return this;
    }

    /** Makes it a stun ({@link Stuns}); the generated registration calls it while it builds the effect. */
    public StatusEffect stuns() {
        stuns = true;
        return this;
    }

    /** Whether it is a stun: its holder neither attacks nor casts, and a mob's AI stands still. */
    public boolean isStun() {
        return stuns;
    }

    /**
     * Adds the modifier {@code id} on {@code attribute} (both full ids), its amount at the first level. NeoForge
     * registers attributes before status effects, so the attribute, ours included, exists when the effect is built.
     */
    public StatusEffect modifier(String attribute, String id, double amount, AttributeModifier.Operation operation) {
        addAttributeModifier(attribute(attribute), Identifier.parse(id), amount, operation);
        return this;
    }

    /**
     * Adds the modifier {@code id} on {@code attribute} that gives {@code amount} at the first level and
     * {@code perAmplifier} more at each level above it, instead of vanilla's multiplying by the level (Battle Shout's
     * 10, 15 and 20 %): NeoForge's attribute curve.
     */
    public StatusEffect modifier(String attribute, String id, double amount, AttributeModifier.Operation operation,
            double perAmplifier) {
        addAttributeModifier(attribute(attribute), Identifier.parse(id), operation,
                amplifier -> amount + perAmplifier * amplifier);
        return this;
    }

    private static Holder<Attribute> attribute(String attribute) {
        return BuiltInRegistries.ATTRIBUTE.get(Identifier.parse(attribute))
                .orElseThrow(() -> new IllegalStateException("No attribute " + attribute + " for a status effect"));
    }

    /** A stacked effect counts its stacks down every tick; a periodic one ticks at the end of every interval. */
    @Override
    public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
        return stacks.isPresent() || periodic.isPresent() && periodic.get().ticksAt(tickCount);
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplification) {
        Holder<MobEffect> holder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(this);
        periodic.ifPresent(ticks -> EffectTicks.tick(level, mob, holder, ticks, amplification));
        stacks.ifPresent(damage -> StackTicks.tick(level, mob, holder, damage));
        return true;
    }

    /** How clients draw the holder of an effect, which they learn through {@code realmforge:effect_looks}. */
    public enum Look implements StringRepresentable {
        /** Frozen (RLM-539, Matěj, 2026-10-30): its body covered in Packed Ice, and shivering from the cold. */
        ICE("ice");

        public static final Codec<Look> CODEC = StringRepresentable.fromEnum(Look::values);
        public static final StreamCodec<ByteBuf, Look> STREAM_CODEC = ByteBufCodecs.idMapper(
                id -> values()[id], Look::ordinal);

        private final String serializedName;

        Look(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }
}
