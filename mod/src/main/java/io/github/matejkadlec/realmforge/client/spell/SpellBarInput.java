package io.github.matejkadlec.realmforge.client.spell;

import io.github.matejkadlec.realmforge.client.ClientConfig;
import io.github.matejkadlec.realmforge.client.input.SpellKeys;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellAttachments;
import io.github.matejkadlec.realmforge.spell.Spellbook;
import io.github.matejkadlec.realmforge.spell.Spells;
import io.github.matejkadlec.realmforge.spell.net.CastSpellPayload;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The spell bar's keys in play (architecture 11.2 step 1; C3b, RLM-25): every client tick, the presses and the held
 * keys of the bar's slots and the racial slot go through {@link CastQueue}, with the options of
 * {@link ClientConfig}, and what it decides goes to the server as {@code CastSpellPayload}, aimed at the entity under
 * the crosshair ({@link SpellTargetPicker}). The server decides; a refusal comes back with its reason. Right click
 * with a wand presses and holds the first slot's key too ({@link WandCasting}, RLM-500).
 */
public final class SpellBarInput {
    private static final CastQueue QUEUE = new CastQueue();

    private SpellBarInput() {
    }

    /** Called once from {@code RealmforgeClient}. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> onClientTick());
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> QUEUE.clear());
    }

    private static void onClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        boolean canCast = player.isAlive() && !player.isSpectator();
        Spellbook book = player.getData(SpellAttachments.SPELLBOOK);
        ClientSpellState state = new ClientSpellState(player);
        CastQueue.Options options = ClientConfig.castOptions();
        SortedMap<Integer, ResourceKey<Spell>> held = new TreeMap<>();
        for (int slot = 0; slot < Spellbook.SLOT_COUNT; slot++) {
            KeyMapping key = SpellKeys.slot(slot);
            Optional<ResourceKey<Spell>> spell = book.slot(slot);
            boolean wandSlot = slot == WandCasting.SLOT;
            while (key.consumeClick() || wandSlot && WandCasting.consumeClick()) {
                if (canCast && spell.isPresent()) {
                    QUEUE.press(slot, spell.get(), state, options);
                }
            }
            boolean wandDown = wandSlot && WandCasting.isDown(player);
            if (canCast && spell.isPresent() && (key.isDown() || wandDown)) {
                held.put(slot, spell.get());
            }
        }
        if (!canCast) {
            QUEUE.clear();
            return;
        }
        QUEUE.tick(player.level().getGameTime(), held, state, options).ifPresent(send -> send(player, send));
    }

    private static void send(LocalPlayer player, CastQueue.Send send) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        // A server without Realmforge (or without this payload) would drop the connection for an unknown payload.
        if (connection == null || !connection.hasChannel(CastSpellPayload.TYPE)) {
            return;
        }
        Optional<Integer> target = Spells.get(player.registryAccess(), send.spell())
                .flatMap(spell -> SpellTargetPicker.pick(player, spell.value())).map(Entity::getId);
        ClientPacketDistributor.sendToServer(new CastSpellPayload(send.spell(), target, send.replaceCast()));
    }
}
