package io.github.matejkadlec.realmforge.client.screen;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.client.hud.SpellBarHud;
import io.github.matejkadlec.realmforge.client.hud.SpellBarLayout;
import io.github.matejkadlec.realmforge.client.hud.SpellSlots;
import io.github.matejkadlec.realmforge.client.input.SpellKeys;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import io.github.matejkadlec.realmforge.spell.Passives;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellAttachments;
import io.github.matejkadlec.realmforge.spell.SpellBarView;
import io.github.matejkadlec.realmforge.spell.SpellText;
import io.github.matejkadlec.realmforge.spell.SpellTooltip;
import io.github.matejkadlec.realmforge.spell.Spellbook;
import io.github.matejkadlec.realmforge.spell.Spells;
import io.github.matejkadlec.realmforge.spell.net.SetSpellBarSlotPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The spellbook (C3b, RLM-25), opened and closed by its key (P by default): the spell bar as on the HUD, the spells the
 * player knows, and each spell's tooltip with live numbers ({@link SpellTooltip}). A click on a known spell, then on a
 * bar slot, puts it there ({@code SetSpellBarSlotPayload}); a click on a slot's spell picks it up to move it, and
 * dropping it on another spell swaps them; a right click empties a slot. The racial slot is the race's (C4), so it
 * only shows. A passive spell (RLM-541) is listed with its rank and "Passive" and never goes onto the bar; its tooltip
 * shows its rank and the next one. The gear button top right, level with the title (RLM-497; Matěj, 2026-10-29: an
 * icon instead of the word), opens the Realmforge config screen ({@link RealmforgeConfigScreen}), "Options" in its
 * tooltip. The panel is drawn as the background, under the button, so the button shows at full brightness.
 */
public final class SpellbookScreen extends Screen {
    private static final int PANEL_WIDTH = 236;
    private static final int PADDING = 8;
    private static final int ROW = 20;
    /** The known spells' slots, smaller than the bar's to fit a row. */
    private static final int LIST_SLOT = 18;
    private static final int TOOLTIP_WIDTH = 200;
    private static final int PANEL = 0xC0101010;
    private static final int HEADING = 0xFFFFFFFF;
    private static final int TITLE = 0xFFFFC94A;
    private static final int HINT = 0xFFA0A0A0;
    private static final int SELECTED = 0xFFFFD24A;
    private static final int HOVER = 0x30FFFFFF;
    /** The gear button and its sprite ({@code realmforge:icon/gear}, drawn in {@code generate/gui_icons.py}). */
    private static final int GEAR_BUTTON = 16;
    private static final int GEAR_SPRITE = 12;

    /** The spell picked up to put on the bar, if any. */
    @Nullable
    private ResourceKey<Spell> selected;
    private int scroll;

    public SpellbookScreen() {
        super(Component.translatable(GeneratedTexts.GUI_SPELLBOOK));
    }

    @Override
    protected void init() {
        SpriteIconButton gear = SpriteIconButton.builder(Component.translatable(GeneratedTexts.GUI_SPELL_OPTIONS),
                        button -> minecraft.setScreen(new RealmforgeConfigScreen(this)), true)
                .size(GEAR_BUTTON, GEAR_BUTTON)
                .sprite(Realmforge.id("icon/gear"), GEAR_SPRITE, GEAR_SPRITE)
                .withTootip()
                .build();
        // Level with the title: its middle on the title's line of text.
        gear.setPosition(left() + PANEL_WIDTH - PADDING - GEAR_BUTTON,
                top() + PADDING + font.lineHeight / 2 - GEAR_BUTTON / 2);
        addRenderableWidget(gear);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (SpellKeys.SPELLBOOK.get().matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        scroll = Math.clamp(scroll - (int) Math.signum(scrollY), 0, Math.max(0, known().size() - visibleRows()));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return super.mouseClicked(event, doubleClick);
        }
        int slot = slotAt(event.x(), event.y());
        if (slot >= 0 && slot < Spellbook.BAR_SLOTS) {
            Optional<ResourceKey<Spell>> there = player.getData(SpellAttachments.SPELLBOOK).slot(slot);
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                send(slot, Optional.empty());
                selected = null;
            } else if (selected != null) {
                send(slot, Optional.of(selected));
                selected = null;
            } else {
                selected = there.orElse(null);
            }
            return true;
        }
        int row = rowAt(event.x(), event.y());
        if (row >= 0 && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            ResourceKey<Spell> spell = known().get(row).getKey();
            // A passive spell (RLM-541) is never cast, so it never goes onto the bar.
            if (!passive(player, spell)) {
                selected = spell.equals(selected) ? null : spell;
                click();
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** The panel, under the widgets: drawn over them, it greyed the old Options button out (RLM-497). */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        graphics.fill(left(), top(), left() + PANEL_WIDTH, bottom(), PANEL);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        int x = left();
        int y = top();
        graphics.text(font, title.copy().withStyle(ChatFormatting.BOLD), x + PADDING, y + PADDING, HEADING);

        SpellBarView bar = SpellBarHud.view(player);
        graphics.text(font, Component.translatable(GeneratedTexts.GUI_SPELL_BAR), x + PADDING, barTitleY(), TITLE);
        for (int slot = 0; slot < Spellbook.SLOT_COUNT; slot++) {
            SpellBarView.Slot view = bar.slots().get(slot);
            int slotX = slotX(slot);
            SpellSlots.draw(graphics, font, slotX, barY(), SpellBarLayout.SLOT, view, SpellBarHud.keyLabel(slot));
            if (selected != null && view.key().filter(selected::equals).isPresent()) {
                graphics.outline(slotX - 1, barY() - 1, SpellBarLayout.SLOT + 2, SpellBarLayout.SLOT + 2, SELECTED);
            }
        }
        List<FormattedCharSequence> hint = font.split(Component.translatable(GeneratedTexts.GUI_SPELLBOOK_HINT),
                PANEL_WIDTH - 2 * PADDING);
        for (int line = 0; line < hint.size(); line++) {
            graphics.text(font, hint.get(line), x + PADDING, hintY() + line * 10, HINT);
        }

        graphics.text(font, Component.translatable(GeneratedTexts.GUI_KNOWN_SPELLS), x + PADDING, listTitleY(), TITLE);
        List<Map.Entry<ResourceKey<Spell>, Integer>> known = known();
        if (known.isEmpty()) {
            graphics.text(font, Component.translatable(GeneratedTexts.GUI_NO_KNOWN_SPELLS), x + PADDING,
                    listY() + 4, HINT);
        }
        int hoveredRow = rowAt(mouseX, mouseY);
        for (int row = scroll; row < Math.min(known.size(), scroll + visibleRows()); row++) {
            Map.Entry<ResourceKey<Spell>, Integer> entry = known.get(row);
            int rowY = listY() + (row - scroll) * ROW;
            Optional<Spell> spell = Spells.get(player.registryAccess(), entry.getKey()).map(Holder::value);
            if (row == hoveredRow) {
                graphics.fill(x + PADDING - 2, rowY - 1, x + PANEL_WIDTH - PADDING, rowY + ROW - 1, HOVER);
            }
            SpellSlots.draw(graphics, font, x + PADDING, rowY, LIST_SLOT, new SpellBarView.Slot(
                    Optional.of(entry.getKey()), spell, entry.getValue(), 0, 0, true, false), Optional.empty());
            if (entry.getKey().equals(selected)) {
                graphics.outline(x + PADDING - 1, rowY - 1, LIST_SLOT + 2, LIST_SLOT + 2, SELECTED);
            }
            Component name = spell.map(value -> value.maxLevel() > 1
                    ? Component.translatable(GeneratedTexts.GUI_SPELL_WITH_RANK,
                    Component.translatable(value.name()), entry.getValue())
                    : Component.translatable(value.name())).orElse(Component.literal(entry.getKey().identifier()
                    .toString()));
            if (spell.map(Spell::isPassive).orElse(false)) {
                name = name.copy().append(Component.literal(" ")).append(Component.translatable(
                        GeneratedTexts.SPELL_TOOLTIP_PASSIVE).withStyle(ChatFormatting.GRAY));
            }
            graphics.text(font, name, x + PADDING + LIST_SLOT + 6, rowY + 5, HEADING);
        }

        tooltip(graphics, player, mouseX, mouseY, bar);
    }

    /** The tooltip of the spell or slot under the mouse. */
    private void tooltip(GuiGraphicsExtractor graphics, LocalPlayer player, int mouseX, int mouseY,
            SpellBarView bar) {
        int slot = slotAt(mouseX, mouseY);
        int row = rowAt(mouseX, mouseY);
        Optional<ResourceKey<Spell>> spell = Optional.empty();
        int level = 1;
        if (slot >= 0) {
            spell = bar.slots().get(slot).key();
            level = Math.max(1, bar.slots().get(slot).level());
            if (spell.isEmpty()) {
                graphics.setTooltipForNextFrame(Component.translatable(slot == Spellbook.RACIAL_SLOT
                        ? GeneratedTexts.SPELL_TOOLTIP_RACIAL_EMPTY : GeneratedTexts.SPELL_TOOLTIP_EMPTY_SLOT),
                        mouseX, mouseY);
                return;
            }
        } else if (row >= 0) {
            spell = Optional.of(known().get(row).getKey());
            level = known().get(row).getValue();
        }
        int spellLevel = level;
        spell.flatMap(key -> Spells.get(player.registryAccess(), key)).ifPresent(holder -> {
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (Component line : SpellTooltip.lines(holder.value(), Math.min(spellLevel, holder.value().maxLevel()),
                    SpellText.statsOf(player), SpellText.schoolsOf(player.registryAccess()),
                    Passives.damageBonus(player, holder.key()))) {
                lines.addAll(font.split(line, TOOLTIP_WIDTH));
            }
            graphics.setTooltipForNextFrame(lines, mouseX, mouseY);
        });
    }

    /** The known spells, by name, with their levels. */
    private List<Map.Entry<ResourceKey<Spell>, Integer>> known() {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return List.of();
        }
        Spellbook book = player.getData(SpellAttachments.SPELLBOOK);
        return book.spells().entrySet().stream()
                .filter(entry -> Spells.get(player.registryAccess(), entry.getKey())
                        .map(holder -> !holder.value().npcOnly()).orElse(false))
                .sorted(Comparator.comparing(entry -> name(entry.getKey())))
                .toList();
    }

    private static boolean passive(LocalPlayer player, ResourceKey<Spell> key) {
        return Spells.get(player.registryAccess(), key).map(holder -> holder.value().isPassive()).orElse(false);
    }

    private String name(ResourceKey<Spell> key) {
        LocalPlayer player = minecraft.player;
        return player == null ? key.identifier().toString() : Spells.get(player.registryAccess(), key)
                .map(holder -> Language.getInstance().getOrDefault(holder.value().name()))
                .orElse(key.identifier().toString());
    }

    private void send(int slot, Optional<ResourceKey<Spell>> spell) {
        if (minecraft.getConnection() != null && minecraft.getConnection().hasChannel(SetSpellBarSlotPayload.TYPE)) {
            ClientPacketDistributor.sendToServer(new SetSpellBarSlotPayload(slot, spell));
            click();
        }
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    /** The bar slot under the screen position, or -1. */
    private int slotAt(double mouseX, double mouseY) {
        if (mouseY < barY() || mouseY >= barY() + SpellBarLayout.SLOT) {
            return -1;
        }
        for (int slot = 0; slot < Spellbook.SLOT_COUNT; slot++) {
            if (mouseX >= slotX(slot) && mouseX < slotX(slot) + SpellBarLayout.SLOT) {
                return slot;
            }
        }
        return -1;
    }

    /** The known spell's index under the screen position, or -1. */
    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < left() + PADDING || mouseX >= left() + PANEL_WIDTH - PADDING || mouseY < listY()) {
            return -1;
        }
        int row = scroll + (int) ((mouseY - listY()) / ROW);
        return row < Math.min(known().size(), scroll + visibleRows()) ? row : -1;
    }

    private int left() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int top() {
        return Math.max(10, height / 2 - 120);
    }

    private int bottom() {
        return Math.min(height - 10, height / 2 + 120);
    }

    private int barTitleY() {
        return top() + PADDING + 14;
    }

    private int barY() {
        return barTitleY() + 12;
    }

    private int slotX(int slot) {
        // The HUD's layout on a screen as wide as the bar starts the bar at 0.
        return left() + (PANEL_WIDTH - SpellBarLayout.WIDTH) / 2 + SpellBarLayout.slotX(slot, SpellBarLayout.WIDTH);
    }

    private int hintY() {
        return barY() + SpellBarLayout.SLOT + 6;
    }

    private int listTitleY() {
        return hintY() + 2 * 10 + 6;
    }

    private int listY() {
        return listTitleY() + 12;
    }

    private int visibleRows() {
        return Math.max(1, (bottom() - 30 - listY()) / ROW);
    }
}
