package io.github.matejkadlec.realmforge.client.screen;

import io.github.matejkadlec.realmforge.client.hud.SpellBarHud;
import io.github.matejkadlec.realmforge.client.hud.SpellBarLayout;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * The spell bar's drag mode (RLM-404; Matěj, 2026-10-24), opened by Spellbar Position's button in the Realmforge config
 * screen ({@link RealmforgeConfigScreen}, RLM-495), which it hides: the player drags the spell bar and the resource bar under it, one group, anywhere on the screen but off it,
 * partly or wholly ({@link SpellBarLayout#clamp}). While dragging, a red dashed line twice a line's width marks the
 * screen's middle, and the group snaps to it ({@link CentreSnap}). Cancel (and Escape) goes back to the config screen
 * with the place before, Save hands it the new one, Reset Position puts the group back at its default place. The
 * config screen keeps the place as a draft, which its own Save &amp; Quit writes into the client config and its Cancel
 * drops; the group is drawn at the size the screen's drafts set. In game the world
 * and the HUD show through, with the group drawn where it is being dragged ({@link SpellBarHud#draw}); from the main
 * menu empty slots stand in for it.
 */
public final class SpellBarPositionScreen extends Screen {
    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 20;
    /** Between the buttons and the screen's edges, and between the two on the right. */
    private static final int MARGIN = 8;
    private static final int BUTTON_GAP = 4;
    /** The middle line: red, dashed, two pixels wide, twice a normal line. */
    private static final int LINE_COLOR = 0xFFE02020;
    private static final int LINE_WIDTH = 2;
    private static final int DASH = 4;
    /** An outline around the group, so its whole area reads as something to grab. */
    private static final int OUTLINE = 0x90FFFFFF;
    private static final int TITLE = 0xFFFFFFFF;
    private static final int HINT = 0xFFC8C8C8;

    private final Screen parent;
    /** The group's scale, as the config screen's drafts set it. */
    private final float scale;
    private final Consumer<RealmforgeConfigScreen.Place> onSave;
    private final CentreSnap snap = new CentreSnap();
    /** The group's place as fractions of the room the screen leaves it, as the client config saves it. */
    private double placeX;
    private double placeY;
    /** Whether the group is being dragged, where the mouse holds it, and where the mouse was last. */
    private boolean moving;
    private double grabX;
    private double grabY;
    private double lastMouseX;
    private double lastMouseY;

    public SpellBarPositionScreen(Screen parent, RealmforgeConfigScreen.Place place, float scale,
            Consumer<RealmforgeConfigScreen.Place> onSave) {
        super(Component.translatable(GeneratedTexts.GUI_SPELL_BAR_POSITION));
        this.parent = parent;
        this.scale = scale;
        this.onSave = onSave;
        placeX = place.x();
        placeY = place.y();
    }

    @Override
    protected void init() {
        int y = height - MARGIN - BUTTON_HEIGHT;
        addRenderableWidget(Button.builder(Component.translatable(GeneratedTexts.GUI_RESET_POSITION), button -> {
            placeX = SpellBarLayout.DEFAULT_PLACE;
            placeY = SpellBarLayout.DEFAULT_PLACE;
        }).bounds(MARGIN, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(width - MARGIN - 2 * BUTTON_WIDTH - BUTTON_GAP, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable(GeneratedTexts.GUI_SAVE), button -> save())
                .bounds(width - MARGIN - BUTTON_WIDTH, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    private SpellBarLayout.Group group() {
        return SpellBarLayout.place(width, height, placeX, placeY, scale);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        SpellBarLayout.Group group = group();
        if (event.button() != 0 || !group.contains(event.x(), event.y())) {
            return false;
        }
        moving = true;
        grabX = event.x() - group.x();
        grabY = event.y() - group.y();
        lastMouseX = event.x();
        lastMouseY = event.y();
        snap.reset();
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!moving) {
            return super.mouseDragged(event, dx, dy);
        }
        move(event.x(), event.y());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (!moving || event.button() != 0) {
            return super.mouseReleased(event);
        }
        moving = false;
        snap.reset();
        return true;
    }

    /** The snap lets go after its pull even while the mouse holds still, when no drag event comes. */
    @Override
    public void tick() {
        if (moving) {
            move(lastMouseX, lastMouseY);
        }
    }

    private void move(double mouseX, double mouseY) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        SpellBarLayout.Group wanted = SpellBarLayout.clamp(width, height, new SpellBarLayout.Group(
                (int) Math.round(mouseX - grabX), (int) Math.round(mouseY - grabY), scale));
        int roomX = width - wanted.width();
        int x = snap.x(wanted.x(), SpellBarLayout.middle(roomX), Util.getMillis());
        placeX = SpellBarLayout.fraction(x, roomX);
        placeY = SpellBarLayout.fraction(wanted.y(), height - wanted.height());
    }

    private void save() {
        onSave.accept(new RealmforgeConfigScreen.Place(placeX, placeY));
        minecraft.setScreen(parent);
    }

    /** In game, no dimmed or blurred background: the group is placed against the world and the HUD as they are. */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if (minecraft.level == null) {
            super.extractBackground(graphics, mouseX, mouseY, a);
        } else {
            minecraft.gui.extractDeferredSubtitles();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        SpellBarLayout.Group group = group();
        SpellBarHud.draw(graphics, font, minecraft.player, group);
        graphics.outline(group.x() - 1, group.y() - 1, group.width() + 2, group.height() + 2, OUTLINE);
        if (moving) {
            int lineX = width / 2 - LINE_WIDTH / 2;
            for (int dashY = 0; dashY < height; dashY += 2 * DASH) {
                graphics.fill(lineX, dashY, lineX + LINE_WIDTH, Math.min(dashY + DASH, height), LINE_COLOR);
            }
        }
        graphics.centeredText(font, title, width / 2, MARGIN, TITLE);
        graphics.centeredText(font, Component.translatable(GeneratedTexts.GUI_SPELL_BAR_POSITION_HINT), width / 2,
                MARGIN + 12, HINT);
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
