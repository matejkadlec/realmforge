package io.github.matejkadlec.realmforge.spell;

import io.github.matejkadlec.realmforge.combat.DamageNumbers;
import io.github.matejkadlec.realmforge.combat.SpellSchool;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.attributes.Attribute;

/**
 * A spell's tooltip in the spellbook (architecture 11.6, 18; C3b, RLM-25), built from data with live numbers: its name,
 * its rank, its cost at that rank (a channel's per second), its range, its cast after Cast Time Reduction, its cooldown
 * after Cooldown Reduction, and its description with the placeholders filled ({@link SpellText}): its damage numbers
 * with their school's icon and colour. A passive spell (RLM-541) shows its rank's description and the next rank's
 * instead of cost, range and cast. A view model in common code, so the screen only draws it.
 */
public final class SpellTooltip {
    private static final double TICKS_PER_SECOND = 20;

    private SpellTooltip() {
    }

    /**
     * The tooltip's lines for {@code spell} at {@code level} and a caster with {@code stats}, without passive spells.
     * The description is translated here, with the game's current language ({@link Language#getInstance()}), because
     * its placeholders are filled after translation: call it on the client.
     */
    public static List<Component> lines(Spell spell, int level, ToDoubleFunction<Holder<Attribute>> stats,
            Function<ResourceKey<SpellSchool>, Optional<SpellSchool>> schools) {
        return lines(spell, level, stats, schools, 0);
    }

    /**
     * The tooltip's lines for {@code spell} at {@code level} and a caster with {@code stats} whose passive spells add
     * {@code passiveDamage} to its hits (RLM-541). A passive spell's tooltip has its name, rank, "Passive", its
     * description at the rank and, below the highest rank, the next rank's ({@link #passiveLines}).
     */
    public static List<Component> lines(Spell spell, int level, ToDoubleFunction<Holder<Attribute>> stats,
            Function<ResourceKey<SpellSchool>, Optional<SpellSchool>> schools, double passiveDamage) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(spell.name()).withStyle(ChatFormatting.WHITE));
        if (spell.maxLevel() > 1) {
            lines.add(Component.translatable(GeneratedTexts.SPELL_TOOLTIP_RANK, level, spell.maxLevel())
                    .withStyle(ChatFormatting.GRAY));
        }
        if (spell.passive().isPresent()) {
            lines.addAll(passiveLines(spell, spell.passive().get(), level));
            return lines;
        }
        String costKey = spell.cast().type() == SpellCast.Type.CHANNEL ? GeneratedTexts.SPELL_TOOLTIP_COST_PER_SECOND
                : GeneratedTexts.SPELL_TOOLTIP_COST;
        spell.cost().ifPresent(cost -> cost.resource().unwrapKey().ifPresent(type -> lines.add(Component.translatable(
                costKey, DamageNumbers.format(spell.costAt(level)), CastTexts.resourceName(type)))));
        double reach = spell.targeting().reach();
        lines.add(reach > 0
                ? Component.translatable(GeneratedTexts.SPELL_TOOLTIP_RANGE, SpellText.decimal(reach))
                : Component.translatable(GeneratedTexts.SPELL_TOOLTIP_SELF));
        lines.add(cast(spell.cast(), stats.applyAsDouble(GeneratedStats.CAST_TIME_REDUCTION)));
        int cooldown = SpellScaling.cooldownTicks(spell.cooldownTicks(),
                stats.applyAsDouble(GeneratedStats.COOLDOWN_REDUCTION));
        if (cooldown > 0) {
            lines.add(Component.translatable(GeneratedTexts.SPELL_TOOLTIP_COOLDOWN, seconds(cooldown)));
        }
        lines.add(SpellText.fill(Language.getInstance().getOrDefault(spell.description()),
                SpellText.values(spell, level, stats, schools, passiveDamage), SpellText.placeholderSchools(spell),
                schools).withStyle(ChatFormatting.YELLOW));
        return lines;
    }

    /**
     * A passive spell's lines below its rank (RLM-541): "Passive", its description at {@code level} and, below its
     * highest rank, "Next rank:" with the description at the next one.
     */
    static List<Component> passiveLines(Spell spell, Passive passive, int level) {
        List<Component> lines = new ArrayList<>();
        String description = Language.getInstance().getOrDefault(spell.description());
        lines.add(Component.translatable(GeneratedTexts.SPELL_TOOLTIP_PASSIVE).withStyle(ChatFormatting.GRAY));
        lines.add(SpellText.fill(description, SpellText.passiveValues(passive, level), Map.of(),
                school -> Optional.empty()).withStyle(ChatFormatting.YELLOW));
        if (level < spell.maxLevel()) {
            lines.add(Component.translatable(GeneratedTexts.SPELL_TOOLTIP_NEXT_RANK).withStyle(ChatFormatting.GRAY));
            lines.add(SpellText.fill(description, SpellText.passiveValues(passive, level + 1), Map.of(),
                    school -> Optional.empty()).withStyle(ChatFormatting.YELLOW));
        }
        return lines;
    }

    /** The cast line: instant, a cast time or a channel, its time after Cast Time Reduction. */
    static Component cast(SpellCast cast, double castTimeReduction) {
        return switch (cast.type()) {
            case INSTANT -> Component.translatable(GeneratedTexts.SPELL_TOOLTIP_INSTANT);
            case CAST_TIME -> Component.translatable(GeneratedTexts.SPELL_TOOLTIP_CAST_TIME,
                    seconds(SpellScaling.castTicks(cast.durationTicks(), castTimeReduction)));
            case CHANNEL -> Component.translatable(GeneratedTexts.SPELL_TOOLTIP_CHANNEL, seconds(
                    SpellScaling.castTicks(cast.intervalTicks(), castTimeReduction) * cast.tickCount()));
        };
    }

    private static String seconds(int ticks) {
        return SpellText.decimal(ticks / TICKS_PER_SECOND);
    }
}
