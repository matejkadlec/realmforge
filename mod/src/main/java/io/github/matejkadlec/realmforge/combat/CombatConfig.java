package io.github.matejkadlec.realmforge.combat;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Combat's common options, in {@code config/realmforge-common.toml}: the rules of blocking with a two-handed weapon
 * ({@link WeaponBlocks}, RLM-297), which Matěj tunes in playtests (architecture 19.2). NeoForge reloads the file when
 * it changes, so a running game picks up a new value at once.
 */
public final class CombatConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder()
            .comment("Blocking with a two-handed weapon: hold right click to raise it.")
            .push("blocking");

    /** How long after raising the weapon a hit is blocked in full. */
    public static final ModConfigSpec.DoubleValue FULL_BLOCK_SECONDS = BUILDER
            .comment("Seconds after raising the weapon in which a hit is blocked in full.")
            .defineInRange("fullBlockSeconds", 0.5, 0.0, 10.0);

    /** The share of a later hit that a raised weapon blocks. */
    public static final ModConfigSpec.DoubleValue BLOCKED_SHARE = BUILDER
            .comment("The share of a hit blocked after that, from 0 (none) to 1 (all of it).")
            .defineInRange("blockedShare", 0.5, 0.0, 1.0);

    /** How long the weapon cannot be raised again after a blocked hit or after lowering it. */
    public static final ModConfigSpec.DoubleValue COOLDOWN_SECONDS = BUILDER
            .comment("Seconds before the weapon can be raised again, after any blocked hit or after lowering it.")
            .defineInRange("cooldownSeconds", 1.0, 0.0, 10.0);

    /** The arc in front of the player that a raised weapon covers. */
    public static final ModConfigSpec.DoubleValue ARC_DEGREES = BUILDER
            .comment("The arc in front of the player that the weapon covers, in degrees: 180 is the front half, "
                    + "360 every side.")
            .defineInRange("arcDegrees", 180.0, 0.0, 360.0);

    public static final ModConfigSpec SPEC = BUILDER.pop().build();

    private CombatConfig() {
    }

    /** Called once from {@link CombatFeature}. */
    static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC);
    }

    /** The blocking rules as they are now. */
    public static WeaponBlocks.Rules blockingRules() {
        return new WeaponBlocks.Rules(seconds(FULL_BLOCK_SECONDS.get()), BLOCKED_SHARE.get(),
                seconds(COOLDOWN_SECONDS.get()), ARC_DEGREES.get());
    }

    private static int seconds(double seconds) {
        return (int) Math.round(seconds * 20);
    }
}
