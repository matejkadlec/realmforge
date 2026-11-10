package io.github.matejkadlec.realmforge.qa.client.gear;

import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedItemExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedItemExpectations.ItemExpectation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Screenshots of worn armor for an agent (RLM-505): with the environment variable {@code REALMFORGE_QA_GEAR_SHOTS} set,
 * the dev client creates a fresh superflat world from the title screen and a Human Mage in it, puts on every armor
 * look of ours in turn (the pieces of {@link GeneratedItemExpectations} that share a look, each in its slot), saves the
 * player from the front and from the back to {@code run/client/screenshots/<label>-gear-<look>-<side>.png}, then opens
 * the survival inventory with every armor piece in it for the item icons ({@code <label>-gear-inventory.png}), and
 * quits. It needs no input, so it also runs headless and in WSL. The variable's value starts every file name.
 */
public final class QaGearShots {
    static final String ENVIRONMENT_VARIABLE = "REALMFORGE_QA_GEAR_SHOTS";

    /** Ticks in the new world before the first set goes on: chunks, the time of day and the weather settle. */
    private static final int SETTLE_TICKS = 40;
    /** Ticks a set, a camera or a screen gets before its shot: the equipment reaches the client and is drawn. */
    private static final int STEP_TICKS = 8;
    /** Ticks for the last screenshot to reach the disk (written on Minecraft's I/O pool) before the client quits. */
    private static final int WRITE_TICKS = 40;
    /** The third-person camera stands 4 blocks from the eyes: at this field of view the whole player fits the frame. */
    private static final int FIELD_OF_VIEW = 50;
    /** The armor slots of {@code /item replace}, by the gear kind of the pipeline. */
    private static final Map<String, String> SLOTS =
            Map.of("head", "armor.head", "chest", "armor.chest", "legs", "armor.legs", "feet", "armor.feet");

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

    private QaGearShots(String prefix) {
        this.prefix = prefix;
    }

    /** Starts the run when the environment variable is set; a normal dev client is unaffected. */
    public static void registerIfRequested() {
        String prefix = System.getenv(ENVIRONMENT_VARIABLE);
        if (prefix == null || prefix.isBlank()) {
            return;
        }
        QaGearShots run = new QaGearShots(prefix.strip());
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> run.tick(Minecraft.getInstance()));
        RealmforgeQa.LOGGER.info("Gear screenshots requested ({}): the client will create a world and quit", prefix);
    }

    /** The armor pieces of each look, in the order the pipeline lists the items: look to (slot, item id) pairs. */
    static Map<String, Map<String, String>> looks() {
        Map<String, Map<String, String>> looks = new LinkedHashMap<>();
        for (ItemExpectation item : GeneratedItemExpectations.ITEMS) {
            item.gear().filter(gear -> !gear.look().isEmpty() && SLOTS.containsKey(gear.kind()))
                    .ifPresent(gear -> looks.computeIfAbsent(gear.look(), look -> new LinkedHashMap<>())
                            .put(SLOTS.get(gear.kind()), item.id()));
        }
        return looks;
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
                    // A new world opens character creation (C1), which closes once the player has a character; the
                    // starting kit goes again, so only the sets under test are worn.
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
                if (minecraft.screen == null) {
                    keepStill(minecraft, player);
                }
                // The command feedback would cover the player; a shot shows the frame before its tick.
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

    /** Every look from the front and from the back, then the survival inventory with every piece in it. */
    private void plan(Minecraft minecraft) {
        int tick = 1;
        minecraft.options.fov().set(FIELD_OF_VIEW);
        // No HUD over the player: the spell bar and the hotbar sit where the body is drawn.
        minecraft.options.hideGui = true;
        Map<String, Map<String, String>> looks = looks();
        for (Map.Entry<String, Map<String, String>> look : looks.entrySet()) {
            // The look's id without its namespace: a file name takes no colon.
            String name = look.getKey().substring(look.getKey().indexOf(':') + 1);
            steps.add(new Step(tick, () -> {
                wear(minecraft, look.getValue());
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            }));
            tick += STEP_TICKS;
            steps.add(new Step(tick, () -> shoot(minecraft, name + "-front")));
            steps.add(new Step(tick + 1, () -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK)));
            tick += STEP_TICKS;
            steps.add(new Step(tick, () -> shoot(minecraft, name + "-back")));
            tick++;
        }
        steps.add(new Step(tick, () -> {
            LocalPlayer player = minecraft.player;
            player.connection.sendCommand("gamemode survival");
            looks.values().forEach(pieces -> pieces.values().forEach(
                    id -> player.connection.sendCommand("give @s " + id)));
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            minecraft.options.hideGui = false;
        }));
        tick += STEP_TICKS;
        steps.add(new Step(tick, () -> minecraft.setScreen(new InventoryScreen(minecraft.player))));
        tick += STEP_TICKS;
        steps.add(new Step(tick, () -> shoot(minecraft, "inventory")));
    }

    private static void wear(Minecraft minecraft, Map<String, String> pieces) {
        LocalPlayer player = minecraft.player;
        SLOTS.values().forEach(slot -> player.connection.sendCommand("item replace entity @s " + slot
                + " with minecraft:air"));
        pieces.forEach((slot, id) -> player.connection.sendCommand("item replace entity @s " + slot + " with " + id));
    }

    private void shoot(Minecraft minecraft, String name) {
        Screenshot.grab(minecraft.gameDirectory, prefix + "-gear-" + name + ".png", minecraft.getMainRenderTarget(), 1,
                message -> RealmforgeQa.LOGGER.info("Gear screenshot: {}", message.getString()));
    }

    private static void createWorld(Minecraft minecraft) {
        String name = "qa-gear-shots-" + System.currentTimeMillis();
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
     * Holds the player facing south and looking level, with nothing in the way: no pause on lost focus and a released
     * mouse, so WSLg's broken pointer lock cannot turn the camera.
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
