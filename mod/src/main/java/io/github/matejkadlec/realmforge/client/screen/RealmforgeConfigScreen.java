package io.github.matejkadlec.realmforge.client.screen;

import com.google.common.collect.ImmutableList;
import io.github.matejkadlec.realmforge.client.ClientConfig;
import io.github.matejkadlec.realmforge.client.ClientConfig.HighlightMode;
import io.github.matejkadlec.realmforge.client.hud.SpellBarLayout;
import io.github.matejkadlec.realmforge.client.spell.CastQueue;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * The Realmforge config screen (RLM-495; Matěj, 2026-10-29), in the style of Better Combat's (Cloth Config's): the
 * title, a search bar that filters the rows by name, one flat list with a row per client option ({@link ClientConfig};
 * no sections until there are many options), each with its name on the left, its control on the right (Yes in green
 * and No in red, a slider reading "Value: N", a button) and Reset, which puts the option back to its default; then
 * Cancel and Save &amp; Quit. The rows edit drafts ({@link OptionDraft}): Save &amp; Quit writes them into the client
 * config and its file, Cancel (and Escape) drops them. A changed row's name is in italics until it is saved. The
 * spellbook's gear button and the mod list's Config button open it. Built on vanilla widgets, so it needs no Cloth
 * Config (architecture 18).
 *
 * <p>The rows, in order: Auto Shoot (RLM-496), Queue Spell Casts and Spells Queue Size, which only applies, and can
 * only be changed, while queueing is on (RLM-25); Spellbar Position, whose button opens the drag mode
 * ({@link SpellBarPositionScreen}), Spellbar Size, greyed out while Mirror Hotbar's Width is on, and Mirror Hotbar's
 * Width (RLM-404); Highlight Spell Target, Toggle or Hold (RLM-468).
 */
public final class RealmforgeConfigScreen extends Screen {
    private static final int HEADER = 48;
    private static final int FOOTER = 36;
    private static final int ROW_HEIGHT = 24;
    private static final int ROW_WIDTH = 360;
    private static final int CONTROL_WIDTH = 150;
    private static final int BUTTON_WIDTH = 150;
    private static final int GAP = 4;
    private static final int SEARCH_WIDTH = 200;
    private static final int NAME = 0xFFFFFFFF;
    private static final int NAME_INACTIVE = 0xFFA0A0A0;

    @Nullable
    private final Screen parent;
    private final OptionDraft<Boolean> autoShoot = OptionDraft.of(ClientConfig.AUTO_SHOOT);
    private final OptionDraft<Boolean> queue = OptionDraft.of(ClientConfig.QUEUE_SPELL_CASTS);
    private final OptionDraft<Integer> queueSize = OptionDraft.of(ClientConfig.SPELLS_QUEUE_SIZE);
    private final OptionDraft<Place> place = new OptionDraft<>(
            new Place(SpellBarLayout.DEFAULT_PLACE, SpellBarLayout.DEFAULT_PLACE),
            () -> new Place(ClientConfig.SPELL_BAR_X.get(), ClientConfig.SPELL_BAR_Y.get()),
            value -> {
                ClientConfig.SPELL_BAR_X.set(value.x());
                ClientConfig.SPELL_BAR_Y.set(value.y());
            });
    private final OptionDraft<Double> barSize = OptionDraft.of(ClientConfig.SPELL_BAR_SIZE);
    private final OptionDraft<Boolean> mirror = OptionDraft.of(ClientConfig.MIRROR_HOTBAR_WIDTH);
    private final OptionDraft<HighlightMode> highlight = OptionDraft.of(ClientConfig.HIGHLIGHT_SPELL_TARGET);
    private final List<OptionDraft<?>> drafts = List.of(autoShoot, queue, queueSize, place, barSize, mirror, highlight);
    /** Kept across {@link #init}, which runs again when the drag mode returns or the window resizes. */
    private String query = "";
    @Nullable
    private OptionList list;

    public RealmforgeConfigScreen(@Nullable Screen parent) {
        super(Component.translatable(GeneratedTexts.GUI_CONFIG_TITLE));
        this.parent = parent;
    }

    /** The spell bar's place in the drag mode's terms: fractions of the room the screen leaves it, -1 for default. */
    public record Place(double x, double y) {
    }

    @Override
    protected void init() {
        EditBox search = new EditBox(font, (width - SEARCH_WIDTH) / 2, 22, SEARCH_WIDTH, 18,
                Component.translatable(GeneratedTexts.GUI_CONFIG_SEARCH));
        search.setHint(Component.translatable(GeneratedTexts.GUI_CONFIG_SEARCH));
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            if (list != null) {
                list.filter();
            }
        });
        addRenderableWidget(search);
        list = addRenderableWidget(new OptionList(minecraft));
        list.filter();
        int y = height - FOOTER + (FOOTER - 20) / 2;
        int x = width / 2 - BUTTON_WIDTH - GAP / 2;
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(x, y, BUTTON_WIDTH, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(GeneratedTexts.GUI_SAVE_AND_QUIT), button -> save())
                .bounds(x + BUTTON_WIDTH + GAP, y, BUTTON_WIDTH, 20).build());
        setInitialFocus(search);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
    }

    private void save() {
        drafts.forEach(OptionDraft::save);
        ClientConfig.SPEC.save();
        minecraft.setScreen(parent);
    }

    /** Cancel and Escape: the drafts are dropped, the config keeps its values. */
    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    /** The spell bar's scale as the drafts set it, which the drag mode shows. */
    private float barScale() {
        return SpellBarLayout.scale(barSize.value(), mirror.value());
    }

    private static Component yesNo(boolean value) {
        return value ? CommonComponents.GUI_YES.copy().withStyle(ChatFormatting.GREEN)
                : CommonComponents.GUI_NO.copy().withStyle(ChatFormatting.RED);
    }

    private static Component valueText(Object value) {
        return Component.translatable(GeneratedTexts.GUI_OPTION_VALUE, value.toString());
    }

    /** The rows: all of them, of which the search shows those whose name matches. */
    private final class OptionList extends ContainerObjectSelectionList<Row> {
        private final List<Row> rows;

        OptionList(Minecraft minecraft) {
            super(minecraft, RealmforgeConfigScreen.this.width, RealmforgeConfigScreen.this.height - HEADER - FOOTER,
                    HEADER, ROW_HEIGHT);
            rows = List.of(
                    toggle(GeneratedTexts.GUI_AUTO_SHOOT, GeneratedTexts.GUI_AUTO_SHOOT_TOOLTIP, autoShoot),
                    toggle(GeneratedTexts.GUI_QUEUE_SPELL_CASTS, GeneratedTexts.GUI_QUEUE_SPELL_CASTS_TOOLTIP, queue),
                    new Row(GeneratedTexts.GUI_SPELLS_QUEUE_SIZE, GeneratedTexts.GUI_SPELLS_QUEUE_SIZE_TOOLTIP,
                            queueSize, new IntSlider(queueSize, 1, CastQueue.MAX_SIZE), queue::value),
                    new Row(GeneratedTexts.GUI_SPELL_BAR_POSITION, GeneratedTexts.GUI_SPELL_BAR_POSITION_TOOLTIP, place,
                            Button.builder(Component.translatable(GeneratedTexts.GUI_SPELL_BAR_POSITION_BUTTON),
                                    button -> minecraft.setScreen(new SpellBarPositionScreen(
                                            RealmforgeConfigScreen.this, place.value(), barScale(), place::set)))
                                    .build(),
                            () -> true),
                    new Row(GeneratedTexts.GUI_SPELL_BAR_SIZE, GeneratedTexts.GUI_SPELL_BAR_SIZE_TOOLTIP, barSize,
                            new SizeSlider(barSize), () -> !mirror.value()),
                    toggle(GeneratedTexts.GUI_MIRROR_HOTBAR_WIDTH, GeneratedTexts.GUI_MIRROR_HOTBAR_WIDTH_TOOLTIP,
                            mirror),
                    new Row(GeneratedTexts.GUI_HIGHLIGHT_SPELL_TARGET, GeneratedTexts.GUI_HIGHLIGHT_SPELL_TARGET_TOOLTIP,
                            highlight, cycle(highlight, HighlightMode.values(), mode -> Component.translatable(
                                    mode.text())), () -> true));
        }

        void filter() {
            replaceEntries(rows.stream()
                    .filter(row -> OptionDraft.matches(row.name.getString(), query))
                    .toList());
            setScrollAmount(0);
            refresh();
        }

        void refresh() {
            rows.forEach(Row::refresh);
        }

        @Override
        public int getRowWidth() {
            return Math.min(ROW_WIDTH, width - 24);
        }

        /** A Yes/No row, Yes in green and No in red, as Better Combat's. */
        private Row toggle(String name, String tooltip, OptionDraft<Boolean> draft) {
            return new Row(name, tooltip, draft, cycle(draft, new Boolean[] {true, false},
                    RealmforgeConfigScreen::yesNo), () -> true);
        }

        /** A button that steps through {@code values}, showing the draft's value. */
        private <T> DraftButton<T> cycle(OptionDraft<T> draft, T[] values, Function<T, Component> text) {
            List<T> all = List.of(values);
            return new DraftButton<>(draft, text, pressed -> {
                draft.set(all.get((all.indexOf(draft.value()) + 1) % all.size()));
                refresh();
            });
        }
    }

    /** A cycle button that re-reads its text from the draft on {@link Row#refresh}. */
    private static final class DraftButton<T> extends Button.Plain {
        private final OptionDraft<T> draft;
        private final Function<T, Component> text;

        DraftButton(OptionDraft<T> draft, Function<T, Component> text, OnPress onPress) {
            super(0, 0, CONTROL_WIDTH, 20, text.apply(draft.value()), onPress, DEFAULT_NARRATION);
            this.draft = draft;
            this.text = text;
        }

        void sync() {
            setMessage(text.apply(draft.value()));
        }
    }

    /** One option's row: its name, its control and Reset. */
    private final class Row extends ContainerObjectSelectionList.Entry<Row> {
        private final Component name;
        private final Component tooltip;
        private final OptionDraft<?> draft;
        private final AbstractWidget control;
        private final Button reset;
        private final BooleanSupplier enabled;

        Row(String name, String tooltip, OptionDraft<?> draft, AbstractWidget control, BooleanSupplier enabled) {
            this.name = Component.translatable(name);
            this.tooltip = Component.translatable(tooltip);
            this.draft = draft;
            this.control = control;
            this.enabled = enabled;
            control.setWidth(CONTROL_WIDTH);
            control.setTooltip(Tooltip.create(this.tooltip));
            Component resetText = Component.translatable("controls.reset");
            reset = Button.builder(resetText, button -> {
                draft.reset();
                // The screen's list: an entry's own field of that name is the vanilla list it sits in.
                if (RealmforgeConfigScreen.this.list != null) {
                    RealmforgeConfigScreen.this.list.refresh();
                }
            }).width(font.width(resetText) + 8).build();
        }

        /** After Reset or a click: the control shows the draft's value again. */
        void refresh() {
            if (control instanceof DraftButton<?> button) {
                button.sync();
            } else if (control instanceof DraftSlider slider) {
                slider.sync();
            }
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            // Every frame, as a slider's drag changes its draft without a refresh.
            boolean on = enabled.getAsBoolean();
            control.active = on;
            reset.active = on && !draft.isDefault();
            int y = getContentYMiddle() - 10;
            int resetX = getContentRight() - reset.getWidth();
            reset.setPosition(resetX, y);
            control.setPosition(resetX - GAP - CONTROL_WIDTH, y);
            Component shown = draft.changed() ? name.copy().withStyle(ChatFormatting.ITALIC) : name;
            int nameY = getContentYMiddle() - font.lineHeight / 2;
            graphics.text(font, shown, getContentX(), nameY, control.active ? NAME : NAME_INACTIVE);
            control.extractRenderState(graphics, mouseX, mouseY, a);
            reset.extractRenderState(graphics, mouseX, mouseY, a);
            if (hovered && mouseX < control.getX() && mouseX >= getContentX()
                    && mouseY >= nameY && mouseY < nameY + font.lineHeight) {
                graphics.setTooltipForNextFrame(font, font.split(tooltip, 200), mouseX, mouseY);
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return ImmutableList.of(control, reset);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return ImmutableList.of(control, reset);
        }
    }

    /** A slider over a draft, which Reset moves back. */
    private abstract static class DraftSlider extends AbstractSliderButton {
        DraftSlider(double initialValue) {
            super(0, 0, CONTROL_WIDTH, 20, Component.empty(), initialValue);
        }

        /** Moves the handle to the draft's value. */
        abstract void sync();
    }

    /** "Value: N" for a whole number from {@code min} to {@code max}, in steps of 1. */
    private static final class IntSlider extends DraftSlider {
        private final OptionDraft<Integer> draft;
        private final int min;
        private final int max;

        IntSlider(OptionDraft<Integer> draft, int min, int max) {
            super(fraction(draft.value(), min, max));
            this.draft = draft;
            this.min = min;
            this.max = max;
            updateMessage();
        }

        private static double fraction(int value, int min, int max) {
            return max == min ? 0 : (double) (value - min) / (max - min);
        }

        private int number() {
            return min + (int) Math.round(value * (max - min));
        }

        @Override
        void sync() {
            value = fraction(draft.value(), min, max);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(valueText(number()));
        }

        @Override
        protected void applyValue() {
            draft.set(number());
        }
    }

    /** Spellbar Size: "Value: 1.25", the slider's 0 to 1 spanning the option's range in its steps. */
    private static final class SizeSlider extends DraftSlider {
        private static final double RANGE = SpellBarLayout.MAX_SIZE - SpellBarLayout.MIN_SIZE;
        private final OptionDraft<Double> draft;

        SizeSlider(OptionDraft<Double> draft) {
            super((draft.value() - SpellBarLayout.MIN_SIZE) / RANGE);
            this.draft = draft;
            updateMessage();
        }

        private double size() {
            return SpellBarLayout.stepSize(SpellBarLayout.MIN_SIZE + value * RANGE);
        }

        @Override
        void sync() {
            value = (draft.value() - SpellBarLayout.MIN_SIZE) / RANGE;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(valueText(SpellBarLayout.sizeText(size())));
        }

        @Override
        protected void applyValue() {
            draft.set(size());
        }
    }
}
