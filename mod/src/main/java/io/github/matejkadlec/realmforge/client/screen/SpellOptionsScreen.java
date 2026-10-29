package io.github.matejkadlec.realmforge.client.screen;

import io.github.matejkadlec.realmforge.client.ClientConfig;
import io.github.matejkadlec.realmforge.client.hud.SpellBarLayout;
import io.github.matejkadlec.realmforge.client.spell.CastQueue;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * The spell options (Matěj, 2026-10-19, RLM-25; {@link ClientConfig}), one list of rows: Queue Spell Casts, off by
 * default, and Spells Queue Size, 1 to 3, which only applies, and can only be changed, while queueing is on; then the
 * spell bar's (RLM-404; Matěj, 2026-10-29: rows of the same list, no section heading): Spellbar Position, which opens
 * the drag mode ({@link SpellBarPositionScreen}), Spellbar Size, 0.5 to 1.5, and Mirror Hotbar's Width, which overrides
 * the size and greys its slider out while on. Opened from the spellbook and from the mod list's Config button; Done
 * saves them.
 */
public final class SpellOptionsScreen extends Screen {
    private static final int WIDTH = 200;
    private static final int ROW = 24;

    @Nullable
    private final Screen parent;
    @Nullable
    private CycleButton<Integer> size;
    @Nullable
    private SizeSlider barSize;

    public SpellOptionsScreen(@Nullable Screen parent) {
        super(Component.translatable(GeneratedTexts.GUI_SPELL_CASTING_OPTIONS));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = (width - WIDTH) / 2;
        int y = height / 4 + 12;
        boolean queue = ClientConfig.QUEUE_SPELL_CASTS.get();
        addRenderableWidget(CycleButton.onOffBuilder(queue)
                .withTooltip(value -> Tooltip.create(Component.translatable(
                        GeneratedTexts.GUI_QUEUE_SPELL_CASTS_TOOLTIP)))
                .create(x, y, WIDTH, 20, Component.translatable(GeneratedTexts.GUI_QUEUE_SPELL_CASTS),
                        (button, value) -> {
                            ClientConfig.QUEUE_SPELL_CASTS.set(value);
                            if (size != null) {
                                size.active = value;
                            }
                        }));
        size = addRenderableWidget(CycleButton.<Integer>builder(value -> Component.literal(value.toString()),
                        ClientConfig.SPELLS_QUEUE_SIZE.get())
                .withValues(IntStream.rangeClosed(1, CastQueue.MAX_SIZE).boxed().toList())
                .withTooltip(value -> Tooltip.create(Component.translatable(
                        GeneratedTexts.GUI_SPELLS_QUEUE_SIZE_TOOLTIP)))
                .create(x, y + ROW, WIDTH, 20, Component.translatable(GeneratedTexts.GUI_SPELLS_QUEUE_SIZE),
                        (button, value) -> ClientConfig.SPELLS_QUEUE_SIZE.set(value)));
        size.active = queue;
        addRenderableWidget(Button.builder(Component.translatable(GeneratedTexts.GUI_SPELL_BAR_POSITION_BUTTON),
                        button -> minecraft.setScreen(new SpellBarPositionScreen(this)))
                .tooltip(Tooltip.create(Component.translatable(GeneratedTexts.GUI_SPELL_BAR_POSITION_TOOLTIP)))
                .bounds(x, y + 2 * ROW, WIDTH, 20).build());
        boolean mirror = ClientConfig.MIRROR_HOTBAR_WIDTH.get();
        barSize = addRenderableWidget(new SizeSlider(x, y + 3 * ROW, WIDTH));
        barSize.setTooltip(Tooltip.create(Component.translatable(GeneratedTexts.GUI_SPELL_BAR_SIZE_TOOLTIP)));
        barSize.active = !mirror;
        addRenderableWidget(CycleButton.onOffBuilder(mirror)
                .withTooltip(value -> Tooltip.create(Component.translatable(
                        GeneratedTexts.GUI_MIRROR_HOTBAR_WIDTH_TOOLTIP)))
                .create(x, y + 4 * ROW, WIDTH, 20, Component.translatable(GeneratedTexts.GUI_MIRROR_HOTBAR_WIDTH),
                        (button, value) -> {
                            ClientConfig.MIRROR_HOTBAR_WIDTH.set(value);
                            if (barSize != null) {
                                barSize.active = !value;
                            }
                        }));
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(x, y + 5 * ROW + 8, WIDTH, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.centeredText(font, title, width / 2, height / 4 - 8, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        ClientConfig.SPEC.save();
        minecraft.setScreen(parent);
    }

    /** Spellbar Size: the slider's 0 to 1 spans the option's range, in its steps. */
    private static final class SizeSlider extends AbstractSliderButton {
        private static final double RANGE = SpellBarLayout.MAX_SIZE - SpellBarLayout.MIN_SIZE;

        SizeSlider(int x, int y, int width) {
            super(x, y, width, 20, Component.empty(),
                    (ClientConfig.SPELL_BAR_SIZE.get() - SpellBarLayout.MIN_SIZE) / RANGE);
            updateMessage();
        }

        private double size() {
            return SpellBarLayout.stepSize(SpellBarLayout.MIN_SIZE + value * RANGE);
        }

        @Override
        protected void updateMessage() {
            setMessage(CommonComponents.optionNameValue(Component.translatable(GeneratedTexts.GUI_SPELL_BAR_SIZE),
                    Component.literal(SpellBarLayout.sizeText(size()))));
        }

        @Override
        protected void applyValue() {
            ClientConfig.SPELL_BAR_SIZE.set(size());
        }
    }
}
