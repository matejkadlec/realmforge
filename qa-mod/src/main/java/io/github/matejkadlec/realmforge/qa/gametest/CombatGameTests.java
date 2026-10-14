package io.github.matejkadlec.realmforge.qa.gametest;

import com.mojang.authlib.GameProfile;
import io.github.matejkadlec.realmforge.combat.SpellSchool;
import io.github.matejkadlec.realmforge.combat.SpellSchools;
import io.github.matejkadlec.realmforge.core.CoreRegistries;
import io.github.matejkadlec.realmforge.core.Curve;
import io.github.matejkadlec.realmforge.generated.GeneratedCurves;
import io.github.matejkadlec.realmforge.generated.GeneratedSpellSchools;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.mob.MobLevels;
import io.github.matejkadlec.realmforge.mob.MobScaling;
import io.github.matejkadlec.realmforge.qa.combat.CombatLog;
import io.github.matejkadlec.realmforge.qa.combat.TrainingDummy;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/**
 * F5: the damage pipeline in a world (architecture 9.1), on husks that stand still and a zombie or a fake player that
 * hits them. Expected numbers follow the content: the armor curves at the attacker's level, the stats set here. The
 * hits of one test land within one game tick, so a hurt cooldown window opened by the first stays open.
 *
 * <p>Husks carry our stats in the dev game (the QA mod's {@code TrainingDummy#addStats}), so a test sets their dodge
 * chance and resistances.
 */
public final class CombatGameTests {
    private static final double TOLERANCE = 1e-4;
    private static final double HEALTH = 1000;

    private CombatGameTests() {
    }

    /**
     * Our armor formula replaces vanilla's, at the attacker's level; damage that bypasses armor keeps it all. A
     * leveled zombie's hit also grows with its level (W1), before the armor takes its share.
     */
    @QaGameTest
    public static void armorReducesHitsByTheAttackersLevel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie zombie = attacker(helper);
        Husk husk = target(helper);
        setBase(husk, Attributes.ARMOR, 15);
        setBase(husk, Attributes.ARMOR_TOUGHNESS, 5);

        assertDealt(helper, husk, level.damageSources().mobAttack(zombie), 10, 10 * (1 - blocked(helper, 20, 1)),
                "a level 1 zombie against 15 armor and 5 toughness");
        settle(husk);
        MobLevels.setLevel(zombie, 20);
        helper.assertTrue(blocked(helper, 20, 20) < blocked(helper, 20, 1), "a level 20 attacker meets less reduction");
        double hit = 10 * MobScaling.at(level.registryAccess(), 20).damage();
        assertDealt(helper, husk, level.damageSources().mobAttack(zombie), 10, hit * (1 - blocked(helper, 20, 20)),
                "a level 20 zombie");
        settle(husk);
        assertDealt(helper, husk, level.damageSources().magic(), 10, 10, "magic bypasses armor");
        helper.succeed();
    }

    /** Done when of F5: school damage right after a melee hit lands in full, and leaves the window as it was. */
    @QaGameTest
    public static void schoolDamageRightAfterMeleeIsNotSwallowed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie zombie = attacker(helper);
        Husk husk = target(helper);
        assertDealt(helper, husk, level.damageSources().mobAttack(zombie), 6, 6, "the melee hit");
        int window = husk.invulnerableTime;
        helper.assertTrue(window > 10, "the melee hit opened the hurt cooldown window: " + window);

        assertDealt(helper, husk, school(helper, GeneratedSpellSchools.FIRE, zombie), 3, 3,
                "a smaller fire hit inside the window (vanilla would drop it)");
        assertDealt(helper, husk, school(helper, GeneratedSpellSchools.FROST, zombie), 20, 20,
                "a bigger frost hit lands in full, not just the difference");
        helper.assertValueEqual(husk.invulnerableTime, window, "school hits keep the window");
        helper.assertTrue(close(husk.lastHurt, 6), "school hits leave lastHurt at the melee hit: " + husk.lastHurt);
        assertDealt(helper, husk, level.damageSources().mobAttack(zombie), 8, 2,
                "the next melee hit in the window deals its difference to the previous melee hit");
        helper.succeed();
    }

    /**
     * Roadmap F5: a melee hit after a damage-over-time tick inside the window. Spell hits restore {@code lastHurt}, so
     * the melee hit is compared with the melee hit that opened the window, as in vanilla (architecture 9.1): not
     * bigger, it is dropped. Without the restore it would have been compared with the tick and dealt 5.
     */
    @QaGameTest
    public static void meleeAfterDotTickInTheWindowIsComparedWithTheMeleeHit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie zombie = attacker(helper);
        Husk husk = target(helper);
        assertDealt(helper, husk, level.damageSources().mobAttack(zombie), 6, 6, "the melee hit");
        assertDealt(helper, husk, school(helper, GeneratedSpellSchools.FIRE, zombie), 1, 1, "the damage-over-time tick");
        assertDealt(helper, husk, level.damageSources().mobAttack(zombie), 6, 0,
                "a second melee hit for 6 in the window");
        helper.succeed();
    }

    /** Roadmap F5: a dodged hit deals nothing and leaves no damage container behind; magic and explosions land. */
    @QaGameTest
    public static void aDodgedHitLeavesNoDamageContainer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie zombie = attacker(helper);
        Husk husk = target(helper);
        setBase(husk, GeneratedStats.DODGE_CHANCE, 1);
        helper.assertTrue(husk.damageContainers.isEmpty(), "no damage container before the hits");
        for (int i = 0; i < 20; i++) {
            helper.assertFalse(husk.hurtServer(level, level.damageSources().mobAttack(zombie), 5),
                    "a dodged hit reports no damage");
        }
        helper.assertTrue(close(husk.getHealth(), HEALTH), "dodged hits dealt nothing: " + husk.getHealth());
        helper.assertTrue(husk.damageContainers.isEmpty(),
                "dodged hits left " + husk.damageContainers.size() + " damage containers");

        assertDealt(helper, husk, school(helper, GeneratedSpellSchools.ARCANE, zombie), 3, 3, "magic is not dodged");
        settle(husk);
        assertDealt(helper, husk, level.damageSources().explosion(zombie, zombie), 4, 4, "an explosion is not dodged");
        settle(husk);
        assertDealt(helper, husk, school(helper, GeneratedSpellSchools.PHYSICAL, zombie), 4, 0,
                "a physical school hit is dodged");
        helper.assertTrue(husk.damageContainers.isEmpty(), "no damage container after the hits");
        helper.succeed();
    }

    /** A player's full-strength melee hit crits on crit chance (not on a jump) and deals 1 + crit damage. */
    @QaGameTest
    public static void playerMeleeCritsOnCritChance(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = QaPlayers.login(server, new GameProfile(UUID.randomUUID(), "qa-combat-crit"));
        Husk husk = target(helper);
        double fist = player.getAttributeValue(Attributes.ATTACK_DAMAGE);

        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 0);
        assertMelee(helper, player, husk, fist, "a hit at 0 % crit chance");
        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 1);
        assertMelee(helper, player, husk, fist * 1.5, "a crit at 100 % crit chance and the base 50 % crit damage");
        StatsCommands.setBase(player, GeneratedStats.CRIT_DAMAGE, 1);
        assertMelee(helper, player, husk, fist * 2, "a crit at 100 % crit damage");
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    /** School hits: spell power for magic, the school's power, the defender's resistance; physical meets armor. */
    @QaGameTest
    public static void schoolHitsGetPowerAndResistance(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = QaPlayers.login(server, new GameProfile(UUID.randomUUID(), "qa-combat-school"));
        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 0);
        StatsCommands.setBase(player, GeneratedStats.SPELL_POWER, 0.5);
        StatsCommands.setBase(player, GeneratedStats.FIRE_POWER, 0.2);
        Husk husk = target(helper);
        setBase(husk, Attributes.ARMOR, 15);
        setBase(husk, GeneratedStats.FIRE_RESISTANCE, 0.25);

        assertDealt(helper, husk, school(helper, GeneratedSpellSchools.FIRE, player), 10, 10 * 1.7 * 0.75,
                "fire: +50 % spell power, +20 % fire power, 25 % fire resistance, armor ignored");
        settle(husk);
        assertDealt(helper, husk, school(helper, GeneratedSpellSchools.FROST, player), 10, 10 * 1.5,
                "frost: spell power only");
        settle(husk);
        assertDealt(helper, husk, school(helper, GeneratedSpellSchools.PHYSICAL, player), 10,
                10 * (1 - blocked(helper, 15, 1)), "physical: no spell power, armor applies");
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    /** The training dummy never dies, refills after every hit, meters the damage and logs it. */
    @QaGameTest
    public static void trainingDummyMetersAndLogsHits(GameTestHelper helper) throws IOException {
        ServerLevel level = helper.getLevel();
        Zombie zombie = attacker(helper);
        Husk dummy = TrainingDummy.spawn(level, helper.absoluteVec(new Vec3(2.5, 1, 2.5)), 0, 0);
        dummy.hurtServer(level, level.damageSources().mobAttack(zombie), 30);
        helper.assertTrue(close(dummy.getHealth(), dummy.getMaxHealth()), "the dummy refilled after the hit");
        TrainingDummy.Session session = TrainingDummy.session(dummy);
        helper.assertTrue(session != null && close(session.total(), 30) && session.hits() == 1,
                "the meter counted the hit: " + session);

        settle(dummy);
        dummy.hurtServer(level, level.damageSources().magic(), 5000);
        helper.assertTrue(dummy.isAlive() && close(dummy.getHealth(), dummy.getMaxHealth()),
                "a hit bigger than its health does not kill it");
        String log = Files.readString(CombatLog.file(level.getServer()));
        helper.assertTrue(log.contains("hit target=training_dummy attacker=Zombie type=minecraft:mob_attack"),
                "the combat log has the zombie's hit on the dummy");
        dummy.discard();
        helper.succeed();
    }

    /** The share armor blocks at an attacker level, from the content's curves (architecture 9.1). */
    private static double blocked(GameTestHelper helper, double armor, int attackerLevel) {
        Registry<Curve> curves = helper.getLevel().registryAccess().lookupOrThrow(CoreRegistries.CURVE);
        double halving = curves.getValue(GeneratedCurves.ARMOR_HALVING).valueAt(attackerLevel);
        double cap = curves.getValue(GeneratedCurves.ARMOR_REDUCTION_CAP).valueAt(attackerLevel);
        return Math.min(cap, armor / (armor + halving));
    }

    private static void assertDealt(GameTestHelper helper, LivingEntity target, DamageSource source, float damage,
            double expected, String what) {
        float before = target.getHealth();
        target.hurtServer(helper.getLevel(), source, damage);
        double dealt = before - target.getHealth();
        helper.assertTrue(close(dealt, expected), what + ": " + expected + " expected, dealt " + dealt);
    }

    /** Attacks with the player's fist after the attack strength has recharged. */
    private static void assertMelee(GameTestHelper helper, ServerPlayer player, LivingEntity target, double expected,
            String what) {
        for (int i = 0; i < 20; i++) {
            player.doTick();
        }
        settle(target);
        float before = target.getHealth();
        player.attack(target);
        double dealt = before - target.getHealth();
        helper.assertTrue(close(dealt, expected), what + ": " + expected + " expected, dealt " + dealt);
    }

    /** Closes the target's hurt cooldown window, so the next hit counts in full. */
    private static void settle(LivingEntity target) {
        target.invulnerableTime = 0;
    }

    private static DamageSource school(GameTestHelper helper, ResourceKey<SpellSchool> school, LivingEntity caster) {
        return SpellSchools.damage(helper.getLevel().registryAccess(), school, caster, caster);
    }

    /** A zombie that stands still, at level 1, so its level neither scales its hits nor weakens the target's armor. */
    private static Zombie attacker(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(0, 1, 0));
        zombie.setNoAi(true);
        MobLevels.setLevel(zombie, EntityLevel.MIN);
        return zombie;
    }

    /**
     * A husk that stands still with {@value #HEALTH} health, so no test kills it, at level 1, so the armor its level
     * would add is not there.
     */
    private static Husk target(GameTestHelper helper) {
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(2, 1, 2));
        husk.setNoAi(true);
        MobLevels.setLevel(husk, EntityLevel.MIN);
        setBase(husk, Attributes.MAX_HEALTH, HEALTH);
        setBase(husk, Attributes.ARMOR, 0);
        setBase(husk, Attributes.KNOCKBACK_RESISTANCE, 1);
        husk.setHealth((float) HEALTH);
        return husk;
    }

    private static void setBase(LivingEntity entity, Holder<Attribute> attribute, double value) {
        var instance = entity.getAttribute(attribute);
        if (instance == null) {
            throw new IllegalStateException(entity.getType() + " lacks " + attribute.getRegisteredName());
        }
        instance.setBaseValue(value);
    }

    private static boolean close(double got, double want) {
        return Math.abs(got - want) <= TOLERANCE;
    }
}
