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
    /** An aura (RLM-595): its press toggles it, as the Warrior's Battle Stance. */
    private static final ResourceKey<Spell> STANCE = key("stance");
    private static final SortedMap<Integer, ResourceKey<Spell>> NOTHING_HELD = new TreeMap<>();
    private static final Options QUEUE_1 = new Options(true, 1);
    private static final Options QUEUE_3 = new Options(true, 3);

    /** A player's casting, set by each test. */
    private static final class FakeState implements CastQueue.State {
        Optional<ResourceKey<Spell>> casting = Optional.empty();
        boolean globalCooldown;
        final Set<ResourceKey<Spell>> cooldowns = new HashSet<>();
        final Set<ResourceKey<Spell>> auras = new HashSet<>();

        @Override
        public Optional<ResourceKey<Spell>> casting() {
            return casting;
        }

        @Override
        public boolean onCooldown(ResourceKey<Spell> spell) {
            return cooldowns.contains(spell);
        }

        /** As {@code ClientSpellState}: an aura's toggle is off the global cooldown. */
        @Override
        public boolean onGlobalCooldown(ResourceKey<Spell> spell) {
            return globalCooldown && !auras.contains(spell);
        }

        @Override
        public boolean toggles(ResourceKey<Spell> spell) {
            return auras.contains(spell);
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

    /** RLM-637: holding a stance's key never flips the stance back, not even once its shared cooldown ends. */
    @Test
    void aHeldAuraKeyNeverSendsItsAuraAgain() {
        state.auras.add(STANCE);
        SortedMap<Integer, ResourceKey<Spell>> held = new TreeMap<>();
        held.put(0, STANCE);
        queue.press(0, STANCE, state, Options.DEFAULT);
        assertEquals(Optional.of(new Send(STANCE, true)), queue.tick(0, held, state, Options.DEFAULT),
                "the press toggles it");
        state.cooldowns.add(STANCE);
        assertEquals(Optional.empty(), queue.tick(CastQueue.REPEAT_TICKS, held, state, Options.DEFAULT),
                "held during its cooldown");
        state.cooldowns.clear();
        assertEquals(Optional.empty(), queue.tick(3600, held, state, Options.DEFAULT),
                "held after its cooldown ended");
        held.put(1, BOLT);
        assertEquals(Optional.of(new Send(BOLT, false)), queue.tick(3610, held, state, Options.DEFAULT),
                "another held key still repeats");
    }

    /** RLM-637: with queueing, an aura's press goes during a cast and leaves it running. */
    @Test
    void withQueueingAnAuraPressDoesNotWaitForTheCast() {
        state.auras.add(STANCE);
        state.casting = Optional.of(BOLT);
        queue.press(1, STANCE, state, QUEUE_1);
        assertEquals(Optional.of(new Send(STANCE, false)), queue.tick(0, NOTHING_HELD, state, QUEUE_1),
                "at once, and it cancels nothing");
        assertEquals(0, queue.waiting());
    }

    /**
     * RLM-637: with Spells Queue Size 2 or 3, a spell pressed during a cast waits at the queue's head; a stance swap
     * pressed after it goes past it, and the spell keeps its place for the cast's end.
     */
    @Test
    void anAuraPressGoesPastASpellThatWaitsForTheCast() {
        state.auras.add(STANCE);
        state.casting = Optional.of(BOLT);
        queue.press(1, NOVA, state, QUEUE_3);
        queue.press(2, STANCE, state, QUEUE_3);
        assertEquals(Optional.of(new Send(STANCE, false)), queue.tick(0, NOTHING_HELD, state, QUEUE_3),
                "the stance goes while bolt still casts");
        assertEquals(1, queue.waiting(), "nova still waits");
        assertEquals(Optional.empty(), queue.tick(CastQueue.AWAIT_TICKS, NOTHING_HELD, state, QUEUE_3),
                "for the cast");
        state.casting = Optional.empty();
        assertEquals(Optional.of(new Send(NOVA, false)), queue.tick(CastQueue.AWAIT_TICKS + 1, NOTHING_HELD, state,
                QUEUE_3), "then nova");
    }

    /** RLM-637: an aura is off the global cooldown, so it passes a press that waits for it, too. */
    @Test
    void anAuraPressGoesPastASpellThatWaitsForTheGlobalCooldown() {
        state.auras.add(STANCE);
        state.globalCooldown = true;
        queue.press(1, NOVA, state, QUEUE_3);
        queue.press(2, STANCE, state, QUEUE_3);
        assertEquals(Optional.of(new Send(STANCE, false)), queue.tick(0, NOTHING_HELD, state, QUEUE_3));
        state.globalCooldown = false;
        assertEquals(Optional.of(new Send(NOVA, false)), queue.tick(CastQueue.AWAIT_TICKS, NOTHING_HELD, state,
                QUEUE_3));
    }

    /**
     * RLM-671: without spell queueing (the default) the queue holds only the latest press, an aura's included, so
     * RLM-666's rule holds with queueing only. A stance pressed while nova waits out the global cooldown drops nova;
     * nova pressed while the stance waits for the server's answer to bolt drops the stance.
     */
    @Test
    void withoutQueueingAnAurasPressIsTheOnlyWaitingPressToo() {
        state.auras.add(STANCE);
        state.globalCooldown = true;
        queue.press(1, NOVA, state, Options.DEFAULT);
        assertEquals(Optional.empty(), queue.tick(0, NOTHING_HELD, state, Options.DEFAULT), "nova waits");
        queue.press(2, STANCE, state, Options.DEFAULT);
        assertEquals(1, queue.waiting(), "the stance takes nova's place");
        assertEquals(Optional.of(new Send(STANCE, true)), queue.tick(1, NOTHING_HELD, state, Options.DEFAULT),
                "off the global cooldown, the stance goes");
        state.globalCooldown = false;
        assertEquals(Optional.empty(), queue.tick(1 + CastQueue.AWAIT_TICKS, NOTHING_HELD, state, Options.DEFAULT),
                "and nova never does");

        queue.press(0, BOLT, state, Options.DEFAULT);
        assertEquals(Optional.of(new Send(BOLT, true)), queue.tick(100, NOTHING_HELD, state, Options.DEFAULT));
        queue.press(2, STANCE, state, Options.DEFAULT);
        queue.press(1, NOVA, state, Options.DEFAULT);
        assertEquals(1, queue.waiting(), "nova takes the stance's place");
        assertEquals(Optional.of(new Send(NOVA, true)), queue.tick(100 + CastQueue.AWAIT_TICKS, NOTHING_HELD, state,
                Options.DEFAULT));
        assertEquals(Optional.empty(), queue.tick(100 + 2 * CastQueue.AWAIT_TICKS, NOTHING_HELD, state,
                Options.DEFAULT), "and the stance never goes");
    }

    /**
     * RLM-666: at Spells Queue Size 2, nova and blink fill the queue during a cast; a stance swap pressed then takes no
     * place in it, so blink is not dropped for it: the stance goes past both, and both still cast after bolt.
     */
    @Test
    void anAuraPressIntoAFullQueueOfTwoKeepsEveryWaitingSpell() {
        Options two = new Options(true, 2);
        state.auras.add(STANCE);
        state.casting = Optional.of(BOLT);
        queue.press(1, NOVA, state, two);
        queue.press(2, BLINK, state, two);
        queue.press(3, STANCE, state, two);
        assertEquals(3, queue.waiting(), "the stance waits beside both spells");
        assertEquals(Optional.of(new Send(STANCE, false)), queue.tick(0, NOTHING_HELD, state, two),
                "and goes while bolt still casts");
        state.casting = Optional.empty();
        assertEquals(Optional.of(new Send(NOVA, false)), queue.tick(CastQueue.AWAIT_TICKS, NOTHING_HELD, state, two));
        assertEquals(Optional.of(new Send(BLINK, false)), queue.tick(2 * CastQueue.AWAIT_TICKS, NOTHING_HELD, state,
                two), "blink was not dropped for the stance");
    }

    /** RLM-666: the same at size 3, with three spells waiting. */
    @Test
    void anAuraPressIntoAFullQueueOfThreeKeepsEveryWaitingSpell() {
        ResourceKey<Spell> ward = key("ward");
        state.auras.add(STANCE);
        state.casting = Optional.of(BOLT);
        queue.press(1, NOVA, state, QUEUE_3);
        queue.press(2, BLINK, state, QUEUE_3);
        queue.press(3, ward, state, QUEUE_3);
        queue.press(4, STANCE, state, QUEUE_3);
        assertEquals(Optional.of(new Send(STANCE, false)), queue.tick(0, NOTHING_HELD, state, QUEUE_3));
        state.casting = Optional.empty();
        assertEquals(Optional.of(new Send(NOVA, false)), queue.tick(CastQueue.AWAIT_TICKS, NOTHING_HELD, state,
                QUEUE_3));
        assertEquals(Optional.of(new Send(BLINK, false)), queue.tick(2 * CastQueue.AWAIT_TICKS, NOTHING_HELD, state,
                QUEUE_3));
        assertEquals(Optional.of(new Send(ward, false)), queue.tick(3 * CastQueue.AWAIT_TICKS, NOTHING_HELD, state,
                QUEUE_3), "ward was not dropped for the stance");
    }

    /**
     * RLM-666: a spell press into a full queue drops the newest waiting spell, never a waiting aura press: the stance
     * pressed while the client still waits for the server's answer keeps its place.
     */
    @Test
    void aSpellPressIntoAFullQueueDropsTheNewestSpellNotAnAura() {
        Options two = new Options(true, 2);
        state.auras.add(STANCE);
        queue.press(0, BOLT, state, two);
        queue.tick(0, NOTHING_HELD, state, two);
        state.casting = Optional.of(BOLT);
        queue.press(1, NOVA, state, two);
        queue.press(2, STANCE, state, two);
        queue.press(3, BLINK, state, two);
        queue.press(4, key("ward"), state, two);
        assertEquals(3, queue.waiting(), "nova, the stance and ward: blink gave way to ward");
        assertEquals(Optional.of(new Send(STANCE, false)), queue.tick(CastQueue.AWAIT_TICKS, NOTHING_HELD, state,
                two));
        state.casting = Optional.empty();
        assertEquals(Optional.of(new Send(NOVA, false)), queue.tick(2 * CastQueue.AWAIT_TICKS, NOTHING_HELD, state,
                two));
        assertEquals(Optional.of(new Send(key("ward"), false)), queue.tick(3 * CastQueue.AWAIT_TICKS, NOTHING_HELD,
                state, two));
        assertEquals(0, queue.waiting());
    }

    @Test
    void onlyAnAuraGoesPastAWaitingPress() {
        state.casting = Optional.of(BOLT);
        queue.press(1, NOVA, state, QUEUE_3);
        queue.press(2, BLINK, state, QUEUE_3);
        assertEquals(Optional.empty(), queue.tick(0, NOTHING_HELD, state, QUEUE_3), "blink waits behind nova");
        assertEquals(2, queue.waiting());
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
