package io.github.matejkadlec.realmforge.qa.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.matejkadlec.realmforge.character.CharacterAttachments;
import io.github.matejkadlec.realmforge.spell.SpellAttachments;
import io.github.matejkadlec.realmforge.stats.CharacterStats;
import io.github.matejkadlec.realmforge.stats.StatsAttachments;
import io.github.matejkadlec.realmforge.talent.TalentAttachments;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;

/**
 * {@code /qa state show|reset [<entity>]}: the player state attachments (architecture 7) of a player or mob, yourself
 * by default. {@code show} prints each attachment the entity has, and names the ones it has none of yet (they read
 * as their defaults). {@code reset} removes them all, which syncs to clients: no character, spells or talents, a
 * fresh combat state and level 1.
 */
public final class StateCommands {
    /** Every attachment {@code show} and {@code reset} cover, in the order of architecture 7. */
    public static final List<Entry> ATTACHMENTS = List.of(
            new Entry("level", StatsAttachments.LEVEL),
            new Entry("character", CharacterAttachments.CHARACTER),
            new Entry("spellbook", SpellAttachments.SPELLBOOK),
            new Entry("combat", SpellAttachments.COMBAT),
            new Entry("talents", TalentAttachments.TALENTS));

    private static final SimpleCommandExceptionType NOT_LIVING =
            new SimpleCommandExceptionType(Component.literal("Only players and mobs have player state attachments"));

    private StateCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("state")
                .then(Commands.literal("show")
                        .executes(context -> show(context, context.getSource().getEntityOrException()))
                        .then(Commands.argument("entity", EntityArgument.entity())
                                .executes(context -> show(context, EntityArgument.getEntity(context, "entity")))))
                .then(Commands.literal("reset")
                        .executes(context -> reset(context, context.getSource().getEntityOrException()))
                        .then(Commands.argument("entity", EntityArgument.entity())
                                .executes(context -> reset(context, EntityArgument.getEntity(context, "entity")))));
    }

    /**
     * Removes every player state attachment from the entity and returns how many it had. A player's stats recompute at
     * the end of their next tick, so the race and class modifiers go with the character.
     */
    public static int reset(LivingEntity entity) {
        int removed = 0;
        for (Entry entry : ATTACHMENTS) {
            if (entity.hasData(entry.type().get())) {
                entity.removeData(entry.type().get());
                removed++;
            }
        }
        if (entity instanceof Player player) {
            CharacterStats.markDirty(player);
        }
        return removed;
    }

    private static int show(CommandContext<CommandSourceStack> context, Entity target) throws CommandSyntaxException {
        LivingEntity entity = living(target);
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("Player state of " + entity.getName().getString() + ":"), false);
        int present = 0;
        for (Entry entry : ATTACHMENTS) {
            String value = entity.getExistingData(entry.type().get()).map(String::valueOf).orElse("none (default)");
            source.sendSuccess(() -> Component.literal("  " + entry.name() + ": " + value), false);
            present += entity.hasData(entry.type().get()) ? 1 : 0;
        }
        return present;
    }

    private static int reset(CommandContext<CommandSourceStack> context, Entity target) throws CommandSyntaxException {
        LivingEntity entity = living(target);
        int removed = reset(entity);
        context.getSource().sendSuccess(() -> Component.literal("Reset the player state of "
                + entity.getName().getString() + " (" + removed + " attachments removed)"), true);
        return removed;
    }

    private static LivingEntity living(Entity target) throws CommandSyntaxException {
        if (target instanceof LivingEntity entity) {
            return entity;
        }
        throw NOT_LIVING.create();
    }

    /** One covered attachment and the name {@code show} prints for it. */
    public record Entry(String name, Supplier<? extends AttachmentType<?>> type) {
    }
}
