package io.github.matejkadlec.realmforge.qa.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets the QA mod's screenshot runs put the game's cursor over a widget, so a frame draws its tooltip: under Xvfb,
 * moving the real cursor reaches no window callback (RLM-333). Its target is a name, as this package is outside
 * {@code client/}; the mixin config loads it on clients only.
 */
@Mixin(targets = "net.minecraft.client.MouseHandler")
public interface MouseHandlerAccessor {
    @Accessor("xpos")
    void realmforgeQa$setXpos(double xpos);

    @Accessor("ypos")
    void realmforgeQa$setYpos(double ypos);
}
