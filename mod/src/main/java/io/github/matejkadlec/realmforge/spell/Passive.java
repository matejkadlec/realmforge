package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What a passive spell does (RLM-541; architecture 11.1): a spell that is learned with ranks but never cast and never
 * on the spell bar. While its player knows it, at its rank, and while the main hand holds an item of
 * {@code whileWielding} if it names one, it gives attribute modifiers ({@link Passives#SOURCE}), raises named spells'
 * damage ({@code spellDamage}, the hit's passives layer), adds crit chance against a target with a status effect, and
 * shortens a two-handed block's cooldown from a rank on, and its procs fire ({@link Passives#procs}; RLM-540: Frostbite,
 * Taste for Blood, Deep Wounds). Every number grows per rank like an active spell's.
 *
 * @param whileWielding the item tag the main hand must hold for any of it to apply, if any
 * @param modifiers attribute modifiers, per rank
 * @param spellDamage more damage for named spells, per rank
 * @param critChance more crit chance against a target with a status effect, per rank
 * @param blockCooldown a shorter two-handed block cooldown from a rank on
 * @param procs what fires on the player's hits, crits, kills, effects and ticks, per rank
 */
public record Passive(Optional<TagKey<Item>> whileWielding, List<Modifier> modifiers, List<SpellDamage> spellDamage,
        Optional<CritChance> critChance, Optional<BlockCooldown> blockCooldown, List<Proc> procs) {
    public static final Codec<Passive> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    TagKey.codec(Registries.ITEM).optionalFieldOf("while_wielding")
                            .forGetter(Passive::whileWielding),
                    Modifier.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(Passive::modifiers),
                    SpellDamage.CODEC.listOf().optionalFieldOf("spell_damage", List.of())
                            .forGetter(Passive::spellDamage),
                    CritChance.CODEC.optionalFieldOf("crit_chance").forGetter(Passive::critChance),
                    BlockCooldown.CODEC.optionalFieldOf("block_cooldown").forGetter(Passive::blockCooldown),
                    Proc.CODEC.listOf().optionalFieldOf("procs", List.of()).forGetter(Passive::procs))
            .apply(instance, Passive::new));

    public Passive {
        modifiers = List.copyOf(modifiers);
        spellDamage = List.copyOf(spellDamage);
        procs = List.copyOf(procs);
    }

    /** Whether it applies with {@code mainHand} in the main hand: always, unless it names a weapon tag. */
    public boolean appliesWith(ItemStack mainHand) {
        return whileWielding.map(mainHand::is).orElse(true);
    }

    /** The share of damage it adds to {@code spell}'s hits at rank {@code level}, 0 for a spell it does not name. */
    public double damageFor(ResourceKey<Spell> spell, int level) {
        return spellDamage.stream().filter(bonus -> bonus.spells().contains(spell))
                .mapToDouble(bonus -> bonus.damageAt(level)).sum();
    }

    /** The crit chance it adds at rank {@code level} against {@code target}, 0 unless the target has the effect. */
    public double critChanceAgainst(LivingEntity target, int level) {
        return critChance.filter(bonus -> target.hasEffect(bonus.against())).map(bonus -> bonus.chanceAt(level))
                .orElse(0.0);
    }

    /** How many ticks shorter a two-handed block's cooldown is at rank {@code level}. */
    public int blockCooldownReductionAt(int level) {
        return blockCooldown.filter(cooldown -> level >= cooldown.fromLevel()).map(BlockCooldown::reductionTicks)
                .orElse(0);
    }

    /**
     * An attribute modifier that grows per rank: {@code amount} at rank 1, {@code perLevel} more for every rank above
     * it, in game units (a fraction for a percentage).
     */
    public record Modifier(Holder<Attribute> attribute, AttributeModifier.Operation operation, double amount,
            double perLevel) {
        public static final Codec<Modifier> CODEC = RecordCodecBuilder.<Modifier>create(instance -> instance.group(
                        Attribute.CODEC.fieldOf("attribute").forGetter(Modifier::attribute),
                        AttributeModifier.Operation.CODEC.fieldOf("operation").forGetter(Modifier::operation),
                        Codec.DOUBLE.fieldOf("amount").forGetter(Modifier::amount),
                        Codec.DOUBLE.optionalFieldOf("per_level", 0.0).forGetter(Modifier::perLevel))
                .apply(instance, Modifier::new))
                .validate(modifier -> Double.isFinite(modifier.amount()) && Double.isFinite(modifier.perLevel())
                        ? DataResult.success(modifier)
                        : DataResult.error(() -> "A passive's modifier is finite: " + modifier));

        /** Its amount at rank {@code level}. */
        public double amountAt(int level) {
            return SpellScaling.atLevel(amount, perLevel, level);
        }
    }

    /** More damage for the named spells' hits: a share at rank 1, growing per rank. */
    public record SpellDamage(List<ResourceKey<Spell>> spells, double damage, double damagePerLevel) {
        public static final Codec<SpellDamage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        ExtraCodecs.nonEmptyList(ResourceKey.codec(SpellRegistries.SPELL).listOf())
                                .fieldOf("spells").forGetter(SpellDamage::spells),
                        Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("damage").forGetter(SpellDamage::damage),
                        Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("damage_per_level", 0.0)
                                .forGetter(SpellDamage::damagePerLevel))
                .apply(instance, SpellDamage::new));

        public SpellDamage {
            spells = List.copyOf(spells);
        }

        /** The share at rank {@code level}. */
        public double damageAt(int level) {
            return SpellScaling.atLevel(damage, damagePerLevel, level);
        }
    }

    /** More crit chance against a target with {@code against}: a share at rank 1, growing per rank. */
    public record CritChance(double chance, double chancePerLevel, Holder<MobEffect> against) {
        public static final Codec<CritChance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.doubleRange(0, 1).fieldOf("chance").forGetter(CritChance::chance),
                        Codec.doubleRange(0, 1).optionalFieldOf("chance_per_level", 0.0)
                                .forGetter(CritChance::chancePerLevel),
                        MobEffect.CODEC.fieldOf("against").forGetter(CritChance::against))
                .apply(instance, CritChance::new));

        /** The share at rank {@code level}. */
        public double chanceAt(int level) {
            return SpellScaling.atLevel(chance, chancePerLevel, level);
        }
    }

    /**
     * A passive spell's proc (RLM-540): when {@code trigger} happens, at its chance at the rank, outside its internal
     * cooldown on that target, it gives the other entity a status effect, casts a spell at it at the passive's rank, or
     * opens a spell's window ({@link io.github.matejkadlec.realmforge.spell.Proc}).
     *
     * @param trigger what makes it fire
     * @param effect for applies_effect and tick: the status effect it watches
     * @param spell for tick: only the ticks of this spell's damage over time
     * @param chance the chance at rank 1, a share
     * @param chancePerLevel added to it for every rank above 1
     * @param cooldownTicks its internal cooldown on each target
     * @param applyEffect the status effect it gives, if that is what it does
     * @param cast the spell it casts, if that is what it does
     * @param openWindow the spell whose window it opens, if that is what it does
     */
    public record Proc(io.github.matejkadlec.realmforge.spell.Proc.Trigger trigger, Optional<Holder<MobEffect>> effect,
            Optional<ResourceKey<Spell>> spell, double chance, double chancePerLevel, int cooldownTicks,
            Optional<ProcEffect> applyEffect, Optional<ResourceKey<Spell>> cast,
            Optional<ResourceKey<Spell>> openWindow) {
        public static final Codec<Proc> CODEC = RecordCodecBuilder.<Proc>create(instance -> instance.group(
                        io.github.matejkadlec.realmforge.spell.Proc.Trigger.CODEC.fieldOf("trigger")
                                .forGetter(Proc::trigger),
                        MobEffect.CODEC.optionalFieldOf("effect").forGetter(Proc::effect),
                        ResourceKey.codec(SpellRegistries.SPELL).optionalFieldOf("spell").forGetter(Proc::spell),
                        Codec.doubleRange(0, 1).fieldOf("chance").forGetter(Proc::chance),
                        Codec.doubleRange(0, 1).optionalFieldOf("chance_per_level", 0.0)
                                .forGetter(Proc::chancePerLevel),
                        ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("cooldown_ticks", 0)
                                .forGetter(Proc::cooldownTicks),
                        ProcEffect.CODEC.optionalFieldOf("apply_effect").forGetter(Proc::applyEffect),
                        ResourceKey.codec(SpellRegistries.SPELL).optionalFieldOf("cast").forGetter(Proc::cast),
                        ResourceKey.codec(SpellRegistries.SPELL).optionalFieldOf("open_window")
                                .forGetter(Proc::openWindow))
                .apply(instance, Proc::new))
                .validate(proc -> (proc.applyEffect().isPresent() ? 1 : 0) + (proc.cast().isPresent() ? 1 : 0)
                        + (proc.openWindow().isPresent() ? 1 : 0) == 1
                        ? DataResult.success(proc)
                        : DataResult.error(() -> "A proc does one thing: " + proc));

        /** The chance at rank {@code level}, at most 1. */
        public double chanceAt(int level) {
            return Math.min(1, SpellScaling.atLevel(chance, chancePerLevel, level));
        }

        /** What it does at rank {@code level}, as the proc engine runs it. */
        public io.github.matejkadlec.realmforge.spell.Proc.Action actionAt(int level) {
            if (cast.isPresent()) {
                return new io.github.matejkadlec.realmforge.spell.Proc.CastSpell(cast.get(), level);
            }
            if (openWindow.isPresent()) {
                return new io.github.matejkadlec.realmforge.spell.Proc.OpenWindow(openWindow.get());
            }
            ProcEffect given = applyEffect.orElseThrow();
            return new io.github.matejkadlec.realmforge.spell.Proc.ApplyEffect(given.effect(), given.durationTicks(),
                    0, false);
        }
    }

    /** The status effect a proc gives: its effect and how long it lasts. */
    public record ProcEffect(Holder<MobEffect> effect, int durationTicks) {
        public static final Codec<ProcEffect> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        MobEffect.CODEC.fieldOf("effect").forGetter(ProcEffect::effect),
                        Codec.intRange(1, ApplyEffectEffect.MAX_DURATION_TICKS).fieldOf("duration_ticks")
                                .forGetter(ProcEffect::durationTicks))
                .apply(instance, ProcEffect::new));
    }

    /** A two-handed block's cooldown, {@code reductionTicks} shorter from rank {@code fromLevel} on. */
    public record BlockCooldown(int reductionTicks, int fromLevel) {
        public static final Codec<BlockCooldown> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        ExtraCodecs.POSITIVE_INT.fieldOf("reduction_ticks").forGetter(BlockCooldown::reductionTicks),
                        ExtraCodecs.POSITIVE_INT.optionalFieldOf("from_level", 1).forGetter(BlockCooldown::fromLevel))
                .apply(instance, BlockCooldown::new));
    }
}
