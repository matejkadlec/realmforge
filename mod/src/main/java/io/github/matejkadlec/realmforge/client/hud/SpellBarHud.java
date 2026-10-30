package io.github.matejkadlec.realmforge.client.hud;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.client.ClientConfig;
import io.github.matejkadlec.realmforge.client.input.KeyLabels;
import io.github.matejkadlec.realmforge.client.input.SpellKeys;
import io.github.matejkadlec.realmforge.client.screen.SpellBarPositionScreen;
import io.github.matejkadlec.realmforge.combat.DamagePipeline;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.spell.CombatState;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellAttachments;
import io.github.matejkadlec.realmforge.spell.SpellBarView;
import io.github.matejkadlec.realmforge.spell.Spellbook;
import io.github.matejkadlec.realmforge.spell.Spells;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The spell bar (architecture 11.6, 18; C3b, RLM-25): eight slots and the racial slot side by side, with the resource
 * bar under them ({@link ResourceBarHud}, RLM-400), drawn from {@link SpellBarView} by {@link SpellSlots}. The two are
 * one group, where and as big as the spell options put it (RLM-404, {@link ClientConfig#spellBarGroup}); by default
 * above the status rows, the XP bar and the selected item's name, in one place whatever those rows are
 * ({@link SpellBarLayout}). It shows even when empty (research SP-1), and never in spectator mode. Its layer comes
 * right after vanilla's item name, and then raises {@code Gui#leftHeight} and {@code Gui#rightHeight} when the group is
 * in the way, so the action bar message stays above it. While the drag mode ({@link SpellBarPositionScreen}) is open,
 * the screen draws the group where it is being dragged instead.
 */
public final class SpellBarHud {
    static final Identifier LAYER = Realmforge.id("spell_bar");

    /** The game time the global cooldown last seen ends, and how long it was when it began, for its sweep. */
    private static long globalCooldownEnd;
    private static int globalCooldownTicks = Spell.DEFAULT_GLOBAL_COOLDOWN_TICKS;

    private SpellBarHud() {
    }

    /** Called once from {@code RealmforgeClient}. */
    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterGuiLayersEvent.class,
                event -> event.registerAbove(VanillaGuiLayers.SELECTED_ITEM_NAME, LAYER, SpellBarHud::render));
    }

    /** The bar as the local player has it now. */
    public static SpellBarView view(LocalPlayer player) {
        long now = player.level().getGameTime();
        CombatState combat = player.getData(SpellAttachments.COMBAT);
        if (combat.globalCooldownEnd() != globalCooldownEnd) {
            if (combat.globalCooldownEnd() > now) {
                globalCooldownTicks = (int) (combat.globalCooldownEnd() - now);
            }
            globalCooldownEnd = combat.globalCooldownEnd();
        }
        return SpellBarView.of(player.getData(SpellAttachments.SPELLBOOK), combat, now,
                key -> Spells.get(player.registryAccess(), key).map(Holder::value),
                DamagePipeline.stat(player, GeneratedStats.COOLDOWN_REDUCTION), globalCooldownTicks);
    }

    /**
     * The short label of {@code slot}'s key bind ("R", "RMB", "CTR-R"), empty while it is unbound; a § shows
     * ({@link KeyLabels#spellBarLabel}, RLM-498).
     */
    public static Optional<Component> keyLabel(int slot) {
        KeyMapping key = SpellKeys.slot(slot);
        return key.isUnbound() ? Optional.empty()
                : Optional.of(KeyLabels.spellBarLabel(key.getKeyModifier(), key.getKey()));
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (minecraft.options.hideGui || player == null || minecraft.gameMode == null
                || minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            return;
        }
        SpellBarLayout.Group group = ClientConfig.spellBarGroup(graphics.guiWidth(), graphics.guiHeight());
        if (!(minecraft.screen instanceof SpellBarPositionScreen)) {
            draw(graphics, minecraft.font, player, group);
        }
        Gui gui = minecraft.gui;
        int raised = SpellBarLayout.raised(graphics.guiWidth(), graphics.guiHeight(), group,
                Math.max(gui.leftHeight, gui.rightHeight));
        gui.leftHeight = raised;
        gui.rightHeight = raised;
    }

    /**
     * Draws the group at its place and scale: {@code player}'s slots and resource bar, or, without a player (the drag
     * mode opened from the main menu), empty slots and an empty resource bar.
     */
    public static void draw(GuiGraphicsExtractor graphics, Font font, @Nullable LocalPlayer player,
            SpellBarLayout.Group group) {
        SpellBarView view = player == null ? null : view(player);
        graphics.pose().pushMatrix();
        graphics.pose().translate(group.x(), group.y());
        graphics.pose().scale(group.scale(), group.scale());
        for (int slot = 0; slot < Spellbook.SLOT_COUNT; slot++) {
            SpellSlots.draw(graphics, font, SpellBarLayout.slotX(slot, SpellBarLayout.WIDTH), 0, SpellBarLayout.SLOT,
                    view == null ? SpellBarView.Slot.EMPTY : view.slots().get(slot), keyLabel(slot));
        }
        if (player == null) {
            ResourceBarHud.drawEmpty(graphics, 0, SpellBarLayout.resourceBarTop(0));
        } else {
            ResourceBarHud.draw(graphics, font, player, 0, SpellBarLayout.resourceBarTop(0));
        }
        graphics.pose().popMatrix();
    }
}
