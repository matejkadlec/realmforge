package io.github.matejkadlec.realmforge.qa.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a GameTest: a {@code public static void} method taking a {@code GameTestHelper}, in a class that
 * {@link QaGameTests} lists. Minecraft 26.1 registers GameTests as test functions and test instances in registries,
 * without an annotation of its own, so this one carries what each test needs and {@link QaGameTests} does the rest.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface QaGameTest {
    /** The structure template, {@code data/realmforge_qa/structure/<template>.nbt}. */
    String template() default QaGameTests.PLATFORM;

    /** Ticks the test may take before it fails. */
    int timeoutTicks() default 100;
}
