package io.github.matejkadlec.realmforge.mixin;

import io.github.matejkadlec.realmforge.dungeon.instance.InstanceRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets a player in Adventure mode break the blocks an instance allows ({@code dungeon/instance/InstanceRules#mayBreak},
 * architecture 15.5, ADR 0055). Adventure mode refuses a break in {@link Player#blockActionRestricted} on the server
 * ({@code ServerPlayerGameMode}) and in the client's prediction ({@code MultiPlayerGameMode}), before NeoForge posts
 * {@code BreakBlockEvent}, whose un-cancelling does not undo it; only a {@code can_break} component on the held item
 * gets past it, never an empty hand. No event or API changes that, hence a mixin (architecture 19.3). Tested by the QA
 * mod's {@code InstanceRulesGameTests}, which break nothing without it.
 */
@Mixin(Player.class)
public abstract class PlayerBlockActionMixin {
    @Inject(method = "blockActionRestricted", at = @At("HEAD"), cancellable = true)
    private void realmforge$breakInInstances(Level level, BlockPos pos, GameType gameType,
            CallbackInfoReturnable<Boolean> result) {
        if (InstanceRules.mayBreak(level, pos, gameType)) {
            result.setReturnValue(false);
        }
    }
}
