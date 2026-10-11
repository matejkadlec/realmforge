package io.github.matejkadlec.realmforge;

import com.mojang.logging.LogUtils;
import io.github.matejkadlec.realmforge.generated.GeneratedItems;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/** Entry point of the Realmforge core mod. */
@Mod(Realmforge.MOD_ID)
public final class Realmforge {
    public static final String MOD_ID = "realmforge";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Realmforge(IEventBus modEventBus) {
        // Registrations generated from content/ (see mod/src/generated, never edit by hand).
        GeneratedItems.register(modEventBus);
    }

    /** Returns {@code realmforge:<path>}. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
