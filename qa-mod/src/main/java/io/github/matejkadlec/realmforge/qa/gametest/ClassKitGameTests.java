package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.character.CharacterBonuses;
import io.github.matejkadlec.realmforge.character.CharacterCreation;
import io.github.matejkadlec.realmforge.character.CharacterRegistries;
import io.github.matejkadlec.realmforge.character.Characters;
import io.github.matejkadlec.realmforge.character.RacialPassives;
import io.github.matejkadlec.realmforge.combat.CombatAttachments;
import io.github.matejkadlec.realmforge.combat.CritRule;
import io.github.matejkadlec.realmforge.combat.DamageCalculator;
import io.github.matejkadlec.realmforge.combat.HitBonus;
import io.github.matejkadlec.realmforge.combat.MagicShield;
import io.github.matejkadlec.realmforge.combat.SpellSchools;
import io.github.matejkadlec.realmforge.effect.Effects;
import io.github.matejkadlec.realmforge.generated.GeneratedEffects;
import io.github.matejkadlec.realmforge.generated.GeneratedItems;
import io.github.matejkadlec.realmforge.generated.GeneratedSpellSchools;
import io.github.matejkadlec.realmforge.generated.GeneratedSpells;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import io.github.matejkadlec.realmforge.spell.ApplyEffectEffect;
import io.github.matejkadlec.realmforge.spell.CastEnd;
import io.github.matejkadlec.realmforge.spell.CastFailure;
import io.github.matejkadlec.realmforge.spell.CastResult;
import io.github.matejkadlec.realmforge.spell.EffectContext;
import io.github.matejkadlec.realmforge.spell.ProjectileEffect;
import io.github.matejkadlec.realmforge.spell.ResourceTypes;
import io.github.matejkadlec.realmforge.spell.Resources;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellArea;
import io.github.matejkadlec.realmforge.spell.SpellAttachments;
import io.github.matejkadlec.realmforge.spell.SpellCaster;
import io.github.matejkadlec.realmforge.spell.SpellProjectile;
import io.github.matejkadlec.realmforge.spell.SpellTarget;
import io.github.matejkadlec.realmforge.spell.SpellText;
import io.github.matejkadlec.realmforge.spell.SpellVisuals;
import io.github.matejkadlec.realmforge.spell.Spellbook;
import io.github.matejkadlec.realmforge.spell.Spells;
import io.github.matejkadlec.realmforge.stats.CharacterStats;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * C4 (RLM-27), RLM-499 and RLM-518: the class kits in a world. Human characters learn their class's spells by level
 * and their racial spell; melee spells need a melee weapon and Shield Bash a shield; a stun stops casts, attacks and a
 * mob's AI, and Will To Survive breaks it; Reckless Strike's crit chance above 100 % turns into crit damage; Slam's
 * armor shred and a hit's spell penetration grow with the rank; Ice Barrier fills the Magic Shield at once; Battle
 * Shout reaches allies; Sword Specialization raises a Human's attack damage with a sword. Expected numbers follow
 * {@code content/spells/} and {@code content/effects/}; husks stand still with 1000 health and no armor unless a test
 * gives them some.
 */
public final class ClassKitGameTests {
    private static final double TOLERANCE = 1e-3;
    private static final double HEALTH = SpellGameTests.HEALTH;

    private ClassKitGameTests() {
    }

    /** Creation teaches the class's level 1 spell onto the bar and the race's racial spell into the racial slot. */
    @QaGameTest
    public static void creationTeachesTheFirstSpellAndTheRacialSpell(GameTestHelper helper) {
        ServerPlayer warrior = character(helper, "qa-kit-warrior", "warrior");
        Spellbook book = warrior.getData(SpellAttachments.SPELLBOOK);
        helper.assertValueEqual(book.level(GeneratedSpells.SLAM), Optional.of(1), "a new Warrior knows Slam");
        helper.assertValueEqual(book.slot(0), Optional.of(GeneratedSpells.SLAM), "on the bar's first slot");
        helper.assertValueEqual(book.level(GeneratedSpells.BATTLE_SHOUT), Optional.empty(), "Battle Shout comes at 3");
        helper.assertValueEqual(book.slot(Spellbook.RACIAL_SLOT), Optional.of(GeneratedSpells.WILL_TO_SURVIVE),
                "a Human's racial spell beside the bar");
        helper.assertValueEqual(book.barSlotOf(GeneratedSpells.WILL_TO_SURVIVE), -1, "and not on the bar itself");

        ServerPlayer mage = character(helper, "qa-kit-mage", "mage");
        helper.assertValueEqual(mage.getData(SpellAttachments.SPELLBOOK).slot(0),
                Optional.of(GeneratedSpells.FROSTBOLT), "a new Mage knows Frostbolt");
        logout(warrior);
        logout(mage);
        helper.succeed();
    }

    /**
     * A level teaches its class spells into the bar's next empty slots; one taken off the bar stays off. A login
     * catches up on spells a character does not know yet (a character from before its class taught them).
     */
    @QaGameTest
    public static void levelsAndLoginsTeachTheClassSpells(GameTestHelper helper) {
        ServerPlayer player = character(helper, "qa-kit-levels", "warrior");
        reachLevel(helper, player, 5);
        Spellbook book = player.getData(SpellAttachments.SPELLBOOK);
        helper.assertValueEqual(List.of(book.slot(0), book.slot(1), book.slot(2), book.slot(3)), List.of(
                Optional.of(GeneratedSpells.SLAM), Optional.of(GeneratedSpells.BATTLE_SHOUT),
                Optional.of(GeneratedSpells.SHIELD_BASH), Optional.empty()), "the spells of levels 1, 3 and 5");
        helper.assertTrue(Spells.placeOnBar(player, 1, Optional.empty()), "Battle Shout taken off the bar");
        reachLevel(helper, player, 8);
        book = player.getData(SpellAttachments.SPELLBOOK);
        helper.assertValueEqual(book.level(GeneratedSpells.RECKLESS_STRIKE), Optional.of(1), "level 8's spell");
        helper.assertValueEqual(book.slot(1), Optional.of(GeneratedSpells.RECKLESS_STRIKE),
                "into the first empty slot; the spell taken off stays off");

        player.setData(SpellAttachments.SPELLBOOK, Spellbook.EMPTY);
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
        book = player.getData(SpellAttachments.SPELLBOOK);
        for (ResourceKey<Spell> spell : List.of(GeneratedSpells.SLAM, GeneratedSpells.BATTLE_SHOUT,
                GeneratedSpells.SHIELD_BASH, GeneratedSpells.RECKLESS_STRIKE, GeneratedSpells.WILL_TO_SURVIVE)) {
            helper.assertValueEqual(book.level(spell), Optional.of(1), "a login teaches " + spell.identifier());
        }
        logout(player);
        helper.succeed();
    }

    /**
     * RLM-499: a melee spell needs a melee weapon in the main hand, and a cast without one is refused and costs
     * nothing; a wand, a bow and a pickaxe are none. Mobs cast without it.
     */
    @QaGameTest
    public static void aMeleeSpellNeedsAMeleeWeaponInTheMainHand(GameTestHelper helper) {
        ServerPlayer player = character(helper, "qa-kit-melee", "warrior");
        Resources.set(player, ResourceTypes.RAGE, 100);
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 2));
        for (ItemStack none : List.of(ItemStack.EMPTY, new ItemStack(Items.BOW),
                new ItemStack(GeneratedItems.STONE_WAND.get()), new ItemStack(Items.IRON_PICKAXE))) {
            player.setItemSlot(EquipmentSlot.MAINHAND, none);
            assertRefused(helper, player, GeneratedSpells.SLAM, husk, CastFailure.NO_MELEE_WEAPON);
        }
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.STONE_SWORD));
        assertRefused(helper, player, GeneratedSpells.SLAM, husk, CastFailure.NO_MELEE_WEAPON);
        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        helper.assertTrue(close(Resources.amount(player, ResourceTypes.RAGE), 100), "the refusals cost nothing");
        helper.assertTrue(close(husk.getHealth(), HEALTH), "and dealt nothing");

        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        assertCast(helper, player, GeneratedSpells.SLAM, husk);
        helper.assertTrue(close(Resources.amount(player, ResourceTypes.RAGE), 70), "Slam costs 30 rage");
        helper.assertTrue(husk.getHealth() < HEALTH, "Slam hit the husk");
        Spells.resetCooldowns(player);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        assertCast(helper, player, GeneratedSpells.SLAM, husk);

        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(3, 1, 2));
        Husk caster = SpellGameTests.husk(helper, new BlockPos(1, 1, 2));
        helper.assertTrue(caster.getMainHandItem().isEmpty(), "the mob holds nothing");
        assertCast(helper, caster, GeneratedSpells.SLAM, pig);
        logout(player);
        helper.succeed();
    }

    /**
     * Shield Bash needs a shield in either hand and no weapon (RLM-418), and stuns its target for 0.5 s at rank 1 and
     * 1.5 s at rank 5.
     */
    @QaGameTest
    public static void shieldBashNeedsAShieldAndStunsByRank(GameTestHelper helper) {
        ServerPlayer player = character(helper, "qa-kit-bash", "warrior");
        Resources.set(player, ResourceTypes.RAGE, 100);
        learn(helper, player, GeneratedSpells.SHIELD_BASH, 1);
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 2));
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        assertRefused(helper, player, GeneratedSpells.SHIELD_BASH, husk, CastFailure.NO_SHIELD);

        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        assertCast(helper, player, GeneratedSpells.SHIELD_BASH, husk);
        assertStunnedFor(helper, husk, 10, "rank 1 stuns for 0.5 s");

        husk.removeAllEffects();
        Spells.resetCooldowns(player);
        learn(helper, player, GeneratedSpells.SHIELD_BASH, 5);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SHIELD));
        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        assertCast(helper, player, GeneratedSpells.SHIELD_BASH, husk);
        assertStunnedFor(helper, husk, 30, "rank 5 stuns for 1.5 s, the shield in the main hand");
        logout(player);
        helper.succeed();
    }

    /**
     * A stun keeps its holder from casting and interrupts a cast in progress; Will To Survive is cast while stunned
     * and takes every harmful effect off its caster, the stun included, and leaves the beneficial ones.
     */
    @QaGameTest
    public static void aStunStopsCastsAndWillToSurviveBreaksIt(GameTestHelper helper) {
        ServerPlayer player = character(helper, "qa-kit-stun-cast", "mage");
        StatsCommands.setBase(player, GeneratedStats.MAX_MANA, 100);
        Resources.set(player, ResourceTypes.MANA, 100);
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 3));
        learn(helper, player, GeneratedSpells.TEST_SLOW_BOLT, 1);
        learn(helper, player, GeneratedSpells.TEST_BOLT, 1);
        assertCast(helper, player, GeneratedSpells.TEST_SLOW_BOLT, husk);
        stun(player, 60);
        CastGameTests.assertEnded(helper, player, GeneratedSpells.TEST_SLOW_BOLT, CastEnd.STUNNED);
        Spells.resetCooldowns(player);
        assertRefused(helper, player, GeneratedSpells.TEST_BOLT, husk, CastFailure.STUNNED);

        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200));
        player.addEffect(new MobEffectInstance(GeneratedEffects.BURNING, 200));
        player.addEffect(new MobEffectInstance(GeneratedEffects.BATTLE_SHOUT, 200));
        assertCast(helper, player, GeneratedSpells.WILL_TO_SURVIVE, null);
        helper.assertFalse(Effects.isStunned(player), "Will To Survive broke the stun");
        helper.assertFalse(player.hasEffect(MobEffects.SLOWNESS), "and took off the slowness");
        helper.assertFalse(player.hasEffect(GeneratedEffects.BURNING), "and the burning");
        helper.assertTrue(player.hasEffect(GeneratedEffects.BATTLE_SHOUT), "but left the buff");
        helper.assertValueEqual(player.getData(SpellAttachments.COMBAT).cooldowns()
                .get(GeneratedSpells.WILL_TO_SURVIVE), helper.getLevel().getGameTime() + 1200,
                "a 1 minute cooldown");
        Spells.resetCooldowns(player);
        assertCast(helper, player, GeneratedSpells.TEST_BOLT, husk);
        logout(player);
        helper.succeed();
    }

    /** A stunned mob's melee hit deals nothing, and its AI stands still until the stun ends. */
    @QaGameTest(timeoutTicks = 120)
    public static void aStunnedMobNeitherHitsNorWalks(GameTestHelper helper) {
        ServerPlayer player = character(helper, "qa-kit-stun-mob", "warrior");
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 2));
        stun(husk, 100);
        float health = player.getHealth();
        husk.doHurtTarget(helper.getLevel(), player);
        helper.assertTrue(close(player.getHealth(), health), "a stunned husk's hit dealt nothing");
        husk.removeAllEffects();
        husk.doHurtTarget(helper.getLevel(), player);
        helper.assertTrue(player.getHealth() < health, "the same hit lands once the stun ended");

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        stun(zombie, 100);
        Vec3 start = zombie.position();
        BlockPos far = helper.absolutePos(new BlockPos(4, 1, 4));
        zombie.getNavigation().moveTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5, 1);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(zombie.position().subtract(start).horizontalDistance() < 0.2,
                    "the stunned zombie walked: " + zombie.position() + " from " + start);
            helper.assertTrue(zombie.getNavigation().isDone(), "its path ended");
            logout(player);
            helper.succeed();
        });
    }

    /**
     * Reckless Strike adds 30 % crit chance at rank 5; on a caster with 100 % crit chance the 30 % above it turns into
     * crit damage 1:1, so the hit deals 1 + 50 % + 30 % of its damage.
     */
    @QaGameTest
    public static void critChanceAbove100TurnsIntoCritDamage(GameTestHelper helper) {
        ServerPlayer player = character(helper, "qa-kit-overflow", "warrior");
        Resources.set(player, ResourceTypes.RAGE, 100);
        learn(helper, player, GeneratedSpells.RECKLESS_STRIKE, 5);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 1);
        StatsCommands.setBase(player, GeneratedStats.CRIT_DAMAGE, 0.5);
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 2));
        double attackDamage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        assertCast(helper, player, GeneratedSpells.RECKLESS_STRIKE, husk);
        double expected = (2 + 4 + attackDamage) * (1 + 0.5 + 0.3);
        helper.assertTrue(close(HEALTH - husk.getHealth(), expected), "a crit for 180 %: " + expected
                + " expected, dealt " + (HEALTH - husk.getHealth()));
        logout(player);
        helper.succeed();
    }

    /** Slam ignores 5 % of the target's armor at rank 1 and 25 % at rank 5, on top of the caster's own shred. */
    @QaGameTest
    public static void slamShredsArmorByRank(GameTestHelper helper) {
        ServerPlayer player = character(helper, "qa-kit-shred", "warrior");
        Resources.set(player, ResourceTypes.RAGE, 100);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 2));
        CombatGameTests.setBase(husk, Attributes.ARMOR, 15);
        double attackDamage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        for (int rank : new int[] {1, 5}) {
            learn(helper, player, GeneratedSpells.SLAM, rank);
            Spells.resetCooldowns(player);
            Resources.set(player, ResourceTypes.RAGE, 100);
            float before = husk.getHealth();
            assertCast(helper, player, GeneratedSpells.SLAM, husk);
            double raw = 4 + 1.5 * (rank - 1) + 1.2 * attackDamage;
            double shred = 0.05 * rank;
            double expected = raw * (1 - DamageCalculator.armorReduction(raw, 15, 0, 0, shred));
            helper.assertTrue(close(before - husk.getHealth(), expected), "rank " + rank + ": " + expected
                    + " expected, dealt " + (before - husk.getHealth()));
        }
        logout(player);
        helper.succeed();
    }

    /** A hit's spell penetration ignores its share of the target's resistance to the school (Arcane Missiles). */
    @QaGameTest
    public static void spellPenetrationIgnoresAShareOfTheResistance(GameTestHelper helper) {
        ServerPlayer target = character(helper, "qa-kit-penetration", "mage");
        StatsCommands.setBase(target, GeneratedStats.ARCANE_RESISTANCE, 0.4);
        StatsCommands.setBase(target, GeneratedStats.MAGIC_SHIELD, 0);
        StatsCommands.setBase(target, Attributes.MAX_HEALTH, 100);
        target.setHealth(100);
        Husk attacker = SpellGameTests.husk(helper, new BlockPos(2, 1, 2));
        hit(helper, target, attacker, HitBonus.NONE);
        helper.assertTrue(close(target.getHealth(), 100 - 6), "10 arcane damage less 40 %: " + target.getHealth());
        target.setHealth(100);
        hit(helper, target, attacker, new HitBonus(0, 0.25));
        helper.assertTrue(close(target.getHealth(), 100 - 7), "25 % penetration leaves 30 %: " + target.getHealth());
        logout(target);
        helper.succeed();
    }

    /**
     * Ice Barrier raises the Magic Shield by 10 for 10 s at rank 1 and by 30 for 30 s at rank 3, and fills those points
     * at once; they go when it ends, and a second cast refreshes the barrier instead of stacking it.
     */
    @QaGameTest
    public static void iceBarrierFillsTheMagicShieldAtOnce(GameTestHelper helper) {
        ServerPlayer player = character(helper, "qa-kit-barrier", "mage");
        StatsCommands.setBase(player, GeneratedStats.MAGIC_SHIELD, 7);
        StatsCommands.setBase(player, GeneratedStats.MAX_MANA, 100);
        Resources.set(player, ResourceTypes.MANA, 100);
        learn(helper, player, GeneratedSpells.ICE_BARRIER, 1);
        player.setData(CombatAttachments.MAGIC_SHIELD, new MagicShield(2, player.tickCount + 1000));
        assertCast(helper, player, GeneratedSpells.ICE_BARRIER, null);
        helper.assertTrue(close(Resources.amount(player, ResourceTypes.MANA), 75), "for 25 mana");
        helper.assertValueEqual(player.getEffect(GeneratedEffects.ICE_BARRIER).getDuration(), 200, "for 10 s");
        player.doTick();
        helper.assertTrue(close(player.getAttributeValue(GeneratedStats.MAGIC_SHIELD), 17), "the stat raised by 10");
        helper.assertTrue(close(player.getData(CombatAttachments.MAGIC_SHIELD).points(), 12),
                "the pool waiting for its refill got the 10 at once: "
                        + player.getData(CombatAttachments.MAGIC_SHIELD).points());
        player.removeEffect(GeneratedEffects.ICE_BARRIER);
        player.doTick();
        helper.assertTrue(close(player.getData(CombatAttachments.MAGIC_SHIELD).points(), 7),
                "the barrier's points go with it");

        learn(helper, player, GeneratedSpells.ICE_BARRIER, 3);
        for (int cast = 0; cast < 2; cast++) {
            Spells.resetCooldowns(player);
            Resources.set(player, ResourceTypes.MANA, 100);
            assertCast(helper, player, GeneratedSpells.ICE_BARRIER, null);
            player.doTick();
        }
        helper.assertValueEqual(player.getEffect(GeneratedEffects.ICE_BARRIER).getDuration(), 599,
                "rank 3 lasts 30 s");
        helper.assertTrue(close(player.getAttributeValue(GeneratedStats.MAGIC_SHIELD), 37), "and raises it by 30");
        helper.assertTrue(close(player.getData(CombatAttachments.MAGIC_SHIELD).points(), 37),
                "a second cast refreshes the barrier, never stacking past the raised stat: "
                        + player.getData(CombatAttachments.MAGIC_SHIELD).points());
        logout(player);
        helper.succeed();
    }

    /**
     * Battle Shout raises the attack damage of its caster and of the allies within 10 blocks by 10 % for 10 s at rank
     * 1 and by 20 % for 20 s at rank 3, on a minute's cooldown; a second shout refreshes the effect instead of stacking
     * it.
     */
    @QaGameTest
    public static void battleShoutRaisesAlliesAttackDamage(GameTestHelper helper) {
        ServerPlayer player = character(helper, "qa-kit-shout", "warrior");
        ServerPlayer ally = QaPlayers.loginOnPlatform(helper, "qa-kit-shout-ally", new Vec3(4.5, 1, 4.5));
        double before = ally.getAttributeValue(Attributes.ATTACK_DAMAGE);
        for (int rank : new int[] {1, 3, 3}) {
            learn(helper, player, GeneratedSpells.BATTLE_SHOUT, rank);
            Spells.resetCooldowns(player);
            Resources.set(player, ResourceTypes.RAGE, 100);
            shout(helper, player);
            helper.assertTrue(close(Resources.amount(player, ResourceTypes.RAGE), 90), "for 10 rage");
            helper.assertValueEqual(player.getData(SpellAttachments.COMBAT).cooldowns()
                    .get(GeneratedSpells.BATTLE_SHOUT), helper.getLevel().getGameTime() + 1200, "a minute's cooldown");
            for (ServerPlayer shouted : List.of(player, ally)) {
                MobEffectInstance effect = shouted.getEffect(GeneratedEffects.BATTLE_SHOUT);
                helper.assertTrue(effect != null && effect.getDuration() == (rank == 1 ? 200 : 400)
                        && effect.getAmplifier() == rank - 1, shouted.getPlainTextName() + " has Battle Shout at rank "
                                + rank + ": " + effect);
            }
            helper.assertTrue(close(ally.getAttributeValue(Attributes.ATTACK_DAMAGE),
                    before * (rank == 1 ? 1.1 : 1.2)), "rank " + rank + " raised the ally's attack damage: "
                            + ally.getAttributeValue(Attributes.ATTACK_DAMAGE));
        }
        logout(player);
        logout(ally);
        helper.succeed();
    }

    /** The warrior's shout: cast, then the area's first tick, which pulses. */
    private static void shout(GameTestHelper helper, ServerPlayer player) {
        List<SpellArea> before = helper.getLevel().getEntitiesOfClass(SpellArea.class,
                player.getBoundingBox().inflate(1));
        assertCast(helper, player, GeneratedSpells.BATTLE_SHOUT, null);
        List<SpellArea> areas = helper.getLevel().getEntitiesOfClass(SpellArea.class,
                player.getBoundingBox().inflate(1), area -> !before.contains(area));
        helper.assertValueEqual(areas.size(), 1, "the shout's area around the warrior");
        // It ticks here: the neighbouring tests clear their space of entities, and a 20 blocks wide area reaches into
        // it.
        areas.getFirst().tick();
    }

    /**
     * Sword Specialization: a Human with a sword-like weapon in the main hand has 10 % more attack damage, and the
     * weapon's tooltip says so; another weapon, or a player without the race, gets neither.
     */
    @QaGameTest
    public static void swordSpecializationRaisesAHumansSwordDamage(GameTestHelper helper) {
        ServerPlayer player = character(helper, "qa-kit-sword", "warrior");
        Identifier passive = CharacterBonuses.passiveModifierId(Realmforge.id("human"), 0);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        CharacterStats.recompute(player);
        AttributeInstance attackDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        helper.assertTrue(attackDamage.getModifier(passive) != null
                && close(attackDamage.getModifier(passive).amount(), 0.1), "+10 % with a sword: "
                        + attackDamage.getModifier(passive));
        List<Component> tooltip = RacialPassives.tooltip(player, new ItemStack(Items.IRON_SWORD));
        helper.assertValueEqual(lines(tooltip), List.of("Sword Specialization", "+10% damage"),
                "the sword's tooltip lines");
        helper.assertValueEqual(tooltip.stream().map(line -> line.getStyle().getColor()).toList(),
                List.of(TextColor.fromLegacyFormat(ChatFormatting.GOLD),
                        TextColor.fromLegacyFormat(ChatFormatting.YELLOW)),
                "the name in gold, the bonus in yellow (Matěj, 2026-10-30)");

        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        CharacterStats.recompute(player);
        helper.assertTrue(attackDamage.getModifier(passive) == null, "nothing with an axe");
        helper.assertValueEqual(RacialPassives.tooltip(player, new ItemStack(Items.IRON_AXE)), List.of(),
                "and no tooltip lines on it");

        ServerPlayer stranger = QaPlayers.loginOnPlatform(helper, "qa-kit-sword-none");
        helper.assertValueEqual(RacialPassives.tooltip(stranger, new ItemStack(Items.IRON_SWORD)), List.of(),
                "no lines for a player without a character");
        logout(player);
        logout(stranger);
        helper.succeed();
    }

    /**
     * A rank makes a status effect last longer and grow stronger (Frostbolt's Slowness II at rank 5), and the
     * descriptions' new placeholders read each rank's numbers: armor shred, penetration, crit chance, a buff's bonus.
     */
    @QaGameTest
    public static void ranksGrowTheEffectsAndTheirTexts(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        ApplyEffectEffect slow = (ApplyEffectEffect) ((ProjectileEffect) spell(registries, GeneratedSpells.FROSTBOLT)
                .effects().getFirst()).effects().get(1);
        helper.assertValueEqual(List.of(slow.durationAt(1), slow.durationAt(5)), List.of(60, 100),
                "Slowness lasts 3 s, 0.5 s longer a rank");
        helper.assertValueEqual(List.of(slow.amplifierAt(4), slow.amplifierAt(5)), List.of(0, 1),
                "Slowness II from rank 5");

        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-kit-texts");
        assertText(helper, player, GeneratedSpells.SLAM, 3, "armor_shred", "15");
        assertText(helper, player, GeneratedSpells.RECKLESS_STRIKE, 5, "crit_chance", "30");
        assertText(helper, player, GeneratedSpells.ARCANE_MISSILES, 2, "penetration", "10");
        assertText(helper, player, GeneratedSpells.FROSTBOLT, 5, "duration", "5");
        assertText(helper, player, GeneratedSpells.ICE_BARRIER, 1, "bonus", "10");
        assertText(helper, player, GeneratedSpells.ICE_BARRIER, 3, "bonus", "30");
        assertText(helper, player, GeneratedSpells.ICE_BARRIER, 3, "duration", "30");
        assertText(helper, player, GeneratedSpells.BATTLE_SHOUT, 1, "bonus", "10");
        assertText(helper, player, GeneratedSpells.BATTLE_SHOUT, 2, "bonus", "15");
        assertText(helper, player, GeneratedSpells.BATTLE_SHOUT, 3, "bonus", "20");
        assertText(helper, player, GeneratedSpells.BATTLE_SHOUT, 3, "duration", "20");
        assertText(helper, player, GeneratedSpells.SHIELD_BASH, 5, "duration", "1.5");
        assertText(helper, player, GeneratedSpells.FIREBALL, 5, "periodic", "1.5");
        assertText(helper, player, GeneratedSpells.FIREBALL, 5, "periodic_total", "18");
        logout(player);
        helper.succeed();
    }

    /**
     * Arcane Missiles' missiles leave from the hands, where Frostbolt and Fireball form theirs, not from the eyes,
     * where one filled the middle of the caster's own screen for a frame (Matěj's playtest of 2026-10-30).
     */
    @QaGameTest
    public static void arcaneMissilesLeaveFromTheHands(GameTestHelper helper) {
        ServerPlayer player = character(helper, "qa-kit-missiles", "mage");
        Holder.Reference<Spell> missiles = Spells.get(helper.getLevel().registryAccess(),
                GeneratedSpells.ARCANE_MISSILES).orElseThrow();
        ProjectileEffect missile = (ProjectileEffect) missiles.value().effects().getFirst();
        Vec3 look = player.getLookAngle();
        List<SpellProjectile> before = helper.getLevel().getEntitiesOfClass(SpellProjectile.class,
                player.getBoundingBox().inflate(4));
        missile.apply(EffectContext.of(helper.getLevel(), player, missiles, 1, Optional.empty()),
                SpellTarget.direction(player.getEyePosition().add(look.scale(40)), look));
        List<SpellProjectile> launched = helper.getLevel().getEntitiesOfClass(SpellProjectile.class,
                player.getBoundingBox().inflate(4), projectile -> !before.contains(projectile));
        helper.assertValueEqual(launched.size(), 1, "one missile");
        Vec3 hands = SpellVisuals.formPoint(player, player.yBodyRot);
        Vec3 middle = launched.getFirst().position().add(0, missile.size() / 2, 0);
        helper.assertTrue(middle.distanceTo(hands) < 1e-6, "the missile left from the hands " + hands + ", not "
                + middle + " (the eyes are at " + player.getEyePosition() + ")");
        launched.getFirst().discard();
        logout(player);
        helper.succeed();
    }

    /** A Human Warrior or Mage at level 1, as a new player creates one, at 0 % crit chance and no magic shield. */
    static ServerPlayer character(GameTestHelper helper, String name, String playerClass) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, name, new Vec3(2.5, 1, 0.5));
        player.setGameMode(GameType.SURVIVAL);
        // A fake player's client never reports that it loaded, which keeps the player invulnerable.
        player.connection.markClientLoaded();
        helper.assertValueEqual(CharacterCreation.create(player,
                        ResourceKey.create(CharacterRegistries.RACE, Realmforge.id("human")),
                        ResourceKey.create(CharacterRegistries.CLASS, Realmforge.id(playerClass))),
                CharacterCreation.Result.CREATED, "a human " + playerClass);
        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 0);
        StatsCommands.setBase(player, GeneratedStats.MAGIC_SHIELD, 0);
        player.getInventory().clearContent();
        return player;
    }

    private static void reachLevel(GameTestHelper helper, ServerPlayer player, int level) {
        long xp = Characters.xpToReach(player, level).orElseThrow();
        helper.assertTrue(Characters.setTotalXp(player, xp), "the XP was set");
        helper.assertValueEqual(Characters.level(player), level, "the level reached");
    }

    private static Spell spell(RegistryAccess registries, ResourceKey<Spell> spell) {
        return Spells.get(registries, spell).orElseThrow().value();
    }

    private static void assertText(GameTestHelper helper, ServerPlayer player, ResourceKey<Spell> key, int level,
            String placeholder, String expected) {
        RegistryAccess registries = player.registryAccess();
        String value = SpellText.values(spell(registries, key), level, SpellText.statsOf(player),
                SpellText.schoolsOf(registries)).get(placeholder);
        helper.assertValueEqual(value, expected, key.identifier() + " {" + placeholder + "} at rank " + level);
    }

    private static void learn(GameTestHelper helper, ServerPlayer player, ResourceKey<Spell> spell, int level) {
        Spells.learn(player, Spells.get(helper.getLevel().registryAccess(), spell).orElseThrow(), level);
    }

    private static void stun(LivingEntity entity, int ticks) {
        Holder<MobEffect> stunned = GeneratedEffects.STUNNED;
        entity.addEffect(new MobEffectInstance(stunned, ticks));
    }

    private static void assertStunnedFor(GameTestHelper helper, LivingEntity entity, int ticks, String what) {
        MobEffectInstance effect = entity.getEffect(GeneratedEffects.STUNNED);
        helper.assertTrue(effect != null && effect.getDuration() == ticks, what + ": " + effect);
        helper.assertTrue(Effects.isStunned(entity), what + ": the entity is stunned");
    }

    /** 10 arcane damage on {@code target} from {@code attacker}, a hit that adds {@code bonus}. */
    private static void hit(GameTestHelper helper, LivingEntity target, LivingEntity attacker, HitBonus bonus) {
        target.hurtServer(helper.getLevel(), SpellSchools.damage(helper.getLevel().registryAccess(),
                GeneratedSpellSchools.ARCANE, attacker, attacker, CritRule.NONE, bonus, Optional.empty()), 10);
    }

    private static void assertCast(GameTestHelper helper, LivingEntity caster, ResourceKey<Spell> spell,
            @Nullable LivingEntity target) {
        CastResult result = SpellCaster.cast(caster, spell, target);
        helper.assertTrue(result.success(), spell.identifier() + " was refused: " + result.failure());
    }

    private static void assertRefused(GameTestHelper helper, LivingEntity caster, ResourceKey<Spell> spell,
            @Nullable LivingEntity target, CastFailure expected) {
        helper.assertValueEqual(SpellCaster.cast(caster, spell, target).failureOrNull(), expected,
                spell.identifier() + " refused with " + caster.getMainHandItem());
    }

    private static List<String> lines(List<Component> lines) {
        return lines.stream().map(Component::getString).toList();
    }

    private static void logout(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }

    private static boolean close(double got, double want) {
        return Math.abs(got - want) <= TOLERANCE;
    }
}
