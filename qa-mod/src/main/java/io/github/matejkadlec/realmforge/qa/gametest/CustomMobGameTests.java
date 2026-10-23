package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.generated.GeneratedMobs;
import io.github.matejkadlec.realmforge.mob.MobLevels;
import io.github.matejkadlec.realmforge.mob.MobScaling;
import io.github.matejkadlec.realmforge.mob.custom.CasterMob;
import io.github.matejkadlec.realmforge.mob.custom.CustomMob;
import io.github.matejkadlec.realmforge.mob.custom.MeleeMob;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedMobExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedMobExpectations.DropExpectation;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedMobExpectations.MobExpectation;
import io.github.matejkadlec.realmforge.spell.Spells;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import io.github.matejkadlec.realmforge.stats.StatsAttachments;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

/**
 * W3's Done when (RLM-33, architecture 14.2): the custom mobs spawn with levels and their scaling, as every hostile
 * mob (architecture 14.1), and drop only their loot, every drop as often as its YAML says. Also what the archetypes
 * do: the Hewn Chanter sings Shard Song at its target and backs away from one that comes too near, the Hewn Sentinel
 * hits its target and walks back to its post once it has none. {@link ContentMobGameTests} checks the registrations.
 *
 * <p>Other tests log their own players in, so a mob that must follow a known player spawns where that player stands:
 * nobody else is nearer.
 */
public final class CustomMobGameTests {
    /** Rolls of a mob's loot: a 15 % drop misses all of them with a chance of 0.85^400. */
    private static final int ROLLS = 400;
    /** How far a drop's share of the rolls may stray from its chance: more than five standard deviations. */
    private static final double SHARE_TOLERANCE = 0.1;
    private static final double TOLERANCE = 1e-4;

    private CustomMobGameTests() {
    }

    /** Each custom mob spawns near a level 20 player at a level near 20, with that level's scaling and full health. */
    @QaGameTest
    public static void customMobsSpawnWithLevelsAndTheirScaling(GameTestHelper helper) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-custom-mob-level");
        try {
            player.setData(StatsAttachments.LEVEL, new EntityLevel(20));
            Vec3 where = new Vec3(2.5, 1, 2.5);
            for (var type : GeneratedMobs.ALL) {
                CustomMob mob = helper.spawn(type.get(), where, EntitySpawnReason.SPAWN_ITEM_USE);
                mob.setNoAi(true);
                String what = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
                OptionalInt level = MobLevels.level(mob);
                helper.assertTrue(level.isPresent(), what + " has no level");
                helper.assertTrue(level.getAsInt() >= 17 && level.getAsInt() <= 23,
                        what + " is level " + level.getAsInt() + " near a level 20 player");
                MobScaling scaling = MobScaling.at(helper.getLevel().registryAccess(), level.getAsInt());
                helper.assertTrue(scaling.health() > 1, "level " + level.getAsInt() + " scales health");
                assertClose(helper, mob.getMaxHealth(),
                        mob.getAttributeBaseValue(Attributes.MAX_HEALTH) * scaling.health(), what + " maximum health");
                assertClose(helper, mob.getHealth(), mob.getMaxHealth(), what + " starts with full health");
                mob.discard();
            }
        } finally {
            logout(player);
        }
        helper.succeed();
    }

    /**
     * Each custom mob's own drop code ({@code LivingEntity#dropFromLootTable}, as when it dies) gives only the items of
     * its YAML, each in its count, and each about as often as its chance.
     */
    @QaGameTest
    public static void customMobsDropTheirLoot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DamageSource source = level.damageSources().generic();
        for (MobExpectation expected : GeneratedMobExpectations.MOBS) {
            String id = expected.id();
            CustomMob mob = spawn(helper, id);
            mob.setNoAi(true);
            ResourceKey<LootTable> table = mob.getLootTable().orElse(null);
            helper.assertTrue(table != null && table.identifier().toString().equals(expected.lootTable()),
                    id + " drops from " + expected.lootTable() + ", not " + table);
            Map<String, DropExpectation> drops = expected.loot().stream()
                    .collect(Collectors.toMap(DropExpectation::item, Function.identity()));
            Map<String, Integer> rolled = new HashMap<>();
            for (int i = 0; i < ROLLS; i++) {
                List<ItemStack> stacks = new ArrayList<>();
                mob.dropFromLootTable(level, source, false, table, stacks::add);
                for (ItemStack stack : stacks) {
                    String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    DropExpectation drop = drops.get(item);
                    helper.assertTrue(drop != null, id + " dropped " + item + ", which is not its loot");
                    helper.assertTrue(stack.getCount() >= drop.min() && stack.getCount() <= drop.max(),
                            id + " dropped " + stack.getCount() + " " + item + ", not " + drop.min() + " to "
                                    + drop.max());
                    rolled.merge(item, 1, Integer::sum);
                }
            }
            for (DropExpectation drop : expected.loot()) {
                double share = rolled.getOrDefault(drop.item(), 0) / (double) ROLLS;
                helper.assertTrue(Math.abs(share - drop.chance()) <= SHARE_TOLERANCE,
                        id + " dropped " + drop.item() + " in " + share + " of " + ROLLS + " rolls, not about "
                                + drop.chance());
            }
            mob.discard();
        }
        helper.succeed();
    }

    /**
     * The Hewn Chanter, 9 blocks from its target (in reach and beyond the distance it keeps), begins a cast of Shard
     * Song, shows it to clients, and the shard hurts the target.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 200)
    public static void aHewnChanterSingsShardSongAtItsTarget(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-chanter-target", new Vec3(13.5, 1, 8.5));
        CasterMob chanter = helper.spawn(GeneratedMobs.HEWN_CHANTER.get(), new Vec3(4.5, 1, 8.5));
        chanter.setTarget(player);
        float before = player.getHealth();
        AtomicBoolean cast = new AtomicBoolean();
        helper.onEachTick(() -> {
            if (Spells.currentCast(chanter).isPresent() && chanter.isCasting()) {
                cast.set(true);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(cast.get(), "the Chanter began a cast and showed it to clients");
            helper.assertTrue(player.getHealth() < before, "Shard Song hurt the Chanter's target");
            logout(player);
        });
    }

    /** The Hewn Chanter backs away from a target 2 blocks from it, past most of the 6 blocks it keeps. */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 200)
    public static void aHewnChanterBacksAwayFromANearTarget(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-chanter-near", new Vec3(8.5, 1, 8.5));
        CasterMob chanter = helper.spawn(GeneratedMobs.HEWN_CHANTER.get(), new Vec3(8.5, 1, 6.5));
        chanter.setTarget(player);
        // Its shards would wear the player down over the test; the distance is what counts.
        helper.onEachTick(() -> player.setHealth(player.getMaxHealth()));
        helper.succeedWhen(() -> {
            helper.assertTrue(chanter.distanceTo(player) > 5, "the Chanter is " + chanter.distanceTo(player)
                    + " blocks from its target");
            logout(player);
        });
    }

    /** The Hewn Sentinel walks up to its target and hits it. */
    @QaGameTest(timeoutTicks = 100)
    public static void aHewnSentinelHitsItsTarget(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-sentinel-target", new Vec3(3.5, 1, 2.5));
        MeleeMob sentinel = helper.spawn(GeneratedMobs.HEWN_SENTINEL.get(), new Vec3(1.5, 1, 2.5));
        sentinel.setTarget(player);
        float before = player.getHealth();
        helper.succeedWhen(() -> {
            helper.assertTrue(player.getHealth() < before, "the Sentinel hit its target");
            logout(player);
        });
    }

    /**
     * The Hewn Sentinel guards: it takes the spot where it first stands as its post, and once it has no target it walks
     * back to the post from wherever it is.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_9X9, timeoutTicks = 200)
    public static void aHewnSentinelWalksBackToItsPost(GameTestHelper helper) {
        MeleeMob sentinel = helper.spawn(GeneratedMobs.HEWN_SENTINEL.get(), new Vec3(2.5, 1, 2.5));
        BlockPos post = helper.absolutePos(new BlockPos(2, 1, 2));
        // No player of another test may draw it away.
        helper.onEachTick(() -> sentinel.setTarget(null));
        AtomicBoolean moved = new AtomicBoolean();
        helper.succeedWhen(() -> {
            if (!moved.get()) {
                helper.assertTrue(sentinel.post().filter(post::equals).isPresent(),
                        "the Sentinel took its post at " + post + ": " + sentinel.post());
                Vec3 away = helper.absoluteVec(new Vec3(6.5, 1, 6.5));
                sentinel.teleportTo(away.x, away.y, away.z);
                moved.set(true);
                throw helper.assertionException("the Sentinel was moved away from its post");
            }
            helper.assertTrue(sentinel.position().closerThan(post.getBottomCenter(), 1.5),
                    "the Sentinel is back at its post: " + sentinel.position());
        });
    }

    private static CustomMob spawn(GameTestHelper helper, String id) {
        for (var type : GeneratedMobs.ALL) {
            if (type.getId().toString().equals(id)) {
                return helper.spawn(type.get(), new BlockPos(2, 1, 2));
            }
        }
        throw helper.assertionException(id + " is not one of GeneratedMobs.ALL");
    }

    /** A fake player in survival at {@code where} on the platform. */
    private static ServerPlayer player(GameTestHelper helper, String name, Vec3 where) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, name, where);
        player.setGameMode(GameType.SURVIVAL);
        // A fake player's client never reports that it loaded, which keeps the player invulnerable.
        player.connection.markClientLoaded();
        return player;
    }

    private static void logout(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }

    private static void assertClose(GameTestHelper helper, double got, double want, String what) {
        helper.assertTrue(Math.abs(got - want) <= TOLERANCE * Math.max(1, Math.abs(want)),
                what + ": expected " + want + ", got " + got);
    }
}
