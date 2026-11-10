package io.github.matejkadlec.realmforge.qa.client.spelltree;

import io.github.matejkadlec.realmforge.character.Characters;
import io.github.matejkadlec.realmforge.character.SpellTrees;
import io.github.matejkadlec.realmforge.client.screen.CharacterCreationScreen;
import io.github.matejkadlec.realmforge.client.screen.SpellbookScreen;
import io.github.matejkadlec.realmforge.generated.GeneratedSpells;
import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import io.github.matejkadlec.realmforge.qa.mixin.MouseHandlerAccessor;
import io.github.matejkadlec.realmforge.spell.Spells;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/**
 * Screenshots of the spellbook's two tabs for an agent (C5, RLM-333), and a run of the window's real path: with the
 * environment variable {@code REALMFORGE_QA_SPELL_TREE_SHOTS} set, the dev client creates a superflat world and a Mage,
 * so the window opens by itself, clicks Choose and spells as a player would (real clicks on the screen, real payloads
 * to the server), closes it and casts Frostbolt for the HUD's spell bar and cast bar with their icons (A1, RLM-642),
 * repeats it with a Warrior for Bloodthirst's arrow, saves each state to
 * {@code run/client/screenshots/<value>-spelltree-<state>.png}, and quits. It logs an error for a click that did not do
 * what it should, as a GameTest's fake player has no client. It needs no input, so it runs headless under Xvfb.
 */
public final class QaSpellTreeShots {
    static final String ENVIRONMENT_VARIABLE = "REALMFORGE_QA_SPELL_TREE_SHOTS";

    private static final int WINDOW_WIDTH = 1280;
    private static final int WINDOW_HEIGHT = 960;
    /** Ticks between steps: for a screen to open, a payload to come back and a screenshot to be taken. */
    private static final int SETTLE_TICKS = 12;
    /** Ticks for the last screenshot to reach the disk (written on Minecraft's I/O pool) before the client quits. */
    private static final int WRITE_TICKS = 40;
    /** Ticks after which a step that waits for something gives up, and the run with it. */
    private static final int STEP_TIMEOUT_TICKS = 1200;

    /** The layout {@code SpellTreeTab} draws, as the tool clicks where a player would. */
    private static final int WIDTH = 352;
    private static final int PADDING = 8;
    private static final int CARD_GAP = 6;
    private static final int CARDS_TOP = 34;
    private static final int HEADER = 14;
    private static final int CELL = 24;
    private static final int CELL_GAP = 9;
    private static final int ROW = 31;
    private static final int BUTTON_HEIGHT = 16;

    private record Step(BooleanSupplier ready, Runnable action) {
    }

    private final String prefix;
    private final Deque<Step> steps = new ArrayDeque<>();
    private boolean planned;
    private int ticks;
    private int waited;

    private QaSpellTreeShots(String prefix) {
        this.prefix = prefix;
    }

    /** Starts the run when the environment variable is set; a normal dev client is unaffected. */
    public static void registerIfRequested() {
        String prefix = System.getenv(ENVIRONMENT_VARIABLE);
        if (prefix == null || prefix.isBlank()) {
            return;
        }
        QaSpellTreeShots run = new QaSpellTreeShots(prefix.strip());
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> run.tick(Minecraft.getInstance()));
        RealmforgeQa.LOGGER.info("Spell tree screenshots requested ({}): the client will play the window and quit",
                prefix);
    }

    private void tick(Minecraft minecraft) {
        // Planned here: while mods load, as this registers, the client does not exist yet.
        if (!planned) {
            planned = true;
            plan(minecraft);
        }
        ticks++;
        Step step = steps.peek();
        if (step == null || ticks < SETTLE_TICKS) {
            return;
        }
        if (!step.ready().getAsBoolean()) {
            if (++waited > STEP_TIMEOUT_TICKS) {
                RealmforgeQa.LOGGER.error("Spell tree screenshots: a step timed out, {} left", steps.size());
                minecraft.stop();
            }
            return;
        }
        steps.remove();
        waited = 0;
        ticks = 0;
        step.action().run();
    }

    private void plan(Minecraft minecraft) {
        when(() -> minecraft.screen instanceof TitleScreen && minecraft.getOverlay() == null, () -> {
            minecraft.options.pauseOnLostFocus = false;
            minecraft.getWindow().setWindowed(WINDOW_WIDTH, WINDOW_HEIGHT);
        });
        then(() -> createWorld(minecraft));
        // As for a player: the creation screen opens first, and its closing opens the window.
        when(() -> minecraft.screen instanceof CharacterCreationScreen, () -> command(minecraft,
                "qa character create realmforge:human realmforge:mage"));
        // Creating the character opens the window by itself, on its Spell Tree tab.
        when(() -> minecraft.screen instanceof SpellbookScreen, () -> hover(minecraft, 0, 0, 0));
        then(() -> shot(minecraft, "1-new-mage"));
        then(() -> clickChoose(minecraft, 0));
        when(() -> SpellTrees.chosen(minecraft.player).isPresent(), () -> {
            check(Spells.knownLevel(minecraft.player, GeneratedSpells.FROSTBOLT).orElse(0) == 1,
                    "Choose teaches Frostbolt");
            hover(minecraft, 0, 0, 1);
        });
        then(() -> shot(minecraft, "2-frost-picked"));
        then(() -> {
            command(minecraft, "advancement grant @s only realmforge:chapter/chapter_1");
            command(minecraft, "qa level 18");
        });
        when(() -> Characters.level(minecraft.player) == 18, () -> shot(minecraft, "3-frost-level-18"));
        then(() -> clickCell(minecraft, 0, 0, 1));
        then(() -> clickCell(minecraft, 0, 0, 1));
        then(() -> clickCell(minecraft, 0, 1, 0));
        then(() -> clickCell(minecraft, 0, 0, 0));
        then(() -> clickCell(minecraft, 0, 2, 1));
        then(() -> {
            check(Spells.knownLevel(minecraft.player, GeneratedSpells.FROSTBOLT).orElse(0) == 3,
                    "two clicks rank Frostbolt up");
            check(Spells.knownLevel(minecraft.player, GeneratedSpells.ICE_LANCE).orElse(0) == 1,
                    "a click teaches Ice Lance");
            check(SpellTrees.unspent(minecraft.player) == 12, "five clicks spend five of 17 points");
            hover(minecraft, 0, 1, 0);
        });
        then(() -> shot(minecraft, "4-frost-spent-tooltip"));
        // The HUD with the window closed (A1, RLM-642): the spell bar's icons, and the cast bar's during a cast.
        then(() -> minecraft.setScreen(null));
        then(() -> command(minecraft, "qa spell cast realmforge:frostbolt"));
        then(() -> shot(minecraft, "4b-hud-cast-bar"));
        then(() -> command(minecraft, "qa character reset"));
        when(() -> minecraft.screen instanceof CharacterCreationScreen, () -> command(minecraft,
                "qa character create realmforge:human realmforge:warrior"));
        when(() -> minecraft.screen instanceof SpellbookScreen && Characters.exists(minecraft.player),
                () -> clickChoose(minecraft, 0));
        when(() -> SpellTrees.chosen(minecraft.player).isPresent(), () -> command(minecraft, "qa level 12"));
        when(() -> Characters.level(minecraft.player) == 12, () -> hover(minecraft, 0, 2, 0));
        then(() -> shot(minecraft, "5-arms-arrow-locked"));
        then(() -> clickCell(minecraft, 0, 1, 0));
        then(() -> {
            check(Spells.knownLevel(minecraft.player, GeneratedSpells.REND).orElse(0) == 1, "a click teaches Rend");
            hover(minecraft, 0, 2, 0);
        });
        then(() -> shot(minecraft, "6-arms-arrow-open"));
        then(() -> clickCell(minecraft, 0, 2, 0));
        then(() -> check(Spells.knownLevel(minecraft.player, GeneratedSpells.BLOODTHIRST).orElse(0) == 1,
                "Rend opens Bloodthirst"));
        then(() -> minecraft.screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_2, 0, 0)));
        then(() -> shot(minecraft, "7-spell-bar-tab"));
        then(() -> learn(minecraft, "fireball", "frostbolt", "charge", "slam", "deep_wounds", "battle_shout"));
        then(() -> shot(minecraft, "8-spell-bar-two-columns"));
        then(() -> learn(minecraft, "execute", "overpower", "victory_rush", "cone_of_cold", "ice_lance", "frost_nova",
                "shatter", "blizzard"));
        then(() -> shot(minecraft, "9-spell-bar-scrollbar"));
        then(() -> {
        });
        when(() -> ticks >= WRITE_TICKS, () -> {
            RealmforgeQa.LOGGER.info("Spell tree screenshots: done");
            minecraft.stop();
        });
    }

    private void when(BooleanSupplier ready, Runnable action) {
        steps.add(new Step(ready, action));
    }

    private void then(Runnable action) {
        when(() -> true, action);
    }

    private void shot(Minecraft minecraft, String state) {
        Screenshot.grab(minecraft.gameDirectory, prefix + "-spelltree-" + state + ".png",
                minecraft.getMainRenderTarget(), 1,
                message -> RealmforgeQa.LOGGER.info("Spell tree screenshot: {}", message.getString()));
    }

    private static void check(boolean holds, String what) {
        if (holds) {
            RealmforgeQa.LOGGER.info("Spell tree check passed: {}", what);
        } else {
            RealmforgeQa.LOGGER.error("Spell tree check FAILED: {}", what);
        }
    }

    private static void learn(Minecraft minecraft, String... spells) {
        for (String spell : spells) {
            command(minecraft, "qa spell learn realmforge:" + spell);
        }
    }

    private static void command(Minecraft minecraft, String command) {
        minecraft.player.connection.sendCommand(command);
    }

    private static int top(Minecraft minecraft) {
        return Math.max(10, minecraft.screen.height / 2 - 120);
    }

    private static int bottom(Minecraft minecraft) {
        return Math.min(minecraft.screen.height - 10, minecraft.screen.height / 2 + 120);
    }

    private static int cardX(Minecraft minecraft, int card) {
        int cardWidth = (WIDTH - 2 * PADDING - 2 * CARD_GAP) / 3;
        return (minecraft.screen.width - WIDTH) / 2 + PADDING + card * (cardWidth + CARD_GAP);
    }

    private static int cellX(Minecraft minecraft, int card, int column) {
        return cardX(minecraft, card) + CELL_GAP + column * (CELL + CELL_GAP) + CELL / 2;
    }

    private static int cellY(Minecraft minecraft, int row) {
        return top(minecraft) + CARDS_TOP + HEADER + row * ROW + CELL / 2;
    }

    /** Puts the game's cursor over a spell, so the next frame draws its tooltip. */
    private static void hover(Minecraft minecraft, int card, int row, int column) {
        int scale = minecraft.getWindow().getGuiScale();
        MouseHandlerAccessor mouse = (MouseHandlerAccessor) minecraft.mouseHandler;
        mouse.realmforgeQa$setXpos(cellX(minecraft, card, column) * (double) scale);
        mouse.realmforgeQa$setYpos(cellY(minecraft, row) * (double) scale);
    }

    private static void click(Minecraft minecraft, double x, double y) {
        minecraft.screen.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0)),
                false);
    }

    private static void clickCell(Minecraft minecraft, int card, int row, int column) {
        click(minecraft, cellX(minecraft, card, column), cellY(minecraft, row));
    }

    private static void clickChoose(Minecraft minecraft, int card) {
        click(minecraft, cardX(minecraft, card) + 20, bottom(minecraft) - PADDING - 3 - BUTTON_HEIGHT / 2);
    }

    private static void createWorld(Minecraft minecraft) {
        String name = "qa-spell-tree-shots-" + System.currentTimeMillis();
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true,
                WorldDataConfiguration.DEFAULT);
        minecraft.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(0L, false, false),
                registries -> registries.lookupOrThrow(Registries.WORLD_PRESET)
                        .getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                minecraft.screen);
    }
}
