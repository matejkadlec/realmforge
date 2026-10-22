package io.github.matejkadlec.betterkeybinds;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.matejkadlec.betterkeybinds.mixin.ToggleKeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.ToggleKeyMapping;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;

/**
 * The secondary key of one key bind (its primary): a hidden key mapping with a key and a modifier of its own and its
 * primary's conflict context. Being a real {@link KeyMapping}, it is in vanilla's key lookup, so a key press reaches
 * it exactly as it reaches a primary key: NeoForge's modifier and conflict context rules, mouse buttons, the release
 * of every key when a screen opens and the re-check of held keys when it closes. It is never in
 * {@code Options.keyMappings}: the Key Binds screen and {@code options.txt} only see primaries. Its primary reads it
 * through {@link SecondaryKeys}: {@code isDown()} holds while either key is held, {@code consumeClick()} counts the
 * clicks of both.
 */
public final class SecondaryKeyMapping extends KeyMapping {
    /** Prefixed to the primary's name, so the hidden mapping never takes a real key bind's place in vanilla's map. */
    static final String NAME_PREFIX = "better_key_binds.secondary/";

    private final KeyMapping primary;

    SecondaryKeyMapping(KeyMapping primary) {
        super(NAME_PREFIX + primary.getName(), KeyConflictContext.UNIVERSAL, KeyModifier.NONE, InputConstants.UNKNOWN,
                primary.getCategory());
        this.primary = primary;
    }

    public KeyMapping primary() {
        return primary;
    }

    /** Always the primary's, which a mod may change at any time. */
    @Override
    public IKeyConflictContext getKeyConflictContext() {
        // Null only while KeyMapping's constructor runs, before this class's field is set.
        return primary == null ? super.getKeyConflictContext() : primary.getKeyConflictContext();
    }

    @Override
    public void setKeyConflictContext(IKeyConflictContext keyConflictContext) {
        // Follows the primary's (getKeyConflictContext).
    }

    /**
     * A toggle key in toggle mode (Sneak and Sprint set to Toggle) flips its primary on each press, as its own key
     * does; otherwise the secondary key is held or released like any key, and its primary reads that.
     */
    @Override
    public void setDown(boolean down) {
        if (togglesPrimary()) {
            if (down) {
                primary.setDown(true);
            }
        } else {
            super.setDown(down);
        }
    }

    /** Like a toggle key's own: a key re-checked as a screen closes must not flip a toggle. */
    @Override
    protected boolean shouldSetOnIngameFocus() {
        return super.shouldSetOnIngameFocus() && !togglesPrimary();
    }

    /** "Open Chat (secondary)", for conflict lists. */
    @Override
    public Component getDisplayName() {
        return Component.translatable("better_key_binds.secondary_of", primary.getDisplayName());
    }

    private boolean togglesPrimary() {
        // The accessor is a mixin interface, which only a toggle key mapping implements: check the class first.
        return primary instanceof ToggleKeyMapping
                && ((ToggleKeyMappingAccessor) primary).betterKeyBinds$needsToggle().getAsBoolean();
    }
}
