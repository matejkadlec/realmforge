package io.github.matejkadlec.realmforge.loot;

import io.github.matejkadlec.realmforge.core.CoreRegistries;
import io.github.matejkadlec.realmforge.core.Curve;
import io.github.matejkadlec.realmforge.generated.GeneratedCurves;
import io.github.matejkadlec.realmforge.item.Coins;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import io.github.matejkadlec.realmforge.stats.MobRanks;
import io.github.matejkadlec.realmforge.stats.StatsAttachments;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * Mob coin drops (architecture 13; RLM-286, ADR 0072): a hostile mob a player killed drops coins by its level and
 * rank ({@link CoinDropMath}), paid in the largest coins ({@link Coins#stacks}), next to its own loot. The amount is
 * our code's, not a loot table's (Matěj, 2026-10-21): neither Looting nor any luck attribute is read, so nothing
 * raises it. A drop needs what vanilla's experience orbs need: a player's kill credit (a hit of theirs within the last
 * 5 seconds, {@code LivingDropsEvent#isRecentlyHit}) and the gamerule {@code mob_drops}; so a mob that falls to its
 * death in a farm, or one another mob killed, leaves no coins. Babies drop as their parents do, as with experience.
 */
public final class CoinDrops {
    private CoinDrops() {
    }

    static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim instanceof Enemy) || !(victim.level() instanceof ServerLevel level) || !event.isRecentlyHit()
                || !level.getGameRules().get(GameRules.MOB_DROPS)) {
            return;
        }
        Optional<Curve> curve = level.registryAccess().lookupOrThrow(CoreRegistries.CURVE)
                .get(GeneratedCurves.COIN_DROP).map(Holder::value);
        if (curve.isEmpty()) {
            return;
        }
        int mobLevel = victim.getExistingData(StatsAttachments.LEVEL).map(EntityLevel::value).orElse(EntityLevel.MIN);
        long amount = CoinDropMath.amount(curve.get().valueAt(mobLevel), MobRanks.of(victim),
                victim.getRandom().nextDouble());
        for (ItemStack stack : Coins.stacks(amount)) {
            ItemEntity drop = new ItemEntity(level, victim.getX(), victim.getY(), victim.getZ(), stack);
            drop.setDefaultPickUpDelay();
            event.getDrops().add(drop);
        }
    }
}
