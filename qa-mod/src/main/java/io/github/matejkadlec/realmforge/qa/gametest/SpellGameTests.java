package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.character.CharacterCreation;
import io.github.matejkadlec.realmforge.character.CharacterRegistries;
import io.github.matejkadlec.realmforge.generated.GeneratedSpells;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.mob.MobLevels;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import io.github.matejkadlec.realmforge.spell.CastFailure;
import io.github.matejkadlec.realmforge.spell.CastResult;
import io.github.matejkadlec.realmforge.spell.CombatState;
import io.github.matejkadlec.realmforge.spell.ResourceType;
import io.github.matejkadlec.realmforge.spell.ResourceTypes;
import io.github.matejkadlec.realmforge.spell.Resources;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellAttachments;
import io.github.matejkadlec.realmforge.spell.SpellCaster;
import io.github.matejkadlec.realmforge.spell.SpellRegistries;
import io.github.matejkadlec.realmforge.spell.Spells;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * C3a-1 (RLM-209): the spell engine in a world (architecture 11). Players cast the test spells of
 * {@code content/spells/} through {@link SpellCaster} at husks that stand still; every test sets the stats it reads
 * (crit chance 0 unless a test wants crits). Expected numbers follow the content: {@code test_bolt} deals 6 fire damage
 * (+2 a level) for 10 mana (+5 a level) with a 3 s cooldown, {@code test_mend} heals 4 (+1 a level) for 8 mana,
 * {@code test_strike} deals 2 + the caster's attack damage for 15 rage and never crits, {@code test_sure_crit} always
 * crits for 200 %, and {@code test_npc_bolt} is for mobs only.
 */
public final class SpellGameTests {
    private static final double TOLERANCE = 1e-4;
    static final double HEALTH = 1000;
    /** Marks this test's husk, as other tests' husks may stand nearby. */
    private static final String TARGET_TAG = "realmforge_qa.spell_target";

    private SpellGameTests() {
    }

    /** A damage spell hits an enemy, pays its cost, and starts its cooldown and the global cooldown. */
    @QaGameTest
    public static void aDamageSpellHitsAnEnemyAndPaysItsCost(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-spell-bolt");
        withMana(player);
        assertPool(helper, player, ResourceTypes.MANA, 100, "a new mana pool starts full");
        Husk husk = husk(helper, new BlockPos(2, 1, 2));
        learn(helper, player, GeneratedSpells.TEST_BOLT, 1);

        long now = helper.getLevel().getGameTime();
        assertCast(helper, player, GeneratedSpells.TEST_BOLT, husk, husk);
        assertHealth(helper, husk, HEALTH - 6, "test_bolt at level 1");
        assertPool(helper, player, ResourceTypes.MANA, 90, "after a cast for 10 mana");
        CombatState state = player.getData(SpellAttachments.COMBAT);
        helper.assertValueEqual(state.cooldowns().get(GeneratedSpells.TEST_BOLT), now + 60, "the 3 s cooldown");
        helper.assertValueEqual(state.globalCooldownEnd(), now + 10, "the 0.5 s global cooldown");

        Spells.resetCooldowns(player);
        learn(helper, player, GeneratedSpells.TEST_BOLT, 3);
        StatsCommands.setBase(player, GeneratedStats.SPELL_POWER, 0.5);
        StatsCommands.setBase(player, GeneratedStats.FIRE_POWER, 0.2);
        assertCast(helper, player, GeneratedSpells.TEST_BOLT, husk, husk);
        assertHealth(helper, husk, HEALTH - 6 - 10 * 1.7,
                "level 3 deals 10, with +50 % spell power and +20 % fire power");
        assertPool(helper, player, ResourceTypes.MANA, 70, "level 3 costs 20 mana");

        Spells.resetCooldowns(player);
        StatsCommands.setBase(player, GeneratedStats.COOLDOWN_REDUCTION, 0.5);
        long later = helper.getLevel().getGameTime();
        assertCast(helper, player, GeneratedSpells.TEST_BOLT, husk, husk);
        state = player.getData(SpellAttachments.COMBAT);
        helper.assertValueEqual(state.cooldowns().get(GeneratedSpells.TEST_BOLT), later + 30,
                "50 % cooldown reduction halves the cooldown");
        helper.assertValueEqual(state.globalCooldownEnd(), later + 10, "but not the global cooldown");
        logout(player);
        helper.succeed();
    }

    /**
     * RLM-214: a spell's school damage pushes nobody, as a spell knocks back only through its knockback effect, while
     * a melee hit still does. The husk has no knockback resistance here; vanilla's knockback sets its velocity, which
     * the test reads right after each hit.
     */
    @QaGameTest
    public static void aSpellHitDealsNoKnockbackButAMeleeHitDoes(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-spell-knockback");
        withMana(player);
        Husk husk = husk(helper, new BlockPos(2, 1, 2));
        CombatGameTests.setBase(husk, Attributes.KNOCKBACK_RESISTANCE, 0);
        learn(helper, player, GeneratedSpells.TEST_BOLT, 1);

        Vec3 before = husk.position();
        assertCast(helper, player, GeneratedSpells.TEST_BOLT, husk, husk);
        assertHealth(helper, husk, HEALTH - 6, "test_bolt hit");
        helper.assertTrue(horizontalSpeed(husk) == 0, "the bolt pushed the husk: " + husk.getDeltaMovement());
        helper.assertTrue(husk.position().equals(before), "the husk left its place: " + husk.position());

        husk.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 1);
        assertHealth(helper, husk, HEALTH - 7, "the melee hit");
        helper.assertTrue(horizontalSpeed(husk) > 0.1, "a melee hit still knocks back: " + husk.getDeltaMovement());
        logout(player);
        helper.succeed();
    }

    private static double horizontalSpeed(LivingEntity entity) {
        return entity.getDeltaMovement().horizontalDistance();
    }

    /**
     * A spell's own cooldown and the global cooldown refuse casts until their end; a spell off the global cooldown
     * goes through it.
     */
    @QaGameTest(timeoutTicks = 120)
    public static void cooldownsRefuseCastsUntilTheyEnd(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-spell-cooldowns");
        withMana(player);
        Husk husk = husk(helper, new BlockPos(2, 1, 2));
        learn(helper, player, GeneratedSpells.TEST_BOLT, 1);
        learn(helper, player, GeneratedSpells.TEST_MEND, 1);
        learn(helper, player, GeneratedSpells.TEST_SURE_CRIT, 1);

        assertCast(helper, player, GeneratedSpells.TEST_BOLT, husk, husk);
        assertRefused(helper, player, GeneratedSpells.TEST_BOLT, husk, CastFailure.ON_COOLDOWN);
        assertRefused(helper, player, GeneratedSpells.TEST_MEND, null, CastFailure.ON_GLOBAL_COOLDOWN);
        assertCast(helper, player, GeneratedSpells.TEST_SURE_CRIT, husk, husk);
        helper.runAfterDelay(21, () -> {
            assertCast(helper, player, GeneratedSpells.TEST_MEND, null, player);
            assertRefused(helper, player, GeneratedSpells.TEST_BOLT, husk, CastFailure.ON_COOLDOWN);
        });
        helper.runAfterDelay(61, () -> {
            assertCast(helper, player, GeneratedSpells.TEST_BOLT, husk, husk);
            logout(player);
            helper.succeed();
        });
    }

    /** A heal mends the caster or an ally, scales with spell power and its school's power, and crits. */
    @QaGameTest
    public static void aHealMendsTheCasterOrAnAllyAndCrits(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-spell-heal");
        withMana(player);
        learn(helper, player, GeneratedSpells.TEST_MEND, 2);
        StatsCommands.setBase(player, GeneratedStats.SPELL_POWER, 0.5);
        StatsCommands.setBase(player, GeneratedStats.ARCANE_POWER, 0.2);

        player.setHealth(5);
        assertCast(helper, player, GeneratedSpells.TEST_MEND, null, player);
        assertHealth(helper, player, 5 + 5 * 1.7, "level 2 heals 5, with +50 % spell power and +20 % arcane power");

        Spells.resetCooldowns(player);
        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 1);
        player.setHealth(5);
        assertCast(helper, player, GeneratedSpells.TEST_MEND, null, player);
        assertHealth(helper, player, 5 + 5 * 1.7 * 1.5, "a healing crit heals 150 %");

        ServerPlayer ally = caster(helper, "qa-spell-heal-ally");
        ally.snapTo(helper.absoluteVec(new Vec3(0.5, 1, 0.5)), 0, 0);
        ally.setHealth(5);
        Spells.resetCooldowns(player);
        assertCast(helper, player, GeneratedSpells.TEST_MEND, ally, ally);
        assertHealth(helper, ally, 5 + 5 * 1.7 * 1.5, "another player is an ally");

        Husk husk = husk(helper, new BlockPos(2, 1, 2));
        husk.setHealth(500);
        Spells.resetCooldowns(player);
        assertRefused(helper, player, GeneratedSpells.TEST_MEND, husk, CastFailure.INVALID_TARGET);
        assertHealth(helper, husk, 500, "an enemy is not healed");
        logout(ally);
        logout(player);
        helper.succeed();
    }

    /**
     * A physical ability adds the caster's attack damage, pays rage, and never crits when its data says so; spell power
     * does nothing for it (Matěj, 2026-10-15).
     */
    @QaGameTest
    public static void aPhysicalAbilityAddsAttackDamageAndPaysRage(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-spell-strike");
        StatsCommands.setBase(player, Attributes.ATTACK_DAMAGE, 8);
        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 1);
        StatsCommands.setBase(player, GeneratedStats.SPELL_POWER, 1);
        double attackDamage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        helper.assertTrue(attackDamage >= 8, "the caster's attack damage: " + attackDamage);
        Resources.set(player, ResourceTypes.RAGE, 30);
        Husk husk = husk(helper, new BlockPos(2, 1, 2));
        learn(helper, player, GeneratedSpells.TEST_STRIKE, 1);

        assertCast(helper, player, GeneratedSpells.TEST_STRIKE, husk, husk);
        assertHealth(helper, husk, HEALTH - (2 + attackDamage),
                "2 + attack damage; no crit at 100 % crit chance, no spell power");
        assertPool(helper, player, ResourceTypes.RAGE, 15, "after a cast for 15 rage");
        assertCast(helper, player, GeneratedSpells.TEST_STRIKE, husk, husk);
        assertPool(helper, player, ResourceTypes.RAGE, 0, "off the global cooldown, so it casts again at once");
        assertRefused(helper, player, GeneratedSpells.TEST_STRIKE, husk, CastFailure.NOT_ENOUGH_RESOURCE);
        assertHealth(helper, husk, HEALTH - 2 * (2 + attackDamage), "the refused cast dealt nothing");
        logout(player);
        helper.succeed();
    }

    /**
     * Spells crit on the caster's crit chance for 1 + the shared crit damage, or by their own crit data: a bonus chance
     * and a multiplier of their own (Matěj, 2026-10-15).
     */
    @QaGameTest
    public static void spellsCritByTheirCritData(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-spell-crit");
        withMana(player);
        Husk husk = husk(helper, new BlockPos(2, 1, 2));
        learn(helper, player, GeneratedSpells.TEST_BOLT, 1);
        learn(helper, player, GeneratedSpells.TEST_SURE_CRIT, 1);

        assertDealt(helper, player, GeneratedSpells.TEST_SURE_CRIT, husk, 5 * 2,
                "test_sure_crit at 0 % crit chance: its 100 % bonus always crits, for its own 200 %");
        StatsCommands.setBase(player, GeneratedStats.CRIT_DAMAGE, 3);
        assertDealt(helper, player, GeneratedSpells.TEST_SURE_CRIT, husk, 5 * 2, "its own multiplier, not crit damage");

        StatsCommands.setBase(player, GeneratedStats.CRIT_DAMAGE, 0.5);
        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 1);
        assertDealt(helper, player, GeneratedSpells.TEST_BOLT, husk, 6 * 1.5,
                "test_bolt at 100 % crit chance: 150 % with the base crit damage");
        Spells.resetCooldowns(player);
        StatsCommands.setBase(player, GeneratedStats.CRIT_DAMAGE, 1);
        assertDealt(helper, player, GeneratedSpells.TEST_BOLT, husk, 6 * 2,
                "crit damage is shared with melee, so +100 % crit damage doubles a spell crit");
        logout(player);
        helper.succeed();
    }

    /** Every failure reason refuses the cast without its cost, its cooldowns or its effects (research SP-3). */
    @QaGameTest
    public static void refusedCastsPayNothingAndDoNothing(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-spell-refused");
        withMana(player);
        Resources.set(player, ResourceTypes.MANA, 5);
        Husk husk = husk(helper, new BlockPos(2, 1, 2));
        ServerPlayer other = caster(helper, "qa-spell-refused-other");
        other.snapTo(helper.absoluteVec(new Vec3(0.5, 1, 0.5)), 0, 0);

        assertRefused(helper, player, GeneratedSpells.TEST_BOLT, husk, CastFailure.NOT_KNOWN);
        assertRefused(helper, player, ResourceKey.create(SpellRegistries.SPELL, Realmforge.id("no_such_spell")), husk,
                CastFailure.UNKNOWN_SPELL);
        assertRefused(helper, player, GeneratedSpells.TEST_NPC_BOLT, husk, CastFailure.NPC_ONLY);
        learn(helper, player, GeneratedSpells.TEST_BOLT, 1);
        learn(helper, player, GeneratedSpells.TEST_MEND, 1);
        assertRefused(helper, player, GeneratedSpells.TEST_BOLT, husk, CastFailure.NOT_ENOUGH_RESOURCE);
        Resources.set(player, ResourceTypes.MANA, 100);
        assertRefused(helper, player, GeneratedSpells.TEST_BOLT, null, CastFailure.NO_TARGET);
        assertRefused(helper, player, GeneratedSpells.TEST_BOLT, player, CastFailure.INVALID_TARGET);
        assertRefused(helper, player, GeneratedSpells.TEST_BOLT, other, CastFailure.INVALID_TARGET);
        player.snapTo(helper.absoluteVec(new Vec3(2.5, 1, -20)), 0, 0);
        assertRefused(helper, player, GeneratedSpells.TEST_BOLT, husk, CastFailure.OUT_OF_RANGE);
        player.snapTo(helper.absoluteVec(new Vec3(2.5, 1, 0.5)), 0, 0);
        player.setGameMode(GameType.SPECTATOR);
        assertRefused(helper, player, GeneratedSpells.TEST_BOLT, husk, CastFailure.CANNOT_CAST);
        player.setGameMode(GameType.SURVIVAL);

        assertHealth(helper, husk, HEALTH, "no refused cast hurt the husk");
        assertPool(helper, player, ResourceTypes.MANA, 100, "no refused cast paid its cost");
        CombatState state = player.getData(SpellAttachments.COMBAT);
        helper.assertTrue(state.cooldowns().isEmpty(), "no refused cast started a cooldown: " + state.cooldowns());
        helper.assertValueEqual(state.globalCooldownEnd(), 0L, "no refused cast started the global cooldown");
        assertCast(helper, player, GeneratedSpells.TEST_BOLT, husk, husk);
        logout(other);
        logout(player);
        helper.succeed();
    }

    /** Blocks between caster and target refuse the cast; once they are gone, it goes through. */
    @QaGameTest
    public static void aWallBlocksTheLineOfSight(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-spell-sight");
        withMana(player);
        Husk husk = husk(helper, new BlockPos(2, 1, 4));
        learn(helper, player, GeneratedSpells.TEST_BOLT, 1);
        for (int x = 0; x < 5; x++) {
            for (int y = 1; y < 5; y++) {
                helper.setBlock(new BlockPos(x, y, 2), Blocks.STONE);
            }
        }
        assertRefused(helper, player, GeneratedSpells.TEST_BOLT, husk, CastFailure.NO_LINE_OF_SIGHT);
        assertHealth(helper, husk, HEALTH, "the wall took the bolt");
        for (int x = 0; x < 5; x++) {
            for (int y = 1; y < 5; y++) {
                helper.setBlock(new BlockPos(x, y, 2), Blocks.AIR);
            }
        }
        assertCast(helper, player, GeneratedSpells.TEST_BOLT, husk, husk);
        logout(player);
        helper.succeed();
    }

    /**
     * Mana (Matěj, 2026-10-14): a new Mage starts with a full pool of 100, regains its 5 Mana Regeneration every
     * second, never beyond Max Mana, and a pool above a lowered Max Mana drops to it.
     */
    @QaGameTest
    public static void manaRegeneratesEverySecondUpToMaxMana(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-spell-mana");
        helper.assertValueEqual(CharacterCreation.create(player,
                        ResourceKey.create(CharacterRegistries.RACE, Realmforge.id("human")),
                        ResourceKey.create(CharacterRegistries.CLASS, Realmforge.id("mage"))),
                CharacterCreation.Result.CREATED, "a human Mage");
        helper.assertTrue(close(player.getAttributeValue(GeneratedStats.MAX_MANA), 100), "a Mage has 100 Max Mana");
        helper.assertFalse(player.getData(SpellAttachments.COMBAT).hasResource(ResourceTypes.MANA.getKey()),
                "no pool before the first second");
        seconds(player, 1);
        assertPool(helper, player, ResourceTypes.MANA, 100, "a new Mage's pool starts full");

        Resources.set(player, ResourceTypes.MANA, 50);
        seconds(player, 1);
        assertPool(helper, player, ResourceTypes.MANA, 55, "5 Mana Regeneration a second");
        seconds(player, 2);
        assertPool(helper, player, ResourceTypes.MANA, 65, "two more seconds");
        Resources.set(player, ResourceTypes.MANA, 98);
        seconds(player, 1);
        assertPool(helper, player, ResourceTypes.MANA, 100, "never beyond Max Mana");

        player.getAttribute(GeneratedStats.MAX_MANA).addOrReplacePermanentModifier(new AttributeModifier(
                Realmforge.id("qa_spell_test"), -20, AttributeModifier.Operation.ADD_VALUE));
        seconds(player, 1);
        assertPool(helper, player, ResourceTypes.MANA, 80, "a lowered Max Mana caps the pool");
        logout(player);
        helper.succeed();
    }

    /** Mobs cast through the same engine, without a spellbook or a cost, and may cast spells for mobs only. */
    @QaGameTest
    public static void aMobCastsThroughTheSameEngine(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 0));
        zombie.setNoAi(true);
        MobLevels.setLevel(zombie, EntityLevel.MIN);
        Husk husk = husk(helper, new BlockPos(2, 1, 3));
        assertCast(helper, zombie, GeneratedSpells.TEST_NPC_BOLT, husk, husk);
        assertHealth(helper, husk, HEALTH - 3, "test_npc_bolt from a zombie");
        assertRefused(helper, zombie, GeneratedSpells.TEST_BOLT, husk, CastFailure.ON_GLOBAL_COOLDOWN);
        Spells.resetCooldowns(zombie);
        assertCast(helper, zombie, GeneratedSpells.TEST_BOLT, husk, husk);
        assertHealth(helper, husk, HEALTH - 3 - 6, "a mob casts any spell at level 1, for free");
        assertRefused(helper, zombie, GeneratedSpells.TEST_BOLT, husk, CastFailure.ON_COOLDOWN);
        helper.succeed();
    }

    /** {@code /qa spell} reaches the engine: learn a spell, set a pool, cast at a selected target, end the cooldowns. */
    @QaGameTest
    public static void spellCommandsDriveTheEngine(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = caster(helper, "qa-spell-commands");
        StatsCommands.setBase(player, GeneratedStats.MAX_MANA, 100);
        Husk husk = husk(helper, new BlockPos(2, 1, 2));
        husk.addTag(TARGET_TAG);
        String cast = "qa spell cast realmforge:test_bolt @n[type=husk,tag=" + TARGET_TAG + "]";

        run(server, player, "qa spell resource mana 40");
        assertPool(helper, player, ResourceTypes.MANA, 40, "/qa spell resource mana 40");
        run(server, player, "qa spell learn realmforge:test_bolt 2");
        helper.assertValueEqual(player.getData(SpellAttachments.SPELLBOOK).level(GeneratedSpells.TEST_BOLT),
                Optional.of(2), "/qa spell learn at level 2");
        run(server, player, cast);
        assertHealth(helper, husk, HEALTH - 8, "/qa spell cast: level 2 deals 8");
        assertPool(helper, player, ResourceTypes.MANA, 25, "and costs 15 mana");
        run(server, player, cast);
        assertHealth(helper, husk, HEALTH - 8, "a cast on cooldown is refused");
        run(server, player, "qa spell reset");
        helper.assertTrue(player.getData(SpellAttachments.COMBAT).cooldowns().isEmpty(), "/qa spell reset");
        run(server, player, cast);
        assertHealth(helper, husk, HEALTH - 16, "after the reset it casts again");
        logout(player);
        helper.succeed();
    }

    /** A fake player at the platform's south edge, in survival, at 0 % crit chance. */
    private static ServerPlayer caster(GameTestHelper helper, String name) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, name, new Vec3(2.5, 1, 0.5));
        player.setGameMode(GameType.SURVIVAL);
        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 0);
        return player;
    }

    /** 100 Max Mana and a full pool, as a Mage starts. */
    private static void withMana(ServerPlayer player) {
        StatsCommands.setBase(player, GeneratedStats.MAX_MANA, 100);
        Resources.tick(player);
    }

    /** A husk that stands still with {@value #HEALTH} health, no armor and level 1. */
    static Husk husk(GameTestHelper helper, BlockPos pos) {
        Husk husk = helper.spawn(EntityType.HUSK, pos);
        husk.setNoAi(true);
        MobLevels.setLevel(husk, EntityLevel.MIN);
        CombatGameTests.setBase(husk, Attributes.MAX_HEALTH, HEALTH);
        CombatGameTests.setBase(husk, Attributes.ARMOR, 0);
        CombatGameTests.setBase(husk, Attributes.KNOCKBACK_RESISTANCE, 1);
        husk.setHealth((float) HEALTH);
        return husk;
    }

    private static void learn(GameTestHelper helper, ServerPlayer player, ResourceKey<Spell> spell, int level) {
        Holder.Reference<Spell> holder = Spells.get(helper.getLevel().registryAccess(), spell)
                .orElseThrow(() -> new IllegalStateException("no spell " + spell.identifier()));
        Spells.learn(player, holder, level);
    }

    private static void assertCast(GameTestHelper helper, LivingEntity caster, ResourceKey<Spell> spell,
            @Nullable LivingEntity target, LivingEntity expectedTarget) {
        CastResult result = SpellCaster.cast(caster, spell, target);
        helper.assertTrue(result.success(), spell.identifier() + " was refused: " + result.failure());
        helper.assertTrue(result.entity().orElse(null) == expectedTarget,
                spell.identifier() + " hit " + result.target());
    }

    private static void assertRefused(GameTestHelper helper, LivingEntity caster, ResourceKey<Spell> spell,
            @Nullable LivingEntity target, CastFailure expected) {
        helper.assertValueEqual(SpellCaster.cast(caster, spell, target).failureOrNull(), expected,
                spell.identifier() + " refused");
    }

    private static void assertDealt(GameTestHelper helper, ServerPlayer caster, ResourceKey<Spell> spell,
            LivingEntity target, double expected, String what) {
        float before = target.getHealth();
        assertCast(helper, caster, spell, target, target);
        double dealt = before - target.getHealth();
        helper.assertTrue(close(dealt, expected), what + ": " + expected + " expected, dealt " + dealt);
    }

    private static void assertHealth(GameTestHelper helper, LivingEntity entity, double expected, String what) {
        helper.assertTrue(close(entity.getHealth(), expected), what + ": health " + expected + " expected, got "
                + entity.getHealth());
    }

    private static void assertPool(GameTestHelper helper, LivingEntity holder, Holder<ResourceType> type,
            double expected, String what) {
        float amount = Resources.amount(holder, type);
        helper.assertTrue(close(amount, expected), what + ": " + type.getRegisteredName() + " " + expected
                + " expected, got " + amount);
    }

    /**
     * Plays {@code count} seconds of player ticks: the level counts its {@code tickCount}, then the connection runs its
     * tick ({@code doTick}), which a fake player's connection never does by itself.
     */
    private static void seconds(ServerPlayer player, int count) {
        for (int i = 0; i < count * Resources.INTERVAL_TICKS; i++) {
            player.tickCount++;
            player.doTick();
        }
    }

    private static void run(MinecraftServer server, ServerPlayer player, String command) {
        CommandSourceStack source = player.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS);
        server.getCommands().performPrefixedCommand(source, command);
    }

    private static void logout(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }

    private static boolean close(double got, double want) {
        return Math.abs(got - want) <= TOLERANCE;
    }
}
