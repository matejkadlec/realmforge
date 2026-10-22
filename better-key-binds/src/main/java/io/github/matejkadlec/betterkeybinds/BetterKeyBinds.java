package io.github.matejkadlec.betterkeybinds;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Better Key Binds: every key bind can have a secondary key, as in World of Warcraft, and either key triggers it.
 * Client-side only. The secondary keys are hidden key mappings ({@link SecondaryKeyMapping}) merged into their
 * primaries by the mixins in {@code mixin/}, saved in a file of their own ({@link SecondaryKeys}), and bound in a second
 * column of the Key Binds screen ({@link SecondaryKeyColumn}), vanilla's or Controlling's.
 */
@Mod(value = BetterKeyBinds.MOD_ID, dist = Dist.CLIENT)
public final class BetterKeyBinds {
    public static final String MOD_ID = "better_key_binds";
    static final Logger LOGGER = LogUtils.getLogger();

    public BetterKeyBinds(IEventBus modBus) {
        // Every mod has registered its key mappings by now (RegisterKeyMappingsEvent and the setup events ran), so
        // every key bind gets its secondary key. The file is not options.txt, whose early startup save drops the
        // lines of key binds it does not know yet.
        modBus.addListener(FMLLoadCompleteEvent.class, event -> event.enqueueWork(
                () -> SecondaryKeys.load(FMLPaths.CONFIGDIR.get(), Minecraft.getInstance().options.keyMappings)));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> SecondaryKeys.saveIfDirty());
        SecondaryKeyCapture.register(NeoForge.EVENT_BUS);
    }
}
