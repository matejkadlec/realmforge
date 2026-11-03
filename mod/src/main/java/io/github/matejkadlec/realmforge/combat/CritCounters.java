package io.github.matejkadlec.realmforge.combat;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * A player's two counts of crit rolls without a crit since the last one (the {@code realmforge:crit_counters}
 * attachment; RLM-526, architecture 9.1): one for weapon attacks (melee and ranged hits), one for spells (school hits
 * and heals), each the N of {@link CritDistribution}. One counter per player, not per target (Matěj, 2026-10-30). A
 * change of crit chance keeps the count, and the next roll uses the new chance's C. Mobs roll plain chance.
 *
 * @param attackMisses weapon attack rolls since the last attack crit
 * @param spellMisses spell rolls since the last spell crit
 */
public record CritCounters(int attackMisses, int spellMisses) {
    /** No roll yet: every new player entity, a login or a respawn included. */
    public static final CritCounters NONE = new CritCounters(0, 0);

    /** Which counter a roll advances. */
    public enum Kind {
        /** A melee hit or a projectile. */
        ATTACK,
        /** A school hit or a heal. */
        SPELL
    }

    /** The misses counted for {@code kind}. */
    public int misses(Kind kind) {
        return kind == Kind.ATTACK ? attackMisses : spellMisses;
    }

    /** These counts after a roll of {@code kind}: a crit starts its count again, a miss adds one. */
    public CritCounters after(Kind kind, boolean crit) {
        int misses = crit ? 0 : misses(kind) + 1;
        return kind == Kind.ATTACK ? new CritCounters(misses, spellMisses) : new CritCounters(attackMisses, misses);
    }

    /**
     * Rolls a crit of {@code kind} for {@code roller} on {@code chance}: a player's by its counter
     * ({@link CritDistribution}), which the roll advances; anyone else's on the plain chance. A chance of 0 or less is
     * no roll and leaves the counter as it is.
     */
    public static boolean roll(LivingEntity roller, Kind kind, double chance, RandomSource random) {
        if (!(roller instanceof Player player)) {
            return DamageCalculator.crits(chance, random);
        }
        if (!(chance > 0)) {
            return false;
        }
        CritCounters counters = player.getData(CombatAttachments.CRIT_COUNTERS);
        boolean crit = CritDistribution.rolls(chance, counters.misses(kind), random);
        player.setData(CombatAttachments.CRIT_COUNTERS, counters.after(kind, crit));
        return crit;
    }

    /** How {@code roller}'s crits of {@code kind} roll, for {@link DamageCalculator#crit}. */
    public static DamageCalculator.CritRoll roller(LivingEntity roller, Kind kind, RandomSource random) {
        return chance -> roll(roller, kind, chance, random);
    }
}
