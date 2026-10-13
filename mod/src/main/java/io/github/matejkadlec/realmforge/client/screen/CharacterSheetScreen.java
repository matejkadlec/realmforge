package io.github.matejkadlec.realmforge.client.screen;

import io.github.matejkadlec.realmforge.character.CharacterSheetView;
import io.github.matejkadlec.realmforge.character.CharacterSheetView.Row;
import io.github.matejkadlec.realmforge.character.CharacterSheetView.Section;
import io.github.matejkadlec.realmforge.character.CharacterSheetView.Tab;
import io.github.matejkadlec.realmforge.client.input.CharacterSheetKey;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

/**
 * The character sheet (RLM-136, architecture 18): draws {@link CharacterSheetView} over the game, which keeps running.
 * The player's name and level head it on the left, the tabs on the right; a click, the keys 1 to 3, Tab or the arrow
 * keys switch tabs, and the sheet reopens on the last one. Each tab has two columns. The values follow the player's
 * attributes every tick while the sheet is open. The panel is as tall as its tallest tab, so it keeps its size when
 * the tab changes, and is drawn smaller when it does not fit the screen.
 */
public final class CharacterSheetScreen extends Screen {
    private static final int COLUMN_WIDTH = 180;
    private static final int GAP = 24;
    private static final int PADDING = 10;
    private static final int LINE = 11;
    private static final int INDENT = 8;
    private static final int SECTION_GAP = 6;
    private static final int TAB_GAP = 12;
    private static final int HEADER = LINE + 2 + SECTION_GAP;
    private static final int CONTENT_WIDTH = 2 * COLUMN_WIDTH + GAP;
    private static final int PANEL = 0xC0101010;
    private static final int RULE = 0x60FFFFFF;
    private static final int TAB_IDLE = 0xFFA0A0A0;
    private static final int TAB_HOVER = 0xFFFFFFFF;

    /** The tab the sheet shows, kept while the game runs so the sheet reopens on it. */
    private static int selectedTab;

    @Nullable
    private CharacterSheetView view;

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
            int tabs = view.tabs().size();
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
            int tab = tabAt(layout(view), event.x(), event.y());
            if (tab >= 0) {
                selectedTab = tab;
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
        Tab tab = view.tabs().get(selectedTab());
        int hovered = tabAt(layout, mouseX, mouseY);

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(layout.x(), layout.y());
        pose.scale(layout.scale());
        graphics.fill(-PADDING, -PADDING, CONTENT_WIDTH + PADDING, layout.height() + PADDING, PANEL);
        graphics.text(font, view.title().copy().withStyle(ChatFormatting.BOLD), 0, 0, CharacterSheetView.TITLE);
        for (int i = 0; i < view.tabs().size(); i++) {
            Component name = view.tabs().get(i).name();
            boolean selected = i == selectedTab();
            int color = selected ? CharacterSheetView.TITLE : i == hovered ? TAB_HOVER : TAB_IDLE;
            graphics.text(font, selected ? name.copy().withStyle(ChatFormatting.UNDERLINE) : name,
                    layout.tabX()[i], 0, color);
        }
        graphics.fill(0, LINE + 1, CONTENT_WIDTH, LINE + 2, RULE);
        drawColumn(graphics, tab.left(), 0, HEADER);
        drawColumn(graphics, tab.right(), COLUMN_WIDTH + GAP, HEADER);
        pose.popMatrix();
    }

    private void refresh() {
        if (minecraft.player != null) {
            view = CharacterSheetView.of(minecraft.player);
        }
    }

    private int selectedTab() {
        return view == null ? 0 : Math.floorMod(selectedTab, view.tabs().size());
    }

    /**
     * Where the panel goes: its top left corner on the screen, its scale, the content's height (the tallest tab's) and
     * each tab label's left edge in panel units, right-aligned.
     */
    private record Layout(float x, float y, float scale, int height, int[] tabX, int[] tabWidth) {
    }

    private Layout layout(CharacterSheetView sheet) {
        int content = 0;
        for (Tab tab : sheet.tabs()) {
            content = Math.max(content, Math.max(height(tab.left()), height(tab.right())));
        }
        int height = HEADER + content;
        float scale = Math.min(1, Math.min((width - 2f * PADDING) / (CONTENT_WIDTH + 2f * PADDING),
                (this.height - 2f * PADDING) / (height + 2f * PADDING)));
        int count = sheet.tabs().size();
        int[] tabX = new int[count];
        int[] tabWidth = new int[count];
        int right = CONTENT_WIDTH;
        for (int i = count - 1; i >= 0; i--) {
            tabWidth[i] = font.width(sheet.tabs().get(i).name());
            tabX[i] = right - tabWidth[i];
            right = tabX[i] - TAB_GAP;
        }
        return new Layout((width - CONTENT_WIDTH * scale) / 2, (this.height - height * scale) / 2, scale, height, tabX,
                tabWidth);
    }

    /** The tab whose label is under the screen position, or -1. */
    private int tabAt(Layout layout, double screenX, double screenY) {
        double x = (screenX - layout.x()) / layout.scale();
        double y = (screenY - layout.y()) / layout.scale();
        if (y < -2 || y > LINE) {
            return -1;
        }
        for (int i = 0; i < layout.tabX().length; i++) {
            if (x >= layout.tabX()[i] - TAB_GAP / 2.0 && x < layout.tabX()[i] + layout.tabWidth()[i] + TAB_GAP / 2.0) {
                return i;
            }
        }
        return -1;
    }

    private void drawColumn(GuiGraphicsExtractor graphics, List<Section> sections, int x, int y) {
        for (Section section : sections) {
            graphics.text(font, section.title().copy().withStyle(ChatFormatting.UNDERLINE), x, y,
                    CharacterSheetView.TITLE);
            y += LINE + 2;
            for (Row row : section.rows()) {
                int indent = row.indented() ? INDENT : 0;
                List<FormattedCharSequence> lines = font.split(row.label(), labelWidth(row));
                for (FormattedCharSequence line : lines) {
                    graphics.text(font, line, x + indent, y, row.color());
                    y += LINE;
                }
                if (!row.value().isEmpty()) {
                    graphics.text(font, row.value(), x + COLUMN_WIDTH - font.width(row.value()), y - LINE * lines.size(),
                            row.color());
                }
            }
            y += SECTION_GAP;
        }
    }

    private int height(List<Section> sections) {
        int height = 0;
        for (Section section : sections) {
            height += LINE + 2 + SECTION_GAP;
            for (Row row : section.rows()) {
                height += LINE * font.split(row.label(), labelWidth(row)).size();
            }
        }
        return height;
    }

    /** The width left for a row's label: the column, less its indent and its value. */
    private int labelWidth(Row row) {
        int value = row.value().isEmpty() ? 0 : font.width(row.value()) + 6;
        return COLUMN_WIDTH - (row.indented() ? INDENT : 0) - value;
    }
}
