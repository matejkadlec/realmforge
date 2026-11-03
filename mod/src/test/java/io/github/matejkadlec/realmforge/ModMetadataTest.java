package io.github.matejkadlec.realmforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ModMetadataTest {
    /** RLM-414, ADR 0067: what the Mods screens read. */
    @Test
    void theMetadataNamesTheLicenseAuthorsAndLogo() throws IOException {
        String metadata = resource("/META-INF/neoforge.mods.toml");
        assertTrue(metadata.contains("license = \"All rights reserved. Use in a modpack prohibited.\""), metadata);
        assertTrue(metadata.contains("authors = \"Matěj Kadlec (https://github.com/matejkadlec)\""), metadata);
        assertTrue(metadata.contains("logoFile = \"realmforge-logo.png\""), metadata);
    }

    @Test
    void theLogoIsASquareImageWithTransparency() throws IOException {
        try (InputStream stream = ModMetadataTest.class.getResourceAsStream("/realmforge-logo.png")) {
            assertNotNull(stream, "the logo is at the jar's root, where logoFile looks");
            BufferedImage logo = ImageIO.read(stream);
            assertEquals(256, logo.getWidth());
            assertEquals(256, logo.getHeight());
            assertTrue(logo.getColorModel().hasAlpha());
        }
    }

    /** MIT asks that its notice stays with the rooms; the CC0 and LGPL-3.0 sources carry no files (ADR 0067). */
    @Test
    void theJarCarriesTheMitNoticesAndNoOtherRoomLicense() throws IOException {
        for (String source : List.of("stonevaults", "dungeonz", "desert_dungeon")) {
            String license = resource("/META-INF/third_party/" + source + "/LICENSE");
            assertTrue(license.contains("The above copyright notice and this permission notice shall be"), source);
            assertTrue(resource("/META-INF/third_party/" + source + "/NOTICE").contains("ADR 0067"), source);
        }
        for (String source : List.of("dungeon_now_loading", "mo_structures")) {
            for (String file : List.of("LICENSE", "NOTICE", "COPYING")) {
                assertNull(ModMetadataTest.class.getResource("/META-INF/third_party/" + source + "/" + file), source);
            }
        }
    }

    private static String resource(String path) throws IOException {
        try (InputStream stream = ModMetadataTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path + " is in the jar");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
