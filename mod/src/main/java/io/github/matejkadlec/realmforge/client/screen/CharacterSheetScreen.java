package io.github.matejkadlec.realmforge.client.screen;

import io.github.matejkadlec.realmforge.character.CharacterSheetView;
import io.github.matejkadlec.realmforge.character.CharacterSheetView.CharacterTab;
import io.github.matejkadlec.realmforge.character.CharacterSheetView.Column;
import io.github.matejkadlec.realmforge.character.CharacterSheetView.Part;
import io.github.matejkadlec.realmforge.character.CharacterSheetView.PrimaryStat;
import io.github.matejkadlec.realmforge.character.CharacterSheetView.Row;
import io.github.matejkadlec.realmforge.character.CharacterSheetView.Section;
import io.github.matejkadlec.realmforge.character.CharacterSheetView.Spend;
import io.github.matejkadlec.realmforge.character.CharacterSheetView.StatTab;
import io.github.matejkadlec.realmforge.character.XpBarView;
import io.github.matejkadlec.realmforge.character.net.SpendStatPointPayload;
import io.github.matejkadlec.realmforge.client.hud.XpBarHud;
import io.github.matejkadlec.realmforge.client.input.CharacterSheetKey;
import io.github.matejkadlec.realmforge.client.screen.layout.Flex;
import io.github.matejkadlec.realmforge.client.screen.layout.Flex.Align;
import io.github.matejkadlec.realmforge.client.screen.layout.Flex.Insets;
import io.github.matejkadlec.realmforge.client.screen.layout.Flex.Rect;
import io.github.matejkadlec.realmforge.client.tooltip.RightAlignedLines;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

/**
 * The character sheet (RLM-136, architecture 18): draws {@link CharacterSheetView} over the game, which keeps running,
 * laid out with {@link Flex} (RLM-560) rather than hand-placed coordinates. The player's name and level head it on the
 * left in bold, the tabs on the right (Character, Offense, Defense: RLM-509, RLM-235); a click, the number keys, Tab or
 * the arrow keys switch tabs, and the sheet reopens on the last one. The heading and the selected tab are white, so gold
 * marks only the column headings (Matěj, 2026-10-14). The panel is opaque, so the HUD never shows through it
 * (RLM-560), as big as its largest tab, so it keeps its size when the tab changes, and drawn smaller when it does not
 * fit the screen. The values follow the player's attributes every tick while the sheet is open. Hovering a stat shows
 * what it does; a primary stat's tooltip adds what its points give, the race's and the class's on the identity line
 * what they are and give.
 *
 * <p>The Character tab: the player's model on the left, turning toward the mouse as in the inventory; on the right the
 * identity line, the XP bar with the XP toward the next level, and the primary stats, one compact row each with a mark
 * for each milestone, gold once reached. While stat points are unspent, a small {@code +} ends each primary stat's row:
 * hovered, it says what the point gives, in the headings' yellow, aligned right under the stat's name; a click sends
 * {@link SpendStatPointPayload}, which the server checks, and the sheet shows the result on the next tick (RLM-196).
 * Below them an inner panel sets the other stats apart in two columns, without a heading. Offense and Defense: two
 * columns of equal width each (Matěj, 2026-10-31: a third column made every tab wide), each a section under its heading
 * or two one under the other (Physical over Critical Hits, Mitigation over Health).
 */
public final class CharacterSheetScreen extends Screen {
    private static final int PADDING = 10;
    private static final int ROW = 11;
    private static final int HEADING_ROW = 13;
    private static final int COLUMN_GAP = 16;
    /** The least space between a stat's name and its value. */
    private static final int VALUE_GAP = 8;
    private static final int TAB_GAP = 12;
    private static final int SECTION_GAP = 8;
    private static final int INNER_PADDING = 6;
    private static final int MODEL_WIDTH = 64;
    private static final int MODEL_HEIGHT = 100;
    /** The model's size, as the inventory's 30 for its smaller box. */
    private static final int MODEL_SIZE = 40;
    private static final int XP_BAR_HEIGHT = 7;
    private static final int MARK_SIZE = 5;
    private static final int MARK_GAP = 2;
    private static final int TOOLTIP_WIDTH = 220;
    /** The panel, opaque so the HUD does not show through (RLM-560), its border, and the inner panels in it. */
    private static final int PANEL = 0xFF141414;
    private static final int PANEL_BORDER = 0xFF4A4A4A;
    private static final int INNER_PANEL = 0xFF1F1F1F;
    private static final int RULE = 0xFF4A4A4A;
    /** The player's name and level, and the selected tab. */
    private static final int HEADING = 0xFFFFFFFF;
    private static final int TAB_IDLE = 0xFFA0A0A0;
    /** Between idle and selected, so a hovered tab does not look selected. */
    private static final int TAB_HOVER = 0xFFC8C8C8;
    /** Under the hovered stat's row. */
    private static final int ROW_HOVER = 0x20FFFFFF;
    /** A milestone still ahead: a dark square in gray. */
    private static final int MARK_EMPTY = 0xFF2A2A2A;
    /** The {@code +} that spends a stat point: a square at the end of the row. */
    private static final int SPEND_SIZE = 9;
    private static final int SPEND_FILL = 0xC0203820;
    private static final int SPEND_FILL_HOVER = 0xE0306030;

    /** The tab the sheet shows, kept while the game runs so the sheet reopens on it. */
    private static int selectedTab;

    @Nullable
    private CharacterSheetView view;

    /** What a node of the layout draws. */
    private sealed interface Paint permits Fill, Text, Tab, Hover, Model, XpBar, Mark, SpendButton {
    }

    /** A rectangle in a colour, with a 1-pixel border unless {@code border} is 0. */
    private record Fill(int color, int border) implements Paint {
    }

    /** A line of text in a colour, at a scale of the font (1 is its own size), centred in its row's height. */
    private record Text(Component text, int color, float scale) implements Paint {
    }

    /** A tab's name, which a click selects. */
    private record Tab(int index, Component name) implements Paint {
    }

    /** A hover area: its tooltip's title and the lines under it, each in its colour. */
    private record Hover(Component title, List<Component> lines) implements Paint {
    }

    /** The player's model. */
    private record Model() implements Paint {
    }

    private record XpBar(XpBarView xp) implements Paint {
    }

    /** A milestone of a primary stat. */
    private record Mark(boolean reached) implements Paint {
    }

    /** The {@code +} that spends a stat point, with the stat's name for its tooltip. */
    private record SpendButton(Spend spend, Component label) implements Paint {
    }

    /** The laid-out panel: its root in panel units, and where and at which scale it sits on the screen. */
    private record Layout(Flex<Paint> root, float x, float y, float scale) {
        double panelX(double screenX) {
            return (screenX - x) / scale;
        }

        double panelY(double screenY) {
            return (screenY - y) / scale;
        }
    }

    public CharacterSheetScreen() {
        super(Component.translatable(GeneratedTexts.GUI_CHARACTER_SHEET));
    }

    @Override
    protected void init() {
        refresh();
    }

    @Override
    public void tick() {
        refresh();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (CharacterSheetKey.KEY.get().matches(event)) {
            onClose();
            return true;
        }
        if (view != null) {
            int tabs = view.tabNames().size();
            int key = event.key();
            if (key >= GLFW.GLFW_KEY_1 && key < GLFW.GLFW_KEY_1 + tabs) {
                selectedTab = key - GLFW.GLFW_KEY_1;
                return true;
            }
            if (key == GLFW.GLFW_KEY_TAB || key == GLFW.GLFW_KEY_RIGHT) {
                selectedTab = (selectedTab + 1) % tabs;
                return true;
            }
            if (key == GLFW.GLFW_KEY_LEFT) {
                selectedTab = (selectedTab + tabs - 1) % tabs;
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (view != null && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            Layout layout = layout(view);
            Paint clicked = interactiveAt(layout, event.x(), event.y());
            if (clicked instanceof Tab tab) {
                selectedTab = tab.index();
                return true;
            }
            if (clicked instanceof SpendButton spend) {
                ClientPacketDistributor.sendToServer(new SpendStatPointPayload(spend.spend().stat()));
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        if (view == null) {
            return;
        }
        Layout layout = layout(view);
        Paint hovered = interactiveAt(layout, mouseX, mouseY);
        List<Rect> models = new ArrayList<>();

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(layout.x(), layout.y());
        pose.scale(layout.scale());
        layout.root().forEach((rect, paint) -> {
            draw(graphics, rect, paint, paint == hovered);
            if (paint instanceof Model) {
                models.add(rect);
            }
        });
        pose.popMatrix();
        // The model is drawn in the screen's own pixels: the entity's picture ignores the pose.
        if (minecraft.player != null) {
            for (Rect rect : models) {
                InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, screenX(layout, rect.x()),
                        screenY(layout, rect.y()), screenX(layout, rect.right()), screenY(layout, rect.bottom()),
                        Math.round(MODEL_SIZE * layout.scale()), 0.0625F, mouseX, mouseY, minecraft.player);
            }
        }
        if (hovered instanceof Hover hover) {
            tooltip(graphics, hover, mouseX, mouseY);
        } else if (hovered instanceof SpendButton spend) {
            spendTooltip(graphics, spend, mouseX, mouseY);
        }
    }

    /** The tab, {@code +} or hover area under the screen position, or null. */
    @Nullable
    private static Paint interactiveAt(Layout layout, double screenX, double screenY) {
        return layout.root().payloadAt(layout.panelX(screenX), layout.panelY(screenY),
                paint -> paint instanceof Tab || paint instanceof SpendButton || paint instanceof Hover).orElse(null);
    }

    private void refresh() {
        if (minecraft.player != null) {
            view = CharacterSheetView.of(minecraft.player);
        }
    }

    private int selectedTab() {
        return view == null ? 0 : Math.floorMod(selectedTab, view.tabNames().size());
    }

    /**
     * The panel for the selected tab: the heading row with the tabs, a rule, and the tab's body in a box as big as the
     * largest tab's, centred on the screen and drawn smaller when it does not fit.
     */
    private Layout layout(CharacterSheetView sheet) {
        List<Flex<Paint>> bodies = new ArrayList<>();
        bodies.add(characterBody(sheet.character()));
        sheet.statTabs().forEach(tab -> bodies.add(statBody(tab)));
        int bodyWidth = 0;
        int bodyHeight = 0;
        for (Flex<Paint> body : bodies) {
            bodyWidth = Math.max(bodyWidth, body.preferredWidth());
            bodyHeight = Math.max(bodyHeight, body.preferredHeight());
        }

        Flex<Paint> tabs = Flex.<Paint>row().gap(TAB_GAP);
        List<Component> names = sheet.tabNames();
        for (int i = 0; i < names.size(); i++) {
            tabs.add(Flex.leaf(font.width(names.get(i)), HEADING_ROW, new Tab(i, names.get(i))));
        }
        Component title = sheet.title().copy().withStyle(ChatFormatting.BOLD);
        Flex<Paint> heading = Flex.<Paint>row().add(text(title, HEADING, HEADING_ROW),
                Flex.<Paint>leaf(TAB_GAP, 0, null).grow(1), tabs);
        Flex<Paint> body = Flex.<Paint>column().align(Align.STRETCH).size(bodyWidth, bodyHeight)
                .add(bodies.get(selectedTab()));
        Flex<Paint> root = Flex.<Paint>column().align(Align.STRETCH).padding(PADDING).gap(4)
                .payload(new Fill(PANEL, PANEL_BORDER))
                .add(heading, Flex.leaf(0, 1, new Fill(RULE, 0)), Flex.<Paint>spacer().height(2), body);

        int panelWidth = root.preferredWidth();
        int panelHeight = root.preferredHeight();
        root.layout(0, 0, panelWidth, panelHeight);
        float scale = Math.min(1, Math.min((width - 2f * PADDING) / panelWidth, (height - 2f * PADDING) / panelHeight));
        return new Layout(root, (width - panelWidth * scale) / 2, (height - panelHeight * scale) / 2, scale);
    }

    /**
     * The Character tab: the model beside the identity line, the XP bar and the primary stats, then the other stats in
     * an inner panel.
     */
    private Flex<Paint> characterBody(CharacterTab tab) {
        Flex<Paint> right = Flex.<Paint>column().gap(4).grow(1).align(Align.STRETCH);
        if (!tab.identity().parts().isEmpty()) {
            Flex<Paint> line = Flex.<Paint>row().gap(font.width(" "));
            for (Part part : tab.identity().parts()) {
                Flex<Paint> name = text(part.name(), CharacterSheetView.TITLE, HEADING_ROW);
                line.add(part.tooltip().isEmpty() ? name
                        : Flex.<Paint>row().payload(new Hover(part.name(), part.tooltip())).add(name));
            }
            right.add(line);
        }
        tab.xp().ifPresent(xp -> right.add(Flex.<Paint>column().gap(2).align(Align.STRETCH)
                .add(Flex.leaf(0, XP_BAR_HEIGHT, new XpBar(xp)),
                        text(CharacterSheetView.xpText(xp), CharacterSheetView.TEXT, ROW))));

        Flex<Paint> stats = Flex.<Paint>column().align(Align.STRETCH);
        Flex<Paint> statsHeading = Flex.<Paint>row().add(text(underlined(tab.primaryTitle()), CharacterSheetView.TITLE,
                HEADING_ROW), Flex.<Paint>leaf(VALUE_GAP, 0, null).grow(1));
        if (tab.unspent() != null) {
            statsHeading.add(text(tab.unspent(), HEADING, HEADING_ROW));
        }
        stats.add(statsHeading);
        tab.primary().forEach(stat -> stats.add(primaryRow(stat)));
        right.add(stats);

        Flex<Paint> top = Flex.<Paint>row().gap(COLUMN_GAP)
                .add(Flex.leaf(MODEL_WIDTH, MODEL_HEIGHT, new Model()), right);
        List<Flex<Paint>> otherColumns = new ArrayList<>();
        tab.other().forEach(rows -> otherColumns.add(rowsColumn(rows)));
        Flex<Paint> otherPanel = Flex.<Paint>row().gap(COLUMN_GAP).padding(Insets.of(INNER_PADDING - 2, INNER_PADDING))
                .payload(new Fill(INNER_PANEL, 0)).addAll(equalColumns(otherColumns));
        return Flex.<Paint>column().gap(SECTION_GAP).align(Align.STRETCH).add(top, otherPanel);
    }

    /** A primary stat: its name, value and milestone marks, then the {@code +} while points are unspent. */
    private Flex<Paint> primaryRow(PrimaryStat stat) {
        Row row = stat.row();
        Flex<Paint> marks = Flex.<Paint>row().gap(MARK_GAP).align(Align.CENTER).padding(new Insets(0, 4, 0, 6));
        stat.marks().forEach(mark -> marks.add(Flex.leaf(MARK_SIZE, MARK_SIZE, new Mark(mark.reached()))));
        Flex<Paint> spend = stat.spend() != null
                ? Flex.leaf(SPEND_SIZE, SPEND_SIZE, new SpendButton(stat.spend(), row.label()))
                : Flex.<Paint>leaf(SPEND_SIZE, SPEND_SIZE, null);
        return Flex.<Paint>row().align(Align.CENTER).payload(new Hover(row.label(), row.tooltip()))
                .add(text(row.label(), row.color(), ROW), Flex.<Paint>leaf(VALUE_GAP, 0, null).grow(1),
                        text(Component.literal(row.value()), row.color(), ROW), marks, spend);
    }

    /** Offense or Defense: columns of equal width, each its sections one under another. */
    private Flex<Paint> statBody(StatTab tab) {
        List<Flex<Paint>> columns = new ArrayList<>();
        for (Column column : tab.columns()) {
            Flex<Paint> sections = Flex.<Paint>column().gap(SECTION_GAP).align(Align.STRETCH);
            column.sections().forEach(section -> sections.add(sectionColumn(section)));
            columns.add(sections);
        }
        return Flex.<Paint>row().gap(COLUMN_GAP).addAll(equalColumns(columns));
    }

    /** Gives every column the widest one's width, so their space splits evenly. */
    private static List<Flex<Paint>> equalColumns(List<Flex<Paint>> columns) {
        int widest = columns.stream().mapToInt(Flex::preferredWidth).max().orElse(0);
        columns.forEach(column -> column.width(widest).grow(1));
        return columns;
    }

    /** A section: its heading, then its rows. */
    private Flex<Paint> sectionColumn(Section section) {
        return Flex.<Paint>column().align(Align.STRETCH)
                .add(text(underlined(section.title()), CharacterSheetView.TITLE, HEADING_ROW))
                .add(rowsColumn(section.rows()));
    }

    /** Stat rows one under another. */
    private Flex<Paint> rowsColumn(List<Row> rows) {
        Flex<Paint> column = Flex.<Paint>column().align(Align.STRETCH);
        for (Row row : rows) {
            column.add(Flex.<Paint>row().payload(new Hover(row.label(), row.tooltip()))
                    .add(text(row.label(), row.color(), ROW), Flex.<Paint>leaf(VALUE_GAP, 0, null).grow(1),
                            text(Component.literal(row.value()), row.color(), ROW)));
        }
        return column;
    }

    private Flex<Paint> text(Component text, int color, int rowHeight) {
        return Flex.leaf(font.width(text), rowHeight, new Text(text, color, 1));
    }

    private static Component underlined(Component text) {
        return text.copy().withStyle(ChatFormatting.UNDERLINE);
    }

    private void draw(GuiGraphicsExtractor graphics, Rect rect, Paint paint, boolean hovered) {
        switch (paint) {
            case Fill fill -> {
                graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), fill.color());
                if (fill.border() != 0) {
                    graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), fill.border());
                }
            }
            case Text text -> drawText(graphics, rect, text.text(), text.color(), text.scale());
            case Tab tab -> drawText(graphics, rect, tab.name(), tab.index() == selectedTab() ? HEADING
                    : hovered ? TAB_HOVER : TAB_IDLE, 1);
            case Hover hover -> {
                if (hovered) {
                    graphics.fill(rect.x() - 2, rect.y(), rect.right() + 2, rect.bottom(), ROW_HOVER);
                }
            }
            case Model model -> graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), INNER_PANEL);
            case XpBar bar -> XpBarHud.drawBar(graphics, rect.x(), rect.y(), rect.width(), rect.height(), bar.xp());
            case Mark mark -> {
                graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(),
                        mark.reached() ? CharacterSheetView.MILESTONE : MARK_EMPTY);
                if (!mark.reached()) {
                    graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), CharacterSheetView.AHEAD);
                }
            }
            case SpendButton spend -> {
                graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), hovered ? SPEND_FILL_HOVER : SPEND_FILL);
                graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), CharacterSheetView.CHOICE);
                graphics.centeredText(font, "+", rect.x() + SPEND_SIZE / 2 + 1, rect.y() + 1,
                        hovered ? HEADING : CharacterSheetView.CHOICE);
            }
        }
    }

    /** Text vertically centred in its rectangle, at {@code scale} of the font's size from its top left corner. */
    private void drawText(GuiGraphicsExtractor graphics, Rect rect, Component text, int color, float scale) {
        int top = rect.y() + Math.round((rect.height() - (font.lineHeight - 1) * scale) / 2);
        if (scale == 1) {
            graphics.text(font, text, rect.x(), top, color);
            return;
        }
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(rect.x(), top);
        pose.scale(scale);
        graphics.text(font, text, 0, 0, color);
        pose.popMatrix();
    }

    /** A stat's, the race's or the class's tooltip: the name, then its lines wrapped, each in its colour. */
    private void tooltip(GuiGraphicsExtractor graphics, Hover hover, int mouseX, int mouseY) {
        if (hover.lines().isEmpty()) {
            return;
        }
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(hover.title().getVisualOrderText());
        hover.lines().forEach(line -> lines.addAll(font.split(line, TOOLTIP_WIDTH)));
        graphics.setTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    /**
     * "Spend a point on Strength", then what the point gives in the headings' yellow, each line ending under the
     * stat's name (Matěj, 2026-10-16).
     */
    private void spendTooltip(GuiGraphicsExtractor graphics, SpendButton spend, int mouseX, int mouseY) {
        Component heading = Component.translatable(GeneratedTexts.GUI_SPEND_STAT_POINT, spend.label());
        List<FormattedCharSequence> gains = spend.spend().gains().stream()
                .map(gain -> gain.copy().withColor(CharacterSheetView.TITLE & 0xFFFFFF).getVisualOrderText())
                .toList();
        Optional<TooltipComponent> lines = gains.isEmpty() ? Optional.empty()
                : Optional.of(new RightAlignedLines(gains, font.width(heading)));
        graphics.setTooltipForNextFrame(font, List.of(heading.getVisualOrderText()), lines,
                DefaultTooltipPositioner.INSTANCE, mouseX, mouseY, false, null);
    }

    private static int screenX(Layout layout, int panelX) {
        return Math.round(layout.x() + panelX * layout.scale());
    }

    private static int screenY(Layout layout, int panelY) {
        return Math.round(layout.y() + panelY * layout.scale());
    }
}
