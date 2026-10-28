package io.github.matejkadlec.realmforge.qa.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.matejkadlec.realmforge.dungeon.DungeonRegistries;
import io.github.matejkadlec.realmforge.dungeon.DungeonType;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceDimension;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceManager;
import io.github.matejkadlec.realmforge.dungeon.instance.Plot;
import io.github.matejkadlec.realmforge.dungeon.instance.RoomPlacement;
import io.github.matejkadlec.realmforge.dungeon.instance.SpawnFinder;
import io.github.matejkadlec.realmforge.dungeon.plan.DungeonPlan;
import io.github.matejkadlec.realmforge.dungeon.plan.Pace;
import io.github.matejkadlec.realmforge.dungeon.plan.PlanningException;
import io.github.matejkadlec.realmforge.dungeon.plan.RoomRole;
import io.github.matejkadlec.realmforge.dungeon.plan.Slot;
import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import io.github.matejkadlec.realmforge.qa.dungeon.LayoutMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
 *   <li>{@code /qa dungeon room <name> [<n>]} moves you, inside your run, into its {@code n}th room (1 by default)
 *   whose template is named {@code name} ({@code egg}, {@code husk_temple}), onto the floor nearest the room's middle:
 *   the rooms a playtest looks at lie anywhere in a run of fifty (RLM-412, RLM-361, RLM-365).</li>
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
    private static final SimpleCommandExceptionType NOT_IN_RUN =
            new SimpleCommandExceptionType(Component.literal("You are not in a built instance run"));
    private static final Dynamic2CommandExceptionType NO_SUCH_ROOM = new Dynamic2CommandExceptionType(
            (name, count) -> Component.literal("Your run has " + count + " room(s) named " + name));
    private static final SimpleCommandExceptionType NO_FLOOR =
            new SimpleCommandExceptionType(Component.literal("That room has no floor to stand on"));
    /** The names of the rooms in the player's run, for {@code /qa dungeon room}. */
    private static final SuggestionProvider<CommandSourceStack> ROOM_NAMES = (context, builder) -> {
        InstanceManager manager = InstanceManager.get(context.getSource().getServer());
        ServerPlayer player = context.getSource().getPlayer();
        List<String> names = manager == null || player == null ? List.of() : manager.plotOf(player)
                .flatMap(Plot::layout).map(layout -> layout.rooms().stream().map(DungeonCommands::name).distinct()
                        .sorted().toList())
                .orElse(List.of());
        return SharedSuggestionProvider.suggest(names, builder);
    };

    private DungeonCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("dungeon")
                .then(Commands.literal("plan").then(typeAndSeed(DungeonCommands::plan)))
                .then(Commands.literal("build").then(typeAndSeed(DungeonCommands::build)))
                .then(Commands.literal("enter").then(typeAndSeed(DungeonCommands::enter)))
                .then(Commands.literal("room").then(Commands.argument("name", StringArgumentType.word())
                        .suggests(ROOM_NAMES).executes(context -> room(context, 1))
                        .then(Commands.argument("n", IntegerArgumentType.integer(1))
                                .executes(context -> room(context, IntegerArgumentType.getInteger(context, "n"))))));
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
        DungeonType type = type(context, id);
        DungeonPlan plan = plan(type, seed);
        InstanceManager manager = manager(context);
        Plot plot = manager.open(plan.toInstanceLayout(), List.of(), null, 0L, OptionalInt.of(type.level()), id);
        BoundingBox entrance = plan.entrance().box();
        Vec3 above = Vec3.atLowerCornerOf(plot.origin()).add(entrance.getCenter().getX() + 0.5, entrance.maxY() + 2,
                entrance.getCenter().getZ() + 0.5);
        String teleport = String.format(Locale.ROOT, "/execute in %s run tp @s %.1f %.1f %.1f",
                InstanceDimension.LEVEL.identifier(), above.x, above.y, above.z);
        Component link = Component.literal("[fly over it]").withStyle(style -> style
                .withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent.RunCommand(teleport)));
        context.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Building %s seed %d (%s) at level %d in plot %d at %s ", id, seed, summary(plan),
                plot.level().orElse(0), plot.id(), plot.origin().toShortString())).append(link), true);
        return plot.id();
    }

    private static int enter(CommandContext<CommandSourceStack> context, long seed) throws CommandSyntaxException {
        Identifier id = IdentifierArgument.getId(context, "type");
        ServerPlayer player = context.getSource().getPlayerOrException();
        InstanceManager manager = manager(context);
        if (manager.plotOf(player).isPresent()) {
            throw ALREADY_IN_RUN.create();
        }
        DungeonType type = type(context, id);
        DungeonPlan plan = plan(type, seed);
        Plot plot = manager.open(plan.toInstanceLayout(), List.of(player.getUUID()), null, 0L,
                OptionalInt.of(type.level()), id);
        context.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Building %s seed %d (%s) at level %d in plot %d; you enter when it is built", id, seed,
                summary(plan), plot.level().orElse(0), plot.id())), true);
        return plot.id();
    }

    private static int room(CommandContext<CommandSourceStack> context, int n) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        ServerPlayer player = context.getSource().getPlayerOrException();
        Plot plot = manager(context).plotOf(player).filter(run -> run.layout().isPresent())
                .orElseThrow(NOT_IN_RUN::create);
        List<RoomPlacement> rooms = plot.layout().orElseThrow().rooms();
        List<Integer> named = new ArrayList<>();
        for (int i = 0; i < rooms.size(); i++) {
            if (name(rooms.get(i)).equals(name)) {
                named.add(i);
            }
        }
        if (n > named.size()) {
            throw NO_SUCH_ROOM.create(name, named.size());
        }
        int index = named.get(n - 1);
        ServerLevel level = InstanceDimension.level(context.getSource().getServer());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos spot = SpawnFinder.find(plot.rooms().get(index), new SpawnFinder.Blocks() {
            @Override
            public boolean floor(int x, int y, int z) {
                pos.set(x, y, z);
                BlockState state = level.getBlockState(pos);
                return state.isFaceSturdy(level, pos, Direction.UP) && !state.is(Blocks.SPAWNER);
            }

            @Override
            public boolean open(int x, int y, int z) {
                pos.set(x, y, z);
                BlockState state = level.getBlockState(pos);
                return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
            }
        }).orElseThrow(NO_FLOOR::create);
        player.teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, java.util.Set.of(),
                player.getYRot(), player.getXRot(), true);
        context.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Moved to %s (%d of %d in"
                + " your run), room %d of the layout", rooms.get(index).template(), n, named.size(), index)), false);
        return index;
    }

    /** A room's name in {@code /qa dungeon room}: the last part of its template's id. */
    private static String name(RoomPlacement room) {
        String path = room.template().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
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
        return plan(type(context, id), seed);
    }

    private static DungeonType type(CommandContext<CommandSourceStack> context, Identifier id)
            throws CommandSyntaxException {
        DungeonType type = context.getSource().getServer().registryAccess()
                .lookupOrThrow(DungeonRegistries.DUNGEON_TYPE).getValue(id);
        if (type == null) {
            throw UNKNOWN_TYPE.create(id);
        }
        return type;
    }

    private static DungeonPlan plan(DungeonType type, long seed) throws CommandSyntaxException {
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
