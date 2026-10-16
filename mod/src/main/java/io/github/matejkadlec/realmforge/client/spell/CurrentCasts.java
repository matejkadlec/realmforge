package io.github.matejkadlec.realmforge.client.spell;

import io.github.matejkadlec.realmforge.spell.CombatState.Cast;
import io.github.matejkadlec.realmforge.spell.Spell;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;

/**
 * The casts in progress that this client knows of, by caster entity id (architecture 11.6, 17.2): what {@code
 * CastStartedPayload} began and {@code CastEndedPayload} has not ended yet. Without world access, so JUnit covers it.
 *
 * <p>A player who stops tracking a caster gets no end for its cast, so a cast whose time is over by more than
 * {@value #GRACE_TICKS} ticks counts as ended and is dropped.
 */
public final class CurrentCasts {
    /** How long after its end a cast whose end never arrived is still kept, in ticks. */
    public static final int GRACE_TICKS = 20;

    private final Map<Integer, Cast> casts = new HashMap<>();

    /** {@code casterId} began {@code cast}, replacing any cast it had. */
    public void start(int casterId, Cast cast) {
        casts.put(casterId, cast);
    }

    /** {@code casterId}'s cast of {@code spell} ended; a newer cast of another spell stays. */
    public void end(int casterId, ResourceKey<Spell> spell) {
        casts.computeIfPresent(casterId, (id, cast) -> cast.spell().equals(spell) ? null : cast);
    }

    /** {@code casterId}'s cast in progress at game time {@code gameTime}, if any. */
    public Optional<Cast> get(int casterId, long gameTime) {
        Cast cast = casts.get(casterId);
        if (cast != null && gameTime > cast.endGameTime() + GRACE_TICKS) {
            casts.remove(casterId);
            return Optional.empty();
        }
        return Optional.ofNullable(cast);
    }

    /** How far the cast is, from 0 at its start to 1 at its end, at game time {@code gameTime} (partial ticks too). */
    public static float progress(Cast cast, double gameTime) {
        double elapsed = gameTime - cast.startGameTime();
        return (float) Math.clamp(elapsed / cast.durationTicks(), 0.0, 1.0);
    }

    public void clear() {
        casts.clear();
    }

    public int size() {
        return casts.size();
    }
}
