package io.github.matejkadlec.realmforge.client.hud;

import io.github.matejkadlec.realmforge.dungeon.instance.InstanceBiomes;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceDimension;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The arrival title of a dungeon run (RLM-409, architecture 15.5): the dungeon's name, with "Realmforge Instance" under
 * it, once the client has loaded the instance dimension. The name is the biome the player stands in, the run's dungeon
 * type's ({@link InstanceBiomes}). Traveler's Titles' own biome title is unfit: it looks while the loading screen still
 * covers the game, which a dungeon's many blocks keep up longer than the title lasts, and its list of recent biomes
 * never empties for a player who changes dimension, so the next run shows nothing; the pack therefore blacklists the
 * dungeon biomes there. The client is loaded when its loading screen closes ({@code ClientPacketListener}
 * {@code #hasClientLoaded}, false again after every dimension change and respawn).
 */
public final class InstanceArrivalTitle {
    /** Traveler's Titles' timing in the pack: half a second in, 2.5 seconds shown, half a second out. */
    private static final int FADE_IN_TICKS = 10;
    private static final int STAY_TICKS = 50;
    private static final int FADE_OUT_TICKS = 10;

    private static boolean wasLoaded;

    private InstanceArrivalTitle() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> onClientTick());
    }

    private static void onClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft.getConnection();
        LocalPlayer player = minecraft.player;
        boolean loaded = connection != null && player != null && connection.hasClientLoaded();
        if (loaded && !wasLoaded && player.level().dimension().equals(InstanceDimension.LEVEL)) {
            player.level().getBiome(player.blockPosition()).unwrapKey().filter(InstanceBiomes::isRunBiome)
                    .ifPresent(biome -> show(minecraft, biome));
        }
        wasLoaded = loaded;
    }

    private static void show(Minecraft minecraft, ResourceKey<Biome> biome) {
        minecraft.gui.setTimes(FADE_IN_TICKS, STAY_TICKS, FADE_OUT_TICKS);
        minecraft.gui.setSubtitle(Component.translatable(GeneratedTexts.GUI_INSTANCE_SUBTITLE));
        minecraft.gui.setTitle(Component.translatable(Util.makeDescriptionId("biome", biome.identifier())));
    }
}
