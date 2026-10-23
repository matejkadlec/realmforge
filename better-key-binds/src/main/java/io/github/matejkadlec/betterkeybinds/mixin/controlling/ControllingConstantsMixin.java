package io.github.matejkadlec.betterkeybinds.mixin.controlling;

import com.blamejared.controlling.api.entries.IKeyEntry;
import com.blamejared.searchables.api.SearchableComponent;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.matejkadlec.betterkeybinds.SecondaryKeyMapping;
import io.github.matejkadlec.betterkeybinds.SecondaryKeys;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Controlling's search by key ({@code key:T}, Searchables' syntax) also finds a key bind by its secondary key. The
 * search's components are built once, in this class's static initializer, from a function that only reads the primary
 * key; the {@code key} component gets a filter that reads both keys, while its suggestions stay the primary keys.
 * Applied only when Controlling is loaded.
 */
@Pseudo
@Mixin(targets = "com.blamejared.controlling.ControllingConstants")
public abstract class ControllingConstantsMixin {
    @WrapOperation(method = "<clinit>", at = @At(value = "INVOKE",
            target = "Lcom/blamejared/searchables/api/SearchableComponent;create(Ljava/lang/String;Ljava/util/function/Function;)Lcom/blamejared/searchables/api/SearchableComponent;"))
    private static SearchableComponent<KeyBindsList.Entry> betterKeyBinds$searchSecondaryKeys(String key,
            Function<KeyBindsList.Entry, Optional<String>> toString,
            Operation<SearchableComponent<KeyBindsList.Entry>> original) {
        if (!"key".equals(key)) {
            return original.call(key, toString);
        }
        return SearchableComponent.create(key, toString, (entry, search) ->
                toString.apply(entry).map(primary -> betterKeyBinds$contains(primary, search)).orElse(false)
                        || betterKeyBinds$secondaryKeyMatches(entry, search));
    }

    @Unique
    private static boolean betterKeyBinds$secondaryKeyMatches(KeyBindsList.Entry entry, String search) {
        if (!(entry instanceof IKeyEntry keyEntry)) {
            return false;
        }
        SecondaryKeyMapping secondary = SecondaryKeys.of(keyEntry.getKey());
        return secondary != null && !secondary.isUnbound()
                && betterKeyBinds$contains(secondary.getTranslatedKeyMessage().getString(), search);
    }

    // Case-insensitive, as Searchables matches the other components.
    @Unique
    private static boolean betterKeyBinds$contains(String text, String search) {
        return text.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT));
    }
}
