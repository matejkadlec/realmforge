package io.github.matejkadlec.realmforge.combat;

/**
 * What one school hit adds to its attacker's own stats (C4; architecture 9.1): a spell's per-rank extra armor shred
 * (Slam) and spell penetration (Arcane Missiles), and the damage its caster's passive spells add to it (RLM-541:
 * Improved Frostbolt's 15 %). {@link SchoolDamageSource} carries it to the damage pipeline, which adds the armor shred to
 * the attacker's own, lowers the defender's resistance by the penetration and adds the damage to the hit's
 * (1 + School Power + passives) layer.
 *
 * @param armorShred armor shred added to the attacker's, a share of the armor its armor pierce leaves
 * @param penetration the share of the defender's resistance to the school the hit ignores
 * @param damage the share the hit's damage grows by, added to the school's power, 0 or more
 */
public record HitBonus(double armorShred, double penetration, double damage) {
    /** Nothing added: every hit but a spell's with a bonus. */
    public static final HitBonus NONE = new HitBonus(0, 0, 0);

    public HitBonus {
        if (!(armorShred >= 0 && armorShred <= 1) || !(penetration >= 0 && penetration <= 1)) {
            throw new IllegalArgumentException("Armor shred and penetration are shares from 0 to 1: " + armorShred
                    + ", " + penetration);
        }
        if (!(damage >= 0) || !Double.isFinite(damage)) {
            throw new IllegalArgumentException("A damage bonus is a share of 0 or more: " + damage);
        }
    }

    /** A bonus of armor shred and spell penetration only. */
    public HitBonus(double armorShred, double penetration) {
        this(armorShred, penetration, 0);
    }

    /** This bonus with {@code more} damage added. */
    public HitBonus withDamage(double more) {
        return more == 0 ? this : new HitBonus(armorShred, penetration, damage + more);
    }
}
