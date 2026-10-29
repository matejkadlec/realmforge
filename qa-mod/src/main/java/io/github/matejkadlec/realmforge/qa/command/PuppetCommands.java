package io.github.matejkadlec.realmforge.qa.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.matejkadlec.realmforge.generated.GeneratedItems;
import io.github.matejkadlec.realmforge.qa.gametest.QaPlayers;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /qa puppet scroll}: logs in a fake player, Puppet, three blocks in front of you and facing you, charging a
 * Scroll of Oblivion. Nothing ticks it, so it stays in the middle of the charge, and your own client shows how other
 * players see a charging scroll (RLM-416) without a second client. It plays in creative mode, so mobs leave it alone.
 * Running the command again replaces the puppet; {@code /qa puppet remove} logs it out.
 */
public final class PuppetCommands {
    /** The puppet's profile: one fixed id, so a new puppet replaces the old one and a world keeps one save of it. */
    public static final GameProfile PROFILE = new GameProfile(
            UUID.nameUUIDFromBytes("realmforge_qa:puppet".getBytes(StandardCharsets.UTF_8)), "Puppet");

    private PuppetCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("puppet")
                .then(Commands.literal("scroll").executes(PuppetCommands::scroll))
                .then(Commands.literal("remove").executes(PuppetCommands::remove));
    }

    private static int scroll(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        logOut(context.getSource().getServer());
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        Vec3 position = player.position().add(flat.scale(DummyCommands.DISTANCE));
        // Facing the player, holding the scroll before any client sees it: nothing ticks the puppet, and a held item
        // reaches the clients that already track an entity only from its tick.
        ServerPlayer puppet = QaPlayers.loginAt(player.level(), PROFILE, position, player.getYRot() + 180,
                prepared -> prepared.setItemInHand(InteractionHand.MAIN_HAND,
                        new ItemStack(GeneratedItems.OBLIVION_SCROLL.get())));
        puppet.setGameMode(GameType.CREATIVE);
        puppet.startUsingItem(InteractionHand.MAIN_HAND);
        context.getSource().sendSuccess(() -> Component.literal(
                "Puppet charges a Scroll of Oblivion in front of you; /qa puppet remove logs it out"), true);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        boolean removed = logOut(context.getSource().getServer());
        context.getSource().sendSuccess(
                () -> Component.literal(removed ? "Puppet logged out" : "No puppet is logged in"), true);
        return removed ? 1 : 0;
    }

    /** Logs the puppet out, saving it as every player is; whether it was logged in. */
    private static boolean logOut(MinecraftServer server) {
        ServerPlayer puppet = server.getPlayerList().getPlayer(PROFILE.id());
        if (puppet == null) {
            return false;
        }
        server.getPlayerList().remove(puppet);
        return true;
    }
}
