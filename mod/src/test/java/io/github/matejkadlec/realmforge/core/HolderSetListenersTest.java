package io.github.matejkadlec.realmforge.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.serialization.Lifecycle;
import io.github.matejkadlec.realmforge.Realmforge;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.neoforged.neoforge.registries.holdersets.NotHolderSet;
import net.neoforged.neoforge.registries.holdersets.OrHolderSet;
import org.junit.jupiter.api.Test;

/**
 * A named tag takes listeners from many threads at once (RLM-612, {@code mixin/HolderSetNamedMixin}, architecture
 * 19.3). Vanilla parses a registry's entries on worker threads, which share each tag through
 * {@code ConcurrentHolderGetter} (the registry's {@code createRegistrationLookup}, as here), and every
 * {@code neoforge:not}, {@code and} or {@code or} set they build adds a listener to the tag it wraps, outside that
 * getter's lock. NeoForge's plain list lost that race now and then and failed a whole registry load. The test run
 * loads the core mod, so its mixins apply.
 */
class HolderSetListenersTest {
    private static final ResourceKey<Registry<String>> REGISTRY =
            ResourceKey.createRegistryKey(Realmforge.id("holder_set_listeners_test"));
    private static final TagKey<String> TAG = TagKey.create(REGISTRY, Realmforge.id("shared"));
    private static final int THREADS = 8;
    private static final int SETS_PER_THREAD = 2_000;
    private static final int ROUNDS = 10;

    /**
     * The race itself: holder sets built on one tag from eight threads at once, half {@code not} and half {@code or},
     * as structures whose biomes wrap the same tag are parsed. Every set must be built (NeoForge's list threw
     * {@code ArrayIndexOutOfBoundsException} from a lost race), and the tag's bind must run every listener once (a
     * lost race also left a null that crashed the bind, or dropped a listener). Without the mixin it failed in the
     * first round of each of four runs (a null once, 12,980 and 15,576 of 16,000 listeners heard twice); a race is
     * not deterministic, so {@link #aListenerAddedDuringABindIsSafe} checks the mixin deterministically.
     */
    @Test
    void holderSetsBuiltInParallelOnOneTagAllHearItsBind() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                MappedRegistry<String> registry = new MappedRegistry<>(REGISTRY, Lifecycle.stable());
                HolderSet.Named<String> tag = registry.createRegistrationLookup().getOrThrow(TAG);
                AtomicInteger heard = new AtomicInteger();
                CountDownLatch start = new CountDownLatch(1);
                List<Future<Void>> workers = new ArrayList<>();
                for (int thread = 0; thread < THREADS; thread++) {
                    boolean negate = thread % 2 == 0;
                    workers.add(pool.submit(() -> {
                        start.await();
                        for (int i = 0; i < SETS_PER_THREAD; i++) {
                            HolderSet<String> set = negate
                                    ? new NotHolderSet<>(registry, tag)
                                    : new OrHolderSet<>(List.of(tag));
                            set.addInvalidationListener(heard::incrementAndGet);
                        }
                        return null;
                    }));
                }
                start.countDown();
                for (Future<Void> worker : workers) {
                    // Rethrows a worker's exception, a lost race's ArrayIndexOutOfBoundsException among them.
                    worker.get(30, TimeUnit.SECONDS);
                }
                registry.bindTags(Map.of(TAG, List.<Holder<String>>of()));
                assertEquals(THREADS * SETS_PER_THREAD, heard.get(), "round " + round + ": every set heard the bind");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * The mixin is in place, checked without a race: a listener that adds another while the tag binds. NeoForge's
     * {@code ArrayList} throws {@code ConcurrentModificationException} there every time; the mixin's
     * {@code CopyOnWriteArrayList} runs the bind over a snapshot, so the new listener waits for the next bind.
     */
    @Test
    void aListenerAddedDuringABindIsSafe() {
        MappedRegistry<String> registry = new MappedRegistry<>(REGISTRY, Lifecycle.stable());
        HolderSet.Named<String> tag = registry.createRegistrationLookup().getOrThrow(TAG);
        AtomicInteger heard = new AtomicInteger();
        tag.addInvalidationListener(() -> tag.addInvalidationListener(heard::incrementAndGet));
        assertDoesNotThrow(() -> registry.bindTags(Map.of(TAG, List.<Holder<String>>of())), "the first bind");
        assertEquals(0, heard.get(), "a listener added during a bind waits for the next one");
        registry.bindTags(Map.of(TAG, List.<Holder<String>>of()));
        assertEquals(1, heard.get(), "the next bind runs it");
    }
}
