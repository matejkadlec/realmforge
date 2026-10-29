package io.github.matejkadlec.realmforge.qa.gametest;

import com.mojang.authlib.GameProfile;
import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.character.CharacterAttachments;
import io.github.matejkadlec.realmforge.character.CharacterData;
import io.github.matejkadlec.realmforge.character.CharacterRegistries;
import io.github.matejkadlec.realmforge.character.ClassSpells;
import io.github.matejkadlec.realmforge.qa.command.StateCommands;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedChapterExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedLevelingExpectations;
import io.github.matejkadlec.realmforge.spell.CombatState;
import io.github.matejkadlec.realmforge.spell.ResourceTypes;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellAttachments;
import io.github.matejkadlec.realmforge.spell.SpellRegistries;
import io.github.matejkadlec.realmforge.spell.Spellbook;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import io.github.matejkadlec.realmforge.stats.StatsAttachments;
import io.github.matejkadlec.realmforge.talent.TalentAllocation;
import io.github.matejkadlec.realmforge.talent.TalentAttachments;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.event.EventHooks;

/**
 * F3: the player state attachments (architecture 7) survive saving and loading, are copied on death without the
 * current cast, and the entity level also works on mobs. Players log in through {@link QaPlayers#login}, the mock
 * server player of {@code GameTestHelper#makeMockServerPlayerInLevel} with a chosen profile.
 */
public final class PlayerStateGameTests {
    private static final ResourceKey<Spell> FIREBOLT =
            ResourceKey.create(SpellRegistries.SPELL, Realmforge.id("firebolt"));
    private static final ResourceKey<Spell> FROSTBOLT =
            ResourceKey.create(SpellRegistries.SPELL, Realmforge.id("frostbolt"));

    /** A mob's level. */
    private static final EntityLevel LEVEL = new EntityLevel(14);
    /**
     * A player's level, XP and cached level cap that agree with leveling (C2), which derives the level from the XP
     * and the cap from the chapters when the player logs in: exactly the XP of level 8 (or of the first chapter's cap,
     * if that is lower), no chapter completed.
     */
    private static final int FIRST_CAP = GeneratedChapterExpectations.CHAPTERS.getFirst().levelCap();
    private static final EntityLevel PLAYER_LEVEL = new EntityLevel(Math.min(8, FIRST_CAP));
    private static final CharacterData CHARACTER = new CharacterData(
            Optional.of(ResourceKey.create(CharacterRegistries.RACE, Realmforge.id("human"))),
            List.of(ResourceKey.create(CharacterRegistries.CLASS, Realmforge.id("mage"))),
            GeneratedLevelingExpectations.LEVELS.get(PLAYER_LEVEL.value() - 1).toReach(), Optional.of(FIRST_CAP));
    private static final Spellbook SPELLBOOK = new Spellbook(Map.of(FIREBOLT, 2, FROSTBOLT, 1),
            Map.of(0, FIREBOLT, 1, FROSTBOLT));
    private static final TalentAllocation TALENTS = new TalentAllocation(
            Map.of(Realmforge.id("mage_fire"), Map.of("improved_firebolt", 3)), 2);

    private PlayerStateGameTests() {
    }

    /** Done when of F3: the attachments come back after the player logs out (saved) and in again (loaded). */
    @QaGameTest
    public static void playerStateSurvivesSaveAndLoad(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "qa-state-save");
        ServerPlayer first = QaPlayers.loginOnPlatform(helper, profile);
        fill(first);
        // A login teaches the class's spells by level (C4): the saved spellbook holds them already, as the level
        // agrees with the XP, so the relog changes nothing.
        ClassSpells.teach(first, false);
        Spellbook saved = first.getData(SpellAttachments.SPELLBOOK);
        helper.assertValueEqual(saved.level(FIREBOLT), Optional.of(2), "a taught spell keeps the rank it had");
        server.getPlayerList().remove(first);

        ServerPlayer again = QaPlayers.login(server, profile);
        assertFilled(helper, again, saved, "after a relog");
        server.getPlayerList().remove(again);
        helper.succeed();
    }

    /** Done when of F3: a respawn after death copies every attachment, but not the cast the player died in. */
    @QaGameTest
    public static void playerStateIsCopiedOnDeath(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer dead = QaPlayers.loginOnPlatform(helper, "qa-state-death");
        fill(dead);
        helper.assertTrue(dead.getData(SpellAttachments.COMBAT).currentCast().isPresent(), "the player was casting");

        ServerPlayer respawned = server.getPlayerList().respawn(dead, false, Entity.RemovalReason.KILLED);
        helper.assertTrue(respawned != dead, "a respawn after death makes a new player entity");
        assertFilled(helper, respawned, SPELLBOOK, "after death");
        server.getPlayerList().remove(respawned);
        helper.succeed();
    }

    /** A new player has none of the attachments yet and reads their defaults. */
    @QaGameTest
    public static void newPlayerReadsTheDefaults(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-state-new");
        for (StateCommands.Entry entry : StateCommands.ATTACHMENTS) {
            helper.assertFalse(player.hasData(entry.type().get()), "a new player has no " + entry.name());
        }
        helper.assertValueEqual(player.getData(StatsAttachments.LEVEL), EntityLevel.DEFAULT, "level");
        helper.assertValueEqual(player.getData(CharacterAttachments.CHARACTER), CharacterData.NONE, "character");
        helper.assertFalse(player.getData(CharacterAttachments.CHARACTER).exists(), "a new player has no character");
        helper.assertValueEqual(player.getData(SpellAttachments.SPELLBOOK), Spellbook.EMPTY, "spellbook");
        helper.assertValueEqual(player.getData(SpellAttachments.COMBAT), new CombatState(), "combat");
        helper.assertValueEqual(player.getData(TalentAttachments.TALENTS), TalentAllocation.NONE, "talents");
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    /** {@code /qa state reset} removes every attachment, so the player reads the defaults again. */
    @QaGameTest
    public static void stateResetRemovesEveryAttachment(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-state-reset");
        fill(player);
        helper.assertValueEqual(StateCommands.reset(player), StateCommands.ATTACHMENTS.size(), "attachments removed");
        for (StateCommands.Entry entry : StateCommands.ATTACHMENTS) {
            helper.assertFalse(player.hasData(entry.type().get()), entry.name() + " was reset");
        }
        helper.assertValueEqual(StateCommands.reset(player), 0, "a second reset finds nothing");
        // getData stores the default it returns, so this read comes last.
        helper.assertValueEqual(player.getData(StatsAttachments.LEVEL), EntityLevel.DEFAULT, "level after reset");
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    /** The entity level lives on mobs too, saved with the mob. A hostile mob gets one when it joins the world (W1). */
    @QaGameTest
    public static void mobLevelIsSavedWithTheMob(GameTestHelper helper) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        helper.assertTrue(zombie.hasData(StatsAttachments.LEVEL), "the zombie got a level when it joined");
        zombie.setData(StatsAttachments.LEVEL, LEVEL);

        ProblemReporter.Collector problems = new ProblemReporter.Collector();
        TagValueOutput output = TagValueOutput.createWithContext(problems, helper.getLevel().registryAccess());
        zombie.saveWithoutId(output);
        CompoundTag saved = output.buildResult();
        Zombie loaded = EntityType.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.LOAD);
        helper.assertTrue(loaded != null, "a zombie can be created");
        loaded.load(TagValueInput.create(problems, helper.getLevel().registryAccess(), saved));
        helper.assertTrue(problems.isEmpty(), "problems saving or loading the zombie: " + problems.getReport());
        helper.assertValueEqual(loaded.getData(StatsAttachments.LEVEL), LEVEL, "the loaded zombie's level");
        helper.succeed();
    }

    /**
     * A converting mob keeps its level: NeoForge copies copy-on-death attachments on {@code LivingConversionEvent}.
     * The conversion is the one vanilla's {@code Zombie#convertToZombieType} makes when a zombie drowns, without its
     * attribute rolls. {@code MobLevelGameTests} checks that the converted mob's scaling follows the copied level.
     */
    @QaGameTest
    public static void convertedMobKeepsItsLevel(GameTestHelper helper) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        zombie.setData(StatsAttachments.LEVEL, LEVEL);
        Drowned drowned = zombie.convertTo(EntityType.DROWNED, ConversionParams.single(zombie, true, true),
                converted -> EventHooks.onLivingConvert(zombie, converted));
        helper.assertTrue(drowned != null, "the zombie converted");
        helper.assertValueEqual(drowned.getData(StatsAttachments.LEVEL), LEVEL, "the drowned's level");
        helper.succeed();
    }

    private static void fill(LivingEntity entity) {
        entity.setData(StatsAttachments.LEVEL, PLAYER_LEVEL);
        entity.setData(CharacterAttachments.CHARACTER, CHARACTER);
        entity.setData(SpellAttachments.SPELLBOOK, SPELLBOOK);
        entity.setData(SpellAttachments.COMBAT, combat(true));
        entity.setData(TalentAttachments.TALENTS, TALENTS);
    }

    private static void assertFilled(GameTestHelper helper, LivingEntity entity, Spellbook spellbook, String when) {
        helper.assertValueEqual(entity.getData(StatsAttachments.LEVEL), PLAYER_LEVEL, "level " + when);
        helper.assertValueEqual(entity.getData(CharacterAttachments.CHARACTER), CHARACTER, "character " + when);
        helper.assertValueEqual(entity.getData(SpellAttachments.SPELLBOOK), spellbook, "spellbook " + when);
        helper.assertValueEqual(entity.getData(SpellAttachments.COMBAT), combat(false),
                "combat state (resource pools and cooldowns, no cast) " + when);
        helper.assertValueEqual(entity.getData(TalentAttachments.TALENTS), TALENTS, "talents " + when);
    }

    private static CombatState combat(boolean casting) {
        CombatState state = new CombatState();
        state.setResource(ResourceTypes.MANA.getKey(), 55.5F);
        state.setResource(ResourceTypes.RAGE.getKey(), 12.0F);
        state.setCooldownEnd(FROSTBOLT, 4_000L);
        state.setGlobalCooldownEnd(3_020L);
        if (casting) {
            state.startCast(new CombatState.Cast(FIREBOLT, 3_000L, 40, false));
        }
        return state;
    }
}
