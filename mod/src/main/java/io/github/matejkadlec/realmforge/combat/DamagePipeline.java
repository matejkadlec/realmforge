package io.github.matejkadlec.realmforge.combat;

import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import io.github.matejkadlec.realmforge.stats.StatsAttachments;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;

/**
 * The damage pipeline on NeoForge's damage events (architecture 9.1): reads the attacker's and the defender's stats,
 * lets {@link DamageCalculator} decide, and applies the result. Logical server only.
 *
 * <ol>
 *   <li>{@code CriticalHitEvent}: a player's full-strength melee attack crits on our crit chance and deals 1 + crit
 *   damage, instead of vanilla's jump crit; vanilla still plays the crit sound and particles, and a crit no longer
 *   stops a sword's sweep (vanilla's jump crits never met one on the ground).</li>
 *   <li>{@code LivingIncomingDamageEvent}: a dodge cancels the hit (melee, projectiles and physical school hits; never
 *   magic, explosions or the world). A school hit gets spell power (magic), the school's power, a crit roll (by the
 *   spell's {@link CritRule} when a {@link SchoolDamageSource} brings one) and the defender's resistance (spell
 *   resistance added for magic). The armor reduction becomes vanilla's formula on the armor the attacker's armor
 *   pierce and shred leave, except for damage in {@code #minecraft:bypasses_armor}. The defender's magic shield joins
 *   the absorption reduction ({@link MagicShields}).</li>
 *   <li>Hurt cooldown: a school hit lands through it ({@code #minecraft:bypasses_cooldown}) and leaves the defender's
 *   window as it was: it grants no invulnerability of its own, and {@code lastHurt}, which vanilla overwrites, is
 *   restored afterwards, so melee hits in an open window are still compared with the previous melee hit, not with a
 *   spell or damage-over-time tick.</li>
 *   <li>{@code LivingDamageEvent.Post}: reports the hit ({@link CombatEvent.Hit}), sends its combat text, and heals the
 *   attacker of a melee or ranged hit by its lifesteal.</li>
 *   <li>{@code LivingHealEvent}, last: turns healing beyond max health into overheal ({@link Overheal}) and sends the
 *   combat text of the health a heal restores.</li>
 * </ol>
 */
public final class DamagePipeline {
    /** Only a full-strength melee attack can crit, like vanilla's jump crit ({@code Player#attack}). */
    private static final float FULL_STRENGTH = 0.9F;

    private DamagePipeline() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(CriticalHitEvent.class, DamagePipeline::onCriticalHit);
        NeoForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class, DamagePipeline::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, DamagePipeline::onDamagePost);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingHealEvent.class, DamagePipeline::onHeal);
    }

    /** A stat's value on the entity, or 0 when the entity lacks the attribute (mobs lack most of ours). */
    public static double stat(LivingEntity entity, Holder<Attribute> stat) {
        return entity.getAttributes().hasAttribute(stat) ? entity.getAttributeValue(stat) : 0;
    }

    /** The entity's level ({@code realmforge:level}), without creating the attachment on an entity that has none. */
    public static int level(LivingEntity entity) {
        return entity.getExistingData(StatsAttachments.LEVEL).map(EntityLevel::value).orElse(EntityLevel.MIN);
    }

    /**
     * Whether the hit can be dodged: a melee hit, a projectile or a physical school hit, from an attacker. Magic,
     * explosions and damage from the world cannot (architecture 9.1).
     */
    public static boolean dodgeable(DamageSource source, Optional<? extends Holder<SpellSchool>> school) {
        if (school.isPresent()) {
            return source.getEntity() != null && !source.is(DamageTypeTags.IS_EXPLOSION)
                    && !school.get().value().magic();
        }
        return meleeOrRanged(source);
    }

    /**
     * Whether the hit is a melee hit or a projectile from an attacker, not a school hit or an explosion: the hits
     * lifesteal heals from (Matěj, 2026-10-14: melee and ranged hits, never spells).
     */
    public static boolean meleeOrRanged(DamageSource source) {
        if (source.getEntity() == null || source.is(DamageTypeTags.IS_EXPLOSION)) {
            return false;
        }
        return source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)
                || source.is(DamageTypeTags.IS_PLAYER_ATTACK) || source.is(DamageTypeTags.IS_PROJECTILE);
    }

    static void onCriticalHit(CriticalHitEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !(event.getTarget() instanceof LivingEntity)) {
            return;
        }
        // Neo resets the attack strength only at the end of Player#attack, so it is still this attack's here.
        boolean crit = player.getAttackStrengthScale(0.5F) > FULL_STRENGTH
                && DamageCalculator.crits(stat(player, GeneratedStats.CRIT_CHANCE), player.getRandom());
        event.setCriticalHit(crit);
        event.setDamageMultiplier(crit
                ? (float) DamageCalculator.critMultiplier(stat(player, GeneratedStats.CRIT_DAMAGE))
                : 1.0F);
        // Vanilla's jump crits never happened on the ground, where sweeps do; ours do, so a crit keeps the sweep.
        event.setDisableSweep(false);
        HitTracker.markMeleeCrit(player, event.getTarget(), crit);
    }

    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity defender = event.getEntity();
        if (!(defender.level() instanceof ServerLevel level)) {
            return;
        }
        DamageSource source = event.getSource();
        Optional<Holder.Reference<SpellSchool>> school = SpellSchools.of(level.registryAccess(), source);
        if (dodgeable(source, school)
                && DamageCalculator.dodges(stat(defender, GeneratedStats.DODGE_CHANCE), defender.getRandom())) {
            // Since NeoForge 26.1 a cancelled hit pops its damage container (architecture 9.1).
            event.setCanceled(true);
            HitTracker.takeMeleeCrit(source, defender);
            NeoForge.EVENT_BUS.post(new CombatEvent.Dodge(defender, source));
            CombatText.dodge(defender, source);
            return;
        }
        event.addReductionModifier(DamageContainer.Reduction.ARMOR,
                (container, vanilla) -> armorReduction(defender, container, vanilla));
        if (MagicShields.applies(defender)) {
            event.addReductionModifier(DamageContainer.Reduction.ABSORPTION,
                    (container, absorbed) -> MagicShields.absorb(defender, container, absorbed));
        }
        if (school.isPresent()) {
            schoolHit(event, level, defender, source, school.get());
        } else if (HitTracker.takeMeleeCrit(source, defender)) {
            HitTracker.start(defender, new HitTracker.Hit(source, level.getGameTime(), Optional.empty(), true,
                    Float.NaN));
        }
    }

    static void onDamagePost(LivingDamageEvent.Post event) {
        LivingEntity defender = event.getEntity();
        if (!(defender.level() instanceof ServerLevel)) {
            return;
        }
        DamageSource source = event.getSource();
        HitTracker.Hit hit = HitTracker.finish(defender, source);
        if (hit != null && !Float.isNaN(hit.lastHurt())) {
            defender.lastHurt = hit.lastHurt();
        }
        Optional<Holder<SpellSchool>> school = hit != null ? hit.school() : Optional.empty();
        boolean crit = hit != null && hit.crit();
        NeoForge.EVENT_BUS.post(new CombatEvent.Hit(defender, source, school, crit, event.getOriginalDamage(),
                event.getReduction(DamageContainer.Reduction.ARMOR), event.getInflictedDamage(),
                event.getHealthDamage()));
        CombatText.hit(defender, source, school, crit, event.getInflictedDamage());
        lifesteal(defender, source, event.getInflictedDamage());
    }

    /**
     * Heals the attacker of a melee or ranged hit by its lifesteal share of the damage the hit dealt, after armor and
     * resistance, absorption hearts and magic shield included; spells never heal this way (Matěj, 2026-10-14).
     */
    private static void lifesteal(LivingEntity defender, DamageSource source, float dealt) {
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == defender || !attacker.isAlive()
                || !meleeOrRanged(source)) {
            return;
        }
        double heal = DamageCalculator.lifesteal(dealt, stat(attacker, GeneratedStats.LIFESTEAL));
        if (heal > 0) {
            attacker.heal((float) heal);
        }
    }

    /** Last of the heal listeners, so it sees the amount the heal restores; a cancelled heal never reaches it. */
    static void onHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel) || entity.getHealth() <= 0) {
            return;
        }
        Overheal.onHeal(entity, event.getAmount());
        float healed = Math.min(event.getAmount(), entity.getMaxHealth() - entity.getHealth());
        if (healed >= CombatText.MIN_HEAL) {
            CombatText.heal(entity, healed);
        }
    }

    /**
     * The share of a {@code damage} hit the armor formula blocks for {@code defender} against an attacker with
     * {@code pierce} flat armor pierce and {@code shred} armor shred (architecture 9.1): vanilla's formula on the
     * defender's whole armor points (as vanilla's {@code getArmorValue}) less the pierce, and its armor toughness;
     * without the weapon's enchantments.
     */
    public static double armorShare(LivingEntity defender, double damage, double pierce, double shred) {
        return DamageCalculator.armorReduction(damage, defender.getArmorValue(),
                defender.getAttributeValue(Attributes.ARMOR_TOUGHNESS), pierce, shred);
    }

    private static void schoolHit(LivingIncomingDamageEvent event, ServerLevel level, LivingEntity defender,
            DamageSource source, Holder.Reference<SpellSchool> holder) {
        SpellSchool school = holder.value();
        DamageCalculator.Attacker attacker = source.getEntity() instanceof LivingEntity living
                ? new DamageCalculator.Attacker(stat(living, GeneratedStats.CRIT_CHANCE),
                        stat(living, GeneratedStats.CRIT_DAMAGE), stat(living, GeneratedStats.SPELL_POWER),
                        school.power().map(power -> stat(living, power)).orElse(0.0))
                : DamageCalculator.Attacker.NONE;
        double schoolResistance = school.resistance().map(stat -> stat(defender, stat)).orElse(0.0);
        double resistance = DamageCalculator.resistance(schoolResistance,
                stat(defender, GeneratedStats.SPELL_RESISTANCE), school.magic());
        DamageCalculator.SchoolHit hit = DamageCalculator.schoolHit(event.getAmount(), school.magic(), attacker,
                resistance, SchoolDamageSource.critOf(source), level.getRandom());
        event.setAmount((float) hit.damage());
        // Keep the defender's window: the hit sets the invulnerable time to this (vanilla refuses 0, and 1 opens no
        // window), and Post restores the lastHurt vanilla overwrites.
        event.setInvulnerabilityTicks(Math.max(1, defender.invulnerableTime));
        HitTracker.start(defender, new HitTracker.Hit(source, level.getGameTime(),
                Optional.<Holder<SpellSchool>>of(holder), hit.crit(), defender.lastHurt));
    }

    /**
     * The armor formula as NeoForge's armor reduction: vanilla's formula ({@link #armorShare}) on the armor the
     * attacker's armor pierce and shred leave, then vanilla's armor-effectiveness enchantment hook (Breach) on the
     * weapon, as {@code CombatRules#getDamageAfterAbsorb} applies it. Computed here rather than through
     * {@code CombatRules}, so a mod that replaces vanilla's formula there does not change ours. Damage in
     * {@code #minecraft:bypasses_armor} keeps vanilla's reduction, which is none.
     */
    private static float armorReduction(LivingEntity defender, DamageContainer container, float vanilla) {
        DamageSource source = container.getSource();
        if (source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            return vanilla;
        }
        float damage = container.getNewDamage();
        LivingEntity attacker = source.getEntity() instanceof LivingEntity living ? living : null;
        double share = attacker != null
                ? armorShare(defender, damage, stat(attacker, GeneratedStats.ARMOR_PIERCE),
                        stat(attacker, GeneratedStats.ARMOR_SHRED))
                : armorShare(defender, damage, 0, 0);
        ItemStack weapon = source.getWeaponItem();
        if (weapon != null && defender.level() instanceof ServerLevel level) {
            share = Mth.clamp(EnchantmentHelper.modifyArmorEffectiveness(level, weapon, defender, source,
                    (float) share), 0, 1);
        }
        return (float) (damage * share);
    }
}
