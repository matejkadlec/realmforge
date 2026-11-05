package io.github.matejkadlec.realmforge.qa.gametest;

import com.mojang.authlib.GameProfile;
import io.github.matejkadlec.realmforge.combat.DamagePipeline;
import io.github.matejkadlec.realmforge.generated.GeneratedEffects;
import io.github.matejkadlec.realmforge.generated.GeneratedSpells;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import io.github.matejkadlec.realmforge.spell.Aura;
import io.github.matejkadlec.realmforge.spell.Auras;
import io.github.matejkadlec.realmforge.spell.CastFailure;
import io.github.matejkadlec.realmforge.spell.CastResult;
import io.github.matejkadlec.realmforge.spell.CombatState;
import io.github.matejkadlec.realmforge.spell.Passives;
import io.github.matejkadlec.realmforge.spell.ResourceType;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellAim;
import io.github.matejkadlec.realmforge.spell.SpellAttachments;
import io.github.matejkadlec.realmforge.spell.SpellBarView;
import io.github.matejkadlec.realmforge.spell.SpellCaster;
import io.github.matejkadlec.realmforge.spell.SpellRegistries;
import io.github.matejkadlec.realmforge.spell.SpellText;
import io.github.matejkadlec.realmforge.spell.SpellTooltip;
import io.github.matejkadlec.realmforge.spell.Spellbook;
import io.github.matejkadlec.realmforge.spell.Spells;
import io.github.matejkadlec.realmforge.stats.CharacterStats;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * RLM-595: aura spells (Matěj, 2026-11-03; architecture 11.1). The engine's test auras, {@code test_stance_a} (+1/2/3
 * Armor Pierce while a melee weapon is in the main hand) and {@code test_stance_b} (+10 % Armor Shred), share the group
 * {@code test_stance}. An aura is learned with ranks and goes onto the bar; a cast of it toggles it for free, off the
 * global cooldown and beside a cast in progress; turning one on, off or switching starts the 3 minute cooldown every
 * aura shares, which Cooldown Reduction never shortens; one aura of a group is on at a time; its bonus pauses, the
 * aura staying on, while its weapon condition is unmet; and it stays on through death and relog.
 */
public final class AuraGameTests {
    private static final ResourceKey<Spell> STANCE_A = GeneratedSpells.TEST_STANCE_A;
    private static final ResourceKey<Spell> STANCE_B = GeneratedSpells.TEST_STANCE_B;
    private static final Identifier PIERCE = Passives.modifierId(STANCE_A.identifier(), 0);
    private static final Identifier SHRED = Passives.modifierId(STANCE_B.identifier(), 0);

    private AuraGameTests() {
    }

    /** Done when: an aura is learned with ranks and goes onto the bar, off; a cast turns it on and the next one off. */
    @QaGameTest
    public static void anAuraIsLearnedWithRanksAndToggledFromTheBar(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-aura-toggle");
        learn(helper, player, STANCE_A, 2);
        Spellbook book = player.getData(SpellAttachments.SPELLBOOK);
        helper.assertValueEqual(book.level(STANCE_A), Optional.of(2), "learned at rank 2");
        helper.assertTrue(book.barSlotOf(STANCE_A) >= 0, "learning it put it on the bar");
        helper.assertFalse(Auras.isOn(player, STANCE_A), "it starts off");

        helper.assertTrue(toggle(player, STANCE_A).success(), "a cast turns it on");
        helper.assertTrue(Auras.isOn(player, STANCE_A), "on");
        Spells.resetCooldowns(player);
        helper.assertTrue(toggle(player, STANCE_A).success(), "the next cast turns it off");
        helper.assertFalse(Auras.isOn(player, STANCE_A), "off");
        logout(player);
        helper.succeed();
    }

    /**
     * Done when: turning an aura on costs nothing, waits for no global cooldown and starts none, and leaves a cast in
     * progress running, even from the spell bar, whose casts replace one.
     */
    @QaGameTest
    public static void aToggleIsFreeOffTheGlobalCooldownAndBesideACast(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-aura-free");
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 3));
        learn(helper, player, STANCE_A, 1);
        learn(helper, player, GeneratedSpells.TEST_HOMING_BOLT, 1);
        CombatState combat = player.getData(SpellAttachments.COMBAT);
        long now = helper.getLevel().getGameTime();

        helper.assertTrue(SpellCaster.cast(player, GeneratedSpells.TEST_HOMING_BOLT, husk).begun(),
                "a 1 s cast begins and starts the global cooldown");
        Map<ResourceKey<ResourceType>, Float> resources = combat.resources();
        long globalEnd = combat.globalCooldownEnd();
        helper.assertTrue(combat.isOnGlobalCooldown(now), "the global cooldown runs");
        CastResult toggled = SpellCaster.cast(player, STANCE_A, SpellAim.NONE, true);
        helper.assertTrue(toggled.success(), "the aura turns on during the global cooldown: " + toggled);
        helper.assertTrue(Auras.isOn(player, STANCE_A), "on");
        helper.assertValueEqual(combat.globalCooldownEnd(), globalEnd, "it started no global cooldown");
        helper.assertValueEqual(combat.currentCast().map(CombatState.Cast::spell),
                Optional.of(GeneratedSpells.TEST_HOMING_BOLT), "the bolt's cast goes on");
        helper.assertTrue(spell(helper, STANCE_A).value().cost().isEmpty(), "an aura has no cost");
        helper.assertValueEqual(combat.resources(), resources, "the toggle took nothing from any pool");
        Spells.cancelCast(player);
        logout(player);
        helper.succeed();
    }

    /**
     * Done when: turning an aura on, off or switching starts 3 minutes on every aura, which Cooldown Reduction (50 %
     * here) never shortens; one aura of a group is on at a time, so turning B on switches A off.
     */
    @QaGameTest
    public static void everyAuraSharesOneCooldownAndAGroupHasOneOn(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-aura-cooldown");
        StatsCommands.setBase(player, GeneratedStats.COOLDOWN_REDUCTION, 0.5);
        learn(helper, player, STANCE_A, 1);
        learn(helper, player, STANCE_B, 1);
        CombatState combat = player.getData(SpellAttachments.COMBAT);

        helper.assertTrue(toggle(player, STANCE_A).success(), "A turns on");
        assertSharedCooldown(helper, player, "turning A on");
        helper.assertValueEqual(toggle(player, STANCE_B).failureOrNull(), CastFailure.ON_COOLDOWN,
                "B waits for the cooldown A started");
        helper.assertValueEqual(toggle(player, STANCE_A).failureOrNull(), CastFailure.ON_COOLDOWN,
                "and so does turning A off");

        Spells.resetCooldowns(player);
        helper.assertTrue(toggle(player, STANCE_B).success(), "after the cooldown, B turns on");
        helper.assertTrue(Auras.isOn(player, STANCE_B), "B is on");
        helper.assertFalse(Auras.isOn(player, STANCE_A), "switching turned A off: one aura of a group at a time");
        assertSharedCooldown(helper, player, "switching");

        Spells.resetCooldowns(player);
        helper.assertTrue(toggle(player, STANCE_B).success(), "B turns off");
        helper.assertTrue(player.getData(SpellAttachments.SPELLBOOK).auras().isEmpty(), "no aura is on");
        assertSharedCooldown(helper, player, "turning B off");
        helper.getLevel().registryAccess().lookupOrThrow(SpellRegistries.SPELL).listElements()
                .filter(holder -> holder.value().isAura())
                .forEach(holder -> helper.assertTrue(combat.isOnCooldown(holder.key(), helper.getLevel().getGameTime()),
                        "every aura of the data shares it: " + holder.key().identifier()));
        logout(player);
        helper.succeed();
    }

    /**
     * Done when: an aura's bonus applies at its rank while it is on and its weapon condition holds, pauses without the
     * weapon (the aura stays on, its slot shows it paused) and resumes with it.
     */
    @QaGameTest
    public static void anAurasBonusPausesWithoutItsWeaponAndResumes(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-aura-weapon");
        learn(helper, player, STANCE_A, 2);
        wield(player, new ItemStack(Items.STONE_SWORD));
        assertPierce(helper, player, Optional.empty(), "nothing while it is off");
        toggle(player, STANCE_A);
        assertPierce(helper, player, Optional.of(2.0), "on at rank 2 with a sword: +2 Armor Pierce");
        helper.assertValueEqual(slot(player, STANCE_A).aura(), SpellBarView.AuraState.ON, "its slot shows it on");

        wield(player, ItemStack.EMPTY);
        assertPierce(helper, player, Optional.empty(), "an empty hand pauses the bonus");
        helper.assertTrue(Auras.isOn(player, STANCE_A), "the aura stays on");
        helper.assertValueEqual(slot(player, STANCE_A).aura(), SpellBarView.AuraState.PAUSED,
                "its slot shows it paused");

        wield(player, new ItemStack(Items.STONE_SWORD));
        assertPierce(helper, player, Optional.of(2.0), "the sword brings it back");
        learn(helper, player, STANCE_A, 3);
        assertPierce(helper, player, Optional.of(3.0), "a new rank raises it while it is on");
        logout(player);
        helper.succeed();
    }

    /** Done when: an aura that is on stays on after a death and after a relog, its bonus with it. */
    @QaGameTest
    public static void anAuraStaysOnThroughDeathAndRelog(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "qa-aura-relog");
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, profile);
        try {
            learn(helper, player, STANCE_B, 1);
            toggle(player, STANCE_B);
            assertShred(helper, player, Optional.of(0.1), "on: +10 % Armor Shred");

            player = server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
            helper.assertTrue(Auras.isOn(player, STANCE_B), "still on after a death");
            assertShred(helper, player, Optional.of(0.1), "its bonus after a death");

            server.getPlayerList().remove(player);
            player = QaPlayers.login(server, profile);
            helper.assertTrue(Auras.isOn(player, STANCE_B), "still on after a relog");
            assertShred(helper, player, Optional.of(0.1), "its bonus after a relog");
            helper.assertTrue(player.getData(SpellAttachments.COMBAT).isOnCooldown(STANCE_B,
                    helper.getLevel().getGameTime()), "the shared cooldown survives the relog too");
        } finally {
            server.getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** A stunned player cannot toggle an aura, a mob cannot turn one on, and forgetting one turns it off. */
    @QaGameTest
    public static void aStunAMobAndForgettingKeepAnAuraOff(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-aura-refused");
        learn(helper, player, STANCE_B, 1);
        player.addEffect(new MobEffectInstance(GeneratedEffects.STUNNED, 40));
        helper.assertValueEqual(toggle(player, STANCE_B).failureOrNull(), CastFailure.STUNNED, "stunned");
        player.removeEffect(GeneratedEffects.STUNNED);
        helper.assertTrue(toggle(player, STANCE_B).success(), "on once the stun is gone");

        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 3));
        helper.assertValueEqual(SpellCaster.cast(husk, spell(helper, STANCE_B), 1, SpellAim.NONE).failureOrNull(),
                CastFailure.CANNOT_CAST, "a mob never turns an aura on");

        Spells.forget(player, STANCE_B);
        helper.assertFalse(Auras.isOn(player, STANCE_B), "forgetting it turns it off");
        assertShred(helper, player, Optional.empty(), "and its bonus goes");
        logout(player);
        helper.succeed();
    }

    /**
     * An aura's tooltip: its name, rank, "Aura", "Instant", the shared cooldown, its description at the rank (a share
     * reads in percent: +10% Armor Shred) and the next rank's; on the bar its cooldown sweeps the whole 3 minutes, never
     * the global cooldown.
     */
    @QaGameTest
    public static void anAurasTooltipAndSlot(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-aura-tooltip");
        helper.assertValueEqual(tooltip(helper, player, STANCE_A, 1), List.of("Test Stance A", "Rank 1 of 3", "Aura",
                "Instant", "3 min cooldown, shared by every aura", "+1 Armor Pierce while you wield a melee weapon.",
                "Next rank:", "+2 Armor Pierce while you wield a melee weapon."), "rank 1 and the next");
        helper.assertValueEqual(tooltip(helper, player, STANCE_B, 1), List.of("Test Stance B", "Aura", "Instant",
                "3 min cooldown, shared by every aura", "+10% Armor Shred."), "a share reads in percent");

        StatsCommands.setBase(player, GeneratedStats.COOLDOWN_REDUCTION, 0.5);
        learn(helper, player, STANCE_B, 1);
        helper.assertValueEqual(slot(player, STANCE_B).aura(), SpellBarView.AuraState.OFF, "off on the bar");
        toggle(player, STANCE_B);
        player.getData(SpellAttachments.COMBAT).setGlobalCooldownEnd(helper.getLevel().getGameTime() + 10);
        SpellBarView.Slot slot = slot(player, STANCE_B);
        helper.assertValueEqual(slot.cooldownTicks(), Aura.COOLDOWN_TICKS, "3 minutes left");
        helper.assertValueEqual(slot.cooldown(), 1.0F, "the sweep is full: its total is the 3 minutes, not halved");
        logout(player);
        helper.succeed();
    }

    private static void assertSharedCooldown(GameTestHelper helper, ServerPlayer player, String what) {
        CombatState combat = player.getData(SpellAttachments.COMBAT);
        long end = helper.getLevel().getGameTime() + Aura.COOLDOWN_TICKS;
        helper.assertValueEqual(combat.cooldowns().get(STANCE_A), end, what + ": 3 minutes on A, no reduction");
        helper.assertValueEqual(combat.cooldowns().get(STANCE_B), end, what + ": and on B");
    }

    private static void assertPierce(GameTestHelper helper, ServerPlayer player, Optional<Double> amount,
            String what) {
        assertModifier(helper, player, GeneratedStats.ARMOR_PIERCE, PIERCE, amount, what);
    }

    private static void assertShred(GameTestHelper helper, ServerPlayer player, Optional<Double> amount,
            String what) {
        assertModifier(helper, player, GeneratedStats.ARMOR_SHRED, SHRED, amount, what);
    }

    private static void assertModifier(GameTestHelper helper, ServerPlayer player,
            Holder<Attribute> attribute, Identifier id,
            Optional<Double> amount, String what) {
        CharacterStats.recompute(player);
        AttributeModifier modifier = player.getAttribute(attribute).getModifier(id);
        helper.assertValueEqual(Optional.ofNullable(modifier).map(AttributeModifier::amount), amount, what);
    }

    private static SpellBarView.Slot slot(ServerPlayer player, ResourceKey<Spell> spell) {
        Spellbook book = player.getData(SpellAttachments.SPELLBOOK);
        SpellBarView view = SpellBarView.of(book, player.getData(SpellAttachments.COMBAT),
                player.level().getGameTime(), key -> Spells.get(player.registryAccess(), key).map(Holder::value),
                DamagePipeline.stat(player, GeneratedStats.COOLDOWN_REDUCTION), 10,
                aura -> aura.bonus().appliesWith(player.getMainHandItem()));
        return view.slots().get(book.barSlotOf(spell));
    }

    private static CastResult toggle(ServerPlayer player, ResourceKey<Spell> spell) {
        return SpellCaster.cast(player, spell, SpellAim.NONE);
    }

    private static void wield(ServerPlayer player, ItemStack stack) {
        player.setItemSlot(EquipmentSlot.MAINHAND, stack);
    }

    private static List<String> tooltip(GameTestHelper helper, ServerPlayer player, ResourceKey<Spell> key,
            int level) {
        return SpellTooltip.lines(spell(helper, key).value(), level, SpellText.statsOf(player),
                SpellText.schoolsOf(helper.getLevel().registryAccess())).stream().map(Component::getString).toList();
    }

    private static Holder.Reference<Spell> spell(GameTestHelper helper, ResourceKey<Spell> key) {
        return Spells.get(helper.getLevel().registryAccess(), key).orElseThrow();
    }

    private static void learn(GameTestHelper helper, ServerPlayer player, ResourceKey<Spell> key, int level) {
        Spells.learn(player, spell(helper, key), level);
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, name, new Vec3(2.5, 1, 0.5));
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static void logout(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }
}
