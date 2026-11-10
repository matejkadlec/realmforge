package io.github.matejkadlec.realmforge.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * A netherite upgrade (RLM-566; architecture 13, gear design "Netherite upgrades"): a smithing table puts it on one
 * netherite piece, at most one per piece ({@link NetheriteUpgradeRecipe}), and the piece then gives the bonus of its
 * archetype while worn ({@link NetheriteUpgrades}). The pipeline also writes the upgrade's item and template, which the
 * recipe reads from its own file, so they are not read here.
 *
 * @param name translation key of the line the upgraded piece's tooltip shows, e.g. "Ruby Upgrade"
 * @param color the colour of that line
 * @param melee the bonus on a melee piece (vanilla's netherite armor)
 * @param caster the bonus on a caster piece (our netherite robes)
 */
public record NetheriteUpgrade(String name, int color, List<Bonus> melee, List<Bonus> caster) {
    public static final Codec<NetheriteUpgrade> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.fieldOf("name").forGetter(NetheriteUpgrade::name),
                    ExtraCodecs.STRING_RGB_COLOR.fieldOf("color").forGetter(NetheriteUpgrade::color),
                    ExtraCodecs.nonEmptyList(Bonus.CODEC.listOf()).fieldOf("melee").forGetter(NetheriteUpgrade::melee),
                    ExtraCodecs.nonEmptyList(Bonus.CODEC.listOf()).fieldOf("caster")
                            .forGetter(NetheriteUpgrade::caster))
            .apply(instance, NetheriteUpgrade::new));

    public NetheriteUpgrade {
        melee = List.copyOf(melee);
        caster = List.copyOf(caster);
    }

    /** The bonus on a piece of {@code archetype}. */
    public List<Bonus> bonus(Archetype archetype) {
        return archetype == Archetype.MELEE ? melee : caster;
    }

    /** The archetype of a piece, which picks its bonus (gear design: the upgrade follows the piece's archetype). */
    public enum Archetype {
        MELEE,
        CASTER
    }

    /**
     * One attribute modifier of the bonus, in game units (fractions for percentages).
     *
     * @param attribute the attribute it modifies
     * @param operation vanilla's operation
     * @param amount the amount
     */
    public record Bonus(Holder<Attribute> attribute, AttributeModifier.Operation operation, double amount) {
        public static final Codec<Bonus> CODEC = RecordCodecBuilder.<Bonus>create(instance -> instance.group(
                        Attribute.CODEC.fieldOf("attribute").forGetter(Bonus::attribute),
                        AttributeModifier.Operation.CODEC.fieldOf("operation").forGetter(Bonus::operation),
                        Codec.DOUBLE.fieldOf("amount").forGetter(Bonus::amount))
                .apply(instance, Bonus::new))
                .validate(bonus -> Double.isFinite(bonus.amount()) ? DataResult.success(bonus)
                        : DataResult.error(() -> "A netherite upgrade's amount is finite: " + bonus));
    }
}
