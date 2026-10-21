package io.github.matejkadlec.realmforge.client.hud;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.client.spell.ClientCasts;
import io.github.matejkadlec.realmforge.client.spell.CurrentCasts;
import io.github.matejkadlec.realmforge.spell.CastEnd;
import io.github.matejkadlec.realmforge.spell.CastTexts;
import io.github.matejkadlec.realmforge.spell.CombatState.Cast;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellText;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * The local player's cast bar (architecture 11.6, 18; C3b, RLM-25), above the spell bar with room for the item name
 * and the action bar message between them ({@link SpellBarLayout}). A cast time fills in gold, a channel empties in
 * green, each with the spell's name and the seconds left. When a cast ends, the bar stays for a moment: full when it
 * completed, red with the reason when it did not ("Interrupted", "Out of range"; {@link CastTexts}). With no cast to
 * show, a refused cast's reason shows there in red (research SP-3).
 */
public final class CastBarHud {
    static final Identifier LAYER = Realmforge.id("cast_bar");
    /** How long the bar stays after a cast ends. */
    private static final int ENDED_TICKS = 15;
    /** How long a refusal's reason shows. */
    private static final int MESSAGE_TICKS = 40;
    private static final int BACKGROUND = 0xC0000000;
    private static final int BORDER = 0xFF000000;
    private static final int CAST_FILL = 0xFFE6B422;
    private static final int CHANNEL_FILL = 0xFF3FB34F;
    private static final int FAILED_FILL = 0xFFC03030;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MESSAGE = 0xFFFF4040;

    private record Ended(ResourceKey<Spell> spell, CastEnd end, boolean channel, long gameTime) {
    }

    private record Message(Component text, long gameTime) {
    }

    @Nullable
    private static Ended ended;
    @Nullable
    private static Message message;
    /** The last cast drawn, so its end knows whether it was a channel. */
    @Nullable
    private static Cast lastCast;

    private CastBarHud() {
    }

    /** Called once from {@code RealmforgeClient}. */
    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterGuiLayersEvent.class,
                event -> event.registerAbove(SpellBarHud.LAYER, LAYER, CastBarHud::render));
    }

    /** A cast ended: the local player's shows how it ended. */
    public static void ended(CastEndedPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.level != null && payload.casterId() == minecraft.player.getId()) {
            boolean channel = lastCast != null && lastCast.spell().equals(payload.spell()) && lastCast.channel();
            ended = new Ended(payload.spell(), payload.end(), channel, minecraft.level.getGameTime());
        }
    }

    /** The server refused a cast from the spell bar: its reason shows over the bar. */
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
        int stack = Math.max(minecraft.gui.leftHeight, minecraft.gui.rightHeight);
        int top = SpellBarLayout.castBarTop(graphics.guiHeight(), stack);
        int x = (graphics.guiWidth() - SpellBarLayout.CAST_BAR_WIDTH) / 2;
        Font font = minecraft.font;
        Optional<Cast> cast = ClientCasts.current(player);
        if (cast.isPresent()) {
            lastCast = cast.get();
            float progress = CurrentCasts.progress(cast.get(), time);
            float filled = cast.get().channel() ? 1 - progress : progress;
            double left = Math.max(0, cast.get().endGameTime() - time) / 20;
            drawBar(graphics, font, x, top, filled, cast.get().channel() ? CHANNEL_FILL : CAST_FILL,
                    name(player, cast.get().spell()), SpellText.decimal(Math.round(left * 10) / 10.0), 255);
            return;
        }
        if (ended != null && now - ended.gameTime() < ENDED_TICKS) {
            int alpha = fade(now - ended.gameTime(), ENDED_TICKS);
            Optional<Spell> spell = Spells.get(player.registryAccess(), ended.spell()).map(Holder::value);
            Optional<Component> why = CastTexts.end(ended.end(), spell);
            drawBar(graphics, font, x, top, 1, why.isPresent() ? FAILED_FILL
                    : ended.channel() ? CHANNEL_FILL : CAST_FILL,
                    why.orElseGet(() -> name(player, ended.spell())), "", alpha);
            return;
        }
        if (message != null && now - message.gameTime() < MESSAGE_TICKS) {
            int alpha = fade(now - message.gameTime(), MESSAGE_TICKS);
            graphics.centeredText(font, message.text(), graphics.guiWidth() / 2, top + 2, ARGB.color(alpha, MESSAGE));
        }
    }

    private static void drawBar(GuiGraphicsExtractor graphics, Font font, int x, int y, float filled, int fill,
            Component name, String seconds, int alpha) {
        int width = SpellBarLayout.CAST_BAR_WIDTH;
        int height = SpellBarLayout.CAST_BAR_HEIGHT;
        graphics.fill(x, y, x + width, y + height, ARGB.color(alpha * 0xC0 / 255, BACKGROUND));
        int inner = Math.round((width - 2) * Math.clamp(filled, 0, 1));
        graphics.fill(x + 1, y + 1, x + 1 + inner, y + height - 1, ARGB.color(alpha, fill));
        graphics.outline(x, y, width, height, ARGB.color(alpha, BORDER));
        graphics.text(font, name, x + 3, y + 2, ARGB.color(alpha, TEXT));
        if (!seconds.isEmpty()) {
            graphics.text(font, seconds, x + width - 3 - font.width(seconds), y + 2, ARGB.color(alpha, TEXT));
        }
    }

    private static Component name(LocalPlayer player, ResourceKey<Spell> spell) {
        return Spells.get(player.registryAccess(), spell).map(holder -> Component.translatable(holder.value().name()))
                .orElseGet(() -> Component.literal(spell.identifier().toString()));
    }

    /** Fully shown for the first half, then fading out. */
    private static int fade(long age, int ticks) {
        double left = 1 - (double) age / ticks;
        return (int) Math.round(255 * Math.clamp(left * 2, 0, 1));
    }
}
