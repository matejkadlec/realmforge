package io.github.matejkadlec.realmforge.client.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.client.spell.CastQueue.Options;
import io.github.matejkadlec.realmforge.client.spell.CastQueue.Send;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellRegistries;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

/** The spell bar's presses, holds and queue (C3b, RLM-25; Matěj, 2026-10-19). */
class CastQueueTest {
    private static final ResourceKey<Spell> BOLT = key("bolt");
    private static final ResourceKey<Spell> NOVA = key("nova");
    private static final ResourceKey<Spell> BLINK = key("blink");
    private static final SortedMap<Integer, ResourceKey<Spell>> NOTHING_HELD = new TreeMap<>();
    private static final Options QUEUE_1 = new Options(true, 1);
    private static final Options QUEUE_3 = new Options(true, 3);

    /** A player's casting, set by each test. */
    private static final class FakeState implements CastQueue.State {
        Optional<ResourceKey<Spell>> casting = Optional.empty();
        boolean globalCooldown;
        final Set<ResourceKey<Spell>> cooldowns = new HashSet<>();

        @Override
        public Optional<ResourceKey<Spell>> casting() {
            return casting;
        }

        @Override
        public boolean onCooldown(ResourceKey<Spell> spell) {
            return cooldowns.contains(spell);
        }

        @Override
        public boolean onGlobalCooldown(ResourceKey<Spell> spell) {
            return globalCooldown;
        }
    }

    private final CastQueue queue = new CastQueue();
    private final FakeState state = new FakeState();

    @Test
    void aPressCastsAtOnceAndMayReplaceTheCastInProgress() {
        queue.press(0, BOLT, state, Options.DEFAULT);
        assertEquals(Optional.of(new Send(BOLT, true)), queue.tick(0, NOTHING_HELD, state, Options.DEFAULT));
        state.casting = Optional.of(BOLT);
        queue.press(1, NOVA, state, Options.DEFAULT);
        assertEquals(Optional.of(new Send(NOVA, true)), queue.tick(10, NOTHING_HELD, state, Options.DEFAULT),
                "without queueing, nova cancels bolt's cast and casts at once");
    }

    @Test
    void pressingTheSpellBeingCastDoesNothing() {
        state.casting = Optional.of(BOLT);
        queue.press(0, BOLT, state, Options.DEFAULT);
        assertEquals(Optional.empty(), queue.tick(0, NOTHING_HELD, state, Options.DEFAULT));
        assertEquals(0, queue.waiting());
    }

    @Test
    void aPressWaitsOutTheGlobalCooldownButNotItsOwnCooldown() {
        state.globalCooldown = true;
        queue.press(0, BOLT, state, Options.DEFAULT);
        assertEquals(Optional.empty(), queue.tick(0, NOTHING_HELD, state, Options.DEFAULT), "the global cooldown");
        state.globalCooldown = false;
        assertEquals(Optional.of(new Send(BOLT, true)), queue.tick(4, NOTHING_HELD, state, Options.DEFAULT),
                "sent when it ends");
        state.cooldowns.add(NOVA);
        queue.press(1, NOVA, state, Options.DEFAULT);
        assertEquals(Optional.of(new Send(NOVA, true)), queue.tick(20, NOTHING_HELD, state, Options.DEFAULT),
                "on its own cooldown it goes anyway, and the server says it is not ready");
    }

    @Test
    void afterASendTheClientWaitsForTheServer() {
        queue.press(0, BOLT, state, Options.DEFAULT);
        queue.tick(100, NOTHING_HELD, state, Options.DEFAULT);
        queue.press(1, NOVA, state, Options.DEFAULT);
        assertEquals(Optional.empty(), queue.tick(100 + CastQueue.AWAIT_TICKS - 1, NOTHING_HELD, state,
                Options.DEFAULT));
        assertEquals(Optional.of(new Send(NOVA, true)), queue.tick(100 + CastQueue.AWAIT_TICKS, NOTHING_HELD, state,
                Options.DEFAULT));
    }

    @Test
    void withQueueingAPressWaitsForTheCastToEnd() {
        state.casting = Optional.of(BOLT);
        queue.press(1, NOVA, state, QUEUE_1);
        assertEquals(Optional.empty(), queue.tick(0, NOTHING_HELD, state, QUEUE_1), "bolt still casts");
        state.casting = Optional.empty();
        assertEquals(Optional.of(new Send(NOVA, false)), queue.tick(1, NOTHING_HELD, state, QUEUE_1),
                "then nova, which cancels nothing");
    }

    @Test
    void atSizeOneANewPressReplacesTheWaitingOne() {
        state.casting = Optional.of(BOLT);
        queue.press(1, NOVA, state, QUEUE_1);
        queue.press(2, BLINK, state, QUEUE_1);
        assertEquals(1, queue.waiting());
        state.casting = Optional.empty();
        assertEquals(Optional.of(new Send(BLINK, false)), queue.tick(0, NOTHING_HELD, state, QUEUE_1));
    }

    @Test
    void aboveSizeOnePressesWaitInOrder() {
        state.casting = Optional.of(BOLT);
        queue.press(1, NOVA, state, QUEUE_3);
        queue.press(2, BLINK, state, QUEUE_3);
        queue.press(1, NOVA, state, QUEUE_3);
        assertEquals(2, queue.waiting(), "a spell waits once");
        state.casting = Optional.empty();
        assertEquals(Optional.of(new Send(BLINK, false)), queue.tick(0, NOTHING_HELD, state, QUEUE_3),
                "pressed again, nova went to the back");
        assertEquals(Optional.of(new Send(NOVA, false)), queue.tick(CastQueue.AWAIT_TICKS, NOTHING_HELD, state,
                QUEUE_3));
        assertEquals(0, queue.waiting());
    }

    @Test
    void aFullQueueDropsItsNewestForTheNextPress() {
        Options two = new Options(true, 2);
        state.casting = Optional.of(BOLT);
        queue.press(1, NOVA, state, two);
        queue.press(2, BLINK, state, two);
        queue.press(3, key("ward"), state, two);
        state.casting = Optional.empty();
        assertEquals(Optional.of(new Send(NOVA, false)), queue.tick(0, NOTHING_HELD, state, two));
        assertEquals(Optional.of(new Send(key("ward"), false)), queue.tick(CastQueue.AWAIT_TICKS, NOTHING_HELD,
                state, two));
    }

    @Test
    void aHeldKeyRepeatsAtMostEveryHalfSecondAndNeverDuringACast() {
        SortedMap<Integer, ResourceKey<Spell>> held = new TreeMap<>();
        held.put(0, BOLT);
        assertEquals(Optional.of(new Send(BOLT, false)), queue.tick(0, held, state, Options.DEFAULT));
        assertEquals(Optional.empty(), queue.tick(CastQueue.REPEAT_TICKS - 1, held, state, Options.DEFAULT),
                "an instant spell off the global cooldown still waits half a second");
        assertEquals(Optional.of(new Send(BOLT, false)), queue.tick(CastQueue.REPEAT_TICKS, held, state,
                Options.DEFAULT));
        state.casting = Optional.of(NOVA);
        assertEquals(Optional.empty(), queue.tick(100, held, state, Options.DEFAULT), "holding never cancels");
        state.casting = Optional.empty();
        state.globalCooldown = true;
        assertEquals(Optional.empty(), queue.tick(200, held, state, Options.DEFAULT), "nor ignores the cooldowns");
    }

    @Test
    void theQueueGoesBeforeAHeldKey() {
        SortedMap<Integer, ResourceKey<Spell>> held = new TreeMap<>();
        held.put(0, BOLT);
        queue.press(1, NOVA, state, Options.DEFAULT);
        assertEquals(Optional.of(new Send(NOVA, true)), queue.tick(0, held, state, Options.DEFAULT));
    }

    @Test
    void theSizeStaysWithinOneToThree() {
        assertEquals(1, new Options(true, 0).size());
        assertEquals(3, new Options(true, 9).size());
    }

    private static ResourceKey<Spell> key(String path) {
        return ResourceKey.create(SpellRegistries.SPELL, Realmforge.id(path));
    }
}
