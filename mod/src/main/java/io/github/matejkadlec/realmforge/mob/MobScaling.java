package io.github.matejkadlec.realmforge.mob;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.core.CoreRegistries;
import io.github.matejkadlec.realmforge.core.Curve;
import io.github.matejkadlec.realmforge.generated.GeneratedCurves;
import io.github.matejkadlec.realmforge.stats.StatBonus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * What a mob's level adds (architecture 14.1), from the curves {@code realmforge:mob_health}, {@code mob_damage} and
 * {@code mob_armor} at that level: a multiple of the mob's maximum health and of the damage it deals, and armor points
 * on top of its own. The shipped curves add nothing at level 1, and a curve a data pack removed adds nothing.
 *
 * <p>Health and armor are permanent attribute modifiers ({@link #modifiers}), saved with the mob. Damage is a
 * multiplier on every hit the mob causes ({@code MobLevels}), so it reaches arrows, explosions and thrown potions as
 * well as melee, which alone reads the attack damage attribute.
 *
 * @param health the multiple of the mob's maximum health
 * @param damage the multiple of the damage the mob's hits deal
 * @param armor armor points added
 */
public record MobScaling(double health, double damage, double armor) {
    /** No scaling: what a level adds when its curves are missing. */
    public static final MobScaling NONE = new MobScaling(1, 1, 0);
    /** Every modifier id of the mob level source starts with this path: {@code realmforge:mob_level/<attribute>}. */
    public static final String MODIFIER_PATH = "mob_level/";
    public static final Identifier HEALTH_MODIFIER = Realmforge.id(MODIFIER_PATH + "max_health");
    public static final Identifier ARMOR_MODIFIER = Realmforge.id(MODIFIER_PATH + "armor");

    /** The scaling at {@code level} from these curves; an empty one keeps that part unscaled. */
    public static MobScaling at(int level, Optional<Curve> health, Optional<Curve> damage, Optional<Curve> armor) {
        return new MobScaling(health.map(curve -> curve.valueAt(level)).orElse(NONE.health()),
                damage.map(curve -> curve.valueAt(level)).orElse(NONE.damage()),
                armor.map(curve -> curve.valueAt(level)).orElse(NONE.armor()));
    }

    /** The scaling at {@code level} from the curves the world loaded. */
    public static MobScaling at(RegistryAccess registries, int level) {
        Registry<Curve> curves = registries.lookupOrThrow(CoreRegistries.CURVE);
        return at(level, curve(curves, GeneratedCurves.MOB_HEALTH), curve(curves, GeneratedCurves.MOB_DAMAGE),
                curve(curves, GeneratedCurves.MOB_ARMOR));
    }

    /** Whether a modifier id belongs to this source, so a recompute may remove it. */
    public static boolean owns(Identifier id) {
        return id.getNamespace().equals(Realmforge.MOD_ID) && id.getPath().startsWith(MODIFIER_PATH);
    }

    /**
     * The permanent modifiers for health and armor: maximum health times {@link #health} (on the base, as vanilla's
     * leader zombie bonus does), and {@link #armor} points. A part that changes nothing has no modifier.
     */
    public List<StatBonus> modifiers() {
        List<StatBonus> modifiers = new ArrayList<>(2);
        if (health != 1) {
            modifiers.add(new StatBonus(Attributes.MAX_HEALTH, new AttributeModifier(HEALTH_MODIFIER, health - 1,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE)));
        }
        if (armor != 0) {
            modifiers.add(new StatBonus(Attributes.ARMOR, new AttributeModifier(ARMOR_MODIFIER, armor,
                    AttributeModifier.Operation.ADD_VALUE)));
        }
        return modifiers;
    }

    private static Optional<Curve> curve(Registry<Curve> curves, ResourceKey<Curve> key) {
        return Optional.ofNullable(curves.getValue(key));
    }
}
