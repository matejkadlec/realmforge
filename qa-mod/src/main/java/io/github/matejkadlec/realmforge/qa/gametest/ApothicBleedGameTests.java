package io.github.matejkadlec.realmforge.qa.gametest;

import dev.shadowsoffire.apothic_attributes.api.AbilityCooldowns;
import io.github.matejkadlec.realmforge.compat.apothic_attributes.ApothicBleeds;
import io.github.matejkadlec.realmforge.effect.EffectStacks;
import io.github.matejkadlec.realmforge.effect.StackTicks;
import io.github.matejkadlec.realmforge.generated.GeneratedEffects;
import io.github.matejkadlec.realmforge.generated.GeneratedSpells;
import io.github.matejkadlec.realmforge.spell.DamageOverTimeEffect;
import io.github.matejkadlec.realmforge.spell.EffectContext;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellTarget;
import io.github.matejkadlec.realmforge.spell.Spells;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * RLM-579 (Matěj's playtest of 2026-11-03: a Bloodletting sword gave a second Bleeding beside Rend's): Apothic
 * Attributes' Bleeding becomes a stack of our {@code realmforge:bleeding}
 * ({@code compat/apothic_attributes/ApothicBleeds} and the mixin {@code ApothicBleedStackingMixin}, architecture 19.4),
 * one per attacker beside Rend's, with the same damage: a quarter of 1 + its level every half second. Bloodletting's
 * own stacking (one level more a hit, at most Bleeding IV, a 4 s cooldown on the target, which the test clears between
 * hits) raises our stack.
 */
public final class ApothicBleedGameTests {
    /** Apotheosis's Bloodletting affix, which gives Bleeding on a melee hit and raises it on the next. */
    private static final Identifier BLOODLETTING = Identifier.parse("apotheosis:melee/mob_effect/bloodletting");
    private static final double TOLERANCE = 1e-6;

    private ApothicBleedGameTests() {
    }

    /**
     * A Bloodletting sword's hit makes the husk bleed with our Bleeding alone, the player's stack at level 0 for its
     * 5 s (Uncommon); a hit within its 4 s cooldown on the target raises nothing; each next hit raises that stack by a
     * level, up to Bleeding IV (its stacking limit, 3), which the target's one effect shows (Matěj's playtest of
     * 2026-11-03), and never adds a second one; Rend's bleed then lies beside it as a stack of its own, under the same
     * one effect, and leaves the level shown.
     */
    @QaGameTest
    public static void aBloodlettingSwordGivesOneBleedingThatStacks(GameTestHelper helper) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-apoth-bleed", new Vec3(2.5, 1, 1.5));
        player.setGameMode(GameType.SURVIVAL);
        player.connection.markClientLoaded();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        ApotheosisGameTests.run(player, "apoth set_rarity apotheosis:uncommon");
        ApotheosisGameTests.run(player, "apoth affix apply " + BLOODLETTING + " 1.0");
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 3));

        for (int level : new int[] {0, 1, 2, 3, 3}) {
            AbilityCooldowns.clear(husk, BLOODLETTING);
            ApotheosisGameTests.ticks(player, 20);
            CombatGameTests.settle(husk);
            player.attack(husk);
            helper.assertFalse(husk.hasEffect(apothicBleeding()), "no Apothic Bleeding on the husk");
            helper.assertTrue(husk.hasEffect(GeneratedEffects.BLEEDING), "our Bleeding on the husk");
            List<EffectStacks.Stack> stacks = StackTicks.of(husk, GeneratedEffects.BLEEDING);
            helper.assertValueEqual(stacks.size(), 1, "one stack after the hit at level " + level + ": " + stacks);
            EffectStacks.Stack stack = stacks.getFirst();
            helper.assertTrue(stack.caster().equals(player.getUUID())
                    && stack.spell().equals(Optional.of(ApothicBleeds.SOURCE)), "the player's Bloodletting stack: "
                            + stack);
            helper.assertTrue(Math.abs(stack.perTick() - (1 + level) * 0.25) < TOLERANCE, "level " + level + " deals "
                    + (1 + level) * 0.25 + " a tick: " + stack);
            helper.assertValueEqual(husk.getEffect(GeneratedEffects.BLEEDING).getAmplifier(), level,
                    "the target shows the level, Bleeding " + (level + 1));
            if (level == 0) {
                ApotheosisGameTests.ticks(player, 20);
                CombatGameTests.settle(husk);
                player.attack(husk);
                helper.assertTrue(Math.abs(StackTicks.of(husk, GeneratedEffects.BLEEDING).getFirst().perTick() - 0.25)
                        < TOLERANCE, "a hit within the 4 s cooldown raises nothing");
            }
        }

        Holder.Reference<Spell> rend = Spells.get(helper.getLevel().registryAccess(), GeneratedSpells.REND)
                .orElseThrow();
        DamageOverTimeEffect bleed = (DamageOverTimeEffect) rend.value().effects().get(1);
        bleed.applyTo(EffectContext.of(helper.getLevel(), player, rend, 1, Optional.empty()), husk,
                SpellTarget.of(player, husk));
        List<EffectStacks.Stack> stacks = StackTicks.of(husk, GeneratedEffects.BLEEDING);
        helper.assertValueEqual(stacks.size(), 2, "Rend's stack beside Bloodletting's: " + stacks);
        helper.assertFalse(husk.hasEffect(apothicBleeding()), "still no Apothic Bleeding");
        helper.assertValueEqual(husk.getEffect(GeneratedEffects.BLEEDING).getAmplifier(), 3, "still Bleeding IV");
        ApotheosisGameTests.logout(player);
        helper.succeed();
    }

    /**
     * Its ticks total 1 + its level every 2 s through 20 armor, here Bleeding IV's 4, with Rend's stack ticking beside
     * it; four half-second ticks fall within 45 ticks of a fresh bleed.
     */
    @QaGameTest(timeoutTicks = 100)
    public static void itsTicksGoThroughArmorBesideRends(GameTestHelper helper) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-apoth-bleed-ticks", new Vec3(2.5, 1, 0.5));
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 3));
        CombatGameTests.setBase(husk, Attributes.ARMOR, 20);
        husk.addEffect(new MobEffectInstance(apothicBleeding(), 200, 3));
        Holder.Reference<Spell> rend = Spells.get(helper.getLevel().registryAccess(), GeneratedSpells.REND)
                .orElseThrow();
        DamageOverTimeEffect bleed = (DamageOverTimeEffect) rend.value().effects().get(1);
        bleed.applyTo(EffectContext.of(helper.getLevel(), player, rend, 1, Optional.empty()), husk,
                SpellTarget.of(player, husk));
        List<EffectStacks.Stack> stacks = StackTicks.of(husk, GeneratedEffects.BLEEDING);
        helper.assertValueEqual(stacks.size(), 2, "Bleeding IV's stack and Rend's: " + stacks);
        double rendTick = stacks.stream().filter(stack -> stack.spell().equals(Optional.of(GeneratedSpells.REND
                .identifier()))).findFirst().orElseThrow().perTick();
        float before = husk.getHealth();
        helper.runAfterDelay(45, () -> {
            double lost = before - husk.getHealth();
            helper.assertTrue(Math.abs(lost - (4 + 4 * rendTick)) < 1e-3, "four ticks of 1 and of Rend's " + rendTick
                    + ", through armor: lost " + lost);
            ApotheosisGameTests.logout(player);
            helper.succeed();
        });
    }

    /**
     * Apothic Bleeding from any other source (a command, a potion) becomes our stack too, at its level and for its
     * duration, without an attacker when the target has none.
     */
    @QaGameTest
    public static void anyApothicBleedingBecomesOurs(GameTestHelper helper) {
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 2));
        husk.addEffect(new MobEffectInstance(apothicBleeding(), 200, 2));
        helper.assertFalse(husk.hasEffect(apothicBleeding()), "no Apothic Bleeding on the husk");
        List<EffectStacks.Stack> stacks = StackTicks.of(husk, GeneratedEffects.BLEEDING);
        helper.assertValueEqual(stacks.size(), 1, "one stack of ours: " + stacks);
        EffectStacks.Stack stack = stacks.getFirst();
        helper.assertTrue(stack.caster().equals(Util.NIL_UUID) && Math.abs(stack.perTick() - 0.75) < TOLERANCE
                && stack.remainingTicks() == 200, "Bleeding III's stack, without a caster, for 10 s: " + stack);
        helper.assertValueEqual(husk.getEffect(GeneratedEffects.BLEEDING).getAmplifier(), 2, "it shows as Bleeding III");
        helper.succeed();
    }

    private static Holder<MobEffect> apothicBleeding() {
        return BuiltInRegistries.MOB_EFFECT.get(ApothicBleeds.EFFECT)
                .orElseThrow(() -> new IllegalStateException("Apothic Attributes has no effect "
                        + ApothicBleeds.EFFECT));
    }
}
