package io.github.matejkadlec.realmforge.qa.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.network.payload.ConfigFilePayload;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import net.neoforged.neoforge.network.registration.NetworkChannel;
import net.neoforged.neoforge.network.registration.NetworkPayloadSetup;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.slf4j.Logger;

/**
 * Fake players for GameTests. A new fake player logs in at the world spawn, where the GameTest server starts its grid
 * of tests, so another test's blocks can stand there and a tick ({@code ServerPlayer#doTick}) would suffocate it.
 * Tests therefore log new players in with {@link #loginOnPlatform}, which puts them on their own test's platform, and
 * move a player that a respawn puts back at the world spawn with {@link #onPlatform} before they tick it.
 * {@link #login} alone is for a returning player, who loads where it left, and for a test that moves the player
 * itself right after.
 */
final class QaPlayers {
    /** The middle of the 5×5 platform, one block above its stone floor. */
    static final Vec3 PLATFORM_MIDDLE = new Vec3(2.5, 1, 2.5);

    private static final Logger LOGGER = LogUtils.getLogger();

    private QaPlayers() {
    }

    /**
     * Logs a fake player in as the normal login path does, so saved data (position, dimension, attachments) is
     * loaded and the login event fires, as for a real reconnect: the saved data first, as the configuration phase's
     * {@code PrepareSpawnTask} reads it, then {@code PlayerList#placeNewPlayer}. Like
     * {@code GameTestHelper#makeMockServerPlayerInLevel}, but with a chosen profile, so the same player can log in
     * again; {@code PlayerList#remove} logs it out and saves it. The connection acts as a NeoForge client that accepts
     * every payload, so synced attachments can be sent to it, except server config files: NeoForge's
     * {@code ConfigSync} tracks those for a connection from its configuration phase, which this login skips, and
     * fails the server tick for a plain player it has no entry for, a respawned one included.
     */
    static ServerPlayer login(MinecraftServer server, GameProfile profile) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        withoutConfigFiles(connection);
        try (ProblemReporter.ScopedCollector problems = new ProblemReporter.ScopedCollector(LOGGER)) {
            Optional<ValueInput> saved = server.getPlayerList().loadPlayerData(new NameAndId(profile))
                    .map(tag -> TagValueInput.create(problems, server.registryAccess(), tag));
            ServerPlayer.SavedPosition position = saved
                    .flatMap(input -> input.read(ServerPlayer.SavedPosition.MAP_CODEC))
                    .orElse(ServerPlayer.SavedPosition.EMPTY);
            ServerLevel level = position.dimension().map(server::getLevel).orElseGet(server::findRespawnDimension);
            LevelData.RespawnData spawn = server.getRespawnData();
            ServerPlayer player = new ServerPlayer(server, level, profile, cookie.clientInformation());
            saved.ifPresent(player::load);
            EventHooks.firePlayerLoadingEvent(player, server.getPlayerList(), player.getStringUUID());
            Vec2 angle = position.rotation().orElse(new Vec2(spawn.yaw(), spawn.pitch()));
            player.snapTo(position.position().orElse(Vec3.atBottomCenterOf(spawn.pos())), angle.x, angle.y);
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
            return player;
        }
    }

    /** A new player named {@code name} (a random id), at the platform's middle. */
    static ServerPlayer loginOnPlatform(GameTestHelper helper, String name) {
        return loginOnPlatform(helper, name, PLATFORM_MIDDLE);
    }

    /** A new player named {@code name} (a random id), at {@code relative} on the test's platform. */
    static ServerPlayer loginOnPlatform(GameTestHelper helper, String name, Vec3 relative) {
        return loginOnPlatform(helper, new GameProfile(UUID.randomUUID(), name), relative);
    }

    /** {@link #loginOnPlatform(GameTestHelper, GameProfile, Vec3)} at the platform's middle. */
    static ServerPlayer loginOnPlatform(GameTestHelper helper, GameProfile profile) {
        return loginOnPlatform(helper, profile, PLATFORM_MIDDLE);
    }

    /**
     * Logs a new fake player in ({@link #login}) and puts it at {@code relative} on the test's platform, facing south.
     */
    static ServerPlayer loginOnPlatform(GameTestHelper helper, GameProfile profile, Vec3 relative) {
        return onPlatform(helper, login(helper.getLevel().getServer(), profile), relative);
    }

    /** {@link #onPlatform(GameTestHelper, ServerPlayer, Vec3)} at the platform's middle. */
    static ServerPlayer onPlatform(GameTestHelper helper, ServerPlayer player) {
        return onPlatform(helper, player, PLATFORM_MIDDLE);
    }

    /** Puts a player of the test's level at {@code relative} on the test's platform, facing south. */
    static ServerPlayer onPlatform(GameTestHelper helper, ServerPlayer player, Vec3 relative) {
        helper.assertTrue(player.level() == helper.getLevel(), "the player is in the test's level");
        player.snapTo(helper.absoluteVec(relative), 0, 0);
        return player;
    }

    /** Takes the config file channel out of the mock connection's payload setup (internal NeoForge API, dev only). */
    private static void withoutConfigFiles(Connection connection) {
        Map<ConnectionProtocol, Map<Identifier, NetworkChannel>> channels = new HashMap<>();
        ChannelAttributes.getPayloadSetup(connection).channels().forEach((protocol, byId) -> {
            Map<Identifier, NetworkChannel> kept = new HashMap<>(byId);
            kept.remove(ConfigFilePayload.TYPE.id());
            channels.put(protocol, kept);
        });
        ChannelAttributes.setPayloadSetup(connection, new NetworkPayloadSetup(channels));
    }
}
