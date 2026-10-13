package io.github.matejkadlec.realmforge.client.screen;

import io.github.matejkadlec.realmforge.character.CharacterSheetView;
import io.github.matejkadlec.realmforge.character.CharacterSheetView.Row;
import io.github.matejkadlec.realmforge.character.CharacterSheetView.Section;
import io.github.matejkadlec.realmforge.client.input.CharacterSheetKey;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fStack;

/**
 * The basic character sheet (RLM-136, architecture 18): draws {@link CharacterSheetView} in two columns over the game,
 * which keeps running. The values follow the player's attributes every tick while the sheet is open. When the sheet is
 * taller or wider than the screen, it is drawn smaller.
 */
public final class CharacterSheetScreen extends Screen {
    private static final int COLUMN_WIDTH = 180;
    private static final int GAP = 24;
    private static final int PADDING = 10;
    private static final int LINE = 11;
    private static final int INDENT = 8;
    private static final int SECTION_GAP = 6;
    private static final int PANEL = 0xC0101010;

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
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        if (view == null) {
            return;
        }
        int contentWidth = 2 * COLUMN_WIDTH + GAP;
        int titleHeight = LINE + SECTION_GAP;
        int contentHeight = titleHeight + Math.max(height(view.left()), height(view.right()));
        float scale = Math.min(1, Math.min((width - 2f * PADDING) / (contentWidth + 2f * PADDING),
                (height - 2f * PADDING) / (contentHeight + 2f * PADDING)));

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate((width - contentWidth * scale) / 2, (height - contentHeight * scale) / 2);
        pose.scale(scale);
        graphics.fill(-PADDING, -PADDING, contentWidth + PADDING, contentHeight + PADDING, PANEL);
        graphics.centeredText(font, title.copy().withStyle(ChatFormatting.BOLD), contentWidth / 2, 0,
                CharacterSheetView.TITLE);
        drawColumn(graphics, view.left(), 0, titleHeight);
        drawColumn(graphics, view.right(), COLUMN_WIDTH + GAP, titleHeight);
        pose.popMatrix();
    }

    private void refresh() {
        if (minecraft.player != null) {
            view = CharacterSheetView.of(minecraft.player);
        }
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
