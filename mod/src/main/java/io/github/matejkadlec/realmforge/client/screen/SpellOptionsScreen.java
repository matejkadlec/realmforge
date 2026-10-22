package io.github.matejkadlec.realmforge.client.screen;

import io.github.matejkadlec.realmforge.client.ClientConfig;
import io.github.matejkadlec.realmforge.client.spell.CastQueue;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * The spell casting options (Matěj, 2026-10-19, RLM-25; {@link ClientConfig}): Queue Spell Casts, off by default, and
 * Spells Queue Size, 1 to 3, which only applies, and can only be changed, while queueing is on. Opened from the
 * spellbook and from the mod list's Config button; Done saves them.
 */
public final class SpellOptionsScreen extends Screen {
    private static final int WIDTH = 200;
    private static final int ROW = 24;

    @Nullable
    private final Screen parent;
    @Nullable
    private CycleButton<Integer> size;

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
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(x, y + 2 * ROW + 8, WIDTH, 20).build());
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
}
