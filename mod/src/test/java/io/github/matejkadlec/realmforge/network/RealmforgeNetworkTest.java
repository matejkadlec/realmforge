package io.github.matejkadlec.realmforge.network;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matejkadlec.realmforge.Realmforge;
import java.util.regex.Pattern;
import net.minecraft.SharedConstants;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.Test;

class RealmforgeNetworkTest {
    /** The version comes from neoforge.mods.toml, which Gradle fills in as {@code <mod_version>+mc<minecraft>}. */
    @Test
    void payloadVersionIsTheModVersion() {
        var container = ModList.get().getModContainerById(Realmforge.MOD_ID).orElseThrow();
        String version = RealmforgeNetwork.version(container);
        String minecraft = Pattern.quote(SharedConstants.getCurrentVersion().name());
        assertTrue(version.matches("\\d+\\.\\d+\\.\\d+\\+mc" + minecraft), version);
    }
}
