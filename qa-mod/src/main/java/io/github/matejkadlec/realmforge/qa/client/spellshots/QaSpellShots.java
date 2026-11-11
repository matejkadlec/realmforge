package io.github.matejkadlec.realmforge.qa.client.spellshots;

import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Screenshots of spell projectiles and of what forms in a caster's hands for an agent (RLM-639, RLM-640): with the
 * environment variable {@code REALMFORGE_QA_SPELL_SHOTS} set, the dev client creates a fresh superflat creative world
 * from the title screen and a Human Mage in it, builds a wall 8 blocks ahead, and casts each of {@link #SCENES} along
 * the player's look: at a villager 5 blocks ahead, at the wall, Frostbolt and Fireball forming in the player's hands
 * (seen from beside the player, and through their eyes at a field of view of 70 and of 110), an interrupted cast
 * fading, a bolt that kills, and a Hewn Chanter's Stoneform, which forms in its hands. It saves frames of each scene
 * to {@code run/client/screenshots/<label>-<scene>-t<tick>.png} and quits. Without the HUD, so the models, their
 * materials, the trails and the bursts are in the frame. It needs no input, so it also runs in WSL, where mouse look
 * does not work ({@code docs/dev-setup.md}, 3.3).
 */
public final class QaSpellShots {
    static final String ENVIRONMENT_VARIABLE = "REALMFORGE_QA_SPELL_SHOTS";

    /** Ticks in the new world before the first scene: chunks, the time of day and the weather settle. */
    private static final int SETTLE_TICKS = 60;
    /** Ticks a scene's setup gets before its cast: a summoned mob arrives on the client. */
    private static final int SETUP_TICKS = 30;
    /** Ticks for the last screenshot to reach the disk (written on Minecraft's I/O pool) before the client quits. */
    private static final int WRITE_TICKS = 40;

    /** Where a scene is seen from. */
    private enum View {
        /** A still camera 4 blocks beside the flight, looking across it. */
        FLIGHT,
        /** A still camera 2 blocks from the player, looking at their hands. */
        HANDS,
        /** Through the player's eyes. */
        EYES
    }

    /**
     * One cast and its frames.
     *
     * @param name the scene's part of the file names
     * @param setup commands that prepare it, run {@link #SETUP_TICKS} before the cast
     * @param cast the command that casts
     * @param frames how many ticks after the cast frames are saved
     * @param every a frame every this many ticks
     * @param view where it is seen from
     * @param fov the field of view, in degrees
     * @param cancelAt the tick after the cast at which the cast is cancelled, or 0 for a cast that runs out
     */
    private record Scene(String name, List<String> setup, String cast, int frames, int every, View view, int fov,
            int cancelAt) {
        Scene(String name, List<String> setup, String cast, int frames, boolean firstPerson) {
            this(name, setup, cast, frames, 1, firstPerson ? View.EYES : View.FLIGHT, 70, 0);
        }
    }

    private static final String VILLAGER = "summon minecraft:villager ~ ~ ~5 {NoAI:1b,Silent:1b,Rotation:[180.0f,0.0f]}";
    /** Out of the frame, without the puff a killed mob leaves behind for a scene after it. */
    private static final String NO_MOBS =
            "tp @e[type=!minecraft:player,type=!minecraft:armor_stand,distance=..24] ~ ~ ~-200";
    private static final String FROSTBOLT = "qa spell cast realmforge:frostbolt";
    private static final String CHANTER =
            "summon realmforge:hewn_chanter ~2 ~ ~3 {NoAI:1b,Silent:1b,Rotation:[0.0f,0.0f]}";

    private static final List<Scene> SCENES = List.of(
            new Scene("frost-creature", List.of(NO_MOBS, VILLAGER), "qa spell cast realmforge:test_twin_frost", 16,
                    false),
            new Scene("fire-creature", List.of(), "qa spell cast realmforge:test_twin_fire", 16, false),
            new Scene("frost-wall", List.of(NO_MOBS), "qa spell cast realmforge:test_twin_frost", 18, false),
            new Scene("fire-wall", List.of(NO_MOBS), "qa spell cast realmforge:test_twin_fire", 18, false),
            new Scene("arcane-wall", List.of(NO_MOBS), "qa spell cast realmforge:arcane_missiles", 40, false),
            new Scene("frost-first-person", List.of(NO_MOBS), "qa spell cast realmforge:test_twin_frost", 14, true),
            // Forming (RLM-640): a 1.5 s cast grows cube by cube in the hands, then leaves them.
            new Scene("forming-frost-hands", List.of(NO_MOBS), FROSTBOLT, 40, 2, View.HANDS, 70, 0),
            new Scene("forming-fire-hands", List.of(NO_MOBS), "qa spell cast realmforge:fireball", 40, 2, View.HANDS,
                    70, 0),
            new Scene("forming-frost-eyes-fov70", List.of(NO_MOBS), FROSTBOLT, 36, 2, View.EYES, 70, 0),
            new Scene("forming-frost-eyes-fov110", List.of(NO_MOBS), FROSTBOLT, 36, 2, View.EYES, 110, 0),
            new Scene("forming-frost-flight", List.of(NO_MOBS), FROSTBOLT, 44, 2, View.FLIGHT, 70, 0),
            new Scene("forming-interrupted", List.of(NO_MOBS), FROSTBOLT, 30, 1, View.HANDS, 70, 18),
            new Scene("forming-interrupted-eyes", List.of(NO_MOBS), FROSTBOLT, 30, 1, View.EYES, 70, 18),
            new Scene("arcane-hands", List.of(NO_MOBS), "qa spell cast realmforge:arcane_missiles", 34, 2, View.HANDS,
                    70, 0),
            new Scene("frost-kill", List.of(NO_MOBS, VILLAGER.replace("NoAI:1b", "NoAI:1b,Health:1.0f")), FROSTBOLT,
                    44, 1, View.FLIGHT, 70, 0),
            new Scene("stone-forming", List.of(NO_MOBS, CHANTER, VILLAGER.replace("~ ~ ~5", "~2 ~ ~7")),
                    "execute as @e[type=realmforge:hewn_chanter,limit=1] run qa spell cast realmforge:stoneform "
                            + "@e[type=minecraft:villager,limit=1,sort=nearest]", 44, false));

    private enum Stage { TITLE, WORLD, SCENES, DONE }

    private final String prefix;
    private Stage stage = Stage.TITLE;
    private int ticksInWorld;
    private boolean characterCreated;
    private int scene;
    private int sceneTicks;

    private QaSpellShots(String prefix) {
        this.prefix = prefix;
    }

    /** Starts the run when the environment variable is set; a normal dev client is unaffected. */
    public static void registerIfRequested() {
        String prefix = System.getenv(ENVIRONMENT_VARIABLE);
        if (prefix == null || prefix.isBlank()) {
            return;
        }
        QaSpellShots run = new QaSpellShots(prefix.strip());
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> run.tick(Minecraft.getInstance()));
        RealmforgeQa.LOGGER.info("Spell screenshots requested ({}): the client will create a world and quit", prefix);
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
                LocalPlayer player = minecraft.player;
                if (player == null || minecraft.level == null) {
                    return;
                }
                if (!characterCreated) {
                    // A new world opens character creation (C1), which closes once the player has a character.
                    player.connection.sendCommand("qa character create realmforge:human realmforge:mage");
                    player.connection.sendCommand("clear");
                    characterCreated = true;
                }
                if (minecraft.screen != null) {
                    return;
                }
                ticksInWorld++;
                keepStill(minecraft, player);
                if (ticksInWorld == 1) {
                    for (String command : List.of("gamerule advance_time false", "gamerule advance_weather false",
                            "time set noon", "weather clear", "fill ~-4 ~ ~8 ~6 ~5 ~8 minecraft:stone",
                            // The camera beside the flight: 4 blocks to the player's right, looking back across it.
                            "summon minecraft:armor_stand ~-4 ~ ~4.5 {Invisible:1b,NoGravity:1b,"
                                    + "Rotation:[-90.0f,0.0f]}",
                            // The camera on the hands: 2 blocks to the player's right and a little ahead, looking
                            // back at them.
                            "summon minecraft:armor_stand ~-2 ~ ~1.6 {Invisible:1b,NoGravity:1b,"
                                    + "Rotation:[-120.0f,8.0f]}",
                            "qa level 18", "qa spell learn realmforge:test_twin_frost",
                            "qa spell learn realmforge:test_twin_fire", "qa spell learn realmforge:arcane_missiles",
                            "qa spell learn realmforge:frostbolt", "qa spell learn realmforge:fireball",
                            "qa spell resource mana 1000")) {
                        player.connection.sendCommand(command);
                    }
                } else if (ticksInWorld == SETTLE_TICKS) {
                    stage = Stage.SCENES;
                }
            }
            case SCENES -> {
                LocalPlayer player = minecraft.player;
                if (player == null) {
                    return;
                }
                keepStill(minecraft, player);
                // Without the HUD: the spell bar would cover the flight.
                minecraft.options.hideGui = true;
                Scene current = SCENES.get(scene);
                sceneTicks++;
                if (sceneTicks == 1) {
                    current.setup().forEach(player.connection::sendCommand);
                    player.connection.sendCommand("qa spell reset");
                    player.connection.sendCommand("qa spell resource mana 1000");
                    minecraft.options.fov().set(current.fov());
                    minecraft.options.setCameraType(CameraType.FIRST_PERSON);
                    minecraft.setCameraEntity(current.view() == View.EYES ? player
                            : camera(player, current.view() == View.HANDS));
                } else if (sceneTicks == SETUP_TICKS) {
                    player.connection.sendCommand(current.cast());
                } else if (sceneTicks > SETUP_TICKS && sceneTicks <= SETUP_TICKS + current.frames()) {
                    int tick = sceneTicks - SETUP_TICKS;
                    if (tick == current.cancelAt()) {
                        player.connection.sendCommand("qa spell cancel");
                    }
                    if (tick % current.every() == 0) {
                        String name = String.format("%s-%s-t%02d.png", prefix, current.name(), tick);
                        Screenshot.grab(minecraft.gameDirectory, name, minecraft.getMainRenderTarget(), 1,
                                message -> RealmforgeQa.LOGGER.info("Spell screenshot: {}", message.getString()));
                    }
                } else if (sceneTicks > SETUP_TICKS + current.frames() + 10) {
                    sceneTicks = 0;
                    scene++;
                    if (scene == SCENES.size()) {
                        stage = Stage.DONE;
                        ticksInWorld = 0;
                    }
                }
            }
            case DONE -> {
                if (++ticksInWorld == WRITE_TICKS) {
                    minecraft.stop();
                }
            }
        }
    }

    /**
     * The armor stand a still camera looks through: the one near the player for the camera on the {@code hands}, the
     * far one for the camera beside the flight; the player while it has not arrived.
     */
    private static Entity camera(LocalPlayer player, boolean hands) {
        List<ArmorStand> stands = player.level().getEntitiesOfClass(ArmorStand.class,
                player.getBoundingBox().inflate(8.0));
        Comparator<ArmorStand> nearest = Comparator.comparingDouble(player::distanceToSqr);
        Optional<ArmorStand> stand = stands.stream().min(hands ? nearest : nearest.reversed());
        if (stand.isEmpty()) {
            RealmforgeQa.LOGGER.warn("Spell screenshots: no armor stand for the still camera");
            return player;
        }
        return stand.get();
    }

    private static void createWorld(Minecraft minecraft) {
        String name = "qa-spell-shots-" + System.currentTimeMillis();
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
     * Holds the player looking level to the south with nothing in the way: no pause on lost focus, no screen, and a
     * released mouse, so WSLg's broken pointer lock cannot turn the camera.
     */
    private static void keepStill(Minecraft minecraft, LocalPlayer player) {
        minecraft.options.pauseOnLostFocus = false;
        if (minecraft.mouseHandler.isMouseGrabbed()) {
            minecraft.mouseHandler.releaseMouse();
        }
        player.setYRot(0);
        player.setXRot(0);
        player.yRotO = 0;
        player.xRotO = 0;
        player.setYHeadRot(0);
        player.yHeadRotO = 0;
        player.setYBodyRot(0);
        player.yBodyRotO = 0;
    }
}
