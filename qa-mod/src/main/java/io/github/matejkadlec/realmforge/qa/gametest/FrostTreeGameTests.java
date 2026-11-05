package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.character.Characters;
import io.github.matejkadlec.realmforge.combat.DamagePipeline;
import io.github.matejkadlec.realmforge.combat.SpellSchools;
import io.github.matejkadlec.realmforge.generated.GeneratedEffects;
import io.github.matejkadlec.realmforge.generated.GeneratedItems;
import io.github.matejkadlec.realmforge.generated.GeneratedSpellSchools;
import io.github.matejkadlec.realmforge.generated.GeneratedSpells;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.mob.MobLevels;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import io.github.matejkadlec.realmforge.spell.AreaEffect;
import io.github.matejkadlec.realmforge.spell.CastResult;
import io.github.matejkadlec.realmforge.spell.ConeEffect;
import io.github.matejkadlec.realmforge.spell.DamageEffect;
import io.github.matejkadlec.realmforge.spell.EffectContext;
import io.github.matejkadlec.realmforge.spell.Passive;
import io.github.matejkadlec.realmforge.spell.Passives;
import io.github.matejkadlec.realmforge.spell.ProjectileEffect;
import io.github.matejkadlec.realmforge.spell.Procs;
import io.github.matejkadlec.realmforge.spell.ResourceTypes;
import io.github.matejkadlec.realmforge.spell.Resources;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellAim;
import io.github.matejkadlec.realmforge.spell.SpellAttachments;
import io.github.matejkadlec.realmforge.spell.SpellCaster;
import io.github.matejkadlec.realmforge.spell.SpellEffect;
import io.github.matejkadlec.realmforge.spell.SpellTarget;
import io.github.matejkadlec.realmforge.spell.SpellText;
import io.github.matejkadlec.realmforge.spell.SpellTooltip;
import io.github.matejkadlec.realmforge.spell.Spellbook;
import io.github.matejkadlec.realmforge.spell.Spells;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * The Frost Mage's "Shatter" tree (RLM-542; Matěj, 2026-10-30, "Spell Trees to Level 18"), at the numbers of Matěj's
 * rebalance of 2026-11-01 (RLM-572): every active spell's damage, mana and cooldown at rank 1 and at its highest rank at
 * a fixed Spell Power (system C, RLM-534), Ice Lance's bonus only against a Frozen target, Frostbite's chance, its
 * Frozen of 4/6/8 s and its internal cooldown, Cone of Cold's cone, Frost Nova's freeze round the caster, Blizzard's
 * eight waves at a ground spot with their mana up front and per second, and more waves with Cast Time Reduction,
 * Improved Frostbolt's wider splash at rank 3, and the tooltips (the next rank, Blizzard's waves and cost, Shatter's
 * overflow).
 */
public final class FrostTreeGameTests {
    private static final double HEALTH = SpellGameTests.HEALTH;
    private static final double TOLERANCE = 1e-3;
    private static final double SPELL_POWER = 10;
    /** The player stands at the 17×17 platform's north edge, in the middle, facing south (+z). */
    private static final Vec3 NORTH_EDGE = new Vec3(8.5, 1, 1.5);
    /** Enough tries that a 15 % proc that has not fired is a broken proc: 0.85^300 is about 10^-21. */
    private static final int PROC_TRIES = 300;
    /** The protection and durability every Netherite caster piece gets for the Astone check: level V (gear.md). */
    private static final String LEVEL_V = "[enchantments={\"minecraft:protection\":5,\"minecraft:unbreaking\":5}]";
    /**
     * The playtest's setup line against Astone (RLM-542, RLM-503; gear.md's expected gear for Astone, RLM-551): every
     * stat back to its default (RLM-577: no value from an earlier {@code /qa stats set} leaks in), a new Human Mage,
     * chapter 1's raised cap, level 18, the tree at 26 ranks (Frostbolt 5, Ice Lance 5, Improved Frostbolt, Frostbite,
     * Shatter and Frost Nova 3, Cone of Cold 2, Ice Barrier and Blizzard 1), and the Netherite caster set and wand at
     * level V. Since C5 a real level 18 character has 18 ranks; this line skips the rules to keep the state Astone was
     * balanced with, which has no affixes, gems or curios, and Matěj expects those to match the missing ranks
     * (2026-11-04, RLM-503). The handoff gives it to Matěj as
     * {@code execute as @p at @p run} and this line.
     */
    static final String ASTONE_SETUP = "qa run qa stats reset; qa character reset; "
            + "qa character create realmforge:human realmforge:mage; "
            + "advancement grant @s only realmforge:chapter/chapter_1; qa level 18; "
            + "qa spell learn realmforge:frostbolt 5; qa spell learn realmforge:ice_lance 5; "
            + "qa spell learn realmforge:improved_frostbolt 3; qa spell learn realmforge:frostbite 3; "
            + "qa spell learn realmforge:shatter 3; qa spell learn realmforge:frost_nova 3; "
            + "qa spell learn realmforge:cone_of_cold 2; qa spell learn realmforge:ice_barrier; "
            + "qa spell learn realmforge:blizzard; "
            + "give @s realmforge:netherite_cap" + LEVEL_V + "; give @s realmforge:netherite_robes" + LEVEL_V + "; "
            + "give @s realmforge:netherite_pants" + LEVEL_V + "; give @s realmforge:netherite_sandals" + LEVEL_V + "; "
            + "give @s realmforge:netherite_wand[enchantments={\"minecraft:unbreaking\":5}]";

    private FrostTreeGameTests() {
    }

    /**
     * Each active spell's first damage effect at rank 1 and at its highest rank, at 10 Spell Power, no crit and no
     * school power: the table's amount plus its coefficient of 10 (RLM-572: Frostbolt 8 + 100 % and 12 + 160 %, Cone of
     * Cold 5 + 80 % and 7 + 100 %, Ice Lance 4 + 45 % and 8 + 65 %, Frost Nova 3 + 40 % and 5 + 60 %, Blizzard's wave
     * 4 + 40 % and 8 + 80 %), on a husk without armor that is not Frozen.
     */
    @QaGameTest
    public static void everyActiveDealsItsRanksDamage(GameTestHelper helper) {
        ServerPlayer player = scholar(helper, "qa-frost-ranks", QaPlayers.PLATFORM_MIDDLE);
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 4));
        Map<ResourceKey<Spell>, double[]> table = Map.of(
                GeneratedSpells.FROSTBOLT, new double[] {1, 18, 5, 28},
                GeneratedSpells.CONE_OF_COLD, new double[] {1, 13, 3, 17},
                GeneratedSpells.ICE_LANCE, new double[] {1, 8.5, 5, 14.5},
                GeneratedSpells.FROST_NOVA, new double[] {1, 7, 3, 11},
                GeneratedSpells.BLIZZARD, new double[] {1, 8, 3, 16});
        table.forEach((key, checks) -> {
            Holder.Reference<Spell> spell = spell(helper, key);
            helper.assertValueEqual(spell.value().maxLevel(), (int) checks[2], key.identifier() + "'s highest rank");
            DamageEffect damage = firstDamage(spell.value().effects()).orElseThrow();
            for (int n = 0; n < checks.length; n += 2) {
                int rank = (int) checks[n];
                float before = husk.getHealth();
                damage.applyTo(EffectContext.of(helper.getLevel(), player, spell, rank, Optional.empty()), husk,
                        SpellTarget.of(player, husk));
                helper.assertTrue(close(before - husk.getHealth(), checks[n + 1]), key.identifier() + " rank " + rank
                        + ": " + checks[n + 1] + " expected, dealt " + (before - husk.getHealth()));
            }
        });
        logout(player);
        helper.succeed();
    }

    /**
     * Each active spell's mana and own cooldown at rank 1 and at its highest rank (RLM-572): Frostbolt 10 and 30 mana
     * without a cooldown, Cone of Cold 30 and 50 on 10 s, Ice Lance 15 and 55 without a cooldown, Frost Nova 30 and 50 on
     * 20 and 10 s, Ice Barrier 25 and 45 on 60 and 30 s, Blizzard 40 and 80 up front, then 15 and 25 a second, without a
     * cooldown; and a cast starts its rank's cooldown (Frost Nova at rank 3, 10 s; Ice Lance at rank 5, none).
     */
    @QaGameTest
    public static void everyActiveCostsItsRanksManaAndCooldown(GameTestHelper helper) {
        // {rank 1 cost, top cost, rank 1 cost up front, top's, rank 1 cooldown, top's}, costs a second for Blizzard.
        Map<ResourceKey<Spell>, double[]> table = Map.of(
                GeneratedSpells.FROSTBOLT, new double[] {10, 30, 0, 0, 0, 0},
                GeneratedSpells.CONE_OF_COLD, new double[] {30, 50, 0, 0, 200, 200},
                GeneratedSpells.ICE_LANCE, new double[] {15, 55, 0, 0, 0, 0},
                GeneratedSpells.FROST_NOVA, new double[] {30, 50, 0, 0, 400, 200},
                GeneratedSpells.ICE_BARRIER, new double[] {25, 45, 0, 0, 1200, 600},
                GeneratedSpells.BLIZZARD, new double[] {15, 25, 40, 80, 0, 0});
        table.forEach((key, row) -> {
            Spell spell = spell(helper, key).value();
            int top = spell.maxLevel();
            helper.assertTrue(close(spell.costAt(1), row[0]) && close(spell.costAt(top), row[1]),
                    key.identifier() + "'s mana: " + spell.costAt(1) + " and " + spell.costAt(top));
            helper.assertTrue(close(spell.startCostAt(1) - spell.costAt(1), row[2])
                    && close(spell.startCostAt(top) - spell.costAt(top), row[3]), key.identifier() + "'s mana up front");
            helper.assertValueEqual(spell.cooldownTicksAt(1), (int) row[4], key.identifier() + "'s cooldown at rank 1");
            helper.assertValueEqual(spell.cooldownTicksAt(top), (int) row[5], key.identifier() + "'s cooldown at rank "
                    + top);
        });
        ServerPlayer player = scholar(helper, "qa-frost-cooldowns", QaPlayers.PLATFORM_MIDDLE);
        StatsCommands.setBase(player, GeneratedStats.MAX_MANA, 300);
        Resources.tick(player);
        learn(helper, player, GeneratedSpells.FROST_NOVA, 3);
        learn(helper, player, GeneratedSpells.ICE_LANCE, 5);
        long now = helper.getLevel().getGameTime();
        assertCast(helper, player, GeneratedSpells.FROST_NOVA, SpellAim.at((LivingEntity) null));
        helper.assertValueEqual(Optional.ofNullable(player.getData(SpellAttachments.COMBAT).cooldowns()
                .get(GeneratedSpells.FROST_NOVA)), Optional.of(now + 200), "Frost Nova at rank 3: 10 s");
        helper.runAfterDelay(Spell.DEFAULT_GLOBAL_COOLDOWN_TICKS + 1, () -> {
            assertCast(helper, player, GeneratedSpells.ICE_LANCE, SpellAim.at((LivingEntity) null));
            helper.assertFalse(player.getData(SpellAttachments.COMBAT).cooldowns()
                    .containsKey(GeneratedSpells.ICE_LANCE), "Ice Lance has no cooldown of its own");
            logout(player);
            helper.succeed();
        });
    }

    /** Ice Lance deals × 2 at rank 1 and × 4 at rank 5 against a Frozen target (RLM-572), its plain damage otherwise. */
    @QaGameTest
    public static void iceLanceMultipliesOnlyAgainstFrozenTargets(GameTestHelper helper) {
        ServerPlayer player = scholar(helper, "qa-frost-lance", QaPlayers.PLATFORM_MIDDLE);
        Husk frozen = SpellGameTests.husk(helper, new BlockPos(1, 1, 4));
        Husk plain = SpellGameTests.husk(helper, new BlockPos(3, 1, 4));
        Holder.Reference<Spell> lance = spell(helper, GeneratedSpells.ICE_LANCE);
        DamageEffect damage = firstDamage(lance.value().effects()).orElseThrow();
        for (double[] check : new double[][] {{1, 8.5, 17}, {5, 14.5, 58}}) {
            int rank = (int) check[0];
            for (Husk husk : List.of(frozen, plain)) {
                if (husk == frozen) {
                    // Any damage ends Frozen at a 50 % chance (RLM-542), so the hit before may have ended it.
                    frozen.addEffect(new MobEffectInstance(GeneratedEffects.FROZEN, 1200));
                }
                float before = husk.getHealth();
                damage.applyTo(EffectContext.of(helper.getLevel(), player, lance, rank, Optional.empty()), husk,
                        SpellTarget.of(player, husk));
                double expected = husk == frozen ? check[2] : check[1];
                helper.assertTrue(close(before - husk.getHealth(), expected), "Ice Lance rank " + rank + " on the "
                        + (husk == frozen ? "Frozen" : "plain") + " husk: " + expected + " expected, dealt "
                        + (before - husk.getHealth()));
            }
            Map<String, String> values = SpellText.values(lance.value(), rank, SpellText.statsOf(player),
                    SpellText.schoolsOf(helper.getLevel().registryAccess()));
            helper.assertValueEqual(values.get("against_damage"), SpellText.decimal(check[2]),
                    "the tooltip's damage against a Frozen target at rank " + rank);
        }
        logout(player);
        helper.succeed();
    }

    /**
     * Frozen breaks on damage (Matěj's playtest of 2026-11-01): every hit that deals damage ends it at a 50 % chance,
     * rolled after the hit; with no damage it stays. Of 400 hits, each on a freshly Frozen husk, between 150 and 250
     * end it (a fair 50 % falls outside that about once in 10^10 runs).
     */
    @QaGameTest
    public static void frozenBreaksOnHalfTheHits(GameTestHelper helper) {
        ServerPlayer player = scholar(helper, "qa-frost-break", QaPlayers.PLATFORM_MIDDLE);
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 4));
        husk.addEffect(new MobEffectInstance(GeneratedEffects.FROZEN, 200));
        helper.assertTrue(husk.hasEffect(GeneratedEffects.FROZEN), "Frozen without a hit");
        int broken = 0;
        for (int n = 0; n < 400; n++) {
            husk.addEffect(new MobEffectInstance(GeneratedEffects.FROZEN, 200));
            float before = husk.getHealth();
            husk.hurtServer(helper.getLevel(), SpellSchools.damage(helper.getLevel().registryAccess(),
                    GeneratedSpellSchools.FROST, player, player), 1);
            helper.assertTrue(husk.getHealth() < before, "hit " + (n + 1) + " dealt no damage");
            if (!husk.hasEffect(GeneratedEffects.FROZEN)) {
                broken++;
            }
        }
        helper.assertTrue(broken >= 150 && broken <= 250, "about half the hits end Frozen: " + broken + " of 400");
        logout(player);
        helper.succeed();
    }

    /**
     * Frostbite at rank 3: the player's Slowness Freezes its target at its 15 % chance, for 8 s (4 s at rank 1 and 6 s at
     * rank 2, RLM-572), then never on that target for 5 s, and again once the 5 s are over.
     */
    @QaGameTest(timeoutTicks = 200)
    public static void frostbiteFreezesAtItsChanceOncePerCooldown(GameTestHelper helper) {
        ServerPlayer player = scholar(helper, "qa-frost-bite", QaPlayers.PLATFORM_MIDDLE);
        learn(helper, player, GeneratedSpells.FROSTBITE, 3);
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 4));
        Holder.Reference<Spell> cone = spell(helper, GeneratedSpells.CONE_OF_COLD);
        SpellEffect slow = ((ConeEffect) cone.value().effects().getFirst()).effects().get(1);
        helper.assertTrue(slowUntilFrozen(helper, player, husk, cone, slow), "Frostbite never froze the husk in "
                + PROC_TRIES + " Slownesses");
        helper.assertValueEqual(husk.getEffect(GeneratedEffects.FROZEN).getDuration(), 160, "8 s at rank 3");
        Identifier proc = Passives.procId(GeneratedSpells.FROSTBITE.identifier(), 0);
        helper.assertTrue(Procs.isOnCooldown(player, proc, husk), "the proc waits on the husk");
        husk.removeEffect(GeneratedEffects.FROZEN);
        helper.assertFalse(slowUntilFrozen(helper, player, husk, cone, slow),
                "Frostbite froze the husk again within its 5 s");
        Passive frostbite = spell(helper, GeneratedSpells.FROSTBITE).value().passive().orElseThrow();
        helper.assertValueEqual(SpellText.passiveValues(frostbite, 1), Map.of("proc_chance", "5", "proc_duration", "4",
                "proc_cooldown", "5"), "rank 1: 5 %, 4 s, once every 5 s");
        helper.assertValueEqual(SpellText.passiveValues(frostbite, 2).get("proc_duration"), "6", "rank 2: 6 s");
        helper.runAfterDelay(101, () -> {
            helper.assertFalse(Procs.isOnCooldown(player, proc, husk), "the 5 s are over");
            helper.assertTrue(slowUntilFrozen(helper, player, husk, cone, slow), "Frostbite froze the husk after 5 s");
            logout(player);
            helper.succeed();
        });
    }

    /**
     * Cone of Cold hits the husk in front within 5 blocks, for its damage and Slowness II for 4 s (RLM-572), and spares
     * the husks behind the Mage, 45° to its side and beyond 5 blocks.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17)
    public static void coneOfColdHitsOnlyTheConeInFront(GameTestHelper helper) {
        ServerPlayer player = mage(helper, "qa-frost-cone", new Vec3(8.5, 1, 6.5));
        learn(helper, player, GeneratedSpells.CONE_OF_COLD, 1);
        Husk inFront = husk(helper, new Vec3(8.5, 1, 10.0));
        Husk farAhead = husk(helper, new Vec3(8.5, 1, 13.5));
        Husk aside = husk(helper, new Vec3(12.5, 1, 10.0));
        Husk behind = husk(helper, new Vec3(8.5, 1, 3.5));
        assertCast(helper, player, GeneratedSpells.CONE_OF_COLD, SpellAim.at((LivingEntity) null));
        double expected = 5 + 0.8 * spellPower(player);
        helper.assertTrue(close(HEALTH - inFront.getHealth(), expected), "the husk in front took "
                + (HEALTH - inFront.getHealth()) + ", " + expected + " expected");
        MobEffectInstance slow = inFront.getEffect(MobEffects.SLOWNESS);
        helper.assertTrue(slow != null && slow.getAmplifier() == 1 && slow.getDuration() == 80,
                "Slowness II for 4 s: " + slow);
        for (Husk spared : List.of(farAhead, aside, behind)) {
            helper.assertTrue(close(spared.getHealth(), HEALTH), "a husk outside the cone was hit at "
                    + spared.position());
        }
        logout(player);
        helper.succeed();
    }

    /** Frost Nova hits and Freezes every enemy within 4 blocks for 6 s at rank 1 (RLM-572), and none farther. */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17)
    public static void frostNovaFreezesTheEnemiesAround(GameTestHelper helper) {
        ServerPlayer player = mage(helper, "qa-frost-nova", new Vec3(8.5, 1, 8.5));
        learn(helper, player, GeneratedSpells.FROST_NOVA, 1);
        Husk near = husk(helper, new Vec3(8.5, 1, 11.0));
        Husk behind = husk(helper, new Vec3(6.5, 1, 7.0));
        Husk far = husk(helper, new Vec3(8.5, 1, 15.5));
        assertCast(helper, player, GeneratedSpells.FROST_NOVA, SpellAim.at((LivingEntity) null));
        helper.runAfterDelay(2, () -> {
            double expected = 3 + 0.4 * spellPower(player);
            for (Husk hit : List.of(near, behind)) {
                helper.assertTrue(close(HEALTH - hit.getHealth(), expected), "a husk within 4 blocks took "
                        + (HEALTH - hit.getHealth()) + ", " + expected + " expected");
                MobEffectInstance frozen = hit.getEffect(GeneratedEffects.FROZEN);
                helper.assertTrue(frozen != null && frozen.getDuration() > 110, "Frozen for 6 s: " + frozen);
            }
            helper.assertTrue(close(far.getHealth(), HEALTH) && !far.hasEffect(GeneratedEffects.FROZEN),
                    "the husk 7 blocks away was hit");
            logout(player);
            helper.succeed();
        });
    }

    /**
     * Blizzard channels 8 s at a ground spot (Matěj's rebalance of 2026-11-01, RLM-572): eight waves, one a second, each
     * hitting every enemy within 4 blocks of it (3.5 blocks off is inside) and slowing them, and none farther; at rank 1
     * it costs 40 mana up front and 15 for each of its eight seconds, 160 in all.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 220)
    public static void blizzardChannelsEightWavesAtTheSpot(GameTestHelper helper) {
        ServerPlayer player = blizzardMage(helper, "qa-frost-blizzard", NORTH_EDGE, 0);
        Husk inside = husk(helper, new Vec3(12.0, 1, 9.5));
        Husk outside = husk(helper, new Vec3(13.5, 1, 9.5));
        double mana = Resources.amount(player, ResourceTypes.MANA);
        assertCast(helper, player, GeneratedSpells.BLIZZARD, SpellAim.at(helper.absoluteVec(new Vec3(8.5, 1, 9.5))));
        helper.assertTrue(close(mana - Resources.amount(player, ResourceTypes.MANA), 55),
                "40 up front and the first second's 15 as it begins");
        double wave = 4 + 0.4 * spellPower(player);
        helper.runAfterDelay(90, () -> helper.assertTrue(close(HEALTH - inside.getHealth(), 4 * wave),
                "four waves of " + wave + " after four seconds, the husk took " + (HEALTH - inside.getHealth())));
        helper.runAfterDelay(175, () -> {
            helper.assertTrue(close(HEALTH - inside.getHealth(), 8 * wave), "eight waves of " + wave + " expected, "
                    + "the husk 3.5 blocks off took " + (HEALTH - inside.getHealth()));
            helper.assertTrue(inside.hasEffect(MobEffects.SLOWNESS), "the husk inside is slowed");
            helper.assertTrue(close(outside.getHealth(), HEALTH), "the husk 5 blocks away was hit");
            double spent = mana - Resources.amount(player, ResourceTypes.MANA);
            helper.assertTrue(close(spent, 160), "40 up front and 8 seconds of 15: " + spent + " spent");
            logout(player);
            helper.succeed();
        });
    }

    /**
     * Cast Time Reduction adds Blizzard's waves instead of shortening it (RLM-572): round(8 × (1 + CTR)), halves up,
     * in the same 8 s, so 10 waves with 25 % and with 20 % (9.6), for the same 160 mana; the tooltip counts them.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 220)
    public static void castTimeReductionAddsBlizzardsWaves(GameTestHelper helper) {
        ServerPlayer quarter = blizzardMage(helper, "qa-frost-blizzard-25", new Vec3(4.5, 1, 1.5), 0.25);
        ServerPlayer fifth = blizzardMage(helper, "qa-frost-blizzard-20", new Vec3(12.5, 1, 1.5), 0.2);
        Husk underQuarter = husk(helper, new Vec3(4.5, 1, 9.5));
        Husk underFifth = husk(helper, new Vec3(12.5, 1, 9.5));
        Spell blizzard = spell(helper, GeneratedSpells.BLIZZARD).value();
        helper.assertValueEqual(blizzard.cast().tickCountFor(0.25), 10, "10 waves with 25 %");
        helper.assertValueEqual(blizzard.cast().tickCountFor(0.2), 10, "9.6 rounds to 10 with 20 %");
        helper.assertValueEqual(blizzard.cast().tickCountFor(0.1875), 10, "9.5 rounds up");
        helper.assertValueEqual(blizzard.cast().tickCountFor(0.18625), 9, "9.49 rounds down");
        double mana = Resources.amount(quarter, ResourceTypes.MANA);
        assertCast(helper, quarter, GeneratedSpells.BLIZZARD, SpellAim.at(helper.absoluteVec(new Vec3(4.5, 1, 9.5))));
        assertCast(helper, fifth, GeneratedSpells.BLIZZARD, SpellAim.at(helper.absoluteVec(new Vec3(12.5, 1, 9.5))));
        helper.assertValueEqual(Spells.currentCast(quarter).map(cast -> cast.durationTicks()), Optional.of(160),
                "the channel keeps its 8 s");
        double wave = 4 + 0.4 * spellPower(quarter);
        helper.runAfterDelay(175, () -> {
            for (Husk husk : List.of(underQuarter, underFifth)) {
                helper.assertTrue(close(HEALTH - husk.getHealth(), 10 * wave), "ten waves of " + wave
                        + " expected, the husk took " + (HEALTH - husk.getHealth()));
            }
            double spent = mana - Resources.amount(quarter, ResourceTypes.MANA);
            helper.assertTrue(close(spent, 160), "the same 160 mana for its 8 s: " + spent + " spent");
            logout(quarter);
            logout(fifth);
            helper.succeed();
        });
    }

    /**
     * Improved Frostbolt widens Frostbolt's splash by half a block from rank 3 on (Matěj, 2026-10-31): at rank 3 the
     * husk 1.2 blocks from the target takes half the hit, Improved Frostbolt's 15 % included; below rank 3 the bonus is
     * none.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_9X9, timeoutTicks = 120)
    public static void improvedFrostboltWidensTheSplashAtRank3(GameTestHelper helper) {
        ServerPlayer player = mage(helper, "qa-frost-splash", new Vec3(4.5, 1, 0.5));
        StatsCommands.setBase(player, GeneratedStats.SPELL_POWER, 0);
        learn(helper, player, GeneratedSpells.IMPROVED_FROSTBOLT, 2);
        helper.assertTrue(close(Passives.splashRadiusBonus(player, GeneratedSpells.FROSTBOLT), 0), "none at rank 2");
        learn(helper, player, GeneratedSpells.IMPROVED_FROSTBOLT, 3);
        helper.assertTrue(close(Passives.splashRadiusBonus(player, GeneratedSpells.FROSTBOLT), 0.5),
                "half a block at rank 3");
        learn(helper, player, GeneratedSpells.FROSTBOLT, 1);
        Husk target = husk(helper, new Vec3(4.5, 1, 5.5));
        Husk wide = husk(helper, new Vec3(6.3, 1, 5.5));
        Husk far = husk(helper, new Vec3(7.5, 1, 7.5));
        assertCast(helper, player, GeneratedSpells.FROSTBOLT, SpellAim.at(target));
        helper.runAfterDelay(60, () -> {
            double hit = 8 * 1.15;
            helper.assertTrue(close(HEALTH - target.getHealth(), hit), "the target took "
                    + (HEALTH - target.getHealth()) + ", " + hit + " expected");
            helper.assertTrue(close(HEALTH - wide.getHealth(), hit / 2), "the husk 1.2 blocks off took "
                    + (HEALTH - wide.getHealth()) + ", " + hit / 2 + " expected");
            helper.assertTrue(wide.hasEffect(MobEffects.SLOWNESS), "and the full Slowness");
            helper.assertTrue(close(far.getHealth(), HEALTH), "the far husk was hit");
            logout(player);
            helper.succeed();
        });
    }

    /** An active spell's tooltip shows its next rank's numbers below its own, and none at its highest rank. */
    @QaGameTest
    public static void tooltipsShowTheNextRank(GameTestHelper helper) {
        ServerPlayer player = scholar(helper, "qa-frost-tooltip", QaPlayers.PLATFORM_MIDDLE);
        StatsCommands.setBase(player, GeneratedStats.SPELL_POWER, 0);
        Spell lance = spell(helper, GeneratedSpells.ICE_LANCE).value();
        List<String> first = tooltip(helper, player, lance, 1);
        helper.assertValueEqual(first.subList(first.size() - 3, first.size()), List.of(
                "Throws a shard of ice for Frost 4 damage, Frost 8 damage against a Frozen target.", "Next rank:",
                "Throws a shard of ice for Frost 5 damage, Frost 12.5 damage against a Frozen target."),
                "rank 1 and rank 2; as plain text the school's icon reads as its name");
        List<String> top = tooltip(helper, player, lance, 5);
        helper.assertFalse(top.contains("Next rank:"), "no next rank at rank 5: " + top);
        logout(player);
        helper.succeed();
    }

    /**
     * RLM-572's tooltips: Blizzard's cost up front and per second, its 8 s whatever the Cast Time Reduction, and its
     * wave count with it (8, 10 with 25 %); Frost Nova's cooldown by rank; Shatter's line on crit chance above 100 %.
     */
    @QaGameTest
    public static void tooltipsShowRanksCostsAndWaves(GameTestHelper helper) {
        ServerPlayer player = scholar(helper, "qa-frost-tooltip-waves", QaPlayers.PLATFORM_MIDDLE);
        StatsCommands.setBase(player, GeneratedStats.SPELL_POWER, 0);
        Spell blizzard = spell(helper, GeneratedSpells.BLIZZARD).value();
        List<String> plain = tooltip(helper, player, blizzard, 1);
        helper.assertTrue(plain.contains("40 Mana, then 15 per sec"), "the cost up front, then per second: " + plain);
        helper.assertTrue(plain.contains("Channeled (8 sec)"), "8 s: " + plain);
        helper.assertTrue(plain.get(plain.size() - 3).startsWith("Calls down 8 waves of ice"), "8 waves: " + plain);
        StatsCommands.setBase(player, GeneratedStats.CAST_TIME_REDUCTION, 0.25);
        List<String> hasted = tooltip(helper, player, blizzard, 1);
        helper.assertTrue(hasted.contains("Channeled (8 sec)"), "still 8 s with 25 %: " + hasted);
        helper.assertTrue(hasted.get(hasted.size() - 3).startsWith("Calls down 10 waves of ice"),
                "10 waves with 25 %: " + hasted);
        Spell nova = spell(helper, GeneratedSpells.FROST_NOVA).value();
        helper.assertTrue(tooltip(helper, player, nova, 1).contains("20 sec cooldown"), "Frost Nova: 20 s at rank 1");
        helper.assertTrue(tooltip(helper, player, nova, 3).contains("10 sec cooldown"), "and 10 s at rank 3");
        List<String> shatter = tooltip(helper, player, spell(helper, GeneratedSpells.SHATTER).value(), 3);
        helper.assertTrue(shatter.contains("Your spells have 30% more chance to crit against Frozen targets. Crit "
                + "chance above 100% turns into bonus crit damage."), "Shatter's overflow line: " + shatter);
        logout(player);
        helper.succeed();
    }

    /**
     * The handoff's setup line ({@link #ASTONE_SETUP}) reaches its state: a Human Mage at level 18 knowing the tree at
     * the build's ranks, holding the Netherite caster set with Protection V and Unbreaking V and the wand.
     */
    @QaGameTest
    public static void theAstoneSetupLineReachesItsState(GameTestHelper helper) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-frost-astone");
        player.connection.markClientLoaded();
        StatsCommands.setBase(player, GeneratedStats.CAST_TIME_REDUCTION, 0.25);
        CommandSourceStack source = player.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS);
        helper.getLevel().getServer().getCommands().performPrefixedCommand(source, ASTONE_SETUP);
        helper.assertTrue(close(player.getAttributeBaseValue(GeneratedStats.CAST_TIME_REDUCTION), 0),
                "an earlier Cast Time Reduction is reset");
        helper.assertValueEqual(Characters.mainClass(player).map(entry -> entry.key().identifier().getPath()),
                Optional.of("mage"), "a Mage");
        helper.assertValueEqual(Characters.level(player), 18, "at level 18");
        Spellbook book = player.getData(SpellAttachments.SPELLBOOK);
        Map<ResourceKey<Spell>, Integer> build = Map.of(GeneratedSpells.FROSTBOLT, 5, GeneratedSpells.ICE_LANCE, 5,
                GeneratedSpells.IMPROVED_FROSTBOLT, 3, GeneratedSpells.FROSTBITE, 3, GeneratedSpells.SHATTER, 3,
                GeneratedSpells.FROST_NOVA, 3, GeneratedSpells.CONE_OF_COLD, 2, GeneratedSpells.ICE_BARRIER, 1,
                GeneratedSpells.BLIZZARD, 1);
        build.forEach((spell, rank) -> helper.assertValueEqual(book.level(spell), Optional.of(rank),
                spell.identifier() + "'s rank"));
        helper.assertValueEqual(build.values().stream().mapToInt(Integer::intValue).sum(), 9 + 17,
                "the 26 ranks Astone was balanced with");
        Holder<Enchantment> protection = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.PROTECTION);
        for (Item piece : List.of(GeneratedItems.NETHERITE_CAP.get(), GeneratedItems.NETHERITE_ROBES.get(),
                GeneratedItems.NETHERITE_PANTS.get(), GeneratedItems.NETHERITE_SANDALS.get())) {
            ItemStack stack = held(player, piece);
            helper.assertTrue(!stack.isEmpty(), "holds " + piece);
            helper.assertValueEqual(stack.getTagEnchantments().getLevel(protection), 5, piece + "'s Protection");
        }
        helper.assertTrue(!held(player, GeneratedItems.NETHERITE_WAND.get()).isEmpty(), "holds the Netherite Wand");
        logout(player);
        helper.succeed();
    }

    private static ItemStack held(ServerPlayer player, Item item) {
        for (ItemStack stack : player.getInventory()) {
            if (stack.is(item)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Slows {@code husk} with {@code slow} (Cone of Cold's Slowness, so Frostbite's proc watches it) up to
     * {@link #PROC_TRIES} times within this tick, taking the Slowness off between tries; whether Frostbite froze it.
     */
    private static boolean slowUntilFrozen(GameTestHelper helper, ServerPlayer player, Husk husk,
            Holder.Reference<Spell> cone, SpellEffect slow) {
        EffectContext context = EffectContext.of(helper.getLevel(), player, cone, 1, Optional.empty());
        for (int n = 0; n < PROC_TRIES; n++) {
            husk.removeEffect(MobEffects.SLOWNESS);
            slow.apply(context, SpellTarget.of(player, husk));
            if (husk.hasEffect(GeneratedEffects.FROZEN)) {
                return true;
            }
        }
        return false;
    }

    /** The first damage effect of {@code effects}, a projectile's, an area's or a cone's included. */
    private static Optional<DamageEffect> firstDamage(List<SpellEffect> effects) {
        for (SpellEffect effect : effects) {
            Optional<DamageEffect> found = switch (effect) {
                case DamageEffect damage -> Optional.of(damage);
                case ProjectileEffect projectile -> firstDamage(projectile.effects());
                case AreaEffect area -> firstDamage(area.effects());
                case ConeEffect cone -> firstDamage(cone.effects());
                default -> Optional.empty();
            };
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private static List<String> tooltip(GameTestHelper helper, ServerPlayer player, Spell spell, int level) {
        return SpellTooltip.lines(spell, level, SpellText.statsOf(player),
                SpellText.schoolsOf(helper.getLevel().registryAccess())).stream().map(Component::getString).toList();
    }

    /** A player without a character at 10 Spell Power, no crit and no dodge, for damage numbers. */
    private static ServerPlayer scholar(GameTestHelper helper, String name, Vec3 where) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, name, where);
        player.setGameMode(GameType.SURVIVAL);
        player.connection.markClientLoaded();
        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 0);
        StatsCommands.setBase(player, GeneratedStats.DODGE_CHANCE, 0);
        StatsCommands.setBase(player, GeneratedStats.SPELL_POWER, SPELL_POWER);
        return player;
    }

    /**
     * {@link #scholar} with a full pool of 300 mana that does not regenerate, Blizzard at rank 1 and
     * {@code castTimeReduction}, for Blizzard's 160 mana a cast (RLM-572).
     */
    private static ServerPlayer blizzardMage(GameTestHelper helper, String name, Vec3 where, double castTimeReduction) {
        ServerPlayer player = scholar(helper, name, where);
        StatsCommands.setBase(player, GeneratedStats.MAX_MANA, 300);
        StatsCommands.setBase(player, GeneratedStats.MANA_REGENERATION, 0);
        StatsCommands.setBase(player, GeneratedStats.CAST_TIME_REDUCTION, castTimeReduction);
        Resources.tick(player);
        learn(helper, player, GeneratedSpells.BLIZZARD, 1);
        return player;
    }

    /** {@link #scholar} with a full pool of 100 mana, to cast. */
    private static ServerPlayer mage(GameTestHelper helper, String name, Vec3 where) {
        ServerPlayer player = scholar(helper, name, where);
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

    private static double spellPower(ServerPlayer player) {
        return DamagePipeline.stat(player, GeneratedStats.SPELL_POWER);
    }

    private static Holder.Reference<Spell> spell(GameTestHelper helper, ResourceKey<Spell> key) {
        return Spells.get(helper.getLevel().registryAccess(), key).orElseThrow();
    }

    private static void learn(GameTestHelper helper, ServerPlayer player, ResourceKey<Spell> key, int level) {
        Spells.learn(player, spell(helper, key), level);
    }

    private static void assertCast(GameTestHelper helper, LivingEntity caster, ResourceKey<Spell> spell,
            SpellAim aim) {
        CastResult result = SpellCaster.cast(caster, spell, aim);
        helper.assertTrue(result.success(), spell.identifier() + " was refused: " + result.failure());
    }

    private static void logout(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }

    private static boolean close(double got, double want) {
        return Math.abs(got - want) <= TOLERANCE;
    }
}
