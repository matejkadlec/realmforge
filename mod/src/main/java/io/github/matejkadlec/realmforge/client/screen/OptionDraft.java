package io.github.matejkadlec.realmforge.client.screen;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * One option's value while the Realmforge config screen is open (RLM-495, {@link RealmforgeConfigScreen}): the screen
 * edits the draft, Reset puts it back to the option's default, Save &amp; Quit writes every changed draft into the
 * client config, and Cancel drops them all, so the config keeps its values.
 *
 * @param <T> the option's value
 */
public final class OptionDraft<T> {
    private final T defaultValue;
    private final Supplier<T> saved;
    private final Consumer<T> save;
    private T value;

    public OptionDraft(T defaultValue, Supplier<T> saved, Consumer<T> save) {
        this.defaultValue = defaultValue;
        this.saved = saved;
        this.save = save;
        this.value = saved.get();
    }

    /** The draft of one client config value. */
    public static <T> OptionDraft<T> of(ModConfigSpec.ConfigValue<T> config) {
        return new OptionDraft<>(config.getDefault(), config::get, config::set);
    }

    public T value() {
        return value;
    }

    public void set(T value) {
        this.value = value;
    }

    /** Reset: the option's default. */
    public void reset() {
        value = defaultValue;
    }

    public boolean isDefault() {
        return Objects.equals(value, defaultValue);
    }

    /** Whether the draft differs from the value the config holds. */
    public boolean changed() {
        return !Objects.equals(value, saved.get());
    }

    /** Writes the draft into the config when it changed; the caller saves the config's file. */
    public void save() {
        if (changed()) {
            save.accept(value);
        }
    }

    /** The search bar's filter: a row shows while its name contains the query, ignoring case; an empty query shows all. */
    public static boolean matches(String name, String query) {
        String wanted = query.strip().toLowerCase(Locale.ROOT);
        return wanted.isEmpty() || name.toLowerCase(Locale.ROOT).contains(wanted);
    }
}
