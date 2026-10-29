package io.github.matejkadlec.realmforge.compat.apothic_attributes;

import io.github.matejkadlec.realmforge.mixin.RangedAttributeAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;

/**
 * Apothic Attributes, which Apotheosis needs, neutralized where it would roll a second crit or dodge next to ours
 * (architecture 9.1 and 19.4, ADR 0038). Registered only when the mod is loaded ({@code CompatFeature}); its
 * attributes are looked up by id, so the core mod has no compile-time dependency on it.
 * <ul>
 *   <li>Every entity type gets its crit chance 0 and crit damage 1.0 as base values, after Apothic Attributes added
 *   them at 5 % and 150 % ({@code EntityAttributeModificationEvent} at LOW priority, a phase FML runs after every
 *   mod's NORMAL listeners).</li>
 *   <li>Its crit damage is capped at 1.0 and its dodge chance at 0, so nothing lifts them: its crit needs crit damage
 *   above 1 ({@code AttributeEvents#apothCriticalStrike}), and its dodge rolls a random float against the dodge
 *   chance ({@code AttributeEvents#isDodging}), which at 0 dodges only when the float is exactly 0, once in 2^24
 *   rolls. Apotheosis's affixes, gems and tier augments grant our crit and dodge instead since RLM-272
 *   ({@code content/apotheosis_stat_remaps/}); the caps stay, as they cost nothing and guard against another mod's
 *   data or a later Apotheosis release that grants its own again. They use the accessor of
 *   {@code stats/AttributeCaps} ({@link RangedAttributeAccessor}).</li>
 * </ul>
 * Vanilla's jump crits keep vanilla's multiplier: Apothic Attributes raises it to its crit damage only when that is
 * higher, and a player's melee crit is ours anyway ({@code DamagePipeline#onCriticalHit}). Tested by the QA mod's
 * {@code ApothicAttributesGameTests}.
 */
public final class ApothicAttributesCompat {
    public static final String MOD_ID = "apothic_attributes";
    public static final Identifier CRIT_CHANCE = Identifier.fromNamespaceAndPath(MOD_ID, "crit_chance");
    public static final Identifier CRIT_DAMAGE = Identifier.fromNamespaceAndPath(MOD_ID, "crit_damage");
    public static final Identifier DODGE_CHANCE = Identifier.fromNamespaceAndPath(MOD_ID, "dodge_chance");

    /** The base values and caps that switch its crit and dodge off: no chance, and a crit that adds nothing. */
    public static final double CRIT_CHANCE_BASE = 0;
    public static final double CRIT_DAMAGE_NEUTRAL = 1.0;
    public static final double DODGE_CHANCE_MAX = 0;

    private ApothicAttributesCompat() {
    }

    /** Registers the listeners. Called once from {@code CompatFeature} when Apothic Attributes is loaded. */
    public static void register(IEventBus modBus) {
        modBus.addListener(EventPriority.LOW, EntityAttributeModificationEvent.class,
                ApothicAttributesCompat::onAttributeModification);
        // Its attributes exist from registration on, so their caps are set at common setup, on both sides, as each
        // clamps attribute values itself; deferred work runs serially on the main thread.
        modBus.addListener(FMLCommonSetupEvent.class,
                event -> event.enqueueWork(ApothicAttributesCompat::capAttributes));
    }

    static void onAttributeModification(EntityAttributeModificationEvent event) {
        Holder<Attribute> critChance = attribute(CRIT_CHANCE);
        Holder<Attribute> critDamage = attribute(CRIT_DAMAGE);
        for (EntityType<? extends LivingEntity> type : event.getTypes()) {
            if (event.has(type, critChance)) {
                event.add(type, critChance, CRIT_CHANCE_BASE);
            }
            if (event.has(type, critDamage)) {
                event.add(type, critDamage, CRIT_DAMAGE_NEUTRAL);
            }
        }
    }

    static void capAttributes() {
        cap(CRIT_DAMAGE, CRIT_DAMAGE_NEUTRAL);
        cap(DODGE_CHANCE, DODGE_CHANCE_MAX);
    }

    private static void cap(Identifier id, double max) {
        ((RangedAttributeAccessor) (RangedAttribute) attribute(id).value()).realmforge$setMaxValue(max);
    }

    private static Holder<Attribute> attribute(Identifier id) {
        return BuiltInRegistries.ATTRIBUTE.get(id)
                .orElseThrow(() -> new IllegalStateException("Apothic Attributes has no attribute " + id));
    }
}
