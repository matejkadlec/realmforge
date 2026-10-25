package io.github.matejkadlec.realmforge.qa.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.matejkadlec.realmforge.qa.combat.CastLog;
import io.github.matejkadlec.realmforge.spell.CastResult;
import io.github.matejkadlec.realmforge.spell.CombatState;
import io.github.matejkadlec.realmforge.spell.ResourceType;
import io.github.matejkadlec.realmforge.spell.ResourceTypes;
import io.github.matejkadlec.realmforge.spell.Resources;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellAim;
import io.github.matejkadlec.realmforge.spell.SpellAttachments;
import io.github.matejkadlec.realmforge.spell.SpellCaster;
import io.github.matejkadlec.realmforge.spell.SpellRegistries;
import io.github.matejkadlec.realmforge.spell.SpellTarget;
import io.github.matejkadlec.realmforge.spell.Spellbook;
import io.github.matejkadlec.realmforge.spell.Spells;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * {@code /qa spell} drives the spell engine (C3a-1, architecture 11) and sets up the spell bar (C3b), on the executing
 * entity ({@code /execute as} makes a mob the caster):
 * <ul>
 *   <li>{@code /qa spell learn <spell> [<level>]} teaches the executing player a spell, at level 1 by default; it goes
 *   into the spell bar's first empty slot.</li>
 *   <li>{@code /qa spell bar <slot> [<spell>]} puts a known spell into a bar slot (1 to 8), or empties it; slot 9 is
 *   the racial slot beside the bar (X), which takes any spell here until the race fills it (C4).</li>
 *   <li>{@code /qa spell cast <spell> [<target>]} casts it at the target (none by default: a self or ally spell then
 *   takes the caster, a ground point or a direction follows where the caster looks) and says why when the cast is
 *   refused; a cast-time or channelled spell begins (C3a-2).</li>
 *   <li>{@code /qa spell cast_at <spell> <pos>} casts it at a point: a ground point there, clamped to its range along
 *   the line toward it when farther (RLM-346), or a direction toward it (C3a-3).</li>
 *   <li>{@code /qa spell cancel} cancels the cast in progress.</li>
 *   <li>{@code /qa spell resource <mana|rage> <amount>} sets a resource pool, within its maximum.</li>
 *   <li>{@code /qa spell reset} ends every cooldown, the global one included.</li>
 *   <li>{@code /qa spell show} prints the known spells, the pools, the running cooldowns, the cast in progress and
 *   how the last cast-time or channelled spell ended.</li>
 * </ul>
 */
public final class SpellCommands {
    private static final SuggestionProvider<CommandSourceStack> SPELLS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(context.getSource().getServer().registryAccess()
                    .lookupOrThrow(SpellRegistries.SPELL).keySet(), builder);
    private static final List<DeferredHolder<ResourceType, ResourceType>> RESOURCES =
            List.of(ResourceTypes.MANA, ResourceTypes.RAGE);

    private SpellCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> resource = Commands.literal("resource");
        for (DeferredHolder<ResourceType, ResourceType> type : RESOURCES) {
            resource.then(Commands.literal(type.getId().getPath())
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                            .executes(context -> setResource(context, type))));
        }
        return Commands.literal("spell")
                .then(Commands.literal("learn")
                        .then(Commands.argument("spell", IdentifierArgument.id()).suggests(SPELLS)
                                .executes(context -> learn(context, 1))
                                .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                        .executes(context -> learn(context,
                                                IntegerArgumentType.getInteger(context, "level"))))))
                .then(Commands.literal("cast")
                        .then(Commands.argument("spell", IdentifierArgument.id()).suggests(SPELLS)
                                .executes(context -> cast(context, SpellAim.NONE))
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(context -> castAt(context,
                                                EntityArgument.getEntity(context, "target"))))))
                .then(Commands.literal("cast_at")
                        .then(Commands.argument("spell", IdentifierArgument.id()).suggests(SPELLS)
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(context -> cast(context,
                                                SpellAim.at(Vec3Argument.getVec3(context, "pos")))))))
                .then(Commands.literal("bar")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, Spellbook.SLOT_COUNT))
                                .executes(context -> bar(context, Optional.empty()))
                                .then(Commands.argument("spell", IdentifierArgument.id()).suggests(SPELLS)
                                        .executes(context -> bar(context, Optional.of(spell(context)))))))
                .then(Commands.literal("cancel").executes(SpellCommands::cancel))
                .then(resource)
                .then(Commands.literal("reset").executes(SpellCommands::reset))
                .then(Commands.literal("show").executes(SpellCommands::show));
    }

    private static ResourceKey<Spell> spell(CommandContext<CommandSourceStack> context) {
        return ResourceKey.create(SpellRegistries.SPELL, IdentifierArgument.getId(context, "spell"));
    }

    private static LivingEntity caster(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (context.getSource().getEntityOrException() instanceof LivingEntity living) {
            return living;
        }
        throw EntityArgument.NO_ENTITIES_FOUND.create();
    }

    private static int learn(CommandContext<CommandSourceStack> context, int level) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ResourceKey<Spell> key = spell(context);
        Optional<Holder.Reference<Spell>> spell = Spells.get(player.registryAccess(), key);
        if (spell.isEmpty() || level > spell.get().value().maxLevel()) {
            context.getSource().sendFailure(Component.literal(spell.isEmpty()
                    ? "No spell " + key.identifier()
                    : key.identifier() + " has levels 1 to " + spell.get().value().maxLevel()));
            return 0;
        }
        Spells.learn(player, spell.get(), level);
        context.getSource().sendSuccess(() -> Component.literal(player.getPlainTextName() + " knows "
                + key.identifier() + " at level " + level), true);
        return 1;
    }

    private static int bar(CommandContext<CommandSourceStack> context, Optional<ResourceKey<Spell>> spell)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int slot = IntegerArgumentType.getInteger(context, "slot") - 1;
        if (slot == Spellbook.RACIAL_SLOT) {
            Spells.setRacialSpell(player, spell);
        } else if (!Spells.placeOnBar(player, slot, spell)
                && !player.getData(SpellAttachments.SPELLBOOK).slot(slot).equals(spell)) {
            context.getSource().sendFailure(Component.literal(player.getPlainTextName() + " does not know "
                    + spell.map(key -> key.identifier().toString()).orElse("that spell")));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Slot " + (slot + 1) + " of "
                + player.getPlainTextName() + " holds " + spell.map(key -> key.identifier().toString())
                .orElse("nothing")), true);
        return 1;
    }

    private static int castAt(CommandContext<CommandSourceStack> context, Entity target)
            throws CommandSyntaxException {
        if (!(target instanceof LivingEntity living)) {
            context.getSource().sendFailure(Component.literal("Spells target living entities only"));
            return 0;
        }
        return cast(context, SpellAim.at(living));
    }

    private static int cast(CommandContext<CommandSourceStack> context, SpellAim aim) throws CommandSyntaxException {
        LivingEntity caster = caster(context);
        ResourceKey<Spell> key = spell(context);
        CastResult result = SpellCaster.cast(caster, key, aim);
        if (!result.success()) {
            context.getSource().sendFailure(Component.literal(key.identifier() + " refused: "
                    + result.failureOrNull().getSerializedName()));
            return 0;
        }
        String at = result.target().map(SpellCommands::describe).orElse("?");
        String verb = result.begun() ? " began casting " : " cast ";
        context.getSource().sendSuccess(() -> Component.literal(caster.getPlainTextName() + verb
                + key.identifier() + " at " + at), false);
        return 1;
    }

    /** An entity target by its name, a point or a direction by where it lands. */
    private static String describe(SpellTarget target) {
        if (target.entity().isPresent()) {
            return target.entity().get().getPlainTextName();
        }
        Vec3 at = target.position();
        return String.format(Locale.ROOT, "%s (%.1f, %.1f, %.1f)",
                target.kind() == SpellTarget.Kind.POINT ? "the point" : "the direction to", at.x, at.y, at.z);
    }

    private static int cancel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        LivingEntity caster = caster(context);
        if (!Spells.cancelCast(caster)) {
            context.getSource().sendFailure(Component.literal(caster.getPlainTextName() + " is not casting"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Cancelled the cast of "
                + caster.getPlainTextName()), true);
        return 1;
    }

    private static int setResource(CommandContext<CommandSourceStack> context, Holder<ResourceType> type)
            throws CommandSyntaxException {
        LivingEntity holder = caster(context);
        float set = Resources.set(holder, type, DoubleArgumentType.getDouble(context, "amount"));
        context.getSource().sendSuccess(() -> Component.literal("Set " + type.getRegisteredName() + " of "
                + holder.getPlainTextName() + " to " + set), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        LivingEntity caster = caster(context);
        Spells.resetCooldowns(caster);
        context.getSource().sendSuccess(() -> Component.literal("Ended the cooldowns of "
                + caster.getPlainTextName()), true);
        return 1;
    }

    private static int show(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        LivingEntity caster = caster(context);
        CombatState state = caster.getData(SpellAttachments.COMBAT);
        long now = caster.level().getGameTime();
        StringBuilder text = new StringBuilder(caster.getPlainTextName());
        if (caster instanceof ServerPlayer player) {
            text.append("\nKnown: ").append(player.getData(SpellAttachments.SPELLBOOK).spells().entrySet().stream()
                    .map(entry -> entry.getKey().identifier() + " " + entry.getValue()).sorted().toList());
        }
        text.append("\nPools: ").append(state.resources().entrySet().stream()
                .map(entry -> entry.getKey().identifier() + " " + entry.getValue()).sorted().toList());
        text.append("\nCooldowns (ticks left): ").append(state.cooldowns().entrySet().stream()
                .filter(entry -> entry.getValue() > now)
                .map(entry -> entry.getKey().identifier() + " " + (entry.getValue() - now)).sorted().toList());
        text.append("\nGlobal cooldown (ticks left): ").append(Math.max(0, state.globalCooldownEnd() - now));
        text.append("\nCasting: ").append(state.currentCast()
                .map(cast -> cast.spell().identifier() + (cast.channel() ? " (channel)" : "") + ", "
                        + Math.max(0, cast.endGameTime() - now) + " of " + cast.durationTicks() + " ticks left")
                .orElse("nothing"));
        text.append("\nLast cast ended: ").append(CastLog.last(caster)
                .map(ended -> ended.spell().identifier() + " " + ended.end().getSerializedName())
                .orElse("none"));
        context.getSource().sendSuccess(() -> Component.literal(text.toString()), false);
        return 1;
    }
}
