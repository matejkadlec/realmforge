package io.github.matejkadlec.realmforge.qa.command;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.matejkadlec.realmforge.dungeon.DungeonRegistries;
import io.github.matejkadlec.realmforge.dungeon.DungeonType;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceDimension;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceManager;
import io.github.matejkadlec.realmforge.dungeon.instance.Plot;
import io.github.matejkadlec.realmforge.dungeon.plan.DungeonPlan;
import io.github.matejkadlec.realmforge.dungeon.plan.Pace;
import io.github.matejkadlec.realmforge.dungeon.plan.PlanningException;
import io.github.matejkadlec.realmforge.dungeon.plan.RoomRole;
import io.github.matejkadlec.realmforge.dungeon.plan.Slot;
import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import io.github.matejkadlec.realmforge.qa.dungeon.LayoutMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /qa dungeon ...}: dungeon types and their layouts (W4a, W4b, architecture 15.3 and 15.5). Without a seed, a
 * random one is drawn and named, so a layout can be planned again.
 * <ul>
 *   <li>{@code /qa dungeon plan <type> [<seed>]} plans a layout and names its rooms by slot; the server log gets a
 *   top-down map and a legend.</li>
 *   <li>{@code /qa dungeon build <type> [<seed>]} plans a layout and builds it into a free plot for a fly-through, with
 *   a link that teleports you above its entrance; {@code /qa instance free <plot>} clears it again.</li>
 *   <li>{@code /qa dungeon enter <type> [<seed>]} plans a layout, builds it for you and moves you in at its entrance
 *   once it stands, as a dungeon portal will (W4d); {@code /qa instance leave} brings you back.</li>
 * </ul>
 */
final class DungeonCommands {
    /** How wide the map in the log may get, in characters. */
    private static final int MAP_WIDTH = 96;
    private static final SuggestionProvider<CommandSourceStack> TYPES = (context, builder) ->
            SharedSuggestionProvider.suggestResource(context.getSource().getServer().registryAccess()
                    .lookupOrThrow(DungeonRegistries.DUNGEON_TYPE).keySet(), builder);
    private static final DynamicCommandExceptionType UNKNOWN_TYPE =
            new DynamicCommandExceptionType(id -> Component.literal("Unknown dungeon type " + id));
    private static final DynamicCommandExceptionType NO_LAYOUT =
            new DynamicCommandExceptionType(message -> Component.literal("No layout: " + message));
    private static final SimpleCommandExceptionType NO_DIMENSION =
            new SimpleCommandExceptionType(Component.literal("This world has no realmforge:instances dimension"));
    private static final SimpleCommandExceptionType ALREADY_IN_RUN =
            new SimpleCommandExceptionType(Component.literal("You are already in an instance run; leave it first"));

    private DungeonCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("dungeon")
                .then(Commands.literal("plan").then(typeAndSeed(DungeonCommands::plan)))
                .then(Commands.literal("build").then(typeAndSeed(DungeonCommands::build)))
                .then(Commands.literal("enter").then(typeAndSeed(DungeonCommands::enter)));
    }

    private interface Action {
        int run(CommandContext<CommandSourceStack> context, long seed) throws CommandSyntaxException;
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Identifier> typeAndSeed(Action action) {
        return Commands.argument("type", IdentifierArgument.id()).suggests(TYPES)
                .executes(context -> action.run(context, context.getSource().getLevel().getRandom().nextLong()))
                .then(Commands.argument("seed", LongArgumentType.longArg())
                        .executes(context -> action.run(context, LongArgumentType.getLong(context, "seed"))));
    }

    private static int plan(CommandContext<CommandSourceStack> context, long seed) throws CommandSyntaxException {
        Identifier id = IdentifierArgument.getId(context, "type");
        DungeonPlan plan = plan(context, id, seed);
        BoundingBox bounds = plan.bounds();
        RealmforgeQa.LOGGER.info("Dungeon {} seed {}, attempt {}:\n{}\n{}", id, seed, plan.attempt(),
                String.join("\n", LayoutMap.map(plan, MAP_WIDTH)), String.join("\n", LayoutMap.legend(plan)));
        context.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "%s seed %d: %s; %d x %d x %d blocks. Map and legend in the server log.", id, seed, summary(plan),
                bounds.getXSpan(), bounds.getYSpan(), bounds.getZSpan())), false);
        return plan.rooms().size();
    }

    private static int build(CommandContext<CommandSourceStack> context, long seed) throws CommandSyntaxException {
        Identifier id = IdentifierArgument.getId(context, "type");
        DungeonPlan plan = plan(context, id, seed);
        InstanceManager manager = manager(context);
        Plot plot = manager.open(plan.toInstanceLayout(), List.of());
        BoundingBox entrance = plan.entrance().box();
        Vec3 above = Vec3.atLowerCornerOf(plot.origin()).add(entrance.getCenter().getX() + 0.5, entrance.maxY() + 2,
                entrance.getCenter().getZ() + 0.5);
        String teleport = String.format(Locale.ROOT, "/execute in %s run tp @s %.1f %.1f %.1f",
                InstanceDimension.LEVEL.identifier(), above.x, above.y, above.z);
        Component link = Component.literal("[fly over it]").withStyle(style -> style
                .withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent.RunCommand(teleport)));
        context.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Building %s seed %d (%s) in plot %d at %s ", id, seed, summary(plan), plot.id(),
                plot.origin().toShortString())).append(link), true);
        return plot.id();
    }

    private static int enter(CommandContext<CommandSourceStack> context, long seed) throws CommandSyntaxException {
        Identifier id = IdentifierArgument.getId(context, "type");
        ServerPlayer player = context.getSource().getPlayerOrException();
        InstanceManager manager = manager(context);
        if (manager.plotOf(player).isPresent()) {
            throw ALREADY_IN_RUN.create();
        }
        DungeonPlan plan = plan(context, id, seed);
        Plot plot = manager.open(plan.toInstanceLayout(), List.of(player.getUUID()));
        context.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Building %s seed %d (%s) in plot %d; you enter when it is built", id, seed, summary(plan),
                plot.id())), true);
        return plot.id();
    }

    private static InstanceManager manager(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        InstanceManager manager = InstanceManager.get(context.getSource().getServer());
        if (manager == null) {
            throw NO_DIMENSION.create();
        }
        return manager;
    }

    private static DungeonPlan plan(CommandContext<CommandSourceStack> context, Identifier id, long seed)
            throws CommandSyntaxException {
        DungeonType type = context.getSource().getServer().registryAccess()
                .lookupOrThrow(DungeonRegistries.DUNGEON_TYPE).getValue(id);
        if (type == null) {
            throw UNKNOWN_TYPE.create(id);
        }
        try {
            return type.plan(seed);
        } catch (PlanningException e) {
            throw NO_LAYOUT.create(e.getMessage());
        }
    }

    /**
     * "71 rooms (14 fights, 13 features, 5 treasure rooms): main path 56, 4 branches (10 rooms), 0 dead ends and 3
     * walls as caps".
     */
    static String summary(DungeonPlan plan) {
        BiFunction<Slot, Slot, Long> count = (first, second) -> plan.count(first) + plan.count(second);
        long walls = plan.rooms().stream().filter(room -> room.room().role() == RoomRole.WALL).count();
        Function<Pace, Long> paced = pace -> plan.rooms().stream()
                .filter(room -> room.room().pace().equals(Optional.of(pace))).count();
        return String.format(Locale.ROOT,
                "%d rooms (%d fights, %d features, %d treasure rooms): main path %d, %d branches (%d rooms), %d dead"
                        + " ends and %d walls as caps",
                plan.rooms().size() - walls, paced.apply(Pace.FIGHT), paced.apply(Pace.FEATURE),
                paced.apply(Pace.TREASURE), plan.count(Slot.PATH), plan.count(Slot.BRANCH_END),
                count.apply(Slot.BRANCH, Slot.BRANCH_END), plan.count(Slot.CAP) - walls, walls);
    }
}
