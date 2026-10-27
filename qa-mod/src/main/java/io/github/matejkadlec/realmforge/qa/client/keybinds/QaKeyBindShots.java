package io.github.matejkadlec.realmforge.qa.client.keybinds;

import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Screenshots of the Key Binds screen for an agent (RLM-382): with the environment variable
 * {@code REALMFORGE_QA_KEY_BINDS_SHOTS} set, the dev client opens Key Binds from the title screen, the screen a player
 * gets (Controlling's, with the pack), saves it page by page from the top to the bottom of its list to
 * {@code run/client/screenshots/<value>-keybinds-<page>.png}, and quits. Each page overlaps the one before by a row, so
 * every header shows whole on some page. It needs no input and no world, so it runs headless under Xvfb.
 */
public final class QaKeyBindShots {
    static final String ENVIRONMENT_VARIABLE = "REALMFORGE_QA_KEY_BINDS_SHOTS";

    /** The window the shots are taken in: at the dev client's GUI scale 2 the list shows about twenty rows. */
    private static final int WINDOW_WIDTH = 1280;
    private static final int WINDOW_HEIGHT = 960;
    /** Ticks for a resized window, a new screen and a scrolled list to settle before the next step. */
    private static final int SETTLE_TICKS = 10;
    private static final int SCROLL_TICKS = 3;
    /** Ticks for the last screenshot to reach the disk (written on Minecraft's I/O pool) before the client quits. */
    private static final int WRITE_TICKS = 40;
    /** One row of the list (vanilla's {@code KeyBindsList}), which consecutive pages share. */
    private static final int ROW_HEIGHT = 20;

    private enum Stage { TITLE, RESIZED, OPENED, PAGES, DONE }

    private final String prefix;
    private Stage stage = Stage.TITLE;
    private int ticks;
    private int page;

    private QaKeyBindShots(String prefix) {
        this.prefix = prefix;
    }

    /** Starts the run when the environment variable is set; a normal dev client is unaffected. */
    public static void registerIfRequested() {
        String prefix = System.getenv(ENVIRONMENT_VARIABLE);
        if (prefix == null || prefix.isBlank()) {
            return;
        }
        QaKeyBindShots run = new QaKeyBindShots(prefix.strip());
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> run.tick(Minecraft.getInstance()));
        RealmforgeQa.LOGGER.info("Key Binds screenshots requested ({}): the client will open Key Binds and quit",
                prefix);
    }

    private void tick(Minecraft minecraft) {
        ticks++;
        switch (stage) {
            case TITLE -> {
                // The title screen exists behind the loading overlay before the first resource load has finished.
                if (minecraft.screen instanceof TitleScreen && minecraft.getOverlay() == null) {
                    minecraft.options.pauseOnLostFocus = false;
                    minecraft.getWindow().setWindowed(WINDOW_WIDTH, WINDOW_HEIGHT);
                    next(Stage.RESIZED);
                }
            }
            case RESIZED -> {
                if (ticks >= SETTLE_TICKS) {
                    // Controlling replaces vanilla's screen as it opens (ScreenEvent.Opening), as for a player.
                    minecraft.setScreen(new KeyBindsScreen(minecraft.screen, minecraft.options));
                    next(Stage.OPENED);
                }
            }
            case OPENED -> {
                if (ticks >= SETTLE_TICKS) {
                    if (list(minecraft) == null) {
                        RealmforgeQa.LOGGER.error("Key Binds screenshots: {} has no list", minecraft.screen);
                        finish(minecraft);
                        return;
                    }
                    scrollTo(minecraft, 0);
                    next(Stage.PAGES);
                }
            }
            case PAGES -> {
                if (ticks < SCROLL_TICKS) {
                    return;
                }
                AbstractSelectionList<?> list = list(minecraft);
                if (list == null) {
                    finish(minecraft);
                    return;
                }
                String name = String.format(Locale.ROOT, "%s-keybinds-%02d.png", prefix, page + 1);
                Screenshot.grab(minecraft.gameDirectory, name, minecraft.getMainRenderTarget(), 1,
                        message -> RealmforgeQa.LOGGER.info("Key Binds screenshot: {}", message.getString()));
                if (list.scrollAmount() >= list.maxScrollAmount()) {
                    RealmforgeQa.LOGGER.info("Key Binds screenshots: {} pages of {}", page + 1, minecraft.screen);
                    next(Stage.DONE);
                    return;
                }
                page++;
                scrollTo(minecraft, page);
                ticks = 0;
            }
            case DONE -> {
                if (ticks >= WRITE_TICKS) {
                    finish(minecraft);
                }
            }
        }
    }

    private void next(Stage stage) {
        this.stage = stage;
        ticks = 0;
    }

    private static void finish(Minecraft minecraft) {
        minecraft.stop();
    }

    /** Scrolls the list to the start of page {@code page}, one row before the end of the page above. */
    private static void scrollTo(Minecraft minecraft, int page) {
        AbstractSelectionList<?> list = list(minecraft);
        if (list != null) {
            list.setScrollAmount((double) page * (list.getHeight() - ROW_HEIGHT));
        }
    }

    /** The screen's list of key binds: vanilla's {@code KeyBindsList} or Controlling's, which extends it. */
    private static AbstractSelectionList<?> list(Minecraft minecraft) {
        if (minecraft.screen == null) {
            return null;
        }
        for (GuiEventListener child : minecraft.screen.children()) {
            if (child instanceof AbstractSelectionList<?> list) {
                return list;
            }
        }
        return null;
    }
}
