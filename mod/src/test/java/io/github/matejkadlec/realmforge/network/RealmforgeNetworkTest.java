package io.github.matejkadlec.realmforge.network;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matejkadlec.realmforge.Realmforge;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.Test;

class RealmforgeNetworkTest {
    /** The version comes from neoforge.mods.toml, which Gradle fills in as {@code <mod_version>+mc<minecraft>}. */
    @Test
    void payloadVersionIsTheModVersion() {
        var container = ModList.get().getModContainerById(Realmforge.MOD_ID).orElseThrow();
        String version = RealmforgeNetwork.version(container);
        assertTrue(version.matches("\\d+\\.\\d+\\.\\d+\\+mc1\\.21\\.1"), version);
    }

    @Test
    void installingNoClientHandlerIsRefused() {
        assertThrows(NullPointerException.class, () -> RealmforgeNetwork.installClientHandler(null));
    }
}
