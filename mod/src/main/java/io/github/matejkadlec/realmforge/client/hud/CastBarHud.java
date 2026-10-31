package io.github.matejkadlec.realmforge.client.hud;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.client.ClientConfig;
import io.github.matejkadlec.realmforge.client.spell.ClientCasts;
import io.github.matejkadlec.realmforge.client.spell.CurrentCasts;
import io.github.matejkadlec.realmforge.spell.CastEnd;
import io.github.matejkadlec.realmforge.spell.CastTexts;
import io.github.matejkadlec.realmforge.spell.CombatState.Cast;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.Spells;
import io.github.matejkadlec.realmforge.spell.net.CastEndedPayload;
import io.github.matejkadlec.realmforge.spell.net.CastRefusedPayload;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The local player's cast bar (architecture 11.6, 18; C3b, RLM-25), after WoW's casting bar (Matěj's playtest of
 * 2026-10-21): toward the middle of the screen, clear of the spell bar wherever it is ({@link SpellBarLayout}), in a
 * black frame with rounded corners that holds the spell's icon on the left and the bar on the right. The bar fills in
 * the spell school's colour, shaded like a lit tube ({@link CastBarStyle}) with an almost white leading edge; a cast
 * time fills from the left, a channel starts full and empties toward the left. The spell's name is on the left, the
 * seconds "elapsed / total" on the right. A melee spell's swing (RLM-399; Matěj's playtest of 2026-10-31: an instant
 * melee spell begins at once but plays out over its swing) empties like a channel while its seconds count down,
 * "0.5 / 0.5" to "0.0 / 0.5", and goes at 0. When a cast ends, the bar stays for a moment: full and fading when it
 * completed, red with the reason in place of the name when it did not ("Interrupted", "Out of range";
 * {@link CastTexts}), a swing only then. With no cast to show, a refused cast's reason shows there in red (research
 * SP-3).
 */
public final class CastBarHud {
    static final Identifier LAYER = Realmforge.id("cast_bar");
    /** How long the bar stays after a cast ends. */
    private static final int ENDED_TICKS = 15;
    /** How long a refusal's reason shows. */
    private static final int MESSAGE_TICKS = 40;
    /** The frame's thickness, around the icon, between it and the bar, and around the bar. */
    private static final int BORDER = 2;
    private static final int ICON = SpellBarLayout.CAST_BAR_HEIGHT - 2 * BORDER;
    /** Between the bar's ends and its texts. */
    private static final int TEXT_PADDING = 4;
    private static final float TIME_SCALE = 0.75F;
    /** Extra font pixels between the time's characters. */
    private static final int TIME_SPACING = 1;
    private static final int FRAME = 0x000000;
    private static final int EMPTY = 0x1A1A1A;
    private static final int ICON_BACKGROUND = 0x101010;
    private static final int FAILED = 0xC02828;
    private static final int TEXT = 0xFFFFFF;
    private static final int TIME_TEXT = 0xE8E8E8;
    private static final int MESSAGE = 0xFF4040;

    private record Ended(ResourceKey<Spell> spell, CastEnd end, long gameTime) {
    }

    private record Message(Component text, long gameTime) {
    }

    @Nullable
    private static Ended ended;
    @Nullable
    private static Message message;

    private CastBarHud() {
    }

    /** Called once from {@code RealmforgeClient}. */
    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterGuiLayersEvent.class,
                event -> event.registerAbove(SpellBarHud.LAYER, LAYER, CastBarHud::render));
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            ended = null;
            message = null;
        });
    }

    /** A cast ended: the local player's shows how it ended. */
    public static void ended(CastEndedPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.level != null && payload.casterId() == minecraft.player.getId()) {
            ended = new Ended(payload.spell(), payload.end(), minecraft.level.getGameTime());
        }
    }

    /** The server refused a cast from the spell bar: its reason shows where the cast bar goes. */
    public static void refused(CastRefusedPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.level != null) {
            Optional<Spell> spell = Spells.get(minecraft.player.registryAccess(), payload.spell()).map(Holder::value);
            message = new Message(CastTexts.failure(payload.failure(), spell), minecraft.level.getGameTime());
        }
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (minecraft.options.hideGui || player == null || minecraft.gameMode == null
                || minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            return;
        }
        long now = player.level().getGameTime();
        double time = now + deltaTracker.getGameTimeDeltaPartialTick(false);
        int top = SpellBarLayout.castBarTop(graphics.guiWidth(), graphics.guiHeight(),
                ClientConfig.spellBarGroup(graphics.guiWidth(), graphics.guiHeight()));
        int x = (graphics.guiWidth() - SpellBarLayout.CAST_BAR_WIDTH) / 2;
        Font font = minecraft.font;
        Optional<Cast> cast = ClientCasts.current(player);
        if (cast.isPresent()) {
            Optional<Spell> spell = spell(player, cast.get().spell());
            float progress = CurrentCasts.progress(cast.get(), time);
            boolean drains = cast.get().channel() || cast.get().swing();
            float filled = drains ? 1 - progress : progress;
            int elapsed = (int) Math.floor(time - cast.get().startGameTime());
            String clock = cast.get().swing() ? CastBarStyle.countdown(elapsed, cast.get().durationTicks())
                    : CastBarStyle.time(elapsed, cast.get().durationTicks());
            drawBar(graphics, font, x, top, spell, filled, schoolColor(spell), name(spell, cast.get().spell()), clock,
                    0, 255);
            return;
        }
        if (ended != null && CastBarStyle.showing(now - ended.gameTime(), ENDED_TICKS)) {
            int alpha = fade(now - ended.gameTime(), ENDED_TICKS);
            Optional<Spell> spell = spell(player, ended.spell());
            Optional<Component> why = CastTexts.end(ended.end(), spell);
            // A completed cast flashes toward white as it fades; a failed one turns red.
            float flash = why.isPresent() ? 0 : 0.5F * alpha / 255;
            drawBar(graphics, font, x, top, spell, 1, why.isPresent() ? FAILED : schoolColor(spell),
                    why.orElseGet(() -> name(spell, ended.spell())), "", flash, alpha);
            return;
        }
        if (message != null && CastBarStyle.showing(now - message.gameTime(), MESSAGE_TICKS)) {
            int alpha = fade(now - message.gameTime(), MESSAGE_TICKS);
            graphics.centeredText(font, message.text(), graphics.guiWidth() / 2,
                    top + (SpellBarLayout.CAST_BAR_HEIGHT - 8) / 2, ARGB.color(alpha, MESSAGE));
        }
    }

    private static void drawBar(GuiGraphicsExtractor graphics, Font font, int x, int y, Optional<Spell> spell,
            float filled, int color, Component name, String time, float flash, int alpha) {
        int width = SpellBarLayout.CAST_BAR_WIDTH;
        int height = SpellBarLayout.CAST_BAR_HEIGHT;
        roundedFrame(graphics, x, y, width, height, ARGB.color(alpha, FRAME));

        int iconX = x + BORDER;
        int innerY = y + BORDER;
        int innerHeight = height - 2 * BORDER;
        graphics.fill(iconX, innerY, iconX + ICON, innerY + innerHeight, ARGB.color(alpha, ICON_BACKGROUND));
        spell.ifPresent(value -> {
            graphics.fill(iconX, innerY, iconX + ICON, innerY + innerHeight,
                    ARGB.color(alpha * 110 / 255, value.color()));
            graphics.centeredText(font, SpellSlots.initials(Language.getInstance().getOrDefault(value.name())),
                    iconX + ICON / 2 + 1, innerY + (innerHeight - 8) / 2, ARGB.color(alpha, TEXT));
        });

        int barX = iconX + ICON + BORDER;
        int barWidth = x + width - BORDER - barX;
        graphics.fill(barX, innerY, barX + barWidth, innerY + innerHeight, ARGB.color(alpha, EMPTY));
        int fill = Math.round(barWidth * Math.clamp(filled, 0, 1));
        for (int row = 0; row < innerHeight; row++) {
            int rowColor = CastBarStyle.shade(color, CastBarStyle.rowShade(row, innerHeight));
            if (flash > 0) {
                rowColor = CastBarStyle.shade(rowColor, flash);
            }
            graphics.fill(barX, innerY + row, barX + fill, innerY + row + 1, ARGB.color(alpha, rowColor));
            // The leading edge, almost white, fading inward.
            for (int column = 0; column < CastBarStyle.EDGE_LIGHT.length && column < fill && fill < barWidth;
                    column++) {
                int edgeX = barX + fill - 1 - column;
                graphics.fill(edgeX, innerY + row, edgeX + 1, innerY + row + 1,
                        ARGB.color(alpha, CastBarStyle.shade(rowColor, CastBarStyle.EDGE_LIGHT[column])));
            }
        }
        // Round the inner corners next to the frame's rounded ones.
        int frame = ARGB.color(alpha, FRAME);
        graphics.fill(iconX, innerY, iconX + 1, innerY + 1, frame);
        graphics.fill(iconX, innerY + innerHeight - 1, iconX + 1, innerY + innerHeight, frame);
        graphics.fill(barX + barWidth - 1, innerY, barX + barWidth, innerY + 1, frame);
        graphics.fill(barX + barWidth - 1, innerY + innerHeight - 1, barX + barWidth, innerY + innerHeight, frame);

        graphics.text(font, name, barX + TEXT_PADDING, innerY + (innerHeight - 8) / 2, ARGB.color(alpha, TEXT));
        if (!time.isEmpty()) {
            // Smaller than the name, with a pixel more between its characters, so the decimal points stay readable.
            int timeWidth = time.chars().map(c -> font.width(Character.toString(c)) + TIME_SPACING).sum()
                    - TIME_SPACING;
            graphics.pose().pushMatrix();
            graphics.pose().translate(barX + barWidth - TEXT_PADDING - timeWidth * TIME_SCALE,
                    innerY + (innerHeight - 8 * TIME_SCALE) / 2);
            graphics.pose().scale(TIME_SCALE, TIME_SCALE);
            int characterX = 0;
            for (int index = 0; index < time.length(); index++) {
                String character = time.substring(index, index + 1);
                graphics.text(font, character, characterX, 0, ARGB.color(alpha, TIME_TEXT));
                characterX += font.width(character) + TIME_SPACING;
            }
            graphics.pose().popMatrix();
        }
    }

    /** Fills {@code color} in a box with its corners cut two pixels deep: rounded at the GUI's pixel size. */
    private static void roundedFrame(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x + 2, y, x + width - 2, y + 1, color);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, color);
        graphics.fill(x, y + 2, x + width, y + height - 2, color);
        graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, color);
        graphics.fill(x + 2, y + height - 1, x + width - 2, y + height, color);
    }

    private static Optional<Spell> spell(LocalPlayer player, ResourceKey<Spell> key) {
        return Spells.get(player.registryAccess(), key).map(Holder::value);
    }

    private static int schoolColor(Optional<Spell> spell) {
        return spell.map(Spell::color).orElse(FAILED);
    }

    private static Component name(Optional<Spell> spell, ResourceKey<Spell> key) {
        return spell.<Component>map(value -> Component.translatable(value.name()))
                .orElseGet(() -> Component.literal(key.identifier().toString()));
    }

    /** Fully shown for the first half, then fading out. */
    private static int fade(long age, int ticks) {
        double left = 1 - (double) age / ticks;
        return (int) Math.round(255 * Math.clamp(left * 2, 0, 1));
    }
}
