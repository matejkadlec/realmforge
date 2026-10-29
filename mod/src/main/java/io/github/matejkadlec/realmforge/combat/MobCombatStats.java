package io.github.matejkadlec.realmforge.combat;

import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;

/**
 * The stats of ours every mob has, at their base values, because its gear and Apotheosis's world tier augments can
 * raise them and the damage pipeline reads them on any attacker (architecture 8.1 and 9.1; Matěj, 2026-10-29,
 * RLM-272): armor pierce, armor shred and lifesteal. Players have every stat already ({@code StatsFeature}); a mob
 * without one of them simply has none of it ({@link DamagePipeline#stat}), and Apotheosis skips an augment whose
 * attribute a mob lacks. Mob crits wait for their own ticket.
 */
public final class MobCombatStats {
    /** What the mobs get, in the order they are added. */
    public static final List<Holder<Attribute>> STATS =
            List.of(GeneratedStats.ARMOR_PIERCE, GeneratedStats.ARMOR_SHRED, GeneratedStats.LIFESTEAL);

    private MobCombatStats() {
    }

    /** Adds {@link #STATS} to every living entity type but the player, custom mobs included. */
    static void addToMobs(EntityAttributeModificationEvent event) {
        for (EntityType<? extends LivingEntity> type : event.getTypes()) {
            if (type == EntityType.PLAYER) {
                continue;
            }
            for (Holder<Attribute> stat : STATS) {
                if (!event.has(type, stat)) {
                    event.add(type, stat);
                }
            }
        }
    }
}
