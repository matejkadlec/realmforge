package io.github.matejkadlec.realmforge.effect;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * One of our status effects, from {@code content/effects/} through {@code GeneratedEffects} (architecture 11.4):
 * vanilla's attribute modifiers, and damage or healing at the end of every interval ({@link Periodic}), which takes
 * the stats of the caster that applied it ({@link EffectTicks}).
 */
public class StatusEffect extends MobEffect {
    private final Optional<Periodic> periodic;

    public StatusEffect(MobEffectCategory category, int color, Optional<Periodic> periodic) {
        super(category, color);
        this.periodic = Objects.requireNonNull(periodic, "periodic");
    }

    /** Its damage or healing over time, if it has any. */
    public Optional<Periodic> periodic() {
        return periodic;
    }

    /**
     * Adds the modifier {@code id} on {@code attribute} (both full ids), its amount at the first level. NeoForge
     * registers attributes before status effects, so the attribute, ours included, exists when the effect is built.
     */
    public StatusEffect modifier(String attribute, String id, double amount, AttributeModifier.Operation operation) {
        Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.get(Identifier.parse(attribute))
                .orElseThrow(() -> new IllegalStateException("No attribute " + attribute + " for a status effect"));
        addAttributeModifier(holder, Identifier.parse(id), amount, operation);
        return this;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
        return periodic.isPresent() && periodic.get().ticksAt(tickCount);
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplification) {
        periodic.ifPresent(ticks -> EffectTicks.tick(level, mob, BuiltInRegistries.MOB_EFFECT.wrapAsHolder(this),
                ticks, amplification));
        return true;
    }
}
