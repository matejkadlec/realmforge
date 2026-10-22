package io.github.matejkadlec.realmforge.combat;

import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

/**
 * What the damage pipeline reports on the game bus (architecture 4.4: higher features react to lower ones through
 * our own events), on the logical server only: a hit that went through, and a hit that was dodged. The proc engine
 * (C3a, C5) and the QA mod's combat log listen. The entity is the one hit.
 */
public abstract class CombatEvent extends LivingEvent {
    private final DamageSource source;

    private CombatEvent(LivingEntity entity, DamageSource source) {
        super(entity);
        this.source = source;
    }

    public DamageSource getSource() {
        return source;
    }

    /**
     * A hit that dealt its damage, posted from {@code LivingDamageEvent.Post}.
     *
     * @see net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post
     */
    public static final class Hit extends CombatEvent {
        private final Optional<Holder<SpellSchool>> school;
        private final boolean crit;
        private final float originalDamage;
        private final float armorReduction;
        private final float inflictedDamage;
        private final float healthDamage;

        public Hit(LivingEntity entity, DamageSource source, Optional<Holder<SpellSchool>> school, boolean crit,
                float originalDamage, float armorReduction, float inflictedDamage, float healthDamage) {
            super(entity, source);
            this.school = school;
            this.crit = crit;
            this.originalDamage = originalDamage;
            this.armorReduction = armorReduction;
            this.inflictedDamage = inflictedDamage;
            this.healthDamage = healthDamage;
        }

        /** The hit's spell school, if its damage type is one. */
        public Optional<Holder<SpellSchool>> getSchool() {
            return school;
        }

        /**
         * Whether the hit was critical: our crit roll for a player's melee, a player's projectile at full strength (an
         * arrow from a fully drawn bow or a crossbow, a thrown trident) and school hits. Mobs' melee and projectiles
         * never crit.
         */
        public boolean isCrit() {
            return crit;
        }

        /** The damage the hit started with, before the pipeline and vanilla changed it. */
        public float getOriginalDamage() {
            return originalDamage;
        }

        /** What armor blocked: our armor formula's share of the hit. */
        public float getArmorReduction() {
            return armorReduction;
        }

        /** The damage the hit dealt after every reduction, absorption hearts included. */
        public float getInflictedDamage() {
            return inflictedDamage;
        }

        /** The damage taken from health, after absorption hearts took their share. */
        public float getHealthDamage() {
            return healthDamage;
        }
    }

    /** A hit the entity dodged: it dealt nothing, and its damage events stopped at {@code LivingIncomingDamageEvent}. */
    public static final class Dodge extends CombatEvent {
        public Dodge(LivingEntity entity, DamageSource source) {
            super(entity, source);
        }
    }
}
