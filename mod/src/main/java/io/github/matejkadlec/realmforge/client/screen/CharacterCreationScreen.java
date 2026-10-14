package io.github.matejkadlec.realmforge.client.screen;

import io.github.matejkadlec.realmforge.character.CharacterCreationView;
import io.github.matejkadlec.realmforge.character.CharacterCreationView.ClassOption;
import io.github.matejkadlec.realmforge.character.CharacterCreationView.RaceOption;
import io.github.matejkadlec.realmforge.character.CharacterSheetView;
import io.github.matejkadlec.realmforge.character.Characters;
import io.github.matejkadlec.realmforge.character.net.CreateCharacterPayload;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Character creation (RLM-22, architecture 10.1): while the local player has no character, this screen opens and cannot
 * be closed. It draws {@link CharacterCreationView}: the races on the left, the classes the chosen race allows on the
 * right, each with its description and what it gives; a class also its resource and starting kit. Create Character
 * sends {@link CreateCharacterPayload}, and the screen closes once the server's character attachment arrives. In
 * singleplayer it pauses the game, as a menu does; the server still handles the request while paused.
 */
public final class CharacterCreationScreen extends Screen {
    private static final int COLUMN_WIDTH = 180;
    private static final int GAP = 24;
    private static final int PADDING = 10;
    private static final int LINE = 11;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;
    private static final int SECTION_GAP = 8;
    private static final int CONTENT_WIDTH = 2 * COLUMN_WIDTH + GAP;
    private static final int PANEL = 0xC0101010;
    private static final int HEADING = 0xFFFFFFFF;
    private static final int TEXT = 0xFFE0E0E0;
    private static final int SELECTED = CharacterSheetView.TITLE;
    /** Client ticks a new player entity waits before the screen opens, so its synced character can arrive first. */
    private static final int OPEN_DELAY = 20;
    /** Client ticks before Create Character can be pressed again when the server did not create the character. */
    private static final int RETRY_DELAY = 40;

    private final CharacterCreationView view;
    private int race;
    private int playerClass = -1;
    private int waiting;
    @Nullable
    private Button create;
    @Nullable
    private Button selectedRace;
    @Nullable
    private Button selectedClass;

    private CharacterCreationScreen(CharacterCreationView view) {
        super(Component.translatable(GeneratedTexts.GUI_CHARACTER_CREATION));
        this.view = view;
    }

    /** Called once from {@code RealmforgeClient}: opens the screen whenever the local player has no character. */
    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> openWhenNeeded());
    }

    private static void openWhenNeeded() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || minecraft.player.tickCount < OPEN_DELAY
                || Characters.exists(minecraft.player)) {
            return;
        }
        CharacterCreationView view = CharacterCreationView.of(minecraft.player.registryAccess());
        // Without races the data is broken; a screen nobody can leave would only lock the player out.
        if (!view.races().isEmpty()) {
            minecraft.setScreen(new CharacterCreationScreen(view));
        }
    }

    @Override
    protected void init() {
        Layout layout = layout();
        List<RaceOption> races = view.races();
        selectedRace = null;
        selectedClass = null;
        int raceWidth = buttonWidth(races.size());
        for (int i = 0; i < races.size(); i++) {
            int index = i;
            Button button = addRenderableWidget(Button.builder(races.get(i).name(), pressed -> selectRace(index))
                    .bounds(layout.left() + i * (raceWidth + BUTTON_GAP), layout.buttonsY(), raceWidth, BUTTON_HEIGHT)
                    .build());
            if (i == race) {
                selectedRace = button;
            }
        }
        List<ClassOption> classes = race().classes();
        int classWidth = buttonWidth(classes.size());
        for (int i = 0; i < classes.size(); i++) {
            int index = i;
            Button button = addRenderableWidget(Button.builder(classes.get(i).name(), pressed -> selectClass(index))
                    .bounds(layout.right() + i * (classWidth + BUTTON_GAP), layout.buttonsY(), classWidth,
                            BUTTON_HEIGHT)
                    .build());
            if (i == playerClass) {
                selectedClass = button;
            }
        }
        create = addRenderableWidget(Button.builder(Component.translatable(GeneratedTexts.GUI_CREATE_CHARACTER),
                        pressed -> create())
                .bounds((width - COLUMN_WIDTH) / 2, layout.createY(), COLUMN_WIDTH, BUTTON_HEIGHT).build());
        create.active = playerClass >= 0 && waiting == 0;
    }

    @Override
    public void tick() {
        if (minecraft.player == null || Characters.exists(minecraft.player)) {
            minecraft.setScreen(null);
            return;
        }
        if (waiting > 0 && --waiting == 0 && create != null) {
            create.active = playerClass >= 0;
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        Layout layout = layout();
        graphics.fill(layout.left() - PADDING, layout.top() - PADDING, layout.left() + CONTENT_WIDTH + PADDING,
                layout.createY() + BUTTON_HEIGHT + PADDING, PANEL);
        graphics.centeredText(font, title.copy().withStyle(ChatFormatting.BOLD), width / 2, layout.top(), HEADING);
        int headingsY = layout.top() + LINE + SECTION_GAP;
        graphics.text(font, Component.translatable(GeneratedTexts.GUI_RACE).withStyle(ChatFormatting.UNDERLINE),
                layout.left(), headingsY, SELECTED);
        graphics.text(font, Component.translatable(GeneratedTexts.GUI_CLASS).withStyle(ChatFormatting.UNDERLINE),
                layout.right(), headingsY, SELECTED);
        super.extractRenderState(graphics, mouseX, mouseY, a);
        if (selectedRace != null) {
            outline(graphics, selectedRace);
        }
        if (selectedClass != null) {
            outline(graphics, selectedClass);
        }
        int detailsY = layout.buttonsY() + BUTTON_HEIGHT + SECTION_GAP;
        drawLines(graphics, raceDetails(race()), layout.left(), detailsY);
        if (playerClass >= 0) {
            drawLines(graphics, classDetails(race().classes().get(playerClass)), layout.right(), detailsY);
        }
    }

    private RaceOption race() {
        return view.races().get(race);
    }

    private void selectRace(int index) {
        if (index != race) {
            race = index;
            playerClass = -1;
            rebuildWidgets();
        }
    }

    private void selectClass(int index) {
        playerClass = index;
        rebuildWidgets();
    }

    private void create() {
        if (playerClass < 0 || waiting > 0) {
            return;
        }
        RaceOption chosen = race();
        ClientPacketDistributor.sendToServer(new CreateCharacterPayload(chosen.key(),
                chosen.classes().get(playerClass).key()));
        waiting = RETRY_DELAY;
        if (create != null) {
            create.active = false;
        }
    }

    /** One line per entry, a section of the column: the description, then what it gives. */
    private record Line(Component text, int color, boolean gapBefore) {
    }

    private static List<Line> raceDetails(RaceOption option) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(option.description(), TEXT, false));
        boolean first = true;
        for (Component bonus : option.bonuses()) {
            lines.add(new Line(bonus, CharacterSheetView.BONUS, first));
            first = false;
        }
        return lines;
    }

    private static List<Line> classDetails(ClassOption option) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(option.description(), TEXT, false));
        lines.add(new Line(option.resource(), HEADING, true));
        for (Component bonus : option.bonuses()) {
            lines.add(new Line(bonus, CharacterSheetView.BONUS, false));
        }
        if (!option.startingKit().isEmpty()) {
            lines.add(new Line(Component.translatable(GeneratedTexts.GUI_STARTING_KIT)
                    .withStyle(ChatFormatting.UNDERLINE), SELECTED, true));
            option.startingKit().forEach(item -> lines.add(new Line(item, TEXT, false)));
        }
        return lines;
    }

    private void drawLines(GuiGraphicsExtractor graphics, List<Line> lines, int x, int y) {
        for (Line line : lines) {
            if (line.gapBefore()) {
                y += SECTION_GAP;
            }
            for (FormattedCharSequence part : font.split(line.text(), COLUMN_WIDTH)) {
                graphics.text(font, part, x, y, line.color());
                y += LINE;
            }
        }
    }

    private int detailsHeight(List<Line> lines) {
        int height = 0;
        for (Line line : lines) {
            height += (line.gapBefore() ? SECTION_GAP : 0) + LINE * font.split(line.text(), COLUMN_WIDTH).size();
        }
        return height;
    }

    private static void outline(GuiGraphicsExtractor graphics, Button button) {
        graphics.outline(button.getX() - 1, button.getY() - 1, button.getWidth() + 2, button.getHeight() + 2,
                SELECTED);
    }

    private static int buttonWidth(int count) {
        return count == 0 ? COLUMN_WIDTH : (COLUMN_WIDTH - (count - 1) * BUTTON_GAP) / count;
    }

    /** Where things go: the columns' left edges, the top, the row of choice buttons and the Create button. */
    private record Layout(int left, int right, int top, int buttonsY, int createY) {
    }

    private Layout layout() {
        int details = 0;
        for (RaceOption option : view.races()) {
            details = Math.max(details, detailsHeight(raceDetails(option)));
            for (ClassOption classOption : option.classes()) {
                details = Math.max(details, detailsHeight(classDetails(classOption)));
            }
        }
        int height = 2 * (LINE + SECTION_GAP) + BUTTON_HEIGHT + SECTION_GAP + details + SECTION_GAP + BUTTON_HEIGHT;
        int top = Math.max(PADDING, (this.height - height) / 2);
        int left = (width - CONTENT_WIDTH) / 2;
        int buttonsY = top + 2 * (LINE + SECTION_GAP);
        return new Layout(left, left + COLUMN_WIDTH + GAP, top, buttonsY,
                buttonsY + BUTTON_HEIGHT + SECTION_GAP + details + SECTION_GAP);
    }
}
