package io.github.matejkadlec.realmforge.qa.client.portal;

import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Screenshots of the realm portal's frame, names and refusals for an agent (RLM-306, RLM-337, RLM-394): with the
 * environment variable {@code REALMFORGE_QA_PORTAL_SHOTS} set, the dev client creates a fresh superflat world from
 * the title screen and a level 1 Human Mage in it, builds a frame of Gatewright Bricks round a Gatewright Sealstone in
 * front of them and saves to {@code run/client/screenshots/<label>-portal-<state>.png}:
 *
 * <ul>
 *   <li>{@code held-bricks}, {@code held-sealstone}: the frame, and each block's name over the hotbar;
 *   <li>{@code refusal-unlock}: a trip into Undervault refused without its unlock, with the realm's hint;
 *   <li>{@code refusal-level}: the same trip with the unlock at level 1, with "Requires level 6.";
 *   <li>{@code sealstone-level}: the signet on the sealstone at level 1, refused in the action bar;
 *   <li>{@code opens-onto}: the signet at level 6, the portal lit and "The portal opens onto the Undervault".
 * </ul>
 *
 * <p>Then it quits. It needs no input, so it also runs headless and in WSL. The variable's value starts every file
 * name.
 */
public final class QaPortalShots {
    static final String ENVIRONMENT_VARIABLE = "REALMFORGE_QA_PORTAL_SHOTS";

    /** Ticks in the new world before the first step: chunks, the time of day and the weather settle. */
    private static final int SETTLE_TICKS = 40;
    /** Ticks a command's result gets before its shot: it reaches the client and is drawn. */
    private static final int STEP_TICKS = 10;
    /** Ticks between two refusals: the server tells a player at most once every 3 seconds, and the title fades. */
    private static final int REFUSAL_TICKS = 70;
    /** Ticks for the last screenshot to reach the disk (written on Minecraft's I/O pool) before the client quits. */
    private static final int WRITE_TICKS = 40;
    /** Wide enough for the whole frame, 6 blocks tall, from 4 blocks away. */
    private static final int FIELD_OF_VIEW = 100;
    /** How far in front of the player (south) the frame stands: within a creative player's reach of the sealstone. */
    private static final int FRAME_AHEAD = 4;
    /**
     * The window's size: the HUD sits a fixed distance from the bottom, so in the dev client's small default window
     * the spell bar reaches the middle of the screen, under the refusal's lines.
     */
    private static final int WINDOW_WIDTH = 1280;
    private static final int WINDOW_HEIGHT = 960;

    private enum Stage { TITLE, WORLD, SHOOTING, DONE }

    /** One step of the run: what to do when its tick comes. */
    private record Step(int tick, Runnable action) {
    }

    private final String prefix;
    private final List<Step> steps = new ArrayList<>();
    private Stage stage = Stage.TITLE;
    private boolean characterCreated;
    private int ticksInWorld;
    private int ticks;

    private QaPortalShots(String prefix) {
        this.prefix = prefix;
    }

    /** Starts the run when the environment variable is set; a normal dev client is unaffected. */
    public static void registerIfRequested() {
        String prefix = System.getenv(ENVIRONMENT_VARIABLE);
        if (prefix == null || prefix.isBlank()) {
            return;
        }
        QaPortalShots run = new QaPortalShots(prefix.strip());
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> run.tick(Minecraft.getInstance()));
        RealmforgeQa.LOGGER.info("Portal screenshots requested ({}): the client will create a world and quit", prefix);
    }

    private void tick(Minecraft minecraft) {
        switch (stage) {
            case TITLE -> {
                if (minecraft.screen instanceof TitleScreen) {
                    minecraft.getWindow().setWindowed(WINDOW_WIDTH, WINDOW_HEIGHT);
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
                    // A new world opens character creation (C1), which closes once the player has a character, at
                    // level 1; the starting kit goes, so the hotbar shows only what a step puts there.
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
                    player.connection.sendCommand("gamerule advance_time false");
                    player.connection.sendCommand("gamerule advance_weather false");
                    player.connection.sendCommand("time set noon");
                    player.connection.sendCommand("weather clear");
                } else if (ticksInWorld == SETTLE_TICKS) {
                    plan(minecraft);
                    stage = Stage.SHOOTING;
                }
            }
            case SHOOTING -> {
                LocalPlayer player = minecraft.player;
                if (player == null) {
                    return;
                }
                ticks++;
                keepStill(minecraft, player);
                // The command feedback would cover the frame; a shot shows the frame before its tick.
                minecraft.gui.getChat().clearMessages(false);
                for (Step step : steps) {
                    if (step.tick() == ticks) {
                        step.action().run();
                    }
                }
                if (ticks == steps.getLast().tick() + WRITE_TICKS) {
                    stage = Stage.DONE;
                    minecraft.stop();
                }
            }
            case DONE -> {
            }
        }
    }

    /** The frame and the two blocks' names, the two refusals of a trip, then the sealstone below and at the level. */
    private void plan(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        minecraft.options.fov().set(FIELD_OF_VIEW);
        BlockPos sealstone = player.blockPosition().south(FRAME_AHEAD);
        int tick = 1;
        steps.add(new Step(tick, () -> {
            // 5 wide and 6 tall round a 3x4 opening, the sealstone in the bottom middle.
            command(player, "fill ~-2 ~ ~%1$d ~2 ~5 ~%1$d realmforge:gatewright_bricks".formatted(FRAME_AHEAD));
            command(player, "fill ~-1 ~1 ~%1$d ~1 ~4 ~%1$d minecraft:air".formatted(FRAME_AHEAD));
            command(player, "setblock ~ ~ ~%d realmforge:gatewright_sealstone".formatted(FRAME_AHEAD));
            hold(player, "realmforge:gatewright_bricks");
            // Undervault's chunk at its origin is generated now, not by the first trip there, whose refusal would
            // otherwise come seconds after its command.
            command(player, "execute in realmforge:stone run forceload add 0 0");
        }));
        // The frame's chunk section is rebuilt before it shows, which takes a software renderer a moment.
        tick += 2 * STEP_TICKS;
        shot(tick, minecraft, "held-bricks");
        steps.add(new Step(tick + 1, () -> hold(player, "realmforge:gatewright_sealstone")));
        tick += STEP_TICKS;
        shot(tick, minecraft, "held-sealstone");

        // A trip as /tp makes one: without the unlock, then with it at level 1.
        steps.add(new Step(tick + 1, () -> {
            hold(player, "minecraft:air");
            command(player, "qa realm enter realmforge:stone");
        }));
        tick += STEP_TICKS;
        shot(tick, minecraft, "refusal-unlock");
        tick += REFUSAL_TICKS;
        steps.add(new Step(tick, () -> {
            command(player, "qa realm unlock realmforge:stone");
            command(player, "qa realm enter realmforge:stone");
        }));
        tick += STEP_TICKS;
        shot(tick, minecraft, "refusal-level");
        tick += REFUSAL_TICKS;

        // The signet on the sealstone: refused below the realm's entry level, then the portal opens at it.
        steps.add(new Step(tick, () -> hold(player, "realmforge:undervault_signet")));
        tick += STEP_TICKS;
        steps.add(new Step(tick, () -> use(minecraft, sealstone)));
        tick += STEP_TICKS;
        shot(tick, minecraft, "sealstone-level");
        steps.add(new Step(tick + 1, () -> command(player, "qa level 6")));
        tick += STEP_TICKS;
        steps.add(new Step(tick, () -> use(minecraft, sealstone)));
        tick += STEP_TICKS;
        shot(tick, minecraft, "opens-onto");
    }

    private void shot(int tick, Minecraft minecraft, String name) {
        steps.add(new Step(tick, () -> Screenshot.grab(minecraft.gameDirectory, prefix + "-portal-" + name + ".png",
                minecraft.getMainRenderTarget(), 1,
                message -> RealmforgeQa.LOGGER.info("Portal screenshot: {}", message.getString()))));
    }

    private static void command(LocalPlayer player, String command) {
        player.connection.sendCommand(command);
    }

    /** Puts an item into the main hand, whose name the HUD then shows over the hotbar. */
    private static void hold(LocalPlayer player, String item) {
        command(player, "item replace entity @s weapon.mainhand with " + item);
    }

    /** A right-click on the sealstone's top with the main hand, as the player's own click would send it. */
    private static void use(Minecraft minecraft, BlockPos sealstone) {
        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(sealstone), Direction.UP, sealstone, false));
    }

    private static void createWorld(Minecraft minecraft) {
        String name = "qa-portal-shots-" + System.currentTimeMillis();
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
     * Holds the player facing south, looking a little up at the frame, with nothing in the way: no pause on lost
     * focus and a released mouse, so WSLg's broken pointer lock cannot turn the camera.
     */
    private static void keepStill(Minecraft minecraft, LocalPlayer player) {
        minecraft.options.pauseOnLostFocus = false;
        if (minecraft.mouseHandler.isMouseGrabbed()) {
            minecraft.mouseHandler.releaseMouse();
        }
        player.setYRot(0);
        player.setXRot(-12);
        player.yRotO = 0;
        player.xRotO = -12;
        player.setYHeadRot(0);
        player.yHeadRotO = 0;
        player.setYBodyRot(0);
        player.yBodyRotO = 0;
    }
}
