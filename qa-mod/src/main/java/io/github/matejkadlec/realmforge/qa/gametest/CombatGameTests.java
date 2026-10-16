package io.github.matejkadlec.realmforge.qa.gametest;

import com.mojang.authlib.GameProfile;
import io.github.matejkadlec.realmforge.combat.DamageOverTime;
import io.github.matejkadlec.realmforge.combat.SpellSchool;
import io.github.matejkadlec.realmforge.combat.SpellSchools;
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
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;

/**
 * F5: the damage pipeline in a world (architecture 9.1), on pillagers that stand still and a zombie or a fake player
 * that hits them. Expected numbers follow vanilla's armor formula ({@link #blocked}) and the stats set here.
 * The hits of one test land within one game tick, so a hurt cooldown window opened by the first stays open.
 *
 * <p>Pillagers carry our stats in the dev game (the QA mod's {@code TrainingDummy#addStats}), so a test sets their
 * dodge chance and resistances.
 */
public final class CombatGameTests {
    private static final double TOLERANCE = 1e-4;
    private static final double HEALTH = 1000;

    private CombatGameTests() {
    }

    /**
     * RLM-223: hits meet vanilla's armor formula, with armor and toughness kept apart, so armor blocks less of a
     * bigger hit; damage that bypasses armor keeps it all. A leveled zombie's hit grows with its level (W1) before
     * the armor takes its share, which is how the attacker's level reaches armor.
     */
    @QaGameTest
    public static void armorFollowsVanillasFormula(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie zombie = attacker(helper);
        Pillager mob = target(helper);
        setBase(mob, Attributes.ARMOR, 15);
        setBase(mob, Attributes.ARMOR_TOUGHNESS, 5);

        assertDealt(helper, mob, level.damageSources().mobAttack(zombie), 10, 10 * (1 - blocked(mob, 10, 15, 5)),
                "a level 1 zombie against 15 armor and 5 toughness");
        helper.assertTrue(close(blocked(mob, 10, 15, 5), (15 - 10 / 3.25) / 25),
                "vanilla's formula: 15 − 10 / (2 + 5 / 4) effective armor, 4 % each");
        helper.assertTrue(blocked(mob, 10, 15, 5) != blocked(mob, 10, 20, 0),
                "toughness is not armor: 15 armor and 5 toughness block another share than 20 armor");
        settle(mob);
        MobLevels.setLevel(zombie, 20);
        double hit = 10 * MobScaling.at(level.registryAccess(), 20).damage();
        helper.assertTrue(blocked(mob, hit, 15, 5) < blocked(mob, 10, 15, 5), "the bigger hit meets less reduction");
        assertDealt(helper, mob, level.damageSources().mobAttack(zombie), 10, hit * (1 - blocked(mob, hit, 15, 5)),
                "a level 20 zombie");
        settle(mob);
        assertDealt(helper, mob, level.damageSources().magic(), 10, 10, "magic bypasses armor");
        helper.succeed();
    }

    /** Done when of F5: school damage right after a melee hit lands in full, and leaves the window as it was. */
    @QaGameTest
    public static void schoolDamageRightAfterMeleeIsNotSwallowed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie zombie = attacker(helper);
        Pillager mob = target(helper);
        assertDealt(helper, mob, level.damageSources().mobAttack(zombie), 6, 6, "the melee hit");
        int window = mob.invulnerableTime;
        helper.assertTrue(window > 10, "the melee hit opened the hurt cooldown window: " + window);

        assertDealt(helper, mob, school(helper, GeneratedSpellSchools.FIRE, zombie), 3, 3,
                "a smaller fire hit inside the window (vanilla would drop it)");
        assertDealt(helper, mob, school(helper, GeneratedSpellSchools.FROST, zombie), 20, 20,
                "a bigger frost hit lands in full, not just the difference");
        helper.assertValueEqual(mob.invulnerableTime, window, "school hits keep the window");
        helper.assertTrue(close(mob.lastHurt, 6), "school hits leave lastHurt at the melee hit: " + mob.lastHurt);
        assertDealt(helper, mob, level.damageSources().mobAttack(zombie), 8, 2,
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
        Pillager mob = target(helper);
        assertDealt(helper, mob, level.damageSources().mobAttack(zombie), 6, 6, "the melee hit");
        assertDealt(helper, mob, school(helper, GeneratedSpellSchools.FIRE, zombie), 1, 1, "the damage-over-time tick");
        assertDealt(helper, mob, level.damageSources().mobAttack(zombie), 6, 0,
                "a second melee hit for 6 in the window");
        helper.succeed();
    }

    /** Roadmap F5: a dodged hit deals nothing and leaves no damage container behind; magic and explosions land. */
    @QaGameTest
    public static void aDodgedHitLeavesNoDamageContainer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie zombie = attacker(helper);
        Pillager mob = target(helper);
        setBase(mob, GeneratedStats.DODGE_CHANCE, 1);
        helper.assertTrue(mob.damageContainers.isEmpty(), "no damage container before the hits");
        for (int i = 0; i < 20; i++) {
            helper.assertFalse(mob.hurtServer(level, level.damageSources().mobAttack(zombie), 5),
                    "a dodged hit reports no damage");
        }
        helper.assertTrue(close(mob.getHealth(), HEALTH), "dodged hits dealt nothing: " + mob.getHealth());
        helper.assertTrue(mob.damageContainers.isEmpty(),
                "dodged hits left " + mob.damageContainers.size() + " damage containers");

        assertDealt(helper, mob, school(helper, GeneratedSpellSchools.ARCANE, zombie), 3, 3, "magic is not dodged");
        settle(mob);
        assertDealt(helper, mob, level.damageSources().explosion(zombie, zombie), 4, 4, "an explosion is not dodged");
        settle(mob);
        assertDealt(helper, mob, school(helper, GeneratedSpellSchools.PHYSICAL, zombie), 4, 0,
                "a physical school hit is dodged");
        helper.assertTrue(mob.damageContainers.isEmpty(), "no damage container after the hits");
        helper.succeed();
    }

    /** A player's full-strength melee hit crits on crit chance (not on a jump) and deals 1 + crit damage. */
    @QaGameTest
    public static void playerMeleeCritsOnCritChance(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = QaPlayers.login(server, new GameProfile(UUID.randomUUID(), "qa-combat-crit"));
        Pillager mob = target(helper);
        double fist = player.getAttributeValue(Attributes.ATTACK_DAMAGE);

        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 0);
        assertMelee(helper, player, mob, fist, "a hit at 0 % crit chance");
        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 1);
        assertMelee(helper, player, mob, fist * 1.5, "a crit at 100 % crit chance and the base 50 % crit damage");
        StatsCommands.setBase(player, GeneratedStats.CRIT_DAMAGE, 1);
        assertMelee(helper, player, mob, fist * 2, "a crit at 100 % crit damage");
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
        Pillager mob = target(helper);
        setBase(mob, Attributes.ARMOR, 15);
        setBase(mob, GeneratedStats.FIRE_RESISTANCE, 0.25);

        assertDealt(helper, mob, school(helper, GeneratedSpellSchools.FIRE, player), 10, 10 * 1.7 * 0.75,
                "fire: +50 % spell power, +20 % fire power, 25 % fire resistance, armor ignored");
        settle(mob);
        assertDealt(helper, mob, school(helper, GeneratedSpellSchools.FROST, player), 10, 10 * 1.5,
                "frost: spell power only");
        settle(mob);
        assertDealt(helper, mob, school(helper, GeneratedSpellSchools.PHYSICAL, player), 10,
                10 * (1 - blocked(mob, 10, 15, 0)), "physical: no spell power, armor applies");
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    /** The training dummy never dies, refills after every hit, meters the damage and logs it. */
    @QaGameTest
    public static void trainingDummyMetersAndLogsHits(GameTestHelper helper) throws IOException {
        ServerLevel level = helper.getLevel();
        Zombie zombie = attacker(helper);
        Pillager dummy = TrainingDummy.spawn(level, helper.absoluteVec(new Vec3(2.5, 1, 2.5)), 0, 0);
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

    /**
     * Done when of RLM-187: the training dummy takes damage and healing the way a player does. Poison and harming
     * hurt it (its meter counts them), healing heals it, and daylight never burns it.
     */
    @QaGameTest
    public static void trainingDummyTakesPoisonHarmingAndHealingLikeAPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Pillager dummy = TrainingDummy.spawn(level, helper.absoluteVec(new Vec3(2.5, 1, 2.5)), 0, 0);
        helper.assertFalse(dummy.isInvertedHealAndHarm(), "the dummy is not undead");
        helper.assertFalse(dummy.is(EntityTypeTags.BURN_IN_DAYLIGHT), "daylight does not burn the dummy");
        helper.assertTrue(dummy.getMainHandItem().isEmpty(), "the dummy holds no crossbow");

        helper.assertTrue(dummy.addEffect(new MobEffectInstance(MobEffects.POISON, 100)), "poison takes hold");
        MobEffects.POISON.value().applyEffectTick(level, dummy, 0);
        TrainingDummy.Session session = TrainingDummy.session(dummy);
        helper.assertTrue(session != null && close(session.total(), 1), "a poison tick hurt the dummy: " + session);

        TrainingDummy.reset(dummy);
        settle(dummy);
        MobEffects.INSTANT_DAMAGE.value().applyInstantenousEffect(level, null, null, dummy, 0, 1);
        session = TrainingDummy.session(dummy);
        helper.assertTrue(session != null && close(session.total(), 6), "harming hurt the dummy: " + session);

        dummy.setHealth(500);
        MobEffects.INSTANT_HEALTH.value().applyInstantenousEffect(level, null, null, dummy, 0, 1);
        helper.assertTrue(close(dummy.getHealth(), 504), "healing healed the dummy: " + dummy.getHealth());
        TrainingDummy.remove(dummy);
        helper.succeed();
    }

    /**
     * Done when of RLM-186: the player who threw a splash potion of poison is recorded as its applier, and a poison
     * tick, which has no attacker, is attributed to that player in dark green. A lingering cloud's owner applies
     * wither the same way; an application by a mob forgets the player.
     */
    @QaGameTest
    public static void aPoisonTickIsAttributedToThePlayerWhoThrewThePotion(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ServerPlayer thrower = QaPlayers.login(server, new GameProfile(UUID.randomUUID(), "qa-combat-poisoner"));
        Pillager mob = target(helper);
        ItemStack stack = PotionContents.createItemStack(Items.SPLASH_POTION, Potions.POISON);
        ThrownSplashPotion potion = new ThrownSplashPotion(level, thrower, stack);
        potion.setPos(mob.position());
        potion.onHitAsPotion(level, stack, new EntityHitResult(mob));
        helper.assertTrue(mob.hasEffect(MobEffects.POISON), "the splash potion poisoned the mob");

        DamageSource poison = new DamageSource(
                level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(NeoForgeMod.POISON_DAMAGE));
        DamageOverTime.Tick tick = DamageOverTime.tick(mob, poison);
        helper.assertTrue(tick != null && tick.applier() == thrower, "the thrower applied the poison: " + tick);
        helper.assertValueEqual(tick.color(), DamageOverTime.POISON, "a poison tick's colour");
        MobEffects.POISON.value().applyEffectTick(level, mob, 0);
        helper.assertTrue(close(mob.getHealth(), HEALTH - 1), "the tick hurt the mob: " + mob.getHealth());
        helper.assertTrue(DamageOverTime.tick(mob, level.damageSources().magic()) == null,
                "magic damage is not a poison tick");
        helper.assertTrue(DamageOverTime.tick(mob, level.damageSources().wither()) == null,
                "a wither tick without the wither effect is attributed to nobody");

        AreaEffectCloud cloud = new AreaEffectCloud(level, mob.getX(), mob.getY(), mob.getZ());
        cloud.setOwner(thrower);
        mob.addEffect(new MobEffectInstance(MobEffects.WITHER, 200), cloud);
        tick = DamageOverTime.tick(mob, level.damageSources().wither());
        helper.assertTrue(tick != null && tick.applier() == thrower, "the cloud's owner applied the wither: " + tick);
        helper.assertValueEqual(tick.color(), DamageOverTime.WITHER, "a wither tick's colour");

        mob.addEffect(new MobEffectInstance(MobEffects.POISON, 400), attacker(helper));
        helper.assertTrue(DamageOverTime.tick(mob, poison) == null, "a zombie's poison is the latest application");
        mob.removeEffect(MobEffects.WITHER);
        mob.addEffect(new MobEffectInstance(MobEffects.WITHER, 200));
        helper.assertTrue(DamageOverTime.tick(mob, level.damageSources().wither()) == null,
                "a removed effect forgot its applier");
        server.getPlayerList().remove(thrower);
        helper.succeed();
    }

    /**
     * The share of a {@code damage} hit that {@code armor} and {@code toughness} block, by vanilla's own
     * {@code CombatRules#getDamageAfterAbsorb} (architecture 9.1), for a hit without a weapon.
     */
    static double blocked(LivingEntity target, double damage, double armor, double toughness) {
        float left = CombatRules.getDamageAfterAbsorb(target, (float) damage, target.damageSources().generic(),
                (float) armor, (float) toughness);
        return 1 - left / damage;
    }

    static void assertDealt(GameTestHelper helper, LivingEntity target, DamageSource source, float damage,
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
    static void settle(LivingEntity target) {
        target.invulnerableTime = 0;
    }

    static DamageSource school(GameTestHelper helper, ResourceKey<SpellSchool> school, LivingEntity caster) {
        return SpellSchools.damage(helper.getLevel().registryAccess(), school, caster, caster);
    }

    /** A zombie that stands still, at level 1, so its level neither scales its hits nor weakens the target's armor. */
    static Zombie attacker(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(0, 1, 0));
        zombie.setNoAi(true);
        MobLevels.setLevel(zombie, EntityLevel.MIN);
        return zombie;
    }

    /**
     * A pillager that stands still with {@value #HEALTH} health, so no test kills it, at level 1, so the armor its
     * level would add is not there. It is not undead, so poison and harming hurt it and healing heals it.
     */
    static Pillager target(GameTestHelper helper) {
        Pillager mob = helper.spawn(EntityType.PILLAGER, new BlockPos(2, 1, 2));
        mob.setNoAi(true);
        MobLevels.setLevel(mob, EntityLevel.MIN);
        setBase(mob, Attributes.MAX_HEALTH, HEALTH);
        setBase(mob, Attributes.ARMOR, 0);
        setBase(mob, Attributes.KNOCKBACK_RESISTANCE, 1);
        mob.setHealth((float) HEALTH);
        return mob;
    }

    static void setBase(LivingEntity entity, Holder<Attribute> attribute, double value) {
        var instance = entity.getAttribute(attribute);
        if (instance == null) {
            throw new IllegalStateException(entity.getType() + " lacks " + attribute.getRegisteredName());
        }
        instance.setBaseValue(value);
    }

    static boolean close(double got, double want) {
        return Math.abs(got - want) <= TOLERANCE;
    }
}
