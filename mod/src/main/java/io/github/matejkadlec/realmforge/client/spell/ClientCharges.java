package io.github.matejkadlec.realmforge.client.spell;

import io.github.matejkadlec.realmforge.spell.ChargeMotion;
import io.github.matejkadlec.realmforge.spell.net.ChargePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The local player's charge (RLM-543): {@link ChargeMotion} moves them before each of their ticks, from the server's
 * {@link ChargePayload}. The movement keys do nothing until the hop is over, as they would push the player off the
 * charge's line: the player's input is an empty one meanwhile. The mouse still looks around.
 */
public final class ClientCharges {
    private static ChargeMotion motion;
    private static LocalPlayer owner;
    private static ClientInput heldInput;

    private ClientCharges() {
    }

    /** Called once from {@code RealmforgeClient}. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Pre.class, event -> tick(Minecraft.getInstance()));
    }

    /** Starts the charge the server asked for; an entity this client does not have yet cannot be charged at. */
    public static void start(ChargePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null
                || !(minecraft.level.getEntity(payload.targetId()) instanceof LivingEntity target)) {
            return;
        }
        end();
        owner = player;
        heldInput = player.input;
        player.input = new ClientInput();
        motion = new ChargeMotion(player, target, payload.perTick(), payload.maxTicks());
    }

    private static void tick(Minecraft minecraft) {
        if (motion == null || minecraft.isPaused()) {
            return;
        }
        if (minecraft.player != owner || !owner.isAlive()) {
            end();
            return;
        }
        switch (motion.tick(owner)) {
            case DONE, STOPPED -> end();
            case RUSHING, ARRIVED, RECOILING -> {
            }
        }
    }

    private static void end() {
        if (owner != null) {
            owner.input = heldInput;
        }
        motion = null;
        owner = null;
        heldInput = null;
    }
}
