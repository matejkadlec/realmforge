package io.github.matejkadlec.realmforge.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/**
 * Our key mappings: in game only, and labelled through {@link KeyLabels}, so a key on the section sign shows in Options
 * > Controls and on the spell bar like any other.
 */
public class RealmforgeKeyMapping extends KeyMapping {
    public RealmforgeKeyMapping(String name, int defaultKey, Category category) {
        super(name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, defaultKey, category);
    }

    @Override
    public Component getTranslatedKeyMessage() {
        return KeyLabels.visible(super.getTranslatedKeyMessage());
    }
}
