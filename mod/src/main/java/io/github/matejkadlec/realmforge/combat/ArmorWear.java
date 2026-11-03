package io.github.matejkadlec.realmforge.combat;

import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;

/**
 * How much a hit wears its target's armor (RLM-580; Matěj's playtest of 2026-11-03: Astone should ignore armor, not
 * destroy it): the attacker's {@code realmforge:armor_wear_reduction} lowers the durability each piece loses. Vanilla
 * takes a quarter of the hit from each piece, so a mob with big hits wears armor fast; the hit's damage stays as it is.
 */
final class ArmorWear {
    private ArmorWear() {
    }

    static void onArmorHurt(ArmorHurtEvent event) {
        if (event.getDamageSource().getEntity() instanceof LivingEntity attacker) {
            double kept = 1 - DamagePipeline.stat(attacker, GeneratedStats.ARMOR_WEAR_REDUCTION);
            event.getArmorMap().values().forEach(entry -> entry.newDamage = (float) (entry.newDamage * kept));
        }
    }
}
