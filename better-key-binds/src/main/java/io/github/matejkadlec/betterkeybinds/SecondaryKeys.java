package io.github.matejkadlec.betterkeybinds;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.jspecify.annotations.Nullable;

/**
 * Every key bind's secondary key: the link from a primary {@link KeyMapping} to its {@link SecondaryKeyMapping}, the
 * rules that merge the two (read by the mixins on {@code KeyMapping} and {@code ToggleKeyMapping}), and the secondary
 * key file ({@link SecondaryKeysFile}). A key bind gets its secondary key the first time anything asks for it: when
 * the file is loaded (once every mod has registered its key mappings) and when the Key Binds screen lists it.
 * Default: unbound.
 */
public final class SecondaryKeys {
    public static final String FILE_NAME = "better_key_binds-secondary_keys.txt";

    // KeyMapping keeps Object's equals and hashCode, so this is keyed by identity.
    private static final Map<KeyMapping, SecondaryKeyMapping> SECONDARY = new ConcurrentHashMap<>();
    // The file's bindings by key bind name, including key binds of mods that are not loaded now, which saving keeps.
    // Loaded on the mod loading thread, read on the render thread.
    private static final Map<String, SecondaryKeysFile.Binding> saved = new ConcurrentHashMap<>();
    private static volatile @Nullable Path file;
    private static volatile boolean dirty;

    private SecondaryKeys() {
    }

    /** The secondary key of {@code primary}, or null while it has none yet (always for a secondary key itself). */
    public static @Nullable SecondaryKeyMapping of(KeyMapping primary) {
        return SECONDARY.get(primary);
    }

    /** The secondary key of {@code primary}, created with the file's binding for it (unbound without one). */
    public static SecondaryKeyMapping getOrCreate(KeyMapping primary) {
        if (primary instanceof SecondaryKeyMapping) {
            throw new IllegalArgumentException("A secondary key has no secondary key: " + primary.getName());
        }
        return SECONDARY.computeIfAbsent(primary, key -> {
            SecondaryKeyMapping secondary = new SecondaryKeyMapping(key);
            SecondaryKeysFile.Binding binding = saved.get(key.getName());
            if (binding != null) {
                apply(secondary, binding);
            }
            return secondary;
        });
    }

    /** Binds the secondary key of {@code primary} to {@code key} with {@code modifier}; the file is saved next tick. */
    public static void bind(KeyMapping primary, KeyModifier modifier, InputConstants.Key key) {
        getOrCreate(primary).setKeyModifierAndCode(modifier, key);
        dirty = true;
    }

    /**
     * Binds the secondary key of {@code primary} as the Key Binds screen does: a key bind never holds the same key
     * twice, so when the secondary key takes its primary key (the same key and modifier), the primary key becomes
     * unbound.
     */
    public static void bindFromScreen(KeyMapping primary, KeyModifier modifier, InputConstants.Key key) {
        bind(primary, modifier, key);
        SecondaryKeyMapping secondary = getOrCreate(primary);
        if (!secondary.isUnbound() && secondary.getKey().equals(primary.getKey())
                && secondary.getKeyModifier() == primary.getKeyModifier()) {
            primary.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.UNKNOWN);
        }
    }

    /** The bound secondary keys, for Controlling's list of free keys. */
    public static List<SecondaryKeyMapping> bound() {
        return SECONDARY.values().stream().filter(secondary -> !secondary.isUnbound()).toList();
    }

    /** Unbinds the secondary key of {@code primary}, if it has a bound one. */
    public static void unbind(KeyMapping primary) {
        SecondaryKeyMapping secondary = of(primary);
        if (secondary != null && !secondary.isUnbound()) {
            bind(primary, KeyModifier.NONE, InputConstants.UNKNOWN);
        }
    }

    /** Whether {@code primary} has no bound secondary key. */
    public static boolean isUnbound(KeyMapping primary) {
        SecondaryKeyMapping secondary = of(primary);
        return secondary == null || secondary.isUnbound();
    }

    /** {@code KeyMapping#isDown} merged: down while the primary or the secondary key is held. */
    public static boolean isDown(KeyMapping primary, boolean primaryDown) {
        if (primaryDown) {
            return true;
        }
        SecondaryKeyMapping secondary = of(primary);
        return secondary != null && secondary.isDown();
    }

    /** {@code KeyMapping#consumeClick} merged: the primary's clicks first, then the secondary key's. */
    public static boolean consumeClick(KeyMapping primary, boolean primaryConsumed) {
        if (primaryConsumed) {
            return true;
        }
        SecondaryKeyMapping secondary = of(primary);
        return secondary != null && secondary.consumeClick();
    }

    /** {@code KeyMapping#matches} and {@code #matchesMouse} merged: a screen's check for a key bind's key. */
    public static boolean matches(KeyMapping primary, boolean primaryMatches, Predicate<KeyMapping> test) {
        if (primaryMatches) {
            return true;
        }
        SecondaryKeyMapping secondary = of(primary);
        return secondary != null && !secondary.isUnbound() && test.test(secondary);
    }

    /**
     * In game, a key's primary key binds win: the key mappings vanilla's lookup found for a pressed key (it already
     * left out those whose conflict context or modifier is not active) reach the secondary keys among them only when
     * no primary key bind is among them. D as Strafe Right's key and Strafe Left's secondary key strafes right. Screens
     * check keys through {@code matches} instead, which this leaves alone: L as Advancements' key and JEI's Show
     * Recipe's secondary key still shows recipes in the inventory. A release reaches them all, so no key stays held.
     */
    public static List<KeyMapping> primaryKeysWin(List<KeyMapping> found, boolean releasing) {
        if (releasing || found.stream().allMatch(SecondaryKeyMapping.class::isInstance)
                || found.stream().noneMatch(SecondaryKeyMapping.class::isInstance)) {
            return found;
        }
        return found.stream().filter(keyMapping -> !(keyMapping instanceof SecondaryKeyMapping)).toList();
    }

    /**
     * Whether another key bind that can act now has the key of {@code secondary} as its primary key: then the key's
     * state re-checked as a screen closes ({@code KeyMapping#setAll}) does not hold the secondary key, as a press would
     * not reach it ({@link #primaryKeysWin}).
     */
    public static boolean primaryKeyTakes(SecondaryKeyMapping secondary, KeyMapping[] keyMappings) {
        for (KeyMapping other : keyMappings) {
            if (other != secondary.primary() && !(other instanceof SecondaryKeyMapping)
                    && other.isActiveAndMatches(secondary.getKey())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reads the file from {@code configDir} and gives every key bind in {@code keyMappings} its secondary key. Runs
     * once every mod has registered its key mappings ({@code FMLLoadCompleteEvent}).
     */
    public static void load(Path configDir, KeyMapping[] keyMappings) {
        file = configDir.resolve(FILE_NAME);
        saved.clear();
        if (Files.isRegularFile(file)) {
            try {
                saved.putAll(SecondaryKeysFile.parse(Files.readAllLines(file, StandardCharsets.UTF_8)));
            } catch (IOException e) {
                BetterKeyBinds.LOGGER.error("Could not read {}; secondary keys start unbound", file, e);
            }
        }
        // Key binds already linked (none at startup) take the file's bindings too.
        SECONDARY.forEach((primary, secondary) -> {
            SecondaryKeysFile.Binding binding = saved.get(primary.getName());
            if (binding != null) {
                apply(secondary, binding);
            }
        });
        for (KeyMapping keyMapping : keyMappings) {
            getOrCreate(keyMapping);
        }
    }

    /** Saves the file after a change (called every client tick). */
    public static void saveIfDirty() {
        if (!dirty || file == null) {
            return;
        }
        dirty = false;
        Map<String, SecondaryKeysFile.Binding> bindings = new LinkedHashMap<>(saved);
        SECONDARY.forEach((primary, secondary) -> {
            if (secondary.isUnbound()) {
                bindings.remove(primary.getName());
            } else {
                bindings.put(primary.getName(), new SecondaryKeysFile.Binding(
                        secondary.getKey().getName(), secondary.getKeyModifier().name()));
            }
        });
        saved.clear();
        saved.putAll(bindings);
        Path temporary = file.resolveSibling(FILE_NAME + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.write(temporary, SecondaryKeysFile.write(bindings), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            BetterKeyBinds.LOGGER.error("Could not save the secondary keys to {}", file, e);
        }
    }

    private static void apply(SecondaryKeyMapping secondary, SecondaryKeysFile.Binding binding) {
        try {
            InputConstants.Key key = InputConstants.getKey(binding.key());
            secondary.setKeyModifierAndCode(KeyModifier.valueFromString(binding.modifier()), key);
        } catch (IllegalArgumentException e) {
            // An unknown key name (NumberFormatException included) leaves the secondary key unbound.
            BetterKeyBinds.LOGGER.warn("Secondary key of {}: unknown key {}", secondary.primary().getName(), binding.key());
        }
    }
}
