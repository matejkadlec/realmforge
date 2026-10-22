package io.github.matejkadlec.betterkeybinds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.glfw.GLFW;

/**
 * The merge of a key bind and its secondary key, read through {@link SecondaryKeys} as the mixins on KeyMapping read
 * it (the mixins themselves need the game). The key mappings are real ones; their conflict context and modifier
 * checks need no running game for the universal context without a modifier.
 */
class SecondaryKeysTest {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static KeyMapping keyMapping(int glfwKey) {
        return new KeyMapping("test.key." + NAMES.incrementAndGet(), InputConstants.Type.KEYSYM, glfwKey,
                KeyMapping.Category.MISC);
    }

    private static InputConstants.Key key(int glfwKey) {
        return InputConstants.Type.KEYSYM.getOrCreate(glfwKey);
    }

    @Test
    void aKeyBindStartsWithAnUnboundSecondaryKey() {
        KeyMapping chat = keyMapping(GLFW.GLFW_KEY_ENTER);

        SecondaryKeyMapping secondary = SecondaryKeys.getOrCreate(chat);

        assertTrue(secondary.isUnbound());
        assertTrue(SecondaryKeys.isUnbound(chat));
        assertSame(secondary, SecondaryKeys.getOrCreate(chat));
        assertSame(chat, secondary.primary());
    }

    @Test
    void aSecondaryKeyHasNoSecondaryKey() {
        SecondaryKeyMapping secondary = SecondaryKeys.getOrCreate(keyMapping(GLFW.GLFW_KEY_W));

        assertThrows(IllegalArgumentException.class, () -> SecondaryKeys.getOrCreate(secondary));
        assertEquals(null, SecondaryKeys.of(secondary));
    }

    @Test
    void eitherKeyHoldsTheKeyBind() {
        KeyMapping forward = keyMapping(GLFW.GLFW_KEY_W);
        SecondaryKeys.bind(forward, KeyModifier.NONE, key(GLFW.GLFW_KEY_UP));
        SecondaryKeyMapping secondary = SecondaryKeys.of(forward);
        assertNotNull(secondary);

        assertFalse(SecondaryKeys.isDown(forward, false), "neither held");
        secondary.setDown(true);
        assertTrue(SecondaryKeys.isDown(forward, false), "secondary held");
        assertTrue(SecondaryKeys.isDown(forward, true), "both held");
        // The primary key is released while the secondary key is still held: still down.
        assertTrue(SecondaryKeys.isDown(forward, false), "primary released, secondary held");
        secondary.setDown(false);
        assertTrue(SecondaryKeys.isDown(forward, true), "secondary released, primary held");
        assertFalse(SecondaryKeys.isDown(forward, false), "both released");
    }

    @Test
    void theClicksOfBothKeysCount() throws ReflectiveOperationException {
        KeyMapping chat = keyMapping(GLFW.GLFW_KEY_ENTER);
        SecondaryKeys.bind(chat, KeyModifier.NONE, key(GLFW.GLFW_KEY_T));
        // KeyMapping.click(key) looks the key up through the running game's modifiers; set its counter instead.
        Field clickCount = KeyMapping.class.getDeclaredField("clickCount");
        clickCount.setAccessible(true);
        clickCount.setInt(SecondaryKeys.of(chat), 2);

        assertTrue(SecondaryKeys.consumeClick(chat, true), "the primary's own click comes first");
        assertEquals(2, clickCount.getInt(SecondaryKeys.of(chat)));
        assertTrue(SecondaryKeys.consumeClick(chat, false));
        assertTrue(SecondaryKeys.consumeClick(chat, false));
        assertFalse(SecondaryKeys.consumeClick(chat, false), "both clicks of the secondary key consumed");
    }

    @Test
    void aScreensKeyCheckMatchesEitherKey() {
        KeyMapping inventory = keyMapping(GLFW.GLFW_KEY_E);
        KeyEvent pressedI = new KeyEvent(GLFW.GLFW_KEY_I, 0, 0);
        assertFalse(SecondaryKeys.matches(inventory, false, secondary -> secondary.matches(pressedI)), "unbound");

        SecondaryKeys.bind(inventory, KeyModifier.NONE, key(GLFW.GLFW_KEY_I));

        assertTrue(SecondaryKeys.matches(inventory, false, secondary -> secondary.matches(pressedI)));
        assertFalse(SecondaryKeys.matches(inventory, false,
                secondary -> secondary.matches(new KeyEvent(GLFW.GLFW_KEY_O, 0, 0))));
    }

    @Test
    void unbindingClearsKeyAndModifier() {
        KeyMapping drop = keyMapping(GLFW.GLFW_KEY_Q);
        SecondaryKeys.bind(drop, KeyModifier.CONTROL, key(GLFW.GLFW_KEY_D));
        assertFalse(SecondaryKeys.isUnbound(drop));

        SecondaryKeys.unbind(drop);

        assertTrue(SecondaryKeys.isUnbound(drop));
        assertEquals(KeyModifier.NONE, SecondaryKeys.of(drop).getKeyModifier());
    }

    @Test
    void theSecondaryKeyFollowsItsPrimarysConflictContext() {
        KeyMapping primary = keyMapping(GLFW.GLFW_KEY_G);
        SecondaryKeyMapping secondary = SecondaryKeys.getOrCreate(primary);

        primary.setKeyConflictContext(KeyConflictContext.GUI);

        assertSame(KeyConflictContext.GUI, secondary.getKeyConflictContext());
    }

    @Test
    void conflictsReachSecondaryKeysBothWays() {
        KeyMapping chat = keyMapping(GLFW.GLFW_KEY_ENTER);
        KeyMapping jump = keyMapping(GLFW.GLFW_KEY_SPACE);
        KeyMapping other = keyMapping(GLFW.GLFW_KEY_K);
        KeyMapping[] all = {chat, jump, other};
        SecondaryKeys.bind(chat, KeyModifier.NONE, key(GLFW.GLFW_KEY_T));

        assertTrue(Conflicts.of(SecondaryKeys.of(chat), chat, all, true, true).isEmpty(), "T is free");
        assertFalse(Conflicts.involveSecondaryKey(chat, all));

        SecondaryKeys.bind(jump, KeyModifier.NONE, key(GLFW.GLFW_KEY_T));
        assertEquals(1, Conflicts.of(SecondaryKeys.of(chat), chat, all, true, true).size(), "two secondary keys on T");
        assertTrue(Conflicts.involveSecondaryKey(jump, all));

        SecondaryKeys.unbind(jump);
        SecondaryKeys.bind(other, KeyModifier.NONE, key(GLFW.GLFW_KEY_ENTER));
        assertEquals(1, Conflicts.of(chat, chat, all, false, true).size(), "Enter is another key bind's secondary key");
        assertTrue(Conflicts.involveSecondaryKey(chat, all));
    }

    @Test
    void aKeyBindsOwnKeysDoNotConflict() {
        KeyMapping chat = keyMapping(GLFW.GLFW_KEY_ENTER);
        SecondaryKeys.bind(chat, KeyModifier.NONE, key(GLFW.GLFW_KEY_ENTER));

        assertTrue(Conflicts.of(SecondaryKeys.of(chat), chat, new KeyMapping[] {chat}, true, true).isEmpty());
        assertFalse(Conflicts.involveSecondaryKey(chat, new KeyMapping[] {chat}));
    }

    @Test
    void differentModifiersOutsideTheGameDoNotConflict() {
        KeyMapping a = keyMapping(GLFW.GLFW_KEY_A);
        KeyMapping b = keyMapping(GLFW.GLFW_KEY_B);
        a.setKeyConflictContext(KeyConflictContext.GUI);
        b.setKeyConflictContext(KeyConflictContext.GUI);
        SecondaryKeys.bind(a, KeyModifier.CONTROL, key(GLFW.GLFW_KEY_X));
        SecondaryKeys.bind(b, KeyModifier.SHIFT, key(GLFW.GLFW_KEY_X));

        assertTrue(Conflicts.of(SecondaryKeys.of(a), a, new KeyMapping[] {a, b}, true, true).isEmpty());
    }

    @Test
    void theFileGivesKeyBindsTheirSecondaryKeysAndSavingKeepsUnknownOnes(@TempDir Path config) throws IOException {
        KeyMapping chat = keyMapping(GLFW.GLFW_KEY_ENTER);
        KeyMapping attack = keyMapping(GLFW.GLFW_KEY_F);
        Path file = config.resolve(SecondaryKeys.FILE_NAME);
        Files.write(file, List.of(
                chat.getName() + ":key.keyboard.t",
                attack.getName() + ":key.mouse.4:SHIFT",
                "othermod.key.missing:key.keyboard.m"), StandardCharsets.UTF_8);

        SecondaryKeys.load(config, new KeyMapping[] {chat, attack});

        assertEquals(key(GLFW.GLFW_KEY_T), SecondaryKeys.of(chat).getKey());
        assertEquals(InputConstants.Type.MOUSE.getOrCreate(3), SecondaryKeys.of(attack).getKey());
        assertEquals(KeyModifier.SHIFT, SecondaryKeys.of(attack).getKeyModifier());

        SecondaryKeys.unbind(attack);
        SecondaryKeys.saveIfDirty();

        // The other tests' key binds are in the file too: the links are static, like the game's key mappings.
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertTrue(lines.contains(chat.getName() + ":key.keyboard.t"));
        assertTrue(lines.contains("othermod.key.missing:key.keyboard.m"), "a key bind of a mod not loaded now");
        assertTrue(lines.stream().noneMatch(line -> line.startsWith(attack.getName() + ":")), "unbound: left out");
    }

    @Test
    void anUnknownKeyNameLeavesTheSecondaryKeyUnbound(@TempDir Path config) throws IOException {
        KeyMapping chat = keyMapping(GLFW.GLFW_KEY_ENTER);
        Files.write(config.resolve(SecondaryKeys.FILE_NAME), List.of(chat.getName() + ":key.keyboard.nonsense"),
                StandardCharsets.UTF_8);

        SecondaryKeys.load(config, new KeyMapping[] {chat});

        assertTrue(SecondaryKeys.isUnbound(chat));
    }
}
