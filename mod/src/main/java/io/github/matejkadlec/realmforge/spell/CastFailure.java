package io.github.matejkadlec.realmforge.spell;

import net.minecraft.util.StringRepresentable;

/**
 * Why a cast was refused (architecture 11.2; research SP-3: say why a cast fails). A refused cast pays nothing, starts
 * no cooldown and runs no effect. C3b shows the reason; its texts come with the spell bar (RLM-25).
 */
public enum CastFailure implements StringRepresentable {
    /** The caster is dead or a spectator. */
    CANNOT_CAST("cannot_cast"),
    /** No spell has this id (content removed, or a typo in a command). */
    UNKNOWN_SPELL("unknown_spell"),
    /** A mob or boss spell, which no player may cast. */
    NPC_ONLY("npc_only"),
    /** The player has not learned the spell. */
    NOT_KNOWN("not_known"),
    /** The spell's own cooldown still runs. */
    ON_COOLDOWN("on_cooldown"),
    /** The global cooldown still runs, and the spell is on it. */
    ON_GLOBAL_COOLDOWN("on_global_cooldown"),
    /** The spell needs a target and has none. */
    NO_TARGET("no_target"),
    /** The target is not one the spell may hit: an ally for a harmful spell, an enemy for a helpful one, dead. */
    INVALID_TARGET("invalid_target"),
    /** The target is beyond the spell's range, or in another dimension. */
    OUT_OF_RANGE("out_of_range"),
    /** Blocks stand between the caster and the target. */
    NO_LINE_OF_SIGHT("no_line_of_sight"),
    /** The caster has less of the spell's resource than it costs. */
    NOT_ENOUGH_RESOURCE("not_enough_resource");

    private final String serializedName;

    CastFailure(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
