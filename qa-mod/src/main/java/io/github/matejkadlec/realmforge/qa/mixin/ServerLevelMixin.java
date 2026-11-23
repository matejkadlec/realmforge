package io.github.matejkadlec.realmforge.qa.mixin;

import io.github.matejkadlec.realmforge.qa.instance.HeldBackEntities;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes {@code ServerLevel#areEntitiesLoaded} read false for a chunk a GameTest holds back ({@link HeldBackEntities}),
 * as while its entities still load in the background. The entity manager's loading runs on an IO thread, so a test
 * cannot otherwise make it take as long as it needs; no event or API covers it, hence a mixin (architecture 19.3).
 * Used by {@code InstanceGameTests} (RLM-651).
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {
    @Inject(method = "areEntitiesLoaded", at = @At("HEAD"), cancellable = true)
    private void realmforgeQa$holdBackEntities(long chunkKey, CallbackInfoReturnable<Boolean> callback) {
        if (HeldBackEntities.heldBack((ServerLevel) (Object) this, chunkKey)) {
            callback.setReturnValue(false);
        }
    }
}
