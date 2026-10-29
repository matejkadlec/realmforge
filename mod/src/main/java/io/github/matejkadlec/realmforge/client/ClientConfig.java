package io.github.matejkadlec.realmforge.client;

import io.github.matejkadlec.realmforge.client.hud.SpellBarLayout;
import io.github.matejkadlec.realmforge.client.screen.SpellOptionsScreen;
import io.github.matejkadlec.realmforge.client.spell.CastQueue;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The client options (architecture 19.2: UI preferences only), in {@code config/realmforge-client.toml}: how the spell
 * bar's presses wait (Matěj, 2026-10-19, RLM-25), and where the spell bar and resource bar group goes and how big it is
 * (RLM-404). {@link SpellOptionsScreen} edits them, from the spellbook and from the mod list's Config button.
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

    /** The group's place across the screen: a fraction of the room the screen leaves it, -1 for the default place. */
    public static final ModConfigSpec.DoubleValue SPELL_BAR_X = BUILDER
            .comment("Where the spell bar and resource bar go across the screen: 0 at the left edge, 1 at the right, "
                    + "-1 for the default place. Set by Spellbar Position.")
            .translation(GeneratedTexts.GUI_SPELL_BAR_POSITION)
            .defineInRange("spellBarX", SpellBarLayout.DEFAULT_PLACE, SpellBarLayout.DEFAULT_PLACE, 1);

    /** The group's place down the screen: a fraction of the room the screen leaves it, -1 for the default place. */
    public static final ModConfigSpec.DoubleValue SPELL_BAR_Y = BUILDER
            .comment("Where the spell bar and resource bar go down the screen: 0 at the top, 1 at the bottom, -1 for "
                    + "the default place. Set by Spellbar Position.")
            .translation(GeneratedTexts.GUI_SPELL_BAR_POSITION)
            .defineInRange("spellBarY", SpellBarLayout.DEFAULT_PLACE, SpellBarLayout.DEFAULT_PLACE, 1);

    /** The group's width as a share of its width at size 1, its height scaling with it. */
    public static final ModConfigSpec.DoubleValue SPELL_BAR_SIZE = BUILDER
            .comment("The spell bar and resource bar's size, 0.5 to 1.5: their width, their height scaling with it. "
                    + "Ignored while mirrorHotbarWidth is on.")
            .translation(GeneratedTexts.GUI_SPELL_BAR_SIZE)
            .defineInRange("spellBarSize", 1.0, SpellBarLayout.MIN_SIZE, SpellBarLayout.MAX_SIZE);

    /** On: the group is exactly as wide as the hotbar, whatever the size. */
    public static final ModConfigSpec.BooleanValue MIRROR_HOTBAR_WIDTH = BUILDER
            .comment("Whether the spell bar and resource bar are exactly as wide as the hotbar (true), their height "
                    + "keeping the ratio, instead of spellBarSize (false).")
            .translation(GeneratedTexts.GUI_MIRROR_HOTBAR_WIDTH)
            .define("mirrorHotbarWidth", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }

    /** Called once from {@code RealmforgeClient}. */
    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (mod, parent) -> new SpellOptionsScreen(parent));
    }

    /** The spell bar and resource bar group's scale as the options set it. */
    public static float spellBarScale() {
        return SpellBarLayout.scale(SPELL_BAR_SIZE.get(), MIRROR_HOTBAR_WIDTH.get());
    }

    /**
     * The spell bar and resource bar group where the options put it, on a screen {@code guiWidth} by {@code guiHeight}.
     */
    public static SpellBarLayout.Group spellBarGroup(int guiWidth, int guiHeight) {
        return SpellBarLayout.place(guiWidth, guiHeight, SPELL_BAR_X.get(), SPELL_BAR_Y.get(), spellBarScale());
    }

    /** The cast queue's options as they are now. */
    public static CastQueue.Options castOptions() {
        return new CastQueue.Options(QUEUE_SPELL_CASTS.get(), SPELLS_QUEUE_SIZE.get());
    }
}
