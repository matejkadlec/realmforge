package io.github.matejkadlec.realmforge.compat.apotheosis;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ApotheosisNoticeTest {
    /**
     * RLM-524: the jar carries Apotheosis's data (data/apotheosis/), so it carries Apotheosis's MIT notice next to it,
     * as MIT asks of every copy, and a notice of ours that says what the files are.
     */
    @Test
    void theJarCarriesApotheosisSLicenseNextToItsData() throws IOException {
        String license = resource("/META-INF/third_party/apotheosis/LICENSE");
        assertTrue(license.startsWith("MIT License\n"), license);
        assertTrue(license.contains("The above copyright notice and this permission notice shall be"), license);
        assertTrue(resource("/META-INF/third_party/apotheosis/NOTICE").contains("Apotheosis's own data"));
        assertNotNull(ApotheosisNoticeTest.class.getResource("/data/apotheosis/apotheosis/rarities/rare.json"),
                "the data the notice is about ships too");
    }

    private static String resource(String path) throws IOException {
        try (InputStream stream = ApotheosisNoticeTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path + " is in the jar");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
