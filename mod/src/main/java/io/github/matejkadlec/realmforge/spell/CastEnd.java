package io.github.matejkadlec.realmforge.spell;

import net.minecraft.util.StringRepresentable;

/**
 * How a cast-time or channelled spell ended (architecture 11.2): it completed, something interrupted it, or the check
 * before its effects failed. Every end but {@link #COMPLETED} runs no more effects: a cast time keeps the cost it paid
 * when it began, without its cooldown, and a channel keeps the ticks it already ran and the seconds it paid for. C3b's
 * cast bar shows the end ({@code CastEndedPayload}); its texts come with the spell bar (RLM-25).
 */
public enum CastEnd implements StringRepresentable {
    /** The cast time is over and the effects ran, or the channel ran every tick. */
    COMPLETED("completed", false),
    /** A spell's knockback pushed the caster ({@code realmforge:knockback}), and the spell may not be cast while moving. */
    KNOCKED_BACK("knocked_back", true),
    /** The caster cancelled it (the spell bar's cancel, C3b; {@code /qa spell cancel}), or it was removed. */
    CANCELLED("cancelled", true),
    /** The caster died. */
    DIED("died", true),
    /** The caster changed dimension. */
    CHANGED_DIMENSION("changed_dimension", true),
    /** The caster logged out. */
    LOGGED_OUT("logged_out", true),
    /** The target died or is gone. */
    TARGET_LOST("target_lost", false),
    /** The target moved beyond the spell's range, or into another dimension. */
    OUT_OF_RANGE("out_of_range", false),
    /** Blocks came between the caster and the target. */
    NO_LINE_OF_SIGHT("no_line_of_sight", false),
    /** The target is no longer one the spell may hit (it joined the caster's team, say). */
    INVALID_TARGET("invalid_target", false),
    /** The caster has less of the spell's resource than a channel's next second costs. */
    NOT_ENOUGH_RESOURCE("not_enough_resource", false),
    /** A stun (C4) interrupted it, whether the spell may be cast while moving or not. */
    STUNNED("stunned", true);

    private final String serializedName;
    private final boolean interrupt;

    CastEnd(String serializedName, boolean interrupt) {
        this.serializedName = serializedName;
        this.interrupt = interrupt;
    }

    /** The end that a check failing with {@code failure} before the effects means. */
    public static CastEnd of(CastFailure failure) {
        return switch (failure) {
            case OUT_OF_RANGE -> OUT_OF_RANGE;
            case NO_LINE_OF_SIGHT -> NO_LINE_OF_SIGHT;
            case NOT_ENOUGH_RESOURCE -> NOT_ENOUGH_RESOURCE;
            case NO_TARGET -> TARGET_LOST;
            case INVALID_TARGET -> INVALID_TARGET;
            default -> CANCELLED;
        };
    }

    /** Whether it ran its effects to the end. */
    public boolean completed() {
        return this == COMPLETED;
    }

    /** Whether the caster's side ended it (moving, dying, ...), not a failed check before the effects. */
    public boolean interrupt() {
        return interrupt;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
