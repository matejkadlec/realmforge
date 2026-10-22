package io.github.matejkadlec.realmforge.client.spell;

import io.github.matejkadlec.realmforge.spell.Spell;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import net.minecraft.resources.ResourceKey;

/**
 * What the spell bar's keys ask the server to cast (C3b, RLM-25; Matěj, 2026-10-19), decided once a client tick
 * without world access, so JUnit covers it. The server checks every cast; this only keeps the client from asking for
 * what cannot work yet.
 *
 * <ul>
 *   <li><b>A press</b> waits in the queue. Without spell queueing (the default, {@code Queue Spell Casts = false}) the
 *   queue holds only the latest press, which goes at once and may cancel another spell's cast in progress
 *   ({@code replaceCast}); it waits only for the global cooldown, at most half a second, so a press just before the
 *   global cooldown ends is not refused. With spell queueing, a press waits for the cast in progress to end too, and
 *   up to {@code Spells Queue Size} presses wait in order: at size 1 a new press replaces the waiting one, above 1 it
 *   waits after them (when the queue is full, the newest waiting press gives way to it). Pressing the spell being cast
 *   does nothing.</li>
 *   <li><b>A held key</b> casts its spell again and again, as its cooldown and cast time allow, at most every
 *   {@value #REPEAT_TICKS} ticks (half a second), and never cancels a cast.</li>
 *   <li><b>After each send</b> the client waits {@value #AWAIT_TICKS} ticks for the server's answer (the cast, the
 *   cooldowns, a refusal) before it sends again, so it does not ask twice for the same cast.</li>
 * </ul>
 */
public final class CastQueue {
    /** The shortest time between two casts of a held key: half a second (Matěj, 2026-10-19). */
    public static final int REPEAT_TICKS = 10;
    /** How long the client waits for the server's answer after a send. */
    public static final int AWAIT_TICKS = 5;
    /** The most presses that can wait: the Spells Queue Size option's maximum. */
    public static final int MAX_SIZE = 3;

    /** The client's view of its player's casting this tick. */
    public interface State {
        /** The spell being cast, if a cast is in progress. */
        Optional<ResourceKey<Spell>> casting();

        /** Whether the spell's own cooldown runs. */
        boolean onCooldown(ResourceKey<Spell> spell);

        /** Whether the global cooldown runs and the spell is on it. */
        boolean onGlobalCooldown(ResourceKey<Spell> spell);
    }

    /**
     * The client options: whether presses wait for the cast in progress, and how many may wait (1 to
     * {@value #MAX_SIZE}).
     */
    public record Options(boolean queue, int size) {
        public static final Options DEFAULT = new Options(false, 1);

        public Options {
            size = Math.clamp(size, 1, MAX_SIZE);
        }
    }

    /** A cast to ask for: the spell, and whether it may cancel another spell's cast in progress. */
    public record Send(ResourceKey<Spell> spell, boolean replaceCast) {
    }

    private record Press(int slot, ResourceKey<Spell> spell) {
    }

    private final Deque<Press> queue = new ArrayDeque<>();
    /** Slot to the game time its spell was last sent. */
    private final Map<Integer, Long> lastSend = new HashMap<>();
    private long awaitUntil = Long.MIN_VALUE;

    /** The key of bar slot {@code slot}, holding {@code spell}, was pressed. */
    public void press(int slot, ResourceKey<Spell> spell, State state, Options options) {
        if (state.casting().filter(spell::equals).isPresent()) {
            return;
        }
        if (!options.queue()) {
            queue.clear();
        } else {
            queue.removeIf(press -> press.spell().equals(spell));
            while (queue.size() >= options.size()) {
                queue.removeLast();
            }
        }
        queue.addLast(new Press(slot, spell));
    }

    /**
     * What to send at game time {@code now}, if anything: the first waiting press once it may go, else the spell of a
     * held key ({@code held}: slot to its spell, in slot order) that is ready.
     */
    public Optional<Send> tick(long now, SortedMap<Integer, ResourceKey<Spell>> held, State state, Options options) {
        if (now < awaitUntil) {
            return Optional.empty();
        }
        Optional<ResourceKey<Spell>> casting = state.casting();
        Press head = queue.peekFirst();
        if (head != null) {
            if (casting.filter(head.spell()::equals).isPresent()) {
                queue.removeFirst();
                return Optional.empty();
            }
            if (options.queue() && casting.isPresent()
                    || state.onGlobalCooldown(head.spell()) && !state.onCooldown(head.spell())) {
                return Optional.empty();
            }
            queue.removeFirst();
            return Optional.of(sent(now, head.slot(), new Send(head.spell(), !options.queue())));
        }
        if (casting.isPresent()) {
            return Optional.empty();
        }
        for (Map.Entry<Integer, ResourceKey<Spell>> entry : held.entrySet()) {
            ResourceKey<Spell> spell = entry.getValue();
            Long last = lastSend.get(entry.getKey());
            if (state.onCooldown(spell) || state.onGlobalCooldown(spell) || last != null && now - last < REPEAT_TICKS) {
                continue;
            }
            return Optional.of(sent(now, entry.getKey(), new Send(spell, false)));
        }
        return Optional.empty();
    }

    /** How many presses wait. */
    public int waiting() {
        return queue.size();
    }

    /** Forgets every waiting press and the last sends: a death, a logout. */
    public void clear() {
        queue.clear();
        lastSend.clear();
        awaitUntil = Long.MIN_VALUE;
    }

    private Send sent(long now, int slot, Send send) {
        lastSend.put(slot, now);
        awaitUntil = now + AWAIT_TICKS;
        return send;
    }
}
