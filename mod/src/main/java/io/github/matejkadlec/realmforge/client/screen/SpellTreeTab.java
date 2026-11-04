package io.github.matejkadlec.realmforge.client.screen;

import io.github.matejkadlec.realmforge.character.SpellTreeView;
import io.github.matejkadlec.realmforge.character.SpellTreeView.Cell;
import io.github.matejkadlec.realmforge.character.SpellTreeView.State;
import io.github.matejkadlec.realmforge.character.SpellTreeView.Tree;
import io.github.matejkadlec.realmforge.character.net.PickSpecializationPayload;
import io.github.matejkadlec.realmforge.character.net.SpendSpellPointPayload;
import io.github.matejkadlec.realmforge.client.hud.SpellSlots;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import io.github.matejkadlec.realmforge.spell.Passives;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellBarView;
import io.github.matejkadlec.realmforge.spell.SpellText;
import io.github.matejkadlec.realmforge.spell.SpellTooltip;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The spellbook's Spell Tree tab (C5, RLM-333; Matěj's notes of 2026-10-21): one big card with a rectangle per
 * specialization, each a tree from top to bottom, as in WoW's talent window but with spells only. Before a pick each
 * playable tree has a Choose button; afterwards a click on a spell spends a point on its next rank. The window only
 * asks: {@link PickSpecializationPayload} and {@link SpendSpellPointPayload} go to the server, which checks them
 * again, and the tab redraws from the synced state ({@link SpellTreeView}). Drawn over the plain panel until the book
 * texture exists (RLM-333's follow-up).
 */
final class SpellTreeTab {
    /** The panel's width on this tab, wider than the Spell Bar tab's for its three rectangles. */
    static final int WIDTH = 352;
    private static final int PADDING = 8;
    private static final int CARD_GAP = 6;
    /** The rectangles' top, below the title and the points line. */
    private static final int CARDS_TOP = PADDING + 14 + 12;
    private static final int HEADER = 14;
    private static final int ROW_LABEL = 36;
    private static final int CELL = 20;
    private static final int CELL_GAP = 4;
    /** The rank's height under a cell. */
    private static final int RANK = 11;
    /** A row: its cell, its rank beneath, and the gap an arrow to the next row runs in. */
    private static final int ROW = CELL + RANK + 7;
    private static final int BUTTON_HEIGHT = 16;
    private static final int TOOLTIP_WIDTH = 200;

    private static final int CARD = 0x60000000;
    private static final int CARD_FRAME = 0x60FFFFFF;
    private static final int CHOSEN_FRAME = 0xFFFFC94A;
    private static final int TITLE = 0xFFFFC94A;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int HINT = 0xFFA0A0A0;
    private static final int LEARNABLE = 0xFF55FF55;
    private static final int ARROW = 0xFFC8C8C8;
    private static final int ARROW_LOCKED = 0xFF606060;
    private static final int BUTTON = 0xFF3A3A3A;
    private static final int BUTTON_HOVER = 0xFF5A5A5A;
    private static final int HOVER = 0x30FFFFFF;
    /** Over a spell the player does not know yet, which keeps its school's colour, only darker. */
    private static final int UNKNOWN = 0xA0000000;

    private final Minecraft minecraft;
    private final Font font;

    SpellTreeTab(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.font = minecraft.font;
    }

    void extract(GuiGraphicsExtractor graphics, LocalPlayer player, int left, int top, int bottom, int mouseX,
            int mouseY) {
        SpellTreeView view = SpellTreeView.of(player);
        Component heading = view.picked()
                ? Component.translatable(GeneratedTexts.GUI_SPELL_POINTS, view.unspent())
                : Component.translatable(GeneratedTexts.GUI_SPELL_TREE_PICK);
        graphics.text(font, heading, left + PADDING, top + PADDING + 14, TITLE);
        for (int index = 0; index < view.trees().size(); index++) {
            drawTree(graphics, view, view.trees().get(index), index, left, top, bottom, mouseX, mouseY);
        }
        tooltip(graphics, player, view, left, top, bottom, mouseX, mouseY);
    }

    /** A left click: a Choose button picks its specialization, a learnable spell takes a point. Whether it was ours. */
    boolean click(LocalPlayer player, int left, int top, int bottom, double mouseX, double mouseY) {
        SpellTreeView view = SpellTreeView.of(player);
        for (int index = 0; index < view.trees().size(); index++) {
            Tree tree = view.trees().get(index);
            if (showsButton(view, tree) && onButton(index, view, left, bottom, mouseX, mouseY)) {
                send(new PickSpecializationPayload(tree.specialization().id()), PickSpecializationPayload.TYPE);
                return true;
            }
        }
        Optional<Cell> cell = cellAt(view, left, top, mouseX, mouseY);
        if (cell.isPresent() && cell.get().state() == State.LEARNABLE) {
            cell.get().spell().unwrapKey().ifPresent(spell -> send(new SpendSpellPointPayload(spell),
                    SpendSpellPointPayload.TYPE));
        }
        return cell.isPresent();
    }

    private void drawTree(GuiGraphicsExtractor graphics, SpellTreeView view, Tree tree, int index, int left, int top,
            int bottom, int mouseX, int mouseY) {
        int x = cardX(index, view, left);
        int y = top + CARDS_TOP;
        int width = cardWidth(view);
        graphics.fill(x, y, x + width, bottom - PADDING, CARD);
        graphics.outline(x, y, width, bottom - PADDING - y, tree.chosen() ? CHOSEN_FRAME : CARD_FRAME);
        graphics.centeredText(font, Component.translatable(tree.specialization().name()), x + width / 2, y + 4,
                tree.chosen() || !view.picked() && tree.specialization().playable() ? TITLE : HINT);
        if (!tree.specialization().playable()) {
            graphics.centeredText(font, Component.translatable(GeneratedTexts.GUI_SPELL_TREE_LATER), x + width / 2,
                    y + HEADER + 8, HINT);
            return;
        }
        for (int row = 0; row < tree.rowLevels().size(); row++) {
            graphics.text(font, Component.translatable(GeneratedTexts.GUI_SPELL_TREE_ROW_LEVEL,
                    tree.rowLevels().get(row)), x + 3, rowY(row, y) + 7, HINT);
        }
        tree.cells().forEach(cell -> drawArrow(graphics, tree, cell, x, y));
        tree.cells().forEach(cell -> drawCell(graphics, cell, x, y, mouseX, mouseY));
        if (showsButton(view, tree)) {
            boolean hovered = onButton(index, view, left, bottom, mouseX, mouseY);
            int buttonY = bottom - PADDING - BUTTON_HEIGHT - 3;
            graphics.fill(x + 6, buttonY, x + width - 6, buttonY + BUTTON_HEIGHT, hovered ? BUTTON_HOVER : BUTTON);
            graphics.outline(x + 6, buttonY, width - 12, BUTTON_HEIGHT, CHOSEN_FRAME);
            graphics.centeredText(font, Component.translatable(GeneratedTexts.GUI_SPELL_TREE_CHOOSE), x + width / 2,
                    buttonY + 4, TEXT);
        }
    }

    private void drawCell(GuiGraphicsExtractor graphics, Cell cell, int cardX, int cardY, int mouseX, int mouseY) {
        int x = cellX(cell, cardX);
        int y = rowY(cell.row(), cardY);
        SpellSlots.draw(graphics, font, x, y, CELL, new SpellBarView.Slot(cell.spell().unwrapKey(),
                Optional.of(cell.spell().value()), Math.max(cell.rank(), 1), 0, 0, true, false), Optional.empty());
        if (cell.rank() == 0) {
            graphics.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, UNKNOWN);
        }
        if (cell.state() == State.LEARNABLE) {
            graphics.outline(x - 1, y - 1, CELL + 2, CELL + 2, LEARNABLE);
        }
        if (mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL) {
            graphics.fill(x, y, x + CELL, y + CELL, HOVER);
        }
        int color = switch (cell.state()) {
            case LEARNABLE -> LEARNABLE;
            case MAX_RANK -> TITLE;
            default -> cell.rank() > 0 ? TEXT : HINT;
        };
        graphics.centeredText(font, cell.rank() + "/" + cell.spell().value().maxLevel(), x + CELL / 2, y + CELL + 2,
                color);
    }

    /**
     * The arrow from the spell it needs: from under that spell's rank, across if the columns differ, down to this
     * spell, with its head on top of it. Lit once the player knows the spell it comes from.
     */
    private void drawArrow(GuiGraphicsExtractor graphics, Tree tree, Cell cell, int cardX, int cardY) {
        cell.requires().flatMap(required -> tree.cells().stream()
                .filter(other -> other.spell().is(required.unwrapKey().orElseThrow())).findFirst()).ifPresent(from -> {
                    int color = from.rank() > 0 ? ARROW : ARROW_LOCKED;
                    int fromX = cellX(from, cardX) + CELL / 2;
                    int toX = cellX(cell, cardX) + CELL / 2;
                    int startY = rowY(from.row(), cardY) + CELL + RANK;
                    int endY = rowY(cell.row(), cardY);
                    graphics.fill(fromX, startY, fromX + 1, startY + 1, color);
                    graphics.fill(Math.min(fromX, toX), startY + 1, Math.max(fromX, toX) + 1, startY + 2, color);
                    graphics.fill(toX, startY + 1, toX + 1, endY, color);
                    graphics.fill(toX - 2, endY - 3, toX + 3, endY - 2, color);
                    graphics.fill(toX - 1, endY - 2, toX + 2, endY - 1, color);
                });
    }

    private void tooltip(GuiGraphicsExtractor graphics, LocalPlayer player, SpellTreeView view, int left, int top,
            int bottom, int mouseX, int mouseY) {
        Optional<Cell> hovered = cellAt(view, left, top, mouseX, mouseY);
        if (hovered.isPresent()) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            Cell cell = hovered.get();
            Spell spell = cell.spell().value();
            ResourceKey<Spell> key = cell.spell().unwrapKey().orElseThrow();
            for (Component line : SpellTooltip.lines(spell, Math.clamp(cell.rank(), 1, spell.maxLevel()),
                    SpellText.statsOf(player), SpellText.schoolsOf(player.registryAccess()),
                    Passives.damageBonus(player, key))) {
                lines.addAll(font.split(line, TOOLTIP_WIDTH));
            }
            lines.addAll(font.split(stateLine(cell), TOOLTIP_WIDTH));
            graphics.setTooltipForNextFrame(lines, mouseX, mouseY);
            return;
        }
        for (int index = 0; index < view.trees().size(); index++) {
            int x = cardX(index, view, left);
            int y = top + CARDS_TOP;
            if (mouseX >= x && mouseX < x + cardWidth(view) && mouseY >= y && mouseY < y + HEADER) {
                graphics.setTooltipForNextFrame(font.split(Component.translatable(
                        view.trees().get(index).specialization().description()), TOOLTIP_WIDTH), mouseX, mouseY);
                return;
            }
        }
    }

    /** The tooltip's last line: what a click does, or why it does nothing. */
    private static Component stateLine(Cell cell) {
        return switch (cell.state()) {
            case LEARNABLE -> Component.translatable(GeneratedTexts.GUI_SPELL_TREE_SPEND)
                    .withStyle(ChatFormatting.GREEN);
            case MAX_RANK -> Component.translatable(GeneratedTexts.GUI_SPELL_TREE_MAX_RANK)
                    .withStyle(ChatFormatting.GOLD);
            case NEEDS_LEVEL -> Component.translatable(GeneratedTexts.GUI_SPELL_TREE_NEEDS_LEVEL, cell.level())
                    .withStyle(ChatFormatting.RED);
            case NEEDS_SPELL -> Component.translatable(GeneratedTexts.GUI_SPELL_TREE_NEEDS_SPELL, cell.requires()
                    .map(Holder::value).map(spell -> Component.translatable(spell.name()))
                    .orElse(Component.empty())).withStyle(ChatFormatting.RED);
            case NO_POINTS -> Component.translatable(GeneratedTexts.GUI_SPELL_TREE_NO_POINTS)
                    .withStyle(ChatFormatting.GRAY);
            case NEEDS_PICK -> Component.translatable(GeneratedTexts.GUI_SPELL_TREE_NEEDS_PICK)
                    .withStyle(ChatFormatting.GRAY);
            case FIRST_FREE -> Component.translatable(GeneratedTexts.GUI_SPELL_TREE_FIRST_FREE)
                    .withStyle(ChatFormatting.GREEN);
            case OTHER_TREE -> Component.translatable(GeneratedTexts.GUI_SPELL_TREE_OTHER_TREE)
                    .withStyle(ChatFormatting.GRAY);
        };
    }

    private Optional<Cell> cellAt(SpellTreeView view, int left, int top, double mouseX, double mouseY) {
        for (int index = 0; index < view.trees().size(); index++) {
            int cardX = cardX(index, view, left);
            int cardY = top + CARDS_TOP;
            for (Cell cell : view.trees().get(index).cells()) {
                int x = cellX(cell, cardX);
                int y = rowY(cell.row(), cardY);
                if (mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL) {
                    return Optional.of(cell);
                }
            }
        }
        return Optional.empty();
    }

    private static boolean showsButton(SpellTreeView view, Tree tree) {
        return !view.picked() && tree.specialization().playable();
    }

    private static boolean onButton(int index, SpellTreeView view, int left, int bottom, double mouseX, double mouseY) {
        int x = cardX(index, view, left);
        int buttonY = bottom - PADDING - BUTTON_HEIGHT - 3;
        return mouseX >= x + 6 && mouseX < x + cardWidth(view) - 6 && mouseY >= buttonY
                && mouseY < buttonY + BUTTON_HEIGHT;
    }

    private static int cardWidth(SpellTreeView view) {
        int cards = Math.max(1, view.trees().size());
        return (WIDTH - 2 * PADDING - (cards - 1) * CARD_GAP) / cards;
    }

    private static int cardX(int index, SpellTreeView view, int left) {
        return left + PADDING + index * (cardWidth(view) + CARD_GAP);
    }

    private static int cellX(Cell cell, int cardX) {
        return cardX + ROW_LABEL + cell.column() * (CELL + CELL_GAP);
    }

    private static int rowY(int row, int cardY) {
        return cardY + HEADER + row * ROW;
    }

    private <T extends CustomPacketPayload> void send(T payload, CustomPacketPayload.Type<T> type) {
        if (minecraft.getConnection() != null && minecraft.getConnection().hasChannel(type)) {
            ClientPacketDistributor.sendToServer(payload);
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }
}
