package io.github.matejkadlec.realmforge.compat.apothic_attributes;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.effect.EffectSource;
import io.github.matejkadlec.realmforge.effect.EffectStacks;
import io.github.matejkadlec.realmforge.effect.StackTicks;
import io.github.matejkadlec.realmforge.generated.GeneratedEffects;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * Apothic Attributes' Bleeding as our own (RLM-579; Matěj, 2026-11-03: one Bleeding on a target, the same numbers, and
 * a level that shows, Bleeding IV, as his playtest found the stacking gone without one).
 * Apotheosis's Bloodletting and Devilish affixes and its Tyrannical gem give {@code apothic_attributes:bleeding},
 * which deals 1 + its level every 2 s through armor; a target that also bled from Rend showed two Bleedings. Every
 * application of it becomes a stack of {@code realmforge:bleeding} instead ({@link StackTicks}): one stack per attacker
 * for these sources, beside Rend's and Deep Wounds' stacks, dealing a quarter of 1 + its level every half second (the
 * same damage), physical, through armor, never dodged or blocked, with {@link #SOURCE} as its proc origin so its ticks
 * fire no proc.
 *
 * <ul>
 *   <li>{@link #onApplicable}: any application of the effect (a fresh affix or gem hit, a potion, a command) is refused
 *   and becomes the stack, at the instance's level and duration.</li>
 *   <li>A source that stacks on reapply ({@code stack_on_reapply}) reads the target's Bleeding to raise it: the mixin
 *   {@code ApothicBleedStackingMixin} hands it a stand-in at the stack's level and remaining time
 *   ({@link #current}), and the raised level and duration go to the stack ({@link #update}), so Apotheosis's own
 *   rules (its step, its limit, its longer duration) still decide them.</li>
 * </ul>
 *
 * The attacker is the target's last attacker ({@code getLastHurtByMob}), as the affix fires after the hit; with none,
 * the stack has no caster. Registered only when Apothic Attributes is loaded. Logical server only.
 */
public final class ApothicBleeds {
    /** Apothic Attributes' Bleeding. */
    public static final Identifier EFFECT = Identifier.fromNamespaceAndPath(ApothicAttributesCompat.MOD_ID, "bleeding");
    /** The spell and proc origin of the stack: one per attacker for every Apotheosis bleed. */
    public static final Identifier SOURCE = Realmforge.id("apothic_bleeding");
    /** Its 2 s tick deals 1 + its level; ours ticks every half second, so a quarter of that. */
    static final double PER_TICK_PER_LEVEL = 0.25;

    /** The stand-in handed out last, with its target: the server thread reads it right after (one call). */
    @Nullable
    private static StandIn standIn;

    private ApothicBleeds() {
    }

    /** Whether {@code effect} is Apothic Attributes' Bleeding. */
    public static boolean isApothicBleeding(Holder<MobEffect> effect) {
        return effect.unwrapKey().map(key -> key.identifier().equals(EFFECT)).orElse(false);
    }

    /**
     * What {@code target.getEffect(effect)} gives a source that stacks on reapply: for Apothic Attributes' Bleeding, a
     * stand-in at the level and remaining time of the target's stack from its attacker, or none without one (the source
     * then adds a fresh effect, which {@link #onApplicable} turns into the stack); for any other effect, the effect.
     */
    @Nullable
    public static MobEffectInstance current(LivingEntity target, Holder<MobEffect> effect) {
        if (!isApothicBleeding(effect) || !(target.level() instanceof ServerLevel)) {
            return target.getEffect(effect);
        }
        Optional<EffectStacks.Stack> stack = stack(target, caster(attacker(target)));
        if (stack.isEmpty()) {
            return null;
        }
        MobEffectInstance instance = new MobEffectInstance(effect, stack.get().remainingTicks(), level(stack.get()));
        standIn = new StandIn(instance, target);
        return instance;
    }

    /**
     * {@code existing.update(next)} for a source that stacks on reapply: a stand-in's raise goes to the stack, at
     * {@code next}'s level and duration; any other effect updates as it would.
     */
    public static boolean update(MobEffectInstance existing, MobEffectInstance next) {
        if (!isStandIn(existing)) {
            return existing.update(next);
        }
        return apply(standIn.target(), next.getAmplifier(), next.getDuration());
    }

    /** Whether {@code instance} is the stand-in handed out last, which the target never had. */
    public static boolean isStandIn(MobEffectInstance instance) {
        return standIn != null && standIn.instance() == instance;
    }

    /** Forgets the stand-in once the source is done with it (its last call, {@code onEffectStarted}). */
    public static void release(MobEffectInstance instance) {
        if (isStandIn(instance)) {
            standIn = null;
        }
    }

    static void onApplicable(MobEffectEvent.Applicable event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (isApothicBleeding(instance.getEffect()) && event.getEntity().level() instanceof ServerLevel) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            // A source that read a stand-in but adds a fresh effect anyway is done with the stand-in too.
            standIn = null;
            apply(event.getEntity(), instance.getAmplifier(), instance.getDuration());
        }
    }

    /** Gives {@code target} its attacker's stack at {@code level} for {@code durationTicks}. */
    static boolean apply(LivingEntity target, int level, int durationTicks) {
        LivingEntity attacker = attacker(target);
        EffectSource source = new EffectSource(caster(attacker), Optional.of(SOURCE), Optional.of(SOURCE));
        return StackTicks.applyPerTick(target, GeneratedEffects.BLEEDING, attacker, source,
                (1 + Math.max(0, level)) * PER_TICK_PER_LEVEL, durationTicks, Math.max(0, level));
    }

    /** The level of a stack this class gave: from its damage a tick, a quarter of 1 + the level. */
    static int level(EffectStacks.Stack stack) {
        return Math.max(0, (int) Math.round(stack.perTick() / PER_TICK_PER_LEVEL) - 1);
    }

    /** {@code target}'s stack from {@code caster} for these sources, if it has one. */
    static Optional<EffectStacks.Stack> stack(LivingEntity target, UUID caster) {
        return StackTicks.of(target, GeneratedEffects.BLEEDING).stream()
                .filter(stack -> stack.caster().equals(caster) && stack.spell().equals(Optional.of(SOURCE)))
                .findFirst();
    }

    @Nullable
    private static LivingEntity attacker(LivingEntity target) {
        LivingEntity attacker = target.getLastHurtByMob();
        return attacker != null && attacker != target && attacker.isAlive() ? attacker : null;
    }

    private static UUID caster(@Nullable LivingEntity attacker) {
        return attacker != null ? attacker.getUUID() : Util.NIL_UUID;
    }

    private record StandIn(MobEffectInstance instance, LivingEntity target) {
    }
}
