package io.github.matejkadlec.realmforge.qa.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import io.github.matejkadlec.realmforge.qa.ProfileLookupRetry;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Retries the dev client's profile lookup at startup ({@link ProfileLookupRetry}, RLM-570), so a session server
 * timeout no longer costs the account's skin for the session. {@code Minecraft}'s constructor runs the lookup once in
 * {@code lambda$new$0} on an IO thread, and no event or API reaches it, hence a mixin (architecture 19.3). A client
 * mixin ({@code realmforge_qa.mixins.json}'s {@code client} list), named by its target's string, so this class
 * imports no client code; the offline client never runs the lambda.
 */
@Mixin(targets = "net.minecraft.client.Minecraft")
abstract class MinecraftProfileLookupMixin {
    @WrapOperation(
            method = "lambda$new$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/authlib/minecraft/MinecraftSessionService;fetchProfile(Ljava/util/UUID;Z)"
                            + "Lcom/mojang/authlib/yggdrasil/ProfileResult;"))
    private ProfileResult realmforgeQa$retryProfileLookup(
            MinecraftSessionService service, UUID profileId, boolean requireSecure,
            Operation<ProfileResult> lookup) {
        return ProfileLookupRetry.fetch(() -> lookup.call(service, profileId, requireSecure), Thread::sleep);
    }
}
