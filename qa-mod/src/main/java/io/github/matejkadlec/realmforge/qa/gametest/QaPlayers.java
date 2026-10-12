package io.github.matejkadlec.realmforge.qa.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Fake players for GameTests. */
final class QaPlayers {
    private QaPlayers() {
    }

    /**
     * Logs a fake player in through the normal login path, so saved data (position, dimension, attachments) is
     * loaded and the login event fires, as for a real reconnect. Like
     * {@code GameTestHelper#makeMockServerPlayerInLevel} (deprecated for removal in 1.21.1), but with a chosen
     * profile, so the same player can log in again; {@code PlayerList#remove} logs it out and saves it. The
     * connection acts as a NeoForge client that accepts every payload, so synced attachments can be sent to it.
     */
    static ServerPlayer login(MinecraftServer server, GameProfile profile) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(server, server.overworld(), profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }
}
