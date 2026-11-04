package io.github.matejkadlec.realmforge.client.screen;

import io.github.matejkadlec.realmforge.character.SpellTreeView;
import io.github.matejkadlec.realmforge.character.SpellTreeView.Cell;
import io.github.matejkadlec.realmforge.character.SpellTreeView.State;
import io.github.matejkadlec.realmforge.character.SpellTreeView.Tree;
import io.github.matejkadlec.realmforge.character.net.PickSpecializationPayload;
import io.github.matejkadlec.realmforge.character.net.SpendSpellPointPayload;
import io.github.matejkadlec.realmforge.client.hud.SpellBarLayout;
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
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The spellbook's Spell Tree tab (C5, RLM-333; Matěj's notes of 2026-10-21): one big card with a rectangle per
 * specialization, each a tree from top to bottom, as in WoW's talent window but with spells only. Before a pick each
 * playable tree has a Choose button; afterwards a click on a spell spends a point on its next rank. The window only
 * asks: {@link PickSpecializationPayload} and {@link SpendSpellPointPayload} go to the server, which checks them
 * again, and the tab redraws from the synced state ({@link SpellTreeView}). A spell's square is as big as the bar's
 * and holds its rank in the bottom right corner, and its shade says what it is ({@link SpellShade}; Matěj's playtest of
 * 2026-11-04). Drawn over the plain panel until the book texture exists (RLM-609).
 */
final class SpellTreeTab {
    /** The spellbook's width on both tabs, which its three rectangles here need. */
    static final int WIDTH = 352;
    private static final int PADDING = 8;
    private static final int CARD_GAP = 6;
    /** The rectangles' top, below the title and the line that asks for a specialization. */
    private static final int CARDS_TOP = PADDING + 14 + 12;
    private static final int HEADER = 14;
    private static final int CELL = SpellBarLayout.SLOT;
    /** A row holds this many spells, with the same gap beside each and at the rectangle's edges. */
    private static final int COLUMNS = 3;
    /** A row: its cell, and the gap an arrow to the next row runs in. */
    private static final int ROW = CELL + 7;
    /** The rank inside a cell, at about half the cell's width, as a slot's key label is scaled. */
    private static final float RANK_SCALE = 2 / 3F;
    private static final int RANK_GLYPH_HEIGHT = 8;
    /** A cell's initials, up and left of its middle, clear of the rank. */
    private static final int INITIALS_X = -3;
    private static final int INITIALS_Y = -4;
    private static final int BUTTON_HEIGHT = 16;
    private static final int TOOLTIP_WIDTH = 200;

    private static final int CARD = 0x60000000;
    private static final int CARD_FRAME = 0x60FFFFFF;
    private static final int CHOSEN_FRAME = 0xFFFFC94A;
    private static final int TITLE = 0xFFFFC94A;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int HINT = 0xFFA0A0A0;
    private static final int ARROW = 0xFFC8C8C8;
    private static final int ARROW_LOCKED = 0xFF606060;
    private static final int BUTTON = 0xFF3A3A3A;
    private static final int BUTTON_HOVER = 0xFF5A5A5A;
    private static final int HOVER = 0x30FFFFFF;

    private final Minecraft minecraft;
    private final Font font;

    SpellTreeTab(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.font = minecraft.font;
    }

    void extract(GuiGraphicsExtractor graphics, LocalPlayer player, int left, int top, int bottom, int mouseX,
            int mouseY) {
        SpellTreeView view = SpellTreeView.of(player);
        if (!view.picked()) {
            graphics.text(font, Component.translatable(GeneratedTexts.GUI_SPELL_TREE_PICK), left + PADDING,
                    top + PADDING + 14, TITLE);
        }
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
        tree.cells().forEach(cell -> drawArrow(graphics, view, tree, cell, x, y));
        tree.cells().forEach(cell -> drawCell(graphics, view, cell, x, y, mouseX, mouseY));
        if (showsButton(view, tree)) {
            boolean hovered = onButton(index, view, left, bottom, mouseX, mouseY);
            int buttonY = bottom - PADDING - BUTTON_HEIGHT - 3;
            graphics.fill(x + 6, buttonY, x + width - 6, buttonY + BUTTON_HEIGHT, hovered ? BUTTON_HOVER : BUTTON);
            graphics.outline(x + 6, buttonY, width - 12, BUTTON_HEIGHT, CHOSEN_FRAME);
            graphics.centeredText(font, Component.translatable(GeneratedTexts.GUI_SPELL_TREE_CHOOSE), x + width / 2,
                    buttonY + 4, TEXT);
        }
    }

    private void drawCell(GuiGraphicsExtractor graphics, SpellTreeView view, Cell cell, int cardX, int cardY,
            int mouseX, int mouseY) {
        int x = cellX(view, cell, cardX);
        int y = rowY(cell.row(), cardY);
        SpellSlots.draw(graphics, font, x, y, CELL, new SpellBarView.Slot(cell.spell().unwrapKey(),
                Optional.of(cell.spell().value()), Math.max(cell.rank(), 1), 0, 0, true, false), Optional.empty(),
                INITIALS_X, INITIALS_Y);
        int shade = SpellShade.of(cell.rank(), cell.state()).overlayAlpha(Util.getMillis());
        if (shade > 0) {
            graphics.fill(x, y, x + CELL, y + CELL, ARGB.color(shade, 0));
        }
        if (mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL) {
            graphics.fill(x, y, x + CELL, y + CELL, HOVER);
        }
        drawRank(graphics, cell, x, y);
    }

    /** The rank in the cell's bottom right corner, over its shade: gold at the highest rank, white otherwise. */
    private void drawRank(GuiGraphicsExtractor graphics, Cell cell, int x, int y) {
        String rank = cell.rank() + "/" + cell.spell().value().maxLevel();
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + CELL - 1 - font.width(rank) * RANK_SCALE,
                y + CELL - 1 - RANK_GLYPH_HEIGHT * RANK_SCALE);
        graphics.pose().scale(RANK_SCALE, RANK_SCALE);
        graphics.text(font, rank, 0, 0, cell.state() == State.MAX_RANK ? TITLE : TEXT);
        graphics.pose().popMatrix();
    }

    /**
     * The arrow from the spell it needs: from under that spell, across if the columns differ, down to this spell, with
     * its head on top of it. Lit once the player knows the spell it comes from.
     */
    private void drawArrow(GuiGraphicsExtractor graphics, SpellTreeView view, Tree tree, Cell cell, int cardX,
            int cardY) {
        cell.requires().flatMap(required -> tree.cells().stream()
                .filter(other -> other.spell().is(required.unwrapKey().orElseThrow())).findFirst()).ifPresent(from -> {
                    int color = from.rank() > 0 ? ARROW : ARROW_LOCKED;
                    int fromX = cellX(view, from, cardX) + CELL / 2;
                    int toX = cellX(view, cell, cardX) + CELL / 2;
                    int startY = rowY(from.row(), cardY) + CELL;
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
                int x = cellX(view, cell, cardX);
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

    /** A cell's left edge: the same gap at the rectangle's edges as between the cells. */
    private static int cellX(SpellTreeView view, Cell cell, int cardX) {
        int gap = (cardWidth(view) - COLUMNS * CELL) / (COLUMNS + 1);
        return cardX + gap + cell.column() * (CELL + gap);
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
