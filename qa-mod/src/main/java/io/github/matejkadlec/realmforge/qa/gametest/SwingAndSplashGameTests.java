package io.github.matejkadlec.realmforge.qa.gametest;

import static io.github.matejkadlec.realmforge.qa.gametest.CombatGameTests.close;

import io.github.matejkadlec.realmforge.generated.GeneratedEffects;
import io.github.matejkadlec.realmforge.generated.GeneratedSpells;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.mob.MobLevels;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import io.github.matejkadlec.realmforge.spell.CastResult;
import io.github.matejkadlec.realmforge.spell.CombatState;
import io.github.matejkadlec.realmforge.spell.ResourceTypes;
import io.github.matejkadlec.realmforge.spell.Resources;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellAttachments;
import io.github.matejkadlec.realmforge.spell.SpellCaster;
import io.github.matejkadlec.realmforge.spell.Spells;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * RLM-399: the Warrior's melee spells hit every enemy in a shape in front of the player at their hit moment, and the
 * Mage's bolts explode on the enemy they hit. The player stands at (4.5, 1, 0.5) or (4.5, 1, 2.5) of a 9 x 9 platform,
 * facing south (+z), its eyes 1.62 above its feet; the shapes are 2 blocks tall round the crosshair. Slam: a 120° sector
 * of 1.5 blocks; Reckless Strike and Shield Bash: a box 2 wide and 1 deep in front of the body; test_cleave: a sector of
 * 4 blocks that lands at once, for the walls.
 */
public final class SwingAndSplashGameTests {
    private static final double HEALTH = SpellGameTests.HEALTH;
    private static final Vec3 CENTRE = new Vec3(4.5, 1, 2.5);
    private static final Vec3 EDGE = new Vec3(4.5, 1, 0.5);

    private SwingAndSplashGameTests() {
    }

    /**
     * Slam hits every husk in its sector, the one ahead and the one ahead to the right alike, and none behind the
     * player, beside it or beyond 1.5 blocks; its damage lands at its hit moment, half a second in, not at once.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_9X9)
    public static void slamHitsItsSectorAtItsHitMoment(GameTestHelper helper) {
        ServerPlayer player = warrior(helper, "qa-swing-slam", CENTRE);
        Husk ahead = husk(helper, new Vec3(4.5, 1, 3.5));
        Husk aheadRight = husk(helper, new Vec3(5.5, 1, 3.5));
        Husk behind = husk(helper, new Vec3(4.5, 1, 1.2));
        Husk beside = husk(helper, new Vec3(6.6, 1, 2.5));
        Husk beyond = husk(helper, new Vec3(4.5, 1, 5.5));
        learn(helper, player, GeneratedSpells.SLAM);
        assertCast(helper, player, GeneratedSpells.SLAM, null);
        helper.runAfterDelay(5, () -> helper.assertTrue(close(ahead.getHealth(), HEALTH),
                "Slam landed before its hit moment"));
        helper.runAfterDelay(14, () -> {
            helper.assertTrue(ahead.getHealth() < HEALTH, "Slam missed the husk ahead");
            helper.assertTrue(close(aheadRight.getHealth(), ahead.getHealth()),
                    "every husk in the sector takes the same damage");
            for (Husk spared : List.of(behind, beside, beyond)) {
                helper.assertTrue(close(spared.getHealth(), HEALTH), "Slam hit a husk outside its sector at "
                        + spared.position());
            }
            logout(player);
            helper.succeed();
        });
    }

    /**
     * Reckless Strike and Shield Bash hit every husk in their 2 × 1 box in front of the player, the one ahead and the one
     * ahead to the left, and none beside or behind it; Shield Bash stuns all it hits.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_9X9)
    public static void recklessStrikeAndShieldBashHitTheirFrontBox(GameTestHelper helper) {
        ServerPlayer player = warrior(helper, "qa-swing-box", CENTRE);
        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        Husk ahead = husk(helper, new Vec3(4.5, 1, 3.6));
        Husk aheadLeft = husk(helper, new Vec3(3.6, 1, 3.6));
        Husk beside = husk(helper, new Vec3(5.6, 1, 2.3));
        Husk behind = husk(helper, new Vec3(4.5, 1, 1.4));
        learn(helper, player, GeneratedSpells.RECKLESS_STRIKE);
        learn(helper, player, GeneratedSpells.SHIELD_BASH);
        assertCast(helper, player, GeneratedSpells.RECKLESS_STRIKE, null);
        helper.assertTrue(Spells.landSwing(player), "Reckless Strike swung");
        helper.assertTrue(ahead.getHealth() < HEALTH && aheadLeft.getHealth() < HEALTH,
                "Reckless Strike hit both husks in its box");
        helper.assertTrue(close(beside.getHealth(), HEALTH) && close(behind.getHealth(), HEALTH),
                "Reckless Strike hit a husk beside or behind the player");
        Spells.resetCooldowns(player);
        assertCast(helper, player, GeneratedSpells.SHIELD_BASH, null);
        helper.assertTrue(Spells.landSwing(player), "Shield Bash swung");
        helper.assertTrue(ahead.hasEffect(GeneratedEffects.STUNNED) && aheadLeft.hasEffect(GeneratedEffects.STUNNED),
                "Shield Bash stuns every husk in its box");
        helper.assertFalse(beside.hasEffect(GeneratedEffects.STUNNED) || behind.hasEffect(GeneratedEffects.STUNNED),
                "Shield Bash stunned a husk outside its box");
        logout(player);
        helper.succeed();
    }

    /**
     * A stun before Slam's hit moment stops it: nothing lands, and half its 30 rage and half its 6 s cooldown come back
     * (Matěj, 2026-10-29). Melee is blocked while it plays out.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_9X9)
    public static void aStunBeforeTheHitMomentRefundsHalf(GameTestHelper helper) {
        ServerPlayer player = warrior(helper, "qa-swing-stunned", CENTRE);
        Husk ahead = husk(helper, new Vec3(4.5, 1, 3.5));
        learn(helper, player, GeneratedSpells.SLAM);
        long start = helper.getLevel().getGameTime();
        assertCast(helper, player, GeneratedSpells.SLAM, null);
        helper.assertTrue(player.entityTags().contains(CombatState.BETTER_COMBAT_DISABLED_TAG),
                "no Better Combat swing while Slam plays out");
        helper.assertTrue(close(Resources.amount(player, ResourceTypes.RAGE), 70), "Slam costs 30 rage");
        helper.runAfterDelay(3, () -> player.addEffect(new MobEffectInstance(GeneratedEffects.STUNNED, 40)));
        helper.runAfterDelay(14, () -> {
            helper.assertTrue(close(ahead.getHealth(), HEALTH), "a stunned Slam landed");
            helper.assertTrue(close(Resources.amount(player, ResourceTypes.RAGE), 85), "half the rage came back: "
                    + Resources.amount(player, ResourceTypes.RAGE));
            helper.assertValueEqual(player.getData(SpellAttachments.COMBAT).cooldowns().get(GeneratedSpells.SLAM),
                    start + 60, "half the cooldown came back");
            helper.assertFalse(player.entityTags().contains(CombatState.BETTER_COMBAT_DISABLED_TAG), "melee is back");
            logout(player);
            helper.succeed();
        });
    }

    /**
     * Frostbolt's hit explodes: the target takes all 8 and its Slowness, a husk within a block of it half, 4, and the
     * same Slowness, a husk farther away nothing.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_9X9, timeoutTicks = 120)
    public static void frostboltSplashesHalfOnItsNeighbours(GameTestHelper helper) {
        ServerPlayer player = mage(helper, "qa-splash-frostbolt");
        Husk target = husk(helper, new Vec3(4.5, 1, 5.5));
        Husk near = husk(helper, new Vec3(5.6, 1, 5.5));
        Husk far = husk(helper, new Vec3(7.5, 1, 5.5));
        learn(helper, player, GeneratedSpells.FROSTBOLT);
        assertCast(helper, player, GeneratedSpells.FROSTBOLT, target);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(close(HEALTH - target.getHealth(), 8), "the target took " + (HEALTH - target.getHealth()));
            helper.assertTrue(close(HEALTH - near.getHealth(), 4), "the husk next to it took "
                    + (HEALTH - near.getHealth()));
            helper.assertTrue(close(far.getHealth(), HEALTH), "the far husk was hit");
            helper.assertTrue(target.hasEffect(MobEffects.SLOWNESS) && near.hasEffect(MobEffects.SLOWNESS),
                    "both took the Slowness");
            helper.assertValueEqual(near.getEffect(MobEffects.SLOWNESS).getDuration(),
                    target.getEffect(MobEffects.SLOWNESS).getDuration(), "at full strength");
            helper.assertFalse(far.hasEffect(MobEffects.SLOWNESS), "the far husk was slowed");
            logout(player);
            helper.succeed();
        });
    }

    /** Each of Arcane Missiles' three missiles explodes: 3 each on the target, 1.5 each on its neighbour. */
    @QaGameTest(template = QaGameTests.PLATFORM_9X9, timeoutTicks = 120)
    public static void everyArcaneMissileExplodes(GameTestHelper helper) {
        ServerPlayer player = mage(helper, "qa-splash-missiles");
        Husk target = husk(helper, new Vec3(4.5, 1, 5.5));
        Husk near = husk(helper, new Vec3(5.6, 1, 5.5));
        learn(helper, player, GeneratedSpells.ARCANE_MISSILES);
        assertCast(helper, player, GeneratedSpells.ARCANE_MISSILES, target);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(close(HEALTH - target.getHealth(), 9), "3 missiles of 3: " + (HEALTH - target.getHealth()));
            helper.assertTrue(close(HEALTH - near.getHealth(), 4.5), "3 explosions of 1.5: "
                    + (HEALTH - near.getHealth()));
            logout(player);
            helper.succeed();
        });
    }

    /** A wall hides a husk from the swing entirely; a husk beside the wall, in the open, is hit. */
    @QaGameTest(template = QaGameTests.PLATFORM_9X9)
    public static void aSwingNeverHitsThroughAWall(GameTestHelper helper) {
        ServerPlayer player = warrior(helper, "qa-swing-wall", EDGE);
        wall(helper, 4, 4);
        Husk hidden = husk(helper, new Vec3(4.5, 1, 3.5));
        Husk open = husk(helper, new Vec3(6.5, 1, 3.5));
        learn(helper, player, GeneratedSpells.TEST_CLEAVE);
        assertCast(helper, player, GeneratedSpells.TEST_CLEAVE, null);
        helper.assertTrue(close(hidden.getHealth(), HEALTH), "the swing hit through the wall");
        helper.assertTrue(close(open.getHealth(), HEALTH - 5), "the husk in the open took " + (HEALTH - open.getHealth()));
        logout(player);
        helper.succeed();
    }

    /**
     * Matěj's rule for a corner: a husk more than half hidden behind it is spared (about four fifths behind), one mostly
     * in the open (about a third behind) is hit.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_9X9)
    public static void aHuskMostlyBehindACornerIsSpared(GameTestHelper helper) {
        ServerPlayer player = warrior(helper, "qa-swing-corner", EDGE);
        wall(helper, 3, 4);
        Husk mostlyBehind = husk(helper, new Vec3(5.3, 1, 3.5));
        learn(helper, player, GeneratedSpells.TEST_CLEAVE);
        assertCast(helper, player, GeneratedSpells.TEST_CLEAVE, null);
        helper.assertTrue(close(mostlyBehind.getHealth(), HEALTH), "a husk mostly behind the corner was hit");
        mostlyBehind.discard();
        Husk mostlyOpen = husk(helper, new Vec3(5.6, 1, 3.5));
        Spells.resetCooldowns(player);
        assertCast(helper, player, GeneratedSpells.TEST_CLEAVE, null);
        helper.assertTrue(close(mostlyOpen.getHealth(), HEALTH - 5), "a husk mostly in the open took "
                + (HEALTH - mostlyOpen.getHealth()));
        logout(player);
        helper.succeed();
    }

    /** A wall of stone 3 blocks tall at z = 2, from x = {@code fromX} to {@code toX}. */
    private static void wall(GameTestHelper helper, int fromX, int toX) {
        for (int x = fromX; x <= toX; x++) {
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(new BlockPos(x, y, 2), Blocks.STONE);
            }
        }
    }

    /** A player with a stone sword and 100 rage, facing south from {@code where}, who never crits or dodges. */
    private static ServerPlayer warrior(GameTestHelper helper, String name, Vec3 where) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, name, where);
        player.setGameMode(GameType.SURVIVAL);
        player.connection.markClientLoaded();
        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 0);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        Resources.set(player, ResourceTypes.RAGE, 100);
        return player;
    }

    /** A player with 100 mana and no Spell Power, facing south from the platform's north edge, who never crits. */
    private static ServerPlayer mage(GameTestHelper helper, String name) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, name, EDGE);
        player.setGameMode(GameType.SURVIVAL);
        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 0);
        StatsCommands.setBase(player, GeneratedStats.SPELL_POWER, 0);
        StatsCommands.setBase(player, GeneratedStats.MAX_MANA, 100);
        Resources.tick(player);
        return player;
    }

    /** A husk that stands still at {@code where}, with 1000 health and no armor, at level 1. */
    private static Husk husk(GameTestHelper helper, Vec3 where) {
        Husk husk = helper.spawn(EntityType.HUSK, where);
        husk.setNoAi(true);
        MobLevels.setLevel(husk, EntityLevel.MIN);
        CombatGameTests.setBase(husk, Attributes.MAX_HEALTH, HEALTH);
        CombatGameTests.setBase(husk, Attributes.ARMOR, 0);
        CombatGameTests.setBase(husk, Attributes.KNOCKBACK_RESISTANCE, 1);
        husk.setHealth((float) HEALTH);
        return husk;
    }

    private static void learn(GameTestHelper helper, ServerPlayer player, ResourceKey<Spell> spell) {
        Holder.Reference<Spell> holder = Spells.get(helper.getLevel().registryAccess(), spell)
                .orElseThrow(() -> new IllegalStateException("no spell " + spell.identifier()));
        Spells.learn(player, holder, 1);
    }

    private static void assertCast(GameTestHelper helper, LivingEntity caster, ResourceKey<Spell> spell,
            @Nullable LivingEntity target) {
        CastResult result = SpellCaster.cast(caster, spell, target);
        helper.assertTrue(result.success(), spell.identifier() + " was refused: " + result.failure());
    }

    private static void logout(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }
}
