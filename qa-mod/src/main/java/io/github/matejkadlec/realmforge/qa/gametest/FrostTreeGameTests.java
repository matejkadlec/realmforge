package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.character.Characters;
import io.github.matejkadlec.realmforge.combat.DamagePipeline;
import io.github.matejkadlec.realmforge.generated.GeneratedEffects;
import io.github.matejkadlec.realmforge.generated.GeneratedItems;
import io.github.matejkadlec.realmforge.generated.GeneratedSpells;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.mob.MobLevels;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import io.github.matejkadlec.realmforge.spell.AreaEffect;
import io.github.matejkadlec.realmforge.spell.CastResult;
import io.github.matejkadlec.realmforge.spell.ConeEffect;
import io.github.matejkadlec.realmforge.spell.DamageEffect;
import io.github.matejkadlec.realmforge.spell.EffectContext;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.advancements.AdvancementHolder;
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
 * The Frost Mage's "Shatter" tree (RLM-542; Matěj, 2026-10-30, "Spell Trees to Level 18"): every active spell's damage
 * at rank 1 and at its highest rank at a fixed Spell Power (system C, RLM-534), Ice Lance's bonus only against a Frozen
 * target, Frostbite's chance, its longer Frozen at rank 3 and its internal cooldown, Cone of Cold's cone, Frost Nova's
 * freeze round the caster, Blizzard's six waves at a ground spot, Improved Frostbolt's wider splash at rank 3, the
 * tooltip's next rank, and the Mage learning the tree by its rows (1, 6, 12, 18).
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
     * The playtest's setup line against Astone (RLM-542, RLM-503; gear.md's expected gear for Astone, RLM-551): a new
     * Human Mage, chapter 1's raised cap, level 18, the tree at a 17-point build on top of the kit's rank 1 (Frostbolt 5,
     * Ice Lance 5, Improved Frostbolt, Frostbite, Shatter and Frost Nova 3, Cone of Cold 2, Ice Barrier and Blizzard 1),
     * and the Netherite caster set and wand at level V. The handoff gives it to Matěj as
     * {@code execute as @p at @p run} and this line.
     */
    static final String ASTONE_SETUP = "qa run qa character reset; qa character create realmforge:human realmforge:mage; "
            + "advancement grant @s only realmforge:chapter/chapter_1; qa level 18; "
            + "qa spell learn realmforge:frostbolt 5; qa spell learn realmforge:ice_lance 5; "
            + "qa spell learn realmforge:improved_frostbolt 3; qa spell learn realmforge:frostbite 3; "
            + "qa spell learn realmforge:shatter 3; qa spell learn realmforge:frost_nova 3; "
            + "qa spell learn realmforge:cone_of_cold 2; "
            + "give @s realmforge:netherite_cap" + LEVEL_V + "; give @s realmforge:netherite_robes" + LEVEL_V + "; "
            + "give @s realmforge:netherite_pants" + LEVEL_V + "; give @s realmforge:netherite_sandals" + LEVEL_V + "; "
            + "give @s realmforge:netherite_wand[enchantments={\"minecraft:unbreaking\":5}]";

    private FrostTreeGameTests() {
    }

    /**
     * Each active spell's first damage effect at rank 1 and at its highest rank, at 10 Spell Power, no crit and no
     * school power: the table's amount plus its coefficient of 10 (Frostbolt 8 + 100 % and 12 + 200 %, Cone of Cold
     * 5 + 60 % and 7 + 80 %, Ice Lance 3 + 40 % and 5 + 60 %, Frost Nova 3 + 30 % and 5 + 40 %, Blizzard's wave 2 + 20 %
     * and 3 + 30 %), on a husk without armor that is not Frozen.
     */
    @QaGameTest
    public static void everyActiveDealsItsRanksDamage(GameTestHelper helper) {
        ServerPlayer player = scholar(helper, "qa-frost-ranks", QaPlayers.PLATFORM_MIDDLE);
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 4));
        Map<ResourceKey<Spell>, double[]> table = Map.of(
                GeneratedSpells.FROSTBOLT, new double[] {1, 18, 5, 32},
                GeneratedSpells.CONE_OF_COLD, new double[] {1, 11, 3, 15},
                GeneratedSpells.ICE_LANCE, new double[] {1, 7, 5, 11},
                GeneratedSpells.FROST_NOVA, new double[] {1, 6, 3, 9},
                GeneratedSpells.BLIZZARD, new double[] {1, 4, 3, 6});
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

    /** Ice Lance deals × 2 at rank 1 and × 3 at rank 5 against a Frozen target, and its plain damage otherwise. */
    @QaGameTest
    public static void iceLanceMultipliesOnlyAgainstFrozenTargets(GameTestHelper helper) {
        ServerPlayer player = scholar(helper, "qa-frost-lance", QaPlayers.PLATFORM_MIDDLE);
        Husk frozen = SpellGameTests.husk(helper, new BlockPos(1, 1, 4));
        Husk plain = SpellGameTests.husk(helper, new BlockPos(3, 1, 4));
        frozen.addEffect(new MobEffectInstance(GeneratedEffects.FROZEN, 1200));
        Holder.Reference<Spell> lance = spell(helper, GeneratedSpells.ICE_LANCE);
        DamageEffect damage = firstDamage(lance.value().effects()).orElseThrow();
        for (double[] check : new double[][] {{1, 7, 14}, {5, 11, 33}}) {
            int rank = (int) check[0];
            for (Husk husk : List.of(frozen, plain)) {
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
     * Frostbite at rank 3: the player's Slowness Freezes its target at its 15 % chance, for 3 s (2 s below rank 3),
     * then never on that target for 5 s, and again once the 5 s are over.
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
        helper.assertValueEqual(husk.getEffect(GeneratedEffects.FROZEN).getDuration(), 60, "3 s at rank 3");
        Identifier proc = Passives.procId(GeneratedSpells.FROSTBITE.identifier(), 0);
        helper.assertTrue(Procs.isOnCooldown(player, proc, husk), "the proc waits on the husk");
        husk.removeEffect(GeneratedEffects.FROZEN);
        helper.assertFalse(slowUntilFrozen(helper, player, husk, cone, slow),
                "Frostbite froze the husk again within its 5 s");
        Map<String, String> rankOne = SpellText.passiveValues(spell(helper, GeneratedSpells.FROSTBITE).value()
                .passive().orElseThrow(), 1);
        helper.assertValueEqual(rankOne, Map.of("proc_chance", "5", "proc_duration", "2", "proc_cooldown", "5"),
                "rank 1: 5 %, 2 s, once every 5 s");
        helper.runAfterDelay(101, () -> {
            helper.assertFalse(Procs.isOnCooldown(player, proc, husk), "the 5 s are over");
            helper.assertTrue(slowUntilFrozen(helper, player, husk, cone, slow), "Frostbite froze the husk after 5 s");
            logout(player);
            helper.succeed();
        });
    }

    /**
     * Cone of Cold hits the husk in front within 5 blocks, for its damage and Slowness II for 3 s, and spares the husks
     * behind the Mage, 45° to its side and beyond 5 blocks.
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
        double expected = 5 + 0.6 * spellPower(player);
        helper.assertTrue(close(HEALTH - inFront.getHealth(), expected), "the husk in front took "
                + (HEALTH - inFront.getHealth()) + ", " + expected + " expected");
        MobEffectInstance slow = inFront.getEffect(MobEffects.SLOWNESS);
        helper.assertTrue(slow != null && slow.getAmplifier() == 1 && slow.getDuration() == 60,
                "Slowness II for 3 s: " + slow);
        for (Husk spared : List.of(farAhead, aside, behind)) {
            helper.assertTrue(close(spared.getHealth(), HEALTH), "a husk outside the cone was hit at "
                    + spared.position());
        }
        logout(player);
        helper.succeed();
    }

    /** Frost Nova hits and Freezes every enemy within 4 blocks for 2 s at rank 1, and none farther. */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17)
    public static void frostNovaFreezesTheEnemiesAround(GameTestHelper helper) {
        ServerPlayer player = mage(helper, "qa-frost-nova", new Vec3(8.5, 1, 8.5));
        learn(helper, player, GeneratedSpells.FROST_NOVA, 1);
        Husk near = husk(helper, new Vec3(8.5, 1, 11.0));
        Husk behind = husk(helper, new Vec3(6.5, 1, 7.0));
        Husk far = husk(helper, new Vec3(8.5, 1, 15.5));
        assertCast(helper, player, GeneratedSpells.FROST_NOVA, SpellAim.at((LivingEntity) null));
        helper.runAfterDelay(2, () -> {
            double expected = 3 + 0.3 * spellPower(player);
            for (Husk hit : List.of(near, behind)) {
                helper.assertTrue(close(HEALTH - hit.getHealth(), expected), "a husk within 4 blocks took "
                        + (HEALTH - hit.getHealth()) + ", " + expected + " expected");
                MobEffectInstance frozen = hit.getEffect(GeneratedEffects.FROZEN);
                helper.assertTrue(frozen != null && frozen.getDuration() > 30, "Frozen for 2 s: " + frozen);
            }
            helper.assertTrue(close(far.getHealth(), HEALTH) && !far.hasEffect(GeneratedEffects.FROZEN),
                    "the husk 7 blocks away was hit");
            logout(player);
            helper.succeed();
        });
    }

    /**
     * Blizzard channels 3 s at a ground spot: six waves, each hitting every enemy within 3 blocks of it and slowing
     * them, and none farther; it costs 13 mana for each of its three seconds.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 140)
    public static void blizzardChannelsSixWavesAtTheSpot(GameTestHelper helper) {
        ServerPlayer player = mage(helper, "qa-frost-blizzard", NORTH_EDGE);
        StatsCommands.setBase(player, GeneratedStats.MANA_REGENERATION, 0);
        learn(helper, player, GeneratedSpells.BLIZZARD, 1);
        Husk inside = husk(helper, new Vec3(9.5, 1, 9.5));
        Husk outside = husk(helper, new Vec3(13.5, 1, 9.5));
        double mana = Resources.amount(player, ResourceTypes.MANA);
        assertCast(helper, player, GeneratedSpells.BLIZZARD, SpellAim.at(helper.absoluteVec(new Vec3(8.5, 1, 9.5))));
        double wave = 2 + 0.2 * spellPower(player);
        helper.runAfterDelay(70, () -> {
            helper.assertTrue(close(HEALTH - inside.getHealth(), 6 * wave), "six waves of " + wave + " expected, the "
                    + "husk inside took " + (HEALTH - inside.getHealth()));
            helper.assertTrue(inside.hasEffect(MobEffects.SLOWNESS), "the husk inside is slowed");
            helper.assertTrue(close(outside.getHealth(), HEALTH), "the husk 5 blocks away was hit");
            double spent = mana - Resources.amount(player, ResourceTypes.MANA);
            helper.assertTrue(close(spent, 39), "3 payments of 13 mana: " + spent + " spent");
            logout(player);
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
                "Throws a shard of ice for Frost 3 damage, Frost 6 damage against a Frozen target.", "Next rank:",
                "Throws a shard of ice for Frost 3.5 damage, Frost 7.9 damage against a Frozen target."),
                "rank 1 and rank 2; as plain text the school's icon reads as its name");
        List<String> top = tooltip(helper, player, lance, 5);
        helper.assertFalse(top.contains("Next rank:"), "no next rank at rank 5: " + top);
        logout(player);
        helper.succeed();
    }

    /**
     * The Mage learns the tree at rank 1 by its rows: Frostbolt, Improved Frostbolt and Cone of Cold at level 1,
     * Ice Lance, Frost Nova and Frostbite at 6, Shatter and Ice Barrier at 12, Blizzard at 18 (once chapter 1 raised
     * the cap); the bar holds the actives in that order, never the passives, and Fireball and Arcane Missiles never come.
     */
    @QaGameTest(timeoutTicks = 100)
    public static void theMageLearnsTheTreeByItsRows(GameTestHelper helper) {
        ServerPlayer player = ClassKitGameTests.character(helper, "qa-frost-kit", "mage");
        assertKnows(helper, player, List.of(GeneratedSpells.FROSTBOLT, GeneratedSpells.IMPROVED_FROSTBOLT,
                GeneratedSpells.CONE_OF_COLD), List.of(GeneratedSpells.ICE_LANCE), 1);
        reachLevel(helper, player, 6);
        assertKnows(helper, player, List.of(GeneratedSpells.ICE_LANCE, GeneratedSpells.FROST_NOVA,
                GeneratedSpells.FROSTBITE), List.of(GeneratedSpells.SHATTER, GeneratedSpells.ICE_BARRIER), 6);
        AdvancementHolder chapter = helper.getLevel().getServer().getAdvancements()
                .get(Identifier.parse("realmforge:chapter/chapter_1"));
        helper.assertTrue(player.getAdvancements().award(chapter, "completed"), "chapter 1 raises the cap to 18");
        reachLevel(helper, player, 12);
        assertKnows(helper, player, List.of(GeneratedSpells.SHATTER, GeneratedSpells.ICE_BARRIER),
                List.of(GeneratedSpells.BLIZZARD), 12);
        reachLevel(helper, player, 18);
        assertKnows(helper, player, List.of(GeneratedSpells.BLIZZARD),
                List.of(GeneratedSpells.FIREBALL, GeneratedSpells.ARCANE_MISSILES), 18);
        Spellbook book = player.getData(SpellAttachments.SPELLBOOK);
        List<ResourceKey<Spell>> bar = new ArrayList<>();
        for (int slot = 0; slot < 8; slot++) {
            book.slot(slot).ifPresent(bar::add);
        }
        helper.assertValueEqual(bar, List.of(GeneratedSpells.FROSTBOLT, GeneratedSpells.CONE_OF_COLD,
                GeneratedSpells.ICE_LANCE, GeneratedSpells.FROST_NOVA, GeneratedSpells.ICE_BARRIER,
                GeneratedSpells.BLIZZARD), "the bar holds the six actives in the tree's order");
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
        CommandSourceStack source = player.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS);
        helper.getLevel().getServer().getCommands().performPrefixedCommand(source, ASTONE_SETUP);
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
                "the kit's 9 ranks and 17 points");
        Holder<Enchantment> protection = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.PROTECTION);
        for (Item piece : List.of(GeneratedItems.NETHERITE_CAP.get(), GeneratedItems.NETHERITE_ROBES.get(),
                GeneratedItems.NETHERITE_PANTS.get(), GeneratedItems.NETHERITE_SANDALS.get())) {
            ItemStack stack = held(player, piece);
            helper.assertTrue(!stack.isEmpty(), "holds " + piece);
            helper.assertValueEqual(stack.getEnchantments().getLevel(protection), 5, piece + "'s Protection");
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

    private static void assertKnows(GameTestHelper helper, ServerPlayer player, List<ResourceKey<Spell>> known,
            List<ResourceKey<Spell>> notYet, int level) {
        Spellbook book = player.getData(SpellAttachments.SPELLBOOK);
        for (ResourceKey<Spell> spell : known) {
            helper.assertValueEqual(book.level(spell), Optional.of(1), "level " + level + " knows "
                    + spell.identifier() + " at rank 1");
        }
        for (ResourceKey<Spell> spell : notYet) {
            helper.assertValueEqual(book.level(spell), Optional.empty(), "level " + level + " does not know "
                    + spell.identifier());
        }
    }

    private static void reachLevel(GameTestHelper helper, ServerPlayer player, int level) {
        long xp = Characters.xpToReach(player, level).orElseThrow();
        helper.assertTrue(Characters.setTotalXp(player, xp), "the XP for level " + level + " was set");
        helper.assertValueEqual(Characters.level(player), level, "the level reached");
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
