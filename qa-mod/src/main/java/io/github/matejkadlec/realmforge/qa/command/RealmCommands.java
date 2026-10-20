package io.github.matejkadlec.realmforge.qa.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.matejkadlec.realmforge.world.WorldRegistries;
import io.github.matejkadlec.realmforge.world.realm.Realm;
import io.github.matejkadlec.realmforge.world.realm.Realms;
import java.util.Locale;
import java.util.Set;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /qa realm} for realms without the realm portal (W2a; the portal, W2b, needs a frame and a signet):
 * <ul>
 *   <li>{@code /qa realm enter <realm> [<x> <z>]} takes the player to the realm's floor at x, z (0, 0 by default):
 *       the highest standing spot below y {@value #SEARCH_FROM_Y} with two blocks of air above it. It moves the player
 *       as {@code /tp} does, so a player without the realm's unlock is refused as on every way in;
 *   <li>{@code /qa realm unlock <realm>} and {@code /qa realm lock <realm>} grant and revoke the realm's unlock
 *       advancement, as {@code /advancement grant|revoke @s only realmforge:realm/<realm>} does.
 * </ul>
 */
public final class RealmCommands {
    /** Where the floor search starts: above Undervault's floor (y 32), below its ceiling (about y 100). */
    static final int SEARCH_FROM_Y = 64;

    private static final SuggestionProvider<CommandSourceStack> REALMS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(context.getSource().getServer().registryAccess()
                    .lookupOrThrow(WorldRegistries.REALM).keySet(), builder);
    private static final DynamicCommandExceptionType UNKNOWN_REALM =
            new DynamicCommandExceptionType(id -> Component.literal("Unknown realm " + id));
    private static final DynamicCommandExceptionType NO_LEVEL =
            new DynamicCommandExceptionType(id -> Component.literal("The dimension " + id + " is not loaded"));
    private static final DynamicCommandExceptionType NO_FLOOR =
            new DynamicCommandExceptionType(where -> Component.literal("No floor to stand on at " + where));

    private RealmCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("realm")
                .then(Commands.literal("enter").then(Commands.argument("realm", IdentifierArgument.id()).suggests(REALMS)
                        .executes(context -> enter(context, 0, 0))
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(context -> enter(context, IntegerArgumentType.getInteger(context, "x"),
                                                IntegerArgumentType.getInteger(context, "z")))))))
                .then(Commands.literal("unlock").then(Commands.argument("realm", IdentifierArgument.id())
                        .suggests(REALMS).executes(context -> unlock(context, true))))
                .then(Commands.literal("lock").then(Commands.argument("realm", IdentifierArgument.id())
                        .suggests(REALMS).executes(context -> unlock(context, false))));
    }

    private static int enter(CommandContext<CommandSourceStack> context, int x, int z) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Realm realm = realm(context);
        ServerLevel level = context.getSource().getServer().getLevel(realm.dimension());
        if (level == null) {
            throw NO_LEVEL.create(realm.dimension().identifier());
        }
        BlockPos floor = floor(level, x, z);
        if (floor == null) {
            throw NO_FLOOR.create(x + ", " + z);
        }
        boolean moved = player.teleportTo(level, floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5, Set.of(),
                player.getYRot(), player.getXRot(), true);
        if (!moved) {
            context.getSource().sendFailure(Component.literal("Refused: you have not unlocked "
                    + IdentifierArgument.getId(context, "realm") + " (/qa realm unlock)"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Entered %s at %d %d %d",
                IdentifierArgument.getId(context, "realm"), floor.getX(), floor.getY() + 1, floor.getZ())), false);
        return 1;
    }

    private static int unlock(CommandContext<CommandSourceStack> context, boolean grant) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Identifier id = IdentifierArgument.getId(context, "realm");
        realm(context);
        Identifier unlock = Realms.unlockAdvancement(ResourceKey.create(WorldRegistries.REALM, id));
        AdvancementHolder holder = context.getSource().getServer().getAdvancements().get(unlock);
        if (holder == null) {
            throw UNKNOWN_REALM.create(unlock);
        }
        for (String criterion : holder.value().criteria().keySet()) {
            if (grant) {
                player.getAdvancements().award(holder, criterion);
            } else {
                player.getAdvancements().revoke(holder, criterion);
            }
        }
        context.getSource().sendSuccess(() -> Component.literal((grant ? "Unlocked " : "Locked ") + id), false);
        return 1;
    }

    private static Realm realm(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Identifier id = IdentifierArgument.getId(context, "realm");
        Realm realm = context.getSource().registryAccess().lookupOrThrow(WorldRegistries.REALM).getValue(id);
        if (realm == null) {
            throw UNKNOWN_REALM.create(id);
        }
        return realm;
    }

    /** The highest block below {@link #SEARCH_FROM_Y} whose top a player stands on, with two blocks of air above it. */
    static BlockPos floor(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, SEARCH_FROM_Y, z);
        for (int y = SEARCH_FROM_Y; y > level.getMinY(); y--) {
            pos.setY(y);
            if (level.getBlockState(pos.above()).isAir() && level.getBlockState(pos.above(2)).isAir()
                    && level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP)) {
                return pos.immutable();
            }
        }
        return null;
    }
}
