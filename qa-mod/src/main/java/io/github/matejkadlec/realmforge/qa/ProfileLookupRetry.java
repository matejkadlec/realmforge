package io.github.matejkadlec.realmforge.qa;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.slf4j.Logger;

/**
 * The dev client looks up its account's profile, skin included, once at startup at Mojang's session server
 * ({@code Minecraft}'s {@code profileFuture}). authlib gives up after a 5 s timeout and returns null, and the game then
 * plays the whole session with the account's default skin (RLM-570: 5 of about 120 dev client runs). The dev client
 * tries again instead ({@code mixin/MinecraftProfileLookupMixin}): up to {@link #ATTEMPTS} lookups, waiting
 * {@link #WAITS_MS} between them, the first non-null result winning. A lookup that never succeeds leaves vanilla's
 * fallback. {@code ProfileLookupRetryGameTests} check the rule.
 */
public final class ProfileLookupRetry {
    /** The wait before each retry in milliseconds: before the second lookup, then before the third. */
    public static final List<Long> WAITS_MS = List.of(1000L, 2000L);
    /** Lookups in all, the first one included. */
    public static final int ATTEMPTS = WAITS_MS.size() + 1;

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Waits between two lookups; {@code Thread::sleep} in the game, a recorder in the tests. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private ProfileLookupRetry() {
    }

    /** The first non-null result of {@code lookup} in {@link #ATTEMPTS} calls, or null when every call failed. */
    @Nullable
    public static <T> T fetch(Supplier<T> lookup, Sleeper sleeper) {
        T result = lookup.get();
        for (int retry = 0; result == null && retry < WAITS_MS.size(); retry++) {
            long wait = WAITS_MS.get(retry);
            LOGGER.info("Profile lookup failed; trying again in {} ms (attempt {} of {})", wait, retry + 2, ATTEMPTS);
            try {
                sleeper.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            result = lookup.get();
        }
        return result;
    }
}
