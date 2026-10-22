package io.github.matejkadlec.realmforge.client.hud;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.client.input.SpellKeys;
import io.github.matejkadlec.realmforge.combat.DamagePipeline;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.spell.CombatState;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellAttachments;
import io.github.matejkadlec.realmforge.spell.SpellBarView;
import io.github.matejkadlec.realmforge.spell.Spellbook;
import io.github.matejkadlec.realmforge.spell.Spells;
import java.util.Optional;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
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
 * The spell bar (architecture 11.6, 18; C3b, RLM-25): eight slots and the racial slot side by side, above the status
 * rows, the XP bar and the selected item's name ({@link SpellBarLayout}), drawn from {@link SpellBarView} by
 * {@link SpellSlots}. It shows even when empty (research SP-1), and never in spectator mode. Its layer comes right
 * after vanilla's item name, which is drawn on the line below the bar, and then raises {@code Gui#leftHeight} and
 * {@code Gui#rightHeight}, so the action bar message and the cast bar ({@link CastBarHud}) stay above it.
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

    /** The label of {@code slot}'s key, empty while it is unbound; a § shows ({@code KeyLabels}). */
    public static Optional<Component> keyLabel(int slot) {
        KeyMapping key = SpellKeys.slot(slot);
        return key.isUnbound() ? Optional.empty() : Optional.of(key.getTranslatedKeyMessage());
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (minecraft.options.hideGui || player == null || minecraft.gameMode == null
                || minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            return;
        }
        SpellBarView view = view(player);
        Gui gui = minecraft.gui;
        int stack = Math.max(gui.leftHeight, gui.rightHeight);
        int top = SpellBarLayout.top(graphics.guiHeight(), stack, minecraft.gameMode.canHurtPlayer());
        for (int slot = 0; slot < Spellbook.SLOT_COUNT; slot++) {
            SpellSlots.draw(graphics, minecraft.font, SpellBarLayout.slotX(slot, graphics.guiWidth()), top,
                    SpellBarLayout.SLOT, view.slots().get(slot), keyLabel(slot));
        }
        int raised = SpellBarLayout.raised(graphics.guiHeight(), top);
        gui.leftHeight = raised;
        gui.rightHeight = raised;
    }
}
