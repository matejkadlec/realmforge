package io.github.matejkadlec.realmforge.combat;

/**
 * What one school hit adds to its attacker's own stats (C4; architecture 9.1): a spell's per-rank extra armor shred
 * (Slam) and spell penetration (Arcane Missiles). {@link SchoolDamageSource} carries it to the damage pipeline, which
 * adds the armor shred to the attacker's own and lowers the defender's resistance by the penetration.
 *
 * @param armorShred armor shred added to the attacker's, a share of the armor its armor pierce leaves
 * @param penetration the share of the defender's resistance to the school the hit ignores
 */
public record HitBonus(double armorShred, double penetration) {
    /** Nothing added: every hit but a spell's with a bonus. */
    public static final HitBonus NONE = new HitBonus(0, 0);

    public HitBonus {
        if (!(armorShred >= 0 && armorShred <= 1) || !(penetration >= 0 && penetration <= 1)) {
            throw new IllegalArgumentException("Armor shred and penetration are shares from 0 to 1: " + armorShred
                    + ", " + penetration);
        }
    }
}
