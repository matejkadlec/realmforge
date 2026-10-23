package io.github.matejkadlec.betterkeybinds.mixin;

import java.util.function.BooleanSupplier;
import net.minecraft.client.ToggleKeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Whether a toggle key mapping is in toggle mode right now, which it keeps private ({@code SecondaryKeyMapping}). */
@Mixin(ToggleKeyMapping.class)
public interface ToggleKeyMappingAccessor {
    @Accessor("needsToggle")
    BooleanSupplier betterKeyBinds$needsToggle();
}
