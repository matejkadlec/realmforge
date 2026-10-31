package io.github.matejkadlec.realmforge.combat;

import java.util.Optional;
import net.minecraft.ChatFormatting;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

/**
 * The public API for spell schools (architecture 4.3): which school a hit belongs to, and damage sources of a school's
 * type for the spell engine, abilities and bosses. A school is identified by its damage type, so any damage source of
 * that type is a school hit, the {@code /damage} command's included.
 */
public final class SpellSchools {
    /** The atlas of the GUI's sprites, where the school icons are. */
    private static final Identifier GUI_ATLAS = Identifier.withDefaultNamespace("gui");
    private static final String ICON_PREFIX = "school/";

    private SpellSchools() {
    }

    /** The school whose damage type the hit has, if any. Our registry holds a handful of schools, so a scan is cheap. */
    public static Optional<Holder.Reference<SpellSchool>> of(HolderLookup.Provider registries, DamageSource source) {
        Optional<ResourceKey<DamageType>> type = source.typeHolder().unwrapKey();
        if (type.isEmpty()) {
            return Optional.empty();
        }
        return registries.lookup(CombatRegistries.SPELL_SCHOOL).flatMap(schools -> schools.listElements()
                .filter(school -> school.value().damageType().equals(type.get()))
                .findFirst());
    }

    /**
     * A damage source of the school's type, dealt by {@code causing} (the caster) through {@code direct} (the caster
     * itself, or its projectile); either may be null for a hit nobody caused, such as a trap.
     */
    public static DamageSource damage(RegistryAccess registries, ResourceKey<SpellSchool> school,
            @Nullable Entity direct, @Nullable Entity causing) {
        return new DamageSource(damageType(registries, school), direct, causing);
    }

    /**
     * A damage source of the school's type, as {@link #damage(RegistryAccess, ResourceKey, Entity, Entity)}, that
     * crits by {@code crit}: a spell's crit data (architecture 11.1). {@code procOrigin} names the proc whose spell
     * deals it, if one does, so no proc reacts to the hit (architecture 12).
     */
    public static SchoolDamageSource damage(RegistryAccess registries, ResourceKey<SpellSchool> school,
            @Nullable Entity direct, @Nullable Entity causing, CritRule crit, Optional<Identifier> procOrigin) {
        return damage(registries, school, direct, causing, crit, HitBonus.NONE, procOrigin);
    }

    /**
     * A damage source of the school's type, as {@link #damage(RegistryAccess, ResourceKey, Entity, Entity, CritRule,
     * Optional)}, that also adds {@code bonus} to its attacker's armor shred and spell penetration (C4).
     */
    public static SchoolDamageSource damage(RegistryAccess registries, ResourceKey<SpellSchool> school,
            @Nullable Entity direct, @Nullable Entity causing, CritRule crit, HitBonus bonus,
            Optional<Identifier> procOrigin) {
        return new SchoolDamageSource(damageType(registries, school), direct, causing, crit, bonus, procOrigin);
    }

    /**
     * One tick of a damage over time of the school, {@code tick} (RLM-539), from {@code caster} or from nobody once the
     * caster is gone: it never crits, is never dodged or blocked, and may ignore armor.
     */
    public static SchoolDamageSource tick(RegistryAccess registries, ResourceKey<SpellSchool> school,
            @Nullable Entity caster, Optional<Identifier> procOrigin, SchoolDamageSource.Tick tick) {
        return new SchoolDamageSource(damageType(registries, school), caster, caster, CritRule.NONE, HitBonus.NONE,
                procOrigin, Optional.of(tick));
    }

    private static Holder<DamageType> damageType(RegistryAccess registries, ResourceKey<SpellSchool> school) {
        SpellSchool value = registries.lookupOrThrow(CombatRegistries.SPELL_SCHOOL).getValueOrThrow(school);
        return registries.lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(value.damageType());
    }

    /**
     * The school's icon as a piece of text: its GUI sprite {@code <namespace>:school/<path>}
     * ({@code textures/gui/sprites/school/}), drawn inline as an 8 px glyph in white, so the text around it does not tint
     * it. As plain text (narration, copying) it reads as the school's name.
     */
    public static Component icon(ResourceKey<SpellSchool> key, SpellSchool school) {
        Identifier sprite = key.identifier().withPrefix(ICON_PREFIX);
        return Component.object(new AtlasSprite(GUI_ATLAS, sprite),
                        Component.translatable(school.name()).append(" "))
                .withStyle(ChatFormatting.WHITE);
    }
}
