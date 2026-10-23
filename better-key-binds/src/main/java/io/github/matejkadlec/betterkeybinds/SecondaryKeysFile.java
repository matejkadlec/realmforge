package io.github.matejkadlec.betterkeybinds;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Reads and writes the secondary key file, {@code config/better_key_binds-secondary_keys.txt}: one key bind per line,
 * {@code <key bind>:<key>[:<modifier>]}, for example {@code key.chat:key.keyboard.t} or
 * {@code key.inventory:key.keyboard.e:SHIFT}. The key bind is its name as in {@code options.txt} (without
 * {@code key_}), the key a key name ({@code InputConstants.Key#getName}), the modifier one of NeoForge's
 * {@code KeyModifier} names. Blank lines and lines starting with {@code #} are comments. Plain strings only, so it
 * has no Minecraft types and is tested on its own; {@link SecondaryKeys} turns the strings into keys.
 */
public final class SecondaryKeysFile {
    /** The names of NeoForge's {@code KeyModifier} constants, the values a line's third part can take. */
    static final Set<String> MODIFIERS = Set.of("CONTROL", "CONTROL_OR_COMMAND", "SHIFT", "ALT", "NONE");
    static final String NO_MODIFIER = "NONE";

    private static final List<String> HEADER = List.of(
            "# Better Key Binds: secondary keys, one key bind per line: <key bind>:<key>[:<modifier>]",
            "# <key bind> is its name as in options.txt (without key_), <key> a key name such as key.keyboard.t or",
            "# key.mouse.4, <modifier> CONTROL, SHIFT or ALT. Key binds without a secondary key are left out.");

    private SecondaryKeysFile() {
    }

    /** A secondary key as written in the file: its key name and its modifier name. */
    public record Binding(String key, String modifier) {
    }

    /**
     * The bindings in the file's lines, by key bind name, in file order. A line without a colon is skipped; a later
     * line for the same key bind wins.
     */
    public static Map<String, Binding> parse(List<String> lines) {
        Map<String, Binding> bindings = new LinkedHashMap<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            // Key names hold no colon, key bind names may: split from the right.
            int last = line.lastIndexOf(':');
            if (last <= 0 || last == line.length() - 1) {
                continue;
            }
            String tail = line.substring(last + 1);
            String rest = line.substring(0, last);
            int middle = rest.lastIndexOf(':');
            if (MODIFIERS.contains(tail) && middle > 0) {
                bindings.put(rest.substring(0, middle), new Binding(rest.substring(middle + 1), tail));
            } else {
                bindings.put(rest, new Binding(tail, NO_MODIFIER));
            }
        }
        return bindings;
    }

    /** The file's lines for these bindings: the header, then one line per key bind, sorted by name. */
    public static List<String> write(Map<String, Binding> bindings) {
        List<String> lines = new ArrayList<>(HEADER);
        new TreeMap<>(bindings).forEach((name, binding) -> lines.add(NO_MODIFIER.equals(binding.modifier())
                ? name + ":" + binding.key()
                : name + ":" + binding.key() + ":" + binding.modifier()));
        return lines;
    }
}
