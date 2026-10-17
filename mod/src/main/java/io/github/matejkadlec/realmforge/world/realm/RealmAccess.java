package io.github.matejkadlec.realmforge.world.realm;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.core.Requirement;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import io.github.matejkadlec.realmforge.world.DimensionRules;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;

/**
 * Who may enter and leave a realm (realm portal spec, "Access and unlocks", "Multiplayer", "Respawn"). Every move into
 * another level goes through {@code Entity#teleport(TeleportTransition)} ({@code ServerPlayer} overrides it), which
 * posts the cancellable {@link EntityTravelToDimensionEvent} first: realm portals and nether portals, {@code /tp},
 * {@code /execute in ... run tp}, ender pearls and other mods' warps. A move within one level posts it too, and is
 * left alone.
 *
 * <ul>
 *   <li>A player enters a dimension only with every access requirement of its rules: a realm's is its unlock. A
 *       refused player stays where they are and gets a centre-screen message, at most once every 3 seconds.
 *   <li>No other entity enters or leaves a realm: items, mobs and vehicles stay where they are.
 *   <li>A player's shoulder entities (a parrot) stay in the level the player leaves, on a trip into or out of a realm
 *       and at a death in one: on the shoulder they are no entity, so no event stops them.
 *   <li>A player always respawns in Overworld: a respawn whose level is a realm (a spawn point set there by command
 *       or another mod; beds there set none) goes to the world spawn instead.
 * </ul>
 */
public final class RealmAccess {
    /** How often a refused player hears about it: once every 3 seconds at most. */
    static final int REFUSAL_INTERVAL_TICKS = 60;

    private static final int TITLE_FADE_IN_TICKS = 5;
    private static final int TITLE_STAY_TICKS = 40;
    private static final int TITLE_FADE_OUT_TICKS = 10;
    /** Shoulder entities come down where the player stands, a little above the feet, as vanilla puts them. */
    private static final double SHOULDER_DROP_HEIGHT = 0.7;

    /** The game time of each player's last refusal message; weak, so a player who left is forgotten. */
    private static final Map<ServerPlayer, Long> LAST_REFUSAL = new WeakHashMap<>();

    private RealmAccess() {
    }

    /** Refuses a move into a dimension the player may not enter, and every move of another entity into or out of a realm. */
    public static void onTravel(EntityTravelToDimensionEvent event) {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level) || level.dimension() == event.getDimension()) {
            return;
        }
        RegistryAccess registries = level.registryAccess();
        if (!(entity instanceof ServerPlayer player)) {
            if (Realms.isRealm(registries, level.dimension()) || Realms.isRealm(registries, event.getDimension())) {
                event.setCanceled(true);
            }
            return;
        }
        if (!mayEnter(player, event.getDimension())) {
            event.setCanceled(true);
            tellRefused(player, event.getDimension());
        }
    }

    /**
     * Puts a travelling player's shoulder entities down in the level they leave, on a trip into or out of a realm that
     * no listener refused. Registered at the lowest priority without cancelled events, so it runs last.
     */
    public static void onTravelAllowed(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level().dimension() != event.getDimension()
                && touchesRealm(player.level().registryAccess(), player.level().dimension(), event.getDimension())) {
            dropShoulderEntities(player);
        }
    }

    /**
     * A respawn never puts a player in a realm, and the shoulder entities of a player who died in one stay there. The
     * event's player is the dead one, already taken out of its level, whose shoulder entities the new player would
     * copy.
     */
    public static void onRespawnPosition(PlayerRespawnPositionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        RegistryAccess registries = player.level().registryAccess();
        if (Realms.isRealm(registries, player.level().dimension())) {
            dropShoulderEntities(player);
        }
        TeleportTransition target = event.getTeleportTransition();
        if (Realms.isRealm(registries, target.newLevel().dimension())) {
            event.setTeleportTransition(overworldSpawn(player, target));
        }
    }

    /** Whether the player meets every access requirement of {@code dimension}'s rules; true without rules. */
    public static boolean mayEnter(ServerPlayer player, ResourceKey<Level> dimension) {
        List<Requirement> access = DimensionRules.of(player.level().registryAccess(), dimension)
                .map(DimensionRules::access)
                .orElse(List.of());
        return Requirement.all(access, player);
    }

    /**
     * Puts the player's shoulder entities down at their position, as vanilla's {@code respawnEntityOnShoulder} does,
     * and clears the shoulders. Not vanilla's {@code removeEntitiesOnShoulder}, which does nothing within 20 ticks of
     * a landing.
     */
    public static void dropShoulderEntities(ServerPlayer player) {
        CompoundTag left = player.getShoulderEntityLeft();
        CompoundTag right = player.getShoulderEntityRight();
        if (left.isEmpty() && right.isEmpty()) {
            return;
        }
        respawnShoulderEntity(player, left);
        respawnShoulderEntity(player, right);
        player.setShoulderEntityLeft(new CompoundTag());
        player.setShoulderEntityRight(new CompoundTag());
    }

    private static void respawnShoulderEntity(ServerPlayer player, CompoundTag tag) {
        if (tag.isEmpty()) {
            return;
        }
        ServerLevel level = player.level();
        try (ProblemReporter.ScopedCollector problems =
                new ProblemReporter.ScopedCollector(player.problemPath(), Realmforge.LOGGER)) {
            EntityType.create(TagValueInput.create(problems.forChild(() -> ".shoulder"), level.registryAccess(), tag),
                            level, EntitySpawnReason.LOAD)
                    .ifPresent(entity -> {
                        if (entity instanceof TamableAnimal tamed) {
                            tamed.setOwner(player);
                        }
                        entity.setPos(player.getX(), player.getY() + SHOULDER_DROP_HEIGHT, player.getZ());
                        level.addWithUUID(entity);
                    });
        }
    }

    private static boolean touchesRealm(RegistryAccess registries, ResourceKey<Level> from, ResourceKey<Level> to) {
        return Realms.isRealm(registries, from) || Realms.isRealm(registries, to);
    }

    /**
     * The world spawn, as vanilla respawns a player without a bed, keeping what the original respawn does after it.
     * Should the server's respawn dimension itself be a realm, Overworld's spawn.
     */
    private static TeleportTransition overworldSpawn(ServerPlayer player, TeleportTransition original) {
        TeleportTransition spawn = TeleportTransition.createDefault(player, original.postTeleportTransition());
        if (!Realms.isRealm(player.level().registryAccess(), spawn.newLevel().dimension())) {
            return spawn;
        }
        ServerLevel overworld = player.level().getServer().overworld();
        return new TeleportTransition(overworld, Vec3.atBottomCenterOf(overworld.getRespawnData().pos()),
                original.deltaMovement(), overworld.getRespawnData().yaw(), overworld.getRespawnData().pitch(),
                original.postTeleportTransition());
    }

    /** The centre-screen refusal, with the realm's hint below it and a short sound, once every 3 seconds at most. */
    private static void tellRefused(ServerPlayer player, ResourceKey<Level> dimension) {
        long now = player.level().getGameTime();
        Long last = LAST_REFUSAL.get(player);
        if (last != null && now - last < REFUSAL_INTERVAL_TICKS && now >= last) {
            return;
        }
        LAST_REFUSAL.put(player, now);
        player.connection.send(new ClientboundSetTitlesAnimationPacket(TITLE_FADE_IN_TICKS, TITLE_STAY_TICKS,
                TITLE_FADE_OUT_TICKS));
        Component hint = Realms.of(player.level().registryAccess(), dimension)
                .map(Holder::value)
                .map(realm -> (Component) Component.translatable(realm.lockedHint()))
                .orElse(Component.empty());
        player.connection.send(new ClientboundSetSubtitleTextPacket(hint));
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.translatable(GeneratedTexts.MESSAGE_REALM_NOT_ELIGIBLE).withStyle(ChatFormatting.RED)));
        player.connection.send(new ClientboundSoundPacket(SoundEvents.NOTE_BLOCK_BASS, SoundSource.PLAYERS,
                player.getX(), player.getY(), player.getZ(), 1.0F, 0.5F, player.getRandom().nextLong()));
    }
}
