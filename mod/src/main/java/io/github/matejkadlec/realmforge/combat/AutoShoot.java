package io.github.matejkadlec.realmforge.combat;

import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;

/**
 * Auto Shoot's rule (RLM-496; Matěj, 2026-10-29): while right click is held with the option on, a bow fires at full
 * draw and a crossbow fires once loaded, then both start again. The client releases the item when {@link #ready}
 * says so ({@code client/input/AutoShootInput}); vanilla then starts using it again every few ticks while the button
 * stays down, which draws the bow again, or fires the loaded crossbow and loads it again. Every bow and crossbow
 * counts, other mods' too: an item that charges while right click is held and then shoots, a
 * {@link ProjectileWeaponItem} with the bow's or the crossbow's use animation. The trident (it would throw itself
 * away) and throwables (they repeat while held already) are no projectile weapons.
 */
public final class AutoShoot {
    /**
     * The ticks a bow is drawn before the client releases it: one past full draw ({@link BowItem#MAX_DRAW_DURATION},
     * power 1, a crit arrow, {@code HitTracker}). The server counts the draw from the moment the client's use packet
     * reaches it, and the release packet takes as long, so it counts about the client's ticks; the extra tick keeps a
     * release that lands a tick early from firing short of full draw.
     */
    public static final int BOW_RELEASE_TICKS = BowItem.MAX_DRAW_DURATION + 1;

    /** What Auto Shoot makes of an item. */
    public enum Weapon {
        /** Drawn while held, fired on release: released at full draw. */
        BOW,
        /** Loaded while held, fired by the next use: released once loaded. */
        CROSSBOW,
        /** Anything else: never released by Auto Shoot. */
        NONE
    }

    private AutoShoot() {
    }

    /** What Auto Shoot makes of {@code stack}. */
    public static Weapon weapon(ItemStack stack) {
        if (!(stack.getItem() instanceof ProjectileWeaponItem)) {
            return Weapon.NONE;
        }
        return switch (stack.getUseAnimation()) {
            case BOW -> Weapon.BOW;
            case CROSSBOW -> Weapon.CROSSBOW;
            default -> Weapon.NONE;
        };
    }

    /**
     * Whether Auto Shoot releases the item being used: a bow drawn for {@link #BOW_RELEASE_TICKS}, a crossbow once it
     * holds its projectiles ({@link CrossbowItem#isCharged}, which the server sets when loading ends and syncs to the
     * client, so a crossbow is never released before the server loaded it).
     */
    public static boolean ready(Weapon weapon, int ticksUsing, boolean loaded) {
        return switch (weapon) {
            case BOW -> ticksUsing >= BOW_RELEASE_TICKS;
            case CROSSBOW -> loaded;
            case NONE -> false;
        };
    }

    /** Whether Auto Shoot releases {@code stack}, used for {@code ticksUsing} ticks. */
    public static boolean ready(ItemStack stack, int ticksUsing) {
        return ready(weapon(stack), ticksUsing, CrossbowItem.isCharged(stack));
    }
}
