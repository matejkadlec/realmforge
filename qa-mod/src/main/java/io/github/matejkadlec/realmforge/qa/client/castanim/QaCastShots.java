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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
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
 * set, the dev client creates a fresh superflat creative world from the title screen and a Human Mage in it (character
 * creation would otherwise stay open, C1), casts with {@code /qa cast}, saves the cast pose in every camera at two
 * fields of view to {@code run/client/screenshots/}, and quits. Besides the level look it shoots looking up, looking
 * down and right after a turn, and besides the vanilla cameras it has a still one beside the player (RLM-108). It needs no input, so it also runs in WSL, where mouse look does not work.
 * The variable's value starts every file name. DevLogin still runs before the game starts: without a cached login it
 * waits for its console prompt.
 */
final class QaCastShots {
    static final String ENVIRONMENT_VARIABLE = "REALMFORGE_QA_CAST_SHOTS";

    /**
     * The core mod's cast.json: the arms finish rising at tick 6, pulse at tick 14 and are back at the hold pose at
     * tick 22, where the loop returns to tick 6. PAL reads the playerAnimator file as it played there: once the
     * animation reaches tick 22 it goes on from tick 5 ({@code returnTick - 1}, {@code PlayerAnimatorLoader}), so a
     * loop takes 17 ticks (RLM-108; a 16-tick guess drifts one tick per loop and lands on the pulse after eight).
     */
    private static final int HOLD_TICK = 6;
    private static final int PULSE_TICK = 14;
    private static final int END_TICK = 22;
    private static final int LOOP_TICKS = END_TICK - HOLD_TICK + 1;
    /** Ticks in the new world before the cast starts: chunks, the time of day and the weather settle. */
    private static final int SETTLE_TICKS = 40;
    /** Ticks a new camera or field of view gets before its first shot. */
    private static final int CAMERA_TICKS = 4;
    /** Ticks for the last screenshot to reach the disk (written on Minecraft's I/O pool) before the client quits. */
    private static final int WRITE_TICKS = 40;
    private static final int[] FIELDS_OF_VIEW = {110, 70};

    private enum Stage { TITLE, WORLD, CASTING, DONE }

    private enum Camera {
        FIRST_PERSON(CameraType.FIRST_PERSON),
        THIRD_PERSON_BACK(CameraType.THIRD_PERSON_BACK),
        THIRD_PERSON_FRONT(CameraType.THIRD_PERSON_FRONT),
        /**
         * The eyes of an invisible armor stand 2.5 blocks to the right of the player, looking at them. Unlike the
         * vanilla third-person cameras it does not follow the player's look, so it shows the arms against the body.
         */
        SIDE(CameraType.FIRST_PERSON);

        private final CameraType type;

        Camera(CameraType type) {
            this.type = type;
        }
    }

    /** Where the player looks: the pitch (down is positive) and the yaw of the head and of the body. */
    private enum View {
        LEVEL("level", 0.0F, 0.0F, 0.0F),
        UP_45("up45", -45.0F, 0.0F, 0.0F),
        UP_80("up80", -80.0F, 0.0F, 0.0F),
        DOWN_45("down45", 45.0F, 0.0F, 0.0F),
        DOWN_80("down80", 80.0F, 0.0F, 0.0F),
        /** Right after turning 90 degrees right: the body trails the head by the most vanilla allows, 50 degrees. */
        TURN_90("turn90", 0.0F, 90.0F, 40.0F);

        private final String label;
        private final float pitch;
        private final float yaw;
        private final float bodyYaw;

        View(String label, float pitch, float yaw, float bodyYaw) {
            this.label = label;
            this.pitch = pitch;
            this.yaw = yaw;
            this.bodyYaw = bodyYaw;
        }
    }

    private record Shot(int tick, View view, int fieldOfView, Camera camera, String name) {
    }

    private final String prefix;
    private final List<Shot> shots = new ArrayList<>();
    private Stage stage = Stage.TITLE;
    private int ticksInWorld;
    private boolean characterCreated;
    private int castTicks;
    private View view = View.LEVEL;

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
                if (minecraft.player == null || minecraft.level == null) {
                    return;
                }
                if (!characterCreated) {
                    // A new world opens character creation (C1), which closes once the player has a character; the
                    // starting kit goes again, so the hands are empty as before.
                    minecraft.player.connection.sendCommand("qa character create realmforge:human realmforge:mage");
                    minecraft.player.connection.sendCommand("clear");
                    characterCreated = true;
                }
                if (minecraft.screen != null) {
                    return;
                }
                ticksInWorld++;
                keepStill(minecraft, minecraft.player, view);
                if (ticksInWorld == 1) {
                    minecraft.player.connection.sendCommand("gamerule advance_time false");
                    minecraft.player.connection.sendCommand("gamerule advance_weather false");
                    minecraft.player.connection.sendCommand("time set noon");
                    minecraft.player.connection.sendCommand("weather clear");
                    // The side camera: at the player's right while they face south, facing east.
                    minecraft.player.connection.sendCommand("summon minecraft:armor_stand ~-2.5 ~ ~ "
                            + "{Invisible:1b,NoGravity:1b,Rotation:[-90.0f,0.0f]}");
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
                keepStill(minecraft, player, view);
                // The command feedback would cover the hands; a shot shows the frame before its tick, so clear early.
                minecraft.gui.getChat().clearMessages(false);
                for (Shot shot : shots) {
                    if (castTicks == shot.tick() - CAMERA_TICKS) {
                        view = shot.view();
                        minecraft.options.fov().set(shot.fieldOfView());
                        minecraft.options.setCameraType(shot.camera().type);
                        minecraft.setCameraEntity(shot.camera() == Camera.SIDE ? sideCamera(player) : player);
                    } else if (castTicks == shot.tick()) {
                        Screenshot.grab(minecraft.gameDirectory, shot.name(), minecraft.getMainRenderTarget(), 1,
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

    /** The armor stand the side camera looks through, or the player while it has not arrived. */
    private static Entity sideCamera(LocalPlayer player) {
        List<ArmorStand> stands = player.level().getEntitiesOfClass(ArmorStand.class,
                player.getBoundingBox().inflate(4.0));
        if (stands.isEmpty()) {
            RealmforgeQa.LOGGER.warn("Cast screenshots: no armor stand for the side camera");
            return player;
        }
        return stands.getFirst();
    }

    private static void createWorld(Minecraft minecraft) {
        String name = "qa-cast-shots-" + System.currentTimeMillis();
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true,
                WorldDataConfiguration.DEFAULT);
        // createFreshLevel loads the world without the experimental-settings confirmation that the create-world
        // screen shows for our data pack dimensions, so no click is needed.
        minecraft.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(0L, false, false),
                registries -> registries.lookupOrThrow(Registries.WORLD_PRESET)
                        .getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                minecraft.screen);
    }

    /**
     * Each view, camera and field of view gets one animation loop. Looking level, it shoots the hold pose and the
     * peak of the pulse; every other view only the hold pose, to compare with the level one. The loops start where
     * the animation first returns to tick 6, after the rise and the fade-in. A shot shows the frame drawn before its
     * tick, which at the start of a loop is the hold pose of tick 22.
     */
    private void planShots() {
        String variant = ModList.get().isLoaded("bettercombat") ? "bettercombat" : "plain";
        int loopStart = END_TICK + 1;
        for (View shotView : View.values()) {
            for (int fieldOfView : FIELDS_OF_VIEW) {
                for (Camera camera : Camera.values()) {
                    String base = prefix + "-" + variant + "-" + shotView.label + "-fov" + fieldOfView + "-"
                            + camera.name().toLowerCase();
                    shots.add(new Shot(loopStart, shotView, fieldOfView, camera, base + "-hold.png"));
                    if (shotView == View.LEVEL) {
                        shots.add(new Shot(loopStart + PULSE_TICK - HOLD_TICK, shotView, fieldOfView, camera,
                                base + "-pulse.png"));
                    }
                    loopStart += LOOP_TICKS;
                }
            }
        }
    }

    /**
     * Holds the player in the view, facing south when level, with nothing in the way: no pause on lost focus, no
     * screen, and a released mouse, so WSLg's broken pointer lock cannot turn the camera. Runs after the client tick,
     * so the angles it sets are the ones the next frames draw.
     */
    private static void keepStill(Minecraft minecraft, LocalPlayer player, View view) {
        minecraft.options.pauseOnLostFocus = false;
        if (minecraft.mouseHandler.isMouseGrabbed()) {
            minecraft.mouseHandler.releaseMouse();
        }
        player.setYRot(view.yaw);
        player.setXRot(view.pitch);
        player.yRotO = view.yaw;
        player.xRotO = view.pitch;
        player.setYHeadRot(view.yaw);
        player.yHeadRotO = view.yaw;
        player.setYBodyRot(view.bodyYaw);
        player.yBodyRotO = view.bodyYaw;
    }
}
