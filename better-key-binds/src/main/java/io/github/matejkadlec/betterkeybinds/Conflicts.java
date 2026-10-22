package io.github.matejkadlec.betterkeybinds;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

/**
 * Key conflicts that involve secondary keys, by the Key Binds screen's own rule for primary keys: two keys conflict
 * when {@link KeyMapping#same} says so (same key in conflicting contexts, NeoForge's modifier rules) or when one's
 * modifier is the other's key ({@code hasKeyModifierConflict}, Shift against Shift+G). A key bind's own primary and
 * secondary key never conflict with each other.
 */
public final class Conflicts {
    private Conflicts() {
    }

    /**
     * The display names of the other key binds' keys that conflict with {@code key}, a bound key of {@code owner}
     * (its primary or its secondary key): their primary keys when {@code primaries}, their secondary keys when
     * {@code secondaries}. Empty for an unbound key.
     */
    public static List<Component> of(KeyMapping key, KeyMapping owner, KeyMapping[] keyMappings, boolean primaries,
            boolean secondaries) {
        List<Component> names = new ArrayList<>();
        if (key.isUnbound()) {
            return names;
        }
        for (KeyMapping other : keyMappings) {
            if (other == owner) {
                continue;
            }
            if (primaries && !other.isUnbound() && conflict(key, other)) {
                names.add(other.getDisplayName());
            }
            SecondaryKeyMapping secondary = SecondaryKeys.of(other);
            if (secondaries && secondary != null && !secondary.isUnbound() && conflict(key, secondary)) {
                names.add(secondary.getDisplayName());
            }
        }
        return names;
    }

    /**
     * Whether the row of {@code primary} has a conflict that involves a secondary key: its primary key against
     * another key bind's secondary key, or its secondary key against any other key. The filters that list conflicting
     * key binds add these rows to the ones they find among primary keys.
     */
    public static boolean involveSecondaryKey(KeyMapping primary, KeyMapping[] keyMappings) {
        if (!of(primary, primary, keyMappings, false, true).isEmpty()) {
            return true;
        }
        SecondaryKeyMapping secondary = SecondaryKeys.of(primary);
        return secondary != null && !of(secondary, primary, keyMappings, true, true).isEmpty();
    }

    private static boolean conflict(KeyMapping key, KeyMapping other) {
        return key.same(other) || other.hasKeyModifierConflict(key);
    }
}
