package io.github.matejkadlec.realmforge.qa.client.charge;

import io.github.matejkadlec.realmforge.generated.GeneratedEffects;
import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * A trace of real charges for an agent (RLM-543): with the environment variable {@code REALMFORGE_QA_CHARGE_TRACE} set,
 * the dev client creates a superflat world from the title screen, makes a level-18 Warrior, casts Charge at a target in
 * several setups and logs where the local player goes tick by tick, then quits. A GameTest's fake player has no client,
 * so only this shows what a player's client does with a charge, and it needs no input: it also runs under Xvfb.
 * Every line starts with {@code [charge-trace]}; each setup ends with the closest the player came to the target, how far
 * from it the target was stunned, and where the player stopped, all along the charge's line, in blocks from the target's
 * middle (negative: past it). {@code docs/dev-setup.md} (3.3) has the command.
 */
public final class QaChargeTrace {
    private static final String ENVIRONMENT_VARIABLE = "REALMFORGE_QA_CHARGE_TRACE";
    /** Ticks in the new world before the first setup: chunks and the character settle. */
    private static final int SETTLE_TICKS = 60;
    /** Ticks a setup's teleport and summon get before the cast. */
    private static final int PREPARE_TICKS = 20;
    /** Ticks a charge is traced for. */
    private static final int TRACE_TICKS = 50;
    /** When a setup that holds the forward key lets go: after the hop, or the player would walk on past the target. */
    private static final int RELEASE_TICK = 16;
    private static final String TAG = "[charge-trace] ";

    /**
     * One charge: where the player starts and the target stands, in blocks from the world's base point, whether the
     * player holds the forward key, the entity to charge at, and whether a wall stands between the two.
     */
    private record Setup(String name, Vec3 from, Vec3 at, boolean forward, String entity, boolean wall) {
    }

    private final Deque<Setup> setups = new ArrayDeque<>();
    private Setup setup;
    private boolean worldAsked;
    private boolean characterCreated;
    private Vec3 base;
    private int ticksInWorld;
    private int setupTicks;
    private Vec3 approach;
    private double closest;
    private double atStun;
    private int stunTick;

    private QaChargeTrace() {
    }

    /** The charges to trace; a mod's target dummy too when the pack has one. Registries are full only in a world. */
    private void planSetups() {
        Vec3 here = Vec3.ZERO;
        Vec3 north = new Vec3(0, 0, -8);
        setups.add(new Setup("north, 8 blocks", here, north, false, "minecraft:husk", false));
        setups.add(new Setup("from the far side", new Vec3(0, 0, -16), north, false, "minecraft:husk", false));
        setups.add(new Setup("diagonal", here, new Vec3(5, 0, -5), false, "minecraft:husk", false));
        setups.add(new Setup("holding forward", here, north, true, "minecraft:husk", false));
        setups.add(new Setup("near, 4 blocks", here, new Vec3(0, 0, -4), false, "minecraft:husk", false));
        setups.add(new Setup("a wide boss", here, new Vec3(0, 0, -9), false, "realmforge:astone", false));
        setups.add(new Setup("a low wall in the way", here, north, false, "minecraft:husk", true));
        BuiltInRegistries.ENTITY_TYPE.keySet().stream().filter(id -> id.getPath().contains("dummy")
                && !id.getNamespace().equals(RealmforgeQa.MOD_ID)).findFirst().ifPresent(id -> setups.add(
                        new Setup("target dummy", here, north, false, id.toString(), false)));
    }

    /** Starts the run when the environment variable is set; a normal dev client is unaffected. */
    public static void registerIfRequested() {
        String label = System.getenv(ENVIRONMENT_VARIABLE);
        if (label == null || label.isBlank()) {
            return;
        }
        QaChargeTrace run = new QaChargeTrace();
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> run.tick(Minecraft.getInstance()));
        RealmforgeQa.LOGGER.info("{}requested: the client will create a world, charge and quit", TAG);
    }

    private void tick(Minecraft minecraft) {
        minecraft.options.pauseOnLostFocus = false;
        if (minecraft.screen instanceof TitleScreen && !worldAsked) {
            worldAsked = true;
            createWorld(minecraft);
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        if (!characterCreated) {
            for (String command : List.of("qa character create realmforge:human realmforge:warrior",
                    "advancement grant @s only realmforge:chapter/chapter_1", "qa level 18",
                    "qa spell learn realmforge:charge 2", "gamerule advance_time false",
                    "gamerule advance_weather false", "time set noon", "weather clear")) {
                player.connection.sendCommand(command);
            }
            characterCreated = true;
        }
        if (minecraft.screen != null) {
            return;
        }
        ticksInWorld++;
        if (ticksInWorld == SETTLE_TICKS) {
            base = new Vec3(Math.floor(player.getX()) + 0.5, player.getY(), Math.floor(player.getZ()) + 0.5);
            RealmforgeQa.LOGGER.info("{}base {} width {}", TAG, base, player.getBbWidth());
            planSetups();
        }
        if (ticksInWorld >= SETTLE_TICKS) {
            runSetups(minecraft, player);
        }
    }

    private void runSetups(Minecraft minecraft, LocalPlayer player) {
        if (setup == null) {
            setup = setups.poll();
            setupTicks = 0;
            if (setup == null) {
                RealmforgeQa.LOGGER.info("{}done", TAG);
                minecraft.stop();
                return;
            }
        }
        setupTicks++;
        Vec3 from = base.add(setup.from());
        Vec3 at = base.add(setup.at());
        if (setupTicks == 1) {
            float yaw = (float) Math.toDegrees(Math.atan2(-(at.x - from.x), at.z - from.z));
            player.connection.sendCommand("qa spell reset");
            player.connection.sendCommand("kill @e[type=!minecraft:player]");
            player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.3f %.3f %.3f %.1f 0", from.x, from.y,
                    from.z, yaw));
            player.connection.sendCommand(String.format(Locale.ROOT, "summon %s %.3f %.3f %.3f {NoAI:1b}",
                    setup.entity(), at.x, at.y, at.z));
            player.connection.sendCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d %s",
                    (int) Math.floor(from.x) - 2, (int) Math.floor(from.y), (int) Math.floor(from.z) - 4,
                    (int) Math.floor(from.x) + 2, (int) Math.floor(from.y), (int) Math.floor(from.z) - 4,
                    setup.wall() ? "minecraft:stone" : "minecraft:air"));
        } else if (setupTicks == PREPARE_TICKS) {
            minecraft.options.keyUp.setDown(setup.forward());
            Vec3 toward = new Vec3(at.x - from.x, 0, at.z - from.z);
            approach = toward.normalize();
            closest = Double.MAX_VALUE;
            atStun = Double.NaN;
            stunTick = -1;
            player.connection.sendCommand(String.format(Locale.ROOT,
                    "qa spell cast realmforge:charge @e[type=%s,limit=1,sort=nearest]", setup.entity()));
            RealmforgeQa.LOGGER.info("{}--- {}", TAG, setup.name());
        } else if (setupTicks > PREPARE_TICKS) {
            trace(minecraft, player);
        }
    }

    private void trace(Minecraft minecraft, LocalPlayer player) {
        int tick = setupTicks - PREPARE_TICKS;
        List<Entity> targets = targets(minecraft);
        if (targets.isEmpty()) {
            RealmforgeQa.LOGGER.info("{}tick {}: no target", TAG, tick);
        } else {
            Entity target = targets.getFirst();
            double along = (target.getX() - player.getX()) * approach.x + (target.getZ() - player.getZ()) * approach.z;
            closest = Math.min(closest, along);
            if (tick == RELEASE_TICK) {
                minecraft.options.keyUp.setDown(false);
            }
            boolean stunned = serverTargetStunned(minecraft, target);
            if (stunned && stunTick < 0) {
                stunTick = tick;
                atStun = along;
            }
            Vec3 move = player.getDeltaMovement();
            RealmforgeQa.LOGGER.info(String.format(Locale.ROOT, "%stick %2d: %5.2f from the target, y %+.2f, velocity "
                    + "(%+.2f, %+.2f, %+.2f)%s", TAG, tick, along, player.getY() - base.y, move.x, move.y, move.z,
                    stunned ? ", stunned" : ""));
        }
        if (tick >= TRACE_TICKS) {
            minecraft.options.keyUp.setDown(false);
            Entity target = targets.isEmpty() ? null : targets.getFirst();
            double last = target == null ? Double.NaN : (target.getX() - player.getX()) * approach.x
                    + (target.getZ() - player.getZ()) * approach.z;
            RealmforgeQa.LOGGER.info(String.format(Locale.ROOT, "%s=== %s: target width %.2f, closest %.2f, stunned at "
                    + "%.2f (tick %d), stopped at %.2f", TAG, setup.name(), target == null ? 0 : target.getBbWidth(),
                    closest, atStun, stunTick, last));
            setup = null;
        }
    }

    private List<Entity> targets(Minecraft minecraft) {
        List<Entity> found = new ArrayList<>();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity != minecraft.player
                    && BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals(setup.entity())) {
                found.add(entity);
            }
        }
        return found;
    }

    /** The integrated server's copy of {@code target} has Stunned: the server applied the charge's landing. */
    private static boolean serverTargetStunned(Minecraft minecraft, Entity target) {
        if (minecraft.getSingleplayerServer() == null) {
            return false;
        }
        ServerLevel level = minecraft.getSingleplayerServer().getLevel(minecraft.level.dimension());
        return level != null && level.getEntity(target.getUUID()) instanceof LivingEntity living
                && living.hasEffect(GeneratedEffects.STUNNED);
    }

    private static void createWorld(Minecraft minecraft) {
        String name = "qa-charge-trace-" + System.currentTimeMillis();
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, false), true,
                WorldDataConfiguration.DEFAULT);
        minecraft.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(0L, false, false),
                registries -> registries.lookupOrThrow(Registries.WORLD_PRESET)
                        .getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                minecraft.screen);
    }
}
