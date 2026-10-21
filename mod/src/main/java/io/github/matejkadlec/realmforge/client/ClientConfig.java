package io.github.matejkadlec.realmforge.client;

import io.github.matejkadlec.realmforge.client.screen.SpellOptionsScreen;
import io.github.matejkadlec.realmforge.client.spell.CastQueue;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The client options (architecture 19.2: UI preferences only), in {@code config/realmforge-client.toml}: how the spell
 * bar's presses wait (Matěj, 2026-10-19, RLM-25). {@link SpellOptionsScreen} edits them, from the spellbook and from
 * the mod list's Config button.
 */
public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** Off (the default): a spell pressed during another spell's cast cancels it and casts at once. */
    public static final ModConfigSpec.BooleanValue QUEUE_SPELL_CASTS = BUILDER
            .comment("Whether a spell pressed during another spell's cast waits for it to end (true), or cancels it "
                    + "and casts at once (false).")
            .translation(GeneratedTexts.GUI_QUEUE_SPELL_CASTS)
            .define("queueSpellCasts", false);

    /** How many presses wait while Queue Spell Casts is on: at 1 a new press replaces the waiting one. */
    public static final ModConfigSpec.IntValue SPELLS_QUEUE_SIZE = BUILDER
            .comment("How many spells can wait while queueSpellCasts is on. At 1 a newer spell replaces the waiting "
                    + "one; above 1 it waits after them.")
            .translation(GeneratedTexts.GUI_SPELLS_QUEUE_SIZE)
            .defineInRange("spellsQueueSize", 1, 1, CastQueue.MAX_SIZE);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }

    /** Called once from {@code RealmforgeClient}. */
    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (mod, parent) -> new SpellOptionsScreen(parent));
    }

    /** The cast queue's options as they are now. */
    public static CastQueue.Options castOptions() {
        return new CastQueue.Options(QUEUE_SPELL_CASTS.get(), SPELLS_QUEUE_SIZE.get());
    }
}
