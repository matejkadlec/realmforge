package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.qa.ProfileLookupRetry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * RLM-570: the dev client's profile lookup at startup is tried up to three times, 1 s and then 2 s apart, and the
 * first result wins ({@link ProfileLookupRetry}). The lookups here are scripted and the waits only recorded; the
 * mixin that wraps the real lookup runs only in a dev client.
 */
public final class ProfileLookupRetryGameTests {
    private ProfileLookupRetryGameTests() {
    }

    /** A lookup that works at once is called once, with no wait. */
    @QaGameTest
    public static void aFirstSuccessNeedsNoRetry(GameTestHelper helper) {
        Script lookup = new Script("profile");
        List<Long> waits = new ArrayList<>();
        String result = ProfileLookupRetry.fetch(lookup, waits::add);
        helper.assertValueEqual(result, "profile", "the lookup's result");
        helper.assertValueEqual(lookup.calls, 1, "lookups");
        helper.assertTrue(waits.isEmpty(), "no wait: " + waits);
        helper.succeed();
    }

    /** Two failed lookups (a timeout gives null), then one that works: its result, after 1 s and 2 s. */
    @QaGameTest
    public static void aLaterSuccessWins(GameTestHelper helper) {
        Script lookup = new Script(null, null, "profile");
        List<Long> waits = new ArrayList<>();
        String result = ProfileLookupRetry.fetch(lookup, waits::add);
        helper.assertValueEqual(result, "profile", "the third lookup's result");
        helper.assertValueEqual(lookup.calls, 3, "lookups");
        helper.assertValueEqual(waits, List.of(1000L, 2000L), "the waits before each retry");
        helper.succeed();
    }

    /** A second lookup that works ends the retries: one wait of 1 s. */
    @QaGameTest
    public static void aSecondSuccessStopsTheRetries(GameTestHelper helper) {
        Script lookup = new Script(null, "profile", "unused");
        List<Long> waits = new ArrayList<>();
        String result = ProfileLookupRetry.fetch(lookup, waits::add);
        helper.assertValueEqual(result, "profile", "the second lookup's result");
        helper.assertValueEqual(lookup.calls, 2, "lookups");
        helper.assertValueEqual(waits, List.of(1000L), "one wait");
        helper.succeed();
    }

    /** Three failed lookups give up with null, vanilla's fallback, and a fourth is never made. */
    @QaGameTest
    public static void threeFailuresGiveUp(GameTestHelper helper) {
        Script lookup = new Script(null, null, null, "too late");
        List<Long> waits = new ArrayList<>();
        String result = ProfileLookupRetry.fetch(lookup, waits::add);
        helper.assertTrue(result == null, "no profile after three failed lookups: " + result);
        helper.assertValueEqual(lookup.calls, ProfileLookupRetry.ATTEMPTS, "lookups");
        helper.assertValueEqual(lookup.calls, 3, "three attempts in all");
        helper.assertValueEqual(waits, List.of(1000L, 2000L), "the waits before each retry");
        helper.succeed();
    }

    /** A lookup that answers with the scripted results in turn (null for a failure) and counts its calls. */
    private static final class Script implements Supplier<String> {
        private final List<String> results;
        int calls;

        Script(String... results) {
            this.results = Arrays.asList(results);
        }

        @Override
        public String get() {
            return results.get(calls++);
        }
    }
}
