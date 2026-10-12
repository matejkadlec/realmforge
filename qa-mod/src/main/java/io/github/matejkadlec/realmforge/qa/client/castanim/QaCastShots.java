package io.github.matejkadlec.realmforge.qa.client.castanim;

import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Screenshots of the test cast for an agent (RLM-89): with the environment variable {@code REALMFORGE_QA_CAST_SHOTS}
 * set, the dev client creates a fresh superflat creative world from the title screen, casts with {@code /qa cast},
 * saves the cast pose in every camera at two fields of view to {@code run/client/screenshots/}, and quits. It needs
 * no input, so it also runs in WSL, where mouse look does not work. The variable's value starts every file name.
 * DevLogin still runs before the game starts: without a cached login it waits for its console prompt.
 */
final class QaCastShots {
    static final String ENVIRONMENT_VARIABLE = "REALMFORGE_QA_CAST_SHOTS";

    /** cast_test.json: the arms finish rising at tick 6, then pulse to tick 14 and back, every 16 ticks. */
    private static final int HOLD_TICK = 6;
    private static final int PULSE_TICK = 14;
    private static final int LOOP_TICKS = 16;
    /** Ticks in the new world before the cast starts: chunks, the time of day and the weather settle. */
    private static final int SETTLE_TICKS = 40;
    /** Ticks a new camera or field of view gets before its first shot. */
    private static final int CAMERA_TICKS = 4;
    /** Ticks for the last screenshot to reach the disk (written on Minecraft's I/O pool) before the client quits. */
    private static final int WRITE_TICKS = 40;
    private static final int[] FIELDS_OF_VIEW = {110, 70};
    private static final CameraType[] CAMERAS = {
        CameraType.FIRST_PERSON, CameraType.THIRD_PERSON_BACK, CameraType.THIRD_PERSON_FRONT
    };

    private enum Stage { TITLE, WORLD, CASTING, DONE }

    private record Shot(int tick, int fieldOfView, CameraType camera, String name) {
    }

    private final String prefix;
    private final List<Shot> shots = new ArrayList<>();
    private Stage stage = Stage.TITLE;
    private int ticksInWorld;
    private int castTicks;

    private QaCastShots(String prefix) {
        this.prefix = prefix;
    }

    /** Starts the run when the environment variable is set; a normal dev client is unaffected. */
    static void registerIfRequested() {
        String prefix = System.getenv(ENVIRONMENT_VARIABLE);
        if (prefix == null || prefix.isBlank()) {
            return;
        }
        QaCastShots run = new QaCastShots(prefix.strip());
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> run.tick(Minecraft.getInstance()));
        RealmforgeQa.LOGGER.info("Cast screenshots requested ({}): the client will create a world and quit", prefix);
    }

    private void tick(Minecraft minecraft) {
        switch (stage) {
            case TITLE -> {
                if (minecraft.screen instanceof TitleScreen) {
                    createWorld(minecraft);
                    stage = Stage.WORLD;
                }
            }
            case WORLD -> {
                if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
                    return;
                }
                ticksInWorld++;
                keepStill(minecraft, minecraft.player);
                if (ticksInWorld == 1) {
                    minecraft.player.connection.sendCommand("gamerule doDaylightCycle false");
                    minecraft.player.connection.sendCommand("gamerule doWeatherCycle false");
                    minecraft.player.connection.sendCommand("time set noon");
                    minecraft.player.connection.sendCommand("weather clear");
                } else if (ticksInWorld == SETTLE_TICKS) {
                    minecraft.player.connection.sendCommand("qa cast 6000");
                } else if (ticksInWorld > SETTLE_TICKS && QaCastAnimationClient.isLocalPlayerCasting()) {
                    planShots();
                    stage = Stage.CASTING;
                }
            }
            case CASTING -> {
                LocalPlayer player = minecraft.player;
                if (player == null) {
                    return;
                }
                castTicks++;
                keepStill(minecraft, player);
                // The command feedback would cover the hands; a shot shows the frame before its tick, so clear early.
                minecraft.gui.getChat().clearMessages(false);
                for (Shot shot : shots) {
                    if (castTicks == shot.tick() - CAMERA_TICKS) {
                        minecraft.options.fov().set(shot.fieldOfView());
                        minecraft.options.setCameraType(shot.camera());
                    } else if (castTicks == shot.tick()) {
                        Screenshot.grab(minecraft.gameDirectory, shot.name(), minecraft.getMainRenderTarget(),
                                message -> RealmforgeQa.LOGGER.info("Cast screenshot: {}", message.getString()));
                    }
                }
                if (castTicks == shots.getLast().tick() + WRITE_TICKS) {
                    stage = Stage.DONE;
                    minecraft.stop();
                }
            }
            case DONE -> {
            }
        }
    }

    private static void createWorld(Minecraft minecraft) {
        String name = "qa-cast-shots-" + System.currentTimeMillis();
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                new GameRules(), WorldDataConfiguration.DEFAULT);
        // createFreshLevel loads the world without the experimental-settings confirmation that the create-world
        // screen shows for our data pack dimensions, so no click is needed.
        minecraft.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(0L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                minecraft.screen);
    }

    /**
     * Each camera and field of view gets two shots in one animation loop: the hold pose where the rise ends, and the
     * peak of the pulse. The first comes a full loop after the cast began, once the fade-in is over.
     */
    private void planShots() {
        String variant = ModList.get().isLoaded("bettercombat") ? "bettercombat" : "plain";
        int loopStart = LOOP_TICKS;
        for (int fieldOfView : FIELDS_OF_VIEW) {
            for (CameraType camera : CAMERAS) {
                loopStart += LOOP_TICKS;
                String base = prefix + "-" + variant + "-fov" + fieldOfView + "-" + camera.name().toLowerCase();
                shots.add(new Shot(loopStart + HOLD_TICK, fieldOfView, camera, base + "-hold.png"));
                shots.add(new Shot(loopStart + PULSE_TICK, fieldOfView, camera, base + "-pulse.png"));
            }
        }
    }

    /**
     * Faces the player level and south, with nothing in the way: no pause on lost focus, no screen, and a released
     * mouse, so WSLg's broken pointer lock cannot turn the camera.
     */
    private static void keepStill(Minecraft minecraft, LocalPlayer player) {
        minecraft.options.pauseOnLostFocus = false;
        if (minecraft.mouseHandler.isMouseGrabbed()) {
            minecraft.mouseHandler.releaseMouse();
        }
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        player.yRotO = 0.0F;
        player.xRotO = 0.0F;
        player.setYHeadRot(0.0F);
        player.setYBodyRot(0.0F);
    }
}
