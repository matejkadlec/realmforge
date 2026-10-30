package io.github.matejkadlec.realmforge.qa.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
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
 * itself right after. {@link #loginAt} logs in a puppet for a playtest ({@code /qa puppet}).
 */
public final class QaPlayers {
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
        Connection connection = mockConnection();
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

    /**
     * Logs a new fake player in at {@code position} in {@code level}, facing {@code yaw}, once {@code prepare} has set
     * it up, so a client that starts to track it gets it as prepared (what it holds, for example): a puppet for a
     * playtest ({@code /qa puppet}). Its saved data is not loaded. Nothing ticks it ({@code ServerPlayer#doTick}), so
     * it stays as it was left; {@code PlayerList#remove} logs it out.
     */
    public static ServerPlayer loginAt(ServerLevel level, GameProfile profile, Vec3 position, float yaw,
            Consumer<ServerPlayer> prepare) {
        MinecraftServer server = level.getServer();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(server, level, profile, cookie.clientInformation());
        EventHooks.firePlayerLoadingEvent(player, server.getPlayerList(), player.getStringUUID());
        player.snapTo(position, yaw, 0);
        player.setYHeadRot(yaw);
        player.setYBodyRot(yaw);
        prepare.accept(player);
        server.getPlayerList().placeNewPlayer(mockConnection(), player, cookie);
        return player;
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

    /**
     * The payloads of {@code type} the server sent {@code player} since the last call, oldest first. The fake
     * connection's channel ({@link #login}) keeps every packet written to it; this drains it, bundles included.
     */
    static <T extends CustomPacketPayload> List<T> sentPayloads(ServerPlayer player, Class<T> type) {
        EmbeddedChannel channel = (EmbeddedChannel) player.connection.getConnection().channel();
        List<T> payloads = new ArrayList<>();
        for (Object message = channel.readOutbound(); message != null; message = channel.readOutbound()) {
            collect(message, type, payloads);
        }
        return payloads;
    }

    /**
     * The chat lines (system messages, not the action bar) the server sent {@code player} since the last drain, oldest
     * first. Like {@link #sentPayloads}, this drains the fake connection's channel, bundles included.
     */
    static List<Component> sentChatLines(ServerPlayer player) {
        EmbeddedChannel channel = (EmbeddedChannel) player.connection.getConnection().channel();
        List<ClientboundSystemChatPacket> packets = new ArrayList<>();
        for (Object message = channel.readOutbound(); message != null; message = channel.readOutbound()) {
            collectPackets(message, ClientboundSystemChatPacket.class, packets);
        }
        return packets.stream().filter(packet -> !packet.overlay()).map(ClientboundSystemChatPacket::content).toList();
    }

    private static <T> void collectPackets(Object packet, Class<T> type, List<T> into) {
        if (type.isInstance(packet)) {
            into.add(type.cast(packet));
        } else if (packet instanceof BundlePacket<?> bundle) {
            bundle.subPackets().forEach(inner -> collectPackets(inner, type, into));
        }
    }

    private static <T> void collect(Object packet, Class<T> type, List<T> into) {
        if (packet instanceof ClientboundCustomPayloadPacket(CustomPacketPayload payload) && type.isInstance(payload)) {
            into.add(type.cast(payload));
        } else if (packet instanceof BundlePacket<?> bundle) {
            bundle.subPackets().forEach(inner -> collect(inner, type, into));
        }
    }

    /** A connection that acts as a NeoForge client accepting every payload but server config files ({@link #login}). */
    private static Connection mockConnection() {
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        withoutConfigFiles(connection);
        return connection;
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
