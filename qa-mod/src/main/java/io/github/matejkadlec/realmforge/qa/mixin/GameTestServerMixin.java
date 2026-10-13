package io.github.matejkadlec.realmforge.qa.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.WorldLoader;
import net.minecraft.world.level.dimension.LevelStem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Gives the GameTest server the dimensions from data packs. Vanilla builds the GameTest world from the flat
 * preset and an empty dimension registry ({@code GameTestServer#create}), so {@code realmforge:instances} and every
 * later dimension of ours would be missing in GameTests. A normal world bakes the preset with the data pack
 * dimensions ({@code Main}, {@code WorldOpenFlows}); this does the same. No event or API covers it, hence a mixin
 * (architecture 19.3); the instance GameTests fail without it.
 */
@Mixin(GameTestServer.class)
abstract class GameTestServerMixin {
    @ModifyArg(
            method = "lambda$create$1",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/WorldDimensions;bake(Lnet/minecraft/core/Registry;)"
                            + "Lnet/minecraft/world/level/levelgen/WorldDimensions$Complete;"))
    private static Registry<LevelStem> realmforgeQa$bakeWithDataPackDimensions(
            Registry<LevelStem> empty, @Local(argsOnly = true) WorldLoader.DataLoadContext context) {
        return context.datapackDimensions().lookupOrThrow(Registries.LEVEL_STEM);
    }
}
