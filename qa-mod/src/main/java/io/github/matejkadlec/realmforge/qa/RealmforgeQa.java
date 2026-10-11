package io.github.matejkadlec.realmforge.qa;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/** Entry point of the dev-only Realmforge QA mod. Never released. */
@Mod(RealmforgeQa.MOD_ID)
public final class RealmforgeQa {
    public static final String MOD_ID = "realmforge_qa";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RealmforgeQa() {
    }

    /** Returns {@code realmforge_qa:<path>}. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
