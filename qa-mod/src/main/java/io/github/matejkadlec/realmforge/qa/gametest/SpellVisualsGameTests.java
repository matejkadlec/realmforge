package io.github.matejkadlec.realmforge.qa.gametest;

import static io.github.matejkadlec.realmforge.qa.gametest.CastGameTests.HEALTH;
import static io.github.matejkadlec.realmforge.qa.gametest.CastGameTests.assertHealth;
import static io.github.matejkadlec.realmforge.qa.gametest.CastGameTests.caster;
import static io.github.matejkadlec.realmforge.qa.gametest.CastGameTests.husk;
import static io.github.matejkadlec.realmforge.qa.gametest.CastGameTests.learn;
import static io.github.matejkadlec.realmforge.qa.gametest.CastGameTests.logout;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.character.Characters;
import io.github.matejkadlec.realmforge.generated.GeneratedParticles;
import io.github.matejkadlec.realmforge.generated.GeneratedSpells;
import io.github.matejkadlec.realmforge.qa.combat.SoundLog;
import io.github.matejkadlec.realmforge.qa.combat.TrainingDummy;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedSpellVisualsExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedSpellVisualsExpectations.EmitterExpectation;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedSpellVisualsExpectations.LookExpectation;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedSpellVisualsExpectations.ModelExpectation;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedSpellVisualsExpectations.ParticleExpectation;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedSpellVisualsExpectations.TrajectoryExpectation;
import io.github.matejkadlec.realmforge.spell.CastResult;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellAim;
import io.github.matejkadlec.realmforge.spell.SpellCaster;
import io.github.matejkadlec.realmforge.spell.SpellLook;
import io.github.matejkadlec.realmforge.spell.SpellModel;
import io.github.matejkadlec.realmforge.spell.SpellProjectile;
import io.github.matejkadlec.realmforge.spell.SpellRegistries;
import io.github.matejkadlec.realmforge.spell.SpellTrajectory;
import io.github.matejkadlec.realmforge.spell.Spells;
import io.github.matejkadlec.realmforge.spell.TintedParticles;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The spell visuals kit on the server (RLM-639, ADR 0073, architecture 11.3): its content reaches the game as
 * written, two spells that differ only in their look fly and hit alike, and a projectile's hit sends the entity event
 * its clients burst its look's particles from, with the look's sound. What a client draws from them (materials,
 * trails, bursts) is checked in a rendered client, not here.
 *
 * <p>The twin test spells, {@code test_twin_frost} and {@code test_twin_fire}: instant, along the caster's look, 20
 * blocks a second, 3 damage, the orb, the straight trajectory. A bolt without a lifetime flies until something stops
 * it, so every test puts a wall behind what it shoots at.
 */
public final class SpellVisualsGameTests {
    private static final Identifier SHATTER = Identifier.withDefaultNamespace("block.glass.break");
    private static final Identifier HISS = Identifier.withDefaultNamespace("entity.generic.extinguish_fire");

    private SpellVisualsGameTests() {
    }

    /** Every look, model and trajectory of the content is in its registry as written, and nothing else of ours. */
    @QaGameTest
    public static void theKitsContentIsLoadedAsWritten(GameTestHelper helper) {
        Registry<SpellLook> looks = helper.getLevel().registryAccess().lookupOrThrow(SpellRegistries.SPELL_LOOK);
        helper.assertValueEqual(ours(looks.keySet()), ids(GeneratedSpellVisualsExpectations.LOOKS.stream()
                .map(LookExpectation::id).toList()), "looks in realmforge:spell_look");
        for (LookExpectation expected : GeneratedSpellVisualsExpectations.LOOKS) {
            SpellLook look = looks.getValue(Identifier.parse(expected.id()));
            String id = expected.id();
            helper.assertValueEqual(look.colors().stream().map(SpellVisualsGameTests::hex).toList(),
                    expected.colors(), id + " colours");
            // Rates are floats in the game and doubles in the expectations: compared as floats, so 1.2 equals 1.2.
            helper.assertValueEqual(look.trail().stream().map(trail -> emitter(trail.particle(), trail.perBlock(), 1,
                    trail.colors())).toList(), floats(expected.trail()), id + " trail");
            helper.assertValueEqual(look.burst().stream().map(burst -> emitter(burst.particle(), burst.count(),
                    burst.spread(), burst.colors())).toList(), floats(expected.burst()), id + " burst");
            helper.assertValueEqual(look.swirl().stream().map(swirl -> emitter(swirl.particle(), swirl.perTick(), 1,
                    swirl.colors())).toList(), floats(expected.swirl()), id + " swirl");
            helper.assertValueEqual(look.light().getSerializedName(), expected.light(), id + " light");
            helper.assertValueEqual(look.impactSound().map(Identifier::toString), expected.impactSound(),
                    id + " impact sound");
            look.impactSound().ifPresent(sound -> helper.assertTrue(
                    BuiltInRegistries.SOUND_EVENT.containsKey(sound), id + ": no sound event " + sound));
            for (Identifier particle : Stream.of(look.trail().stream().map(SpellLook.Trail::particle),
                    look.burst().stream().map(SpellLook.Burst::particle),
                    look.swirl().stream().map(SpellLook.Swirl::particle)).flatMap(types -> types).toList()) {
                helper.assertTrue(TintedParticles.of(particle, -1).isPresent(),
                        id + ": " + particle + " is not one of our particles");
            }
        }

        Registry<SpellModel> models = helper.getLevel().registryAccess().lookupOrThrow(SpellRegistries.SPELL_MODEL);
        helper.assertValueEqual(ours(models.keySet()), ids(GeneratedSpellVisualsExpectations.MODELS.stream()
                .map(ModelExpectation::id).toList()), "models in realmforge:spell_model");
        for (ModelExpectation expected : GeneratedSpellVisualsExpectations.MODELS) {
            SpellModel model = models.getValue(Identifier.parse(expected.id()));
            String id = expected.id();
            helper.assertValueEqual(model.shape().getSerializedName(), expected.shape(), id + " shape");
            helper.assertValueEqual(model.scale(), (float) expected.scale(), id + " scale");
            helper.assertValueEqual(model.orientation().getSerializedName(), expected.orientation(),
                    id + " orientation");
            helper.assertValueEqual(model.spinDegreesPerSecond(), (float) expected.spinDegreesPerSecond(),
                    id + " spin");
            if (model.shape() == SpellModel.Shape.CUBES) {
                // Its clients draw the GeckoLib model of its id (RLM-640); without the file they fall back to the orb.
                Identifier geometry = SpellModel.geometry(Identifier.parse(id));
                String file = "/assets/" + geometry.getNamespace() + "/geckolib/models/" + geometry.getPath()
                        + ".geo.json";
                helper.assertTrue(Realmforge.class.getResource(file) != null,
                        id + ": the model of cubes is not in the mod's resources: " + file);
            }
        }

        Registry<SpellTrajectory> trajectories = helper.getLevel().registryAccess()
                .lookupOrThrow(SpellRegistries.SPELL_TRAJECTORY);
        helper.assertValueEqual(ours(trajectories.keySet()), ids(GeneratedSpellVisualsExpectations.TRAJECTORIES
                .stream().map(TrajectoryExpectation::id).toList()), "trajectories in realmforge:spell_trajectory");
        for (TrajectoryExpectation expected : GeneratedSpellVisualsExpectations.TRAJECTORIES) {
            helper.assertValueEqual(trajectories.getValue(Identifier.parse(expected.id())).kind().getSerializedName(),
                    expected.kind(), expected.id() + " kind");
        }
        helper.succeed();
    }

    /** Every particle of the content is a registered particle type with its lifetime, and nothing else of ours. */
    @QaGameTest
    public static void theParticlesAreRegisteredAsWritten(GameTestHelper helper) {
        helper.assertValueEqual(ours(BuiltInRegistries.PARTICLE_TYPE.keySet()),
                ids(GeneratedSpellVisualsExpectations.PARTICLES.stream().map(ParticleExpectation::id).toList()),
                "our particle types");
        for (ParticleExpectation expected : GeneratedSpellVisualsExpectations.PARTICLES) {
            TintedParticles.Entry entry = GeneratedParticles.ALL.stream()
                    .filter(particle -> particle.type().getId().toString().equals(expected.id())).findFirst()
                    .orElseThrow(() -> helper.assertionException("no generated particle " + expected.id()));
            helper.assertValueEqual(entry.motion().shortestLifetimeTicks(), expected.shortestLifetimeTicks(),
                    expected.id() + " shortest life");
            helper.assertValueEqual(entry.motion().longestLifetimeTicks(), expected.longestLifetimeTicks(),
                    expected.id() + " longest life");
            helper.assertTrue(TintedParticles.of(entry.type().getId(), 0xFF336699).filter(
                    particle -> particle.getType() == entry.type().get()).isPresent(),
                    expected.id() + " spawns with a colour");
        }
        helper.succeed();
    }

    /**
     * RLM-639's twins: two spells with one model and one trajectory that differ only in their look. Their projectiles
     * carry those parts, fly the same path tick for tick, and hit for the same damage.
     */
    @QaGameTest
    public static void twoSpellsThatDifferOnlyInTheirLookFlyAndHitAlike(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-visuals-twins");
        wall(helper, 4);
        learn(helper, player, GeneratedSpells.TEST_TWIN_FROST);
        learn(helper, player, GeneratedSpells.TEST_TWIN_FIRE);
        SpellProjectile frost = cast(helper, player, GeneratedSpells.TEST_TWIN_FROST);
        SpellProjectile fire = cast(helper, player, GeneratedSpells.TEST_TWIN_FIRE);
        helper.assertValueEqual(name(frost.look()), "realmforge:frost", "Test Twin Frost's look");
        helper.assertValueEqual(name(fire.look()), "realmforge:fire", "Test Twin Fire's look");
        helper.assertValueEqual(name(frost.model()), "realmforge:orb", "Test Twin Frost's model");
        helper.assertValueEqual(name(fire.model()), name(frost.model()), "the twins' model");
        helper.assertValueEqual(name(frost.trajectory()), "realmforge:straight", "Test Twin Frost's trajectory");
        helper.assertValueEqual(name(fire.trajectory()), name(frost.trajectory()), "the twins' trajectory");
        helper.assertValueEqual(fire.getDeltaMovement(), frost.getDeltaMovement(), "the twins' velocity");
        helper.assertValueEqual(fire.position(), frost.position(), "the twins' launch point");
        Vec3 launch = frost.position();
        Vec3 aim = frost.getDeltaMovement();
        SpellTrajectory straight = frost.trajectory().orElseThrow().value();
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(frost.tickCount > 0 && !frost.isRemoved(), "Test Twin Frost is in flight");
            helper.assertValueEqual(frost.position(), straight.position(launch, aim, frost.tickCount, frost.seed()),
                    frost.tickCount + " tick(s) on, Test Twin Frost is where its trajectory says");
            helper.assertValueEqual(fire.position(), frost.position(), "the twins fly the same path");
            Husk husk = husk(helper, new BlockPos(2, 1, 3));
            helper.runAfterDelay(8, () -> {
                helper.assertTrue(frost.isRemoved() && fire.isRemoved(), "both twins hit");
                assertHealth(helper, husk, HEALTH - 6, "the husk took 3 from each twin");
                logout(player);
                helper.succeed();
            });
        });
    }

    /**
     * The first three spells fly their own models (RLM-440, RLM-441, RLM-442): Frostbolt's ice chunk in the frost
     * look, Fireball's fire ball in the fire look, and each of Arcane Missiles' three crystals in the arcane look. A
     * bolt crosses the platform in a few ticks, so every projectile is noted in the tick it is first seen.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_9X9, timeoutTicks = 200)
    public static void theFirstSpellsFlyTheirOwnModels(GameTestHelper helper) {
        ServerPlayer player = SwingAndSplashGameTests.mage(helper, "qa-visuals-first");
        Husk target = SwingAndSplashGameTests.husk(helper, new Vec3(4.5, 1, 6.5));
        for (ResourceKey<Spell> spell : List.of(GeneratedSpells.FROSTBOLT, GeneratedSpells.FIREBALL,
                GeneratedSpells.ARCANE_MISSILES)) {
            learn(helper, player, spell);
        }
        Map<Integer, String> seen = new LinkedHashMap<>();
        helper.onEachTick(() -> {
            // The player's own: the tests on the platforms beside this one shoot bolts too.
            for (SpellProjectile bolt : helper.getLevel().getEntitiesOfClass(SpellProjectile.class,
                    player.getBoundingBox().inflate(12), bolt -> bolt.getOwner() == player)) {
                seen.putIfAbsent(bolt.getId(), name(bolt.model()) + " in " + name(bolt.look()));
            }
        });
        // Frostbolt and Fireball are 1.5 s casts, Arcane Missiles a 1.5 s channel of three missiles.
        SwingAndSplashGameTests.assertCast(helper, player, GeneratedSpells.FROSTBOLT, target);
        helper.runAfterDelay(45,
                () -> SwingAndSplashGameTests.assertCast(helper, player, GeneratedSpells.FIREBALL, target));
        helper.runAfterDelay(90,
                () -> SwingAndSplashGameTests.assertCast(helper, player, GeneratedSpells.ARCANE_MISSILES, target));
        helper.runAfterDelay(140, () -> {
            helper.assertValueEqual(List.copyOf(seen.values()), List.of("realmforge:ice_chunk in realmforge:frost",
                    "realmforge:fire_ball in realmforge:fire", "realmforge:crystal in realmforge:arcane",
                    "realmforge:crystal in realmforge:arcane", "realmforge:crystal in realmforge:arcane"),
                    "the projectiles of Frostbolt, Fireball and Arcane Missiles, in the order they launched");
            logout(player);
            helper.succeed();
        });
    }

    /**
     * A projectile's hit on a creature (ADR 0073): the server plays the look's sound there and sends one entity event,
     * the creature's, from which each client bursts the look's particles. The twins differ in the sound only.
     */
    @QaGameTest
    public static void aHitOnACreatureSendsItsEntityEventAndPlaysItsLooksSound(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-visuals-creature");
        wall(helper, 4);
        Husk husk = husk(helper, new BlockPos(2, 1, 3));
        learn(helper, player, GeneratedSpells.TEST_TWIN_FROST);
        learn(helper, player, GeneratedSpells.TEST_TWIN_FIRE);
        SpellProjectile[] twins = new SpellProjectile[2];
        long[] since = new long[1];
        watching(helper, player).thenExecute(() -> {
            since[0] = helper.getLevel().getGameTime();
            twins[0] = cast(helper, player, GeneratedSpells.TEST_TWIN_FROST);
            twins[1] = cast(helper, player, GeneratedSpells.TEST_TWIN_FIRE);
        }).thenExecuteAfter(8, () -> {
            assertHealth(helper, husk, HEALTH - 6, "the husk took both twins");
            List<ClientboundEntityEventPacket> sent = QaPlayers.sentPackets(player, ClientboundEntityEventPacket.class);
            helper.assertValueEqual(events(sent, twins[0]), List.of(SpellProjectile.EVENT_HIT_ENTITY),
                    "the entity events of Test Twin Frost");
            helper.assertValueEqual(events(sent, twins[1]), List.of(SpellProjectile.EVENT_HIT_ENTITY),
                    "the entity events of Test Twin Fire");
            List<Identifier> sounds = SoundLog.near(helper.getLevel(), husk.getBoundingBox().getCenter(), 2, since[0]);
            helper.assertTrue(sounds.contains(SHATTER), "the frost look's impact sound: " + sounds);
            helper.assertTrue(sounds.contains(HISS), "the fire look's impact sound: " + sounds);
            logout(player);
        }).thenSucceed();
    }

    /** A projectile's hit on a block sends the block's entity event, so its clients spray its particles off the face. */
    @QaGameTest
    public static void aHitOnABlockSendsItsEntityEvent(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-visuals-block");
        wall(helper, 3);
        learn(helper, player, GeneratedSpells.TEST_TWIN_FROST);
        SpellProjectile[] bolt = new SpellProjectile[1];
        long[] since = new long[1];
        watching(helper, player).thenExecute(() -> {
            since[0] = helper.getLevel().getGameTime();
            bolt[0] = cast(helper, player, GeneratedSpells.TEST_TWIN_FROST);
        }).thenExecuteAfter(8, () -> {
            helper.assertTrue(bolt[0].isRemoved(), "the wall stopped the bolt");
            helper.assertValueEqual(events(QaPlayers.sentPackets(player, ClientboundEntityEventPacket.class), bolt[0]),
                    List.of(SpellProjectile.EVENT_HIT_BLOCK), "the entity events of the bolt");
            List<Identifier> sounds = SoundLog.near(helper.getLevel(), helper.absoluteVec(new Vec3(2.5, 2.6, 3)), 1,
                    since[0]);
            helper.assertTrue(sounds.contains(SHATTER), "the frost look's impact sound on the wall: " + sounds);
            logout(player);
        }).thenSucceed();
    }

    /**
     * A spell without visuals has no look (Test Missile): its projectile is a trail of sparks, and its hit sends no
     * event and plays no sound.
     */
    @QaGameTest
    public static void aProjectileWithoutALookSendsNoImpactEvent(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-visuals-none");
        wall(helper, 4);
        Husk husk = husk(helper, new BlockPos(2, 1, 3));
        learn(helper, player, GeneratedSpells.TEST_MISSILE);
        SpellProjectile[] missile = new SpellProjectile[1];
        watching(helper, player).thenExecute(() -> {
            CastResult result = SpellCaster.cast(player, GeneratedSpells.TEST_MISSILE, husk);
            helper.assertTrue(result.success(), "Test Missile was not cast: " + result);
            List<SpellProjectile> missiles = helper.getLevel().getEntitiesOfClass(SpellProjectile.class,
                    helper.getBounds());
            helper.assertValueEqual(missiles.size(), 1, "missiles launched");
            missile[0] = missiles.getFirst();
            helper.assertTrue(missile[0].look().isEmpty() && missile[0].model().isEmpty(),
                    "Test Missile has neither a look nor a model");
            helper.assertValueEqual(name(missile[0].trajectory()), "realmforge:straight", "it still flies straight");
        }).thenExecuteAfter(8, () -> {
            helper.assertTrue(husk.getHealth() < HEALTH, "the husk took the missile");
            helper.assertValueEqual(events(QaPlayers.sentPackets(player, ClientboundEntityEventPacket.class),
                    missile[0]), List.of(), "the entity events of the missile");
            logout(player);
        }).thenSucceed();
    }

    /**
     * A sequence that starts once {@code player}'s client has the test's chunks ({@link QaPlayers#playClient}), so the
     * server tells it about the projectiles launched there, with nothing sent before left to read.
     */
    private static GameTestSequence watching(GameTestHelper helper, ServerPlayer player) {
        return helper.startSequence().thenWaitUntil(() -> {
            QaPlayers.playClient(helper, player);
            helper.assertTrue(QaPlayers.tracksChunkAt(player, helper.absoluteVec(new Vec3(2.5, 1, 0.5)))
                    && QaPlayers.tracksChunkAt(player, helper.absoluteVec(new Vec3(2.5, 1, 4.5))),
                    "the player's client has the platform's chunks");
        }).thenExecute(() -> QaPlayers.sentPackets(player, ClientboundEntityEventPacket.class));
    }

    /**
     * The playtest handoff's command block line, run through {@code /qa run} as the player (the handoff's
     * {@code execute as @p at @p} only picks that player): a level 18 Human Mage who knows the four bolts at rank 5
     * and the twins, with a training dummy ahead.
     */
    @QaGameTest
    public static void thePlaytestsCommandLineGivesAMageItsBoltsAndADummy(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-visuals-cmd", new Vec3(2.5, 1, 0.5));
        server.getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS),
                "qa run qa character reset; qa character create realmforge:human realmforge:mage; qa level 18; "
                        + "qa spell learn realmforge:frostbolt 5; qa spell learn realmforge:fireball 5; "
                        + "qa spell learn realmforge:arcane_missiles 5; qa spell learn realmforge:ice_lance 5; "
                        + "qa spell learn realmforge:test_twin_frost; qa spell learn realmforge:test_twin_fire; "
                        + "qa dummy");
        helper.assertValueEqual(Characters.mainClass(player).map(entry -> entry.key().identifier().getPath()),
                Optional.of("mage"), "the character's class");
        for (ResourceKey<Spell> bolt : List.of(GeneratedSpells.FROSTBOLT, GeneratedSpells.FIREBALL,
                GeneratedSpells.ARCANE_MISSILES, GeneratedSpells.ICE_LANCE)) {
            helper.assertValueEqual(Spells.knownLevel(player, bolt), Optional.of(5), bolt.identifier() + "'s rank");
        }
        for (ResourceKey<Spell> twin : List.of(GeneratedSpells.TEST_TWIN_FROST, GeneratedSpells.TEST_TWIN_FIRE)) {
            helper.assertValueEqual(Spells.knownLevel(player, twin), Optional.of(1), twin.identifier() + "'s rank");
        }
        List<Entity> dummies = helper.getLevel().getEntities(player, player.getBoundingBox().inflate(6),
                TrainingDummy::isDummy);
        helper.assertValueEqual(dummies.size(), 1, "training dummies ahead");
        dummies.forEach(dummy -> TrainingDummy.remove((LivingEntity) dummy));
        logout(player);
        helper.succeed();
    }

    /** Casts {@code spell} along {@code player}'s look and returns the projectile it launched. */
    private static SpellProjectile cast(GameTestHelper helper, ServerPlayer player, ResourceKey<Spell> spell) {
        List<SpellProjectile> before = helper.getLevel().getEntitiesOfClass(SpellProjectile.class,
                new AABB(player.getEyePosition(), player.getEyePosition()).inflate(1.5));
        Spells.resetCooldowns(player);
        CastResult result = SpellCaster.cast(player, spell, SpellAim.NONE);
        helper.assertTrue(result.success() && !result.begun(), spell.identifier() + " was not cast: " + result);
        List<SpellProjectile> bolts = new ArrayList<>(helper.getLevel().getEntitiesOfClass(SpellProjectile.class,
                new AABB(player.getEyePosition(), player.getEyePosition()).inflate(1.5)));
        bolts.removeAll(before);
        helper.assertValueEqual(bolts.size(), 1, "bolts launched by " + spell.identifier());
        return bolts.getFirst();
    }

    /** The entity events among {@code sent} that are about {@code projectile}, oldest first. */
    private static List<Byte> events(List<ClientboundEntityEventPacket> sent, SpellProjectile projectile) {
        // The packet names its entity by id, and a removed entity is no longer in the level: compare the ids.
        return sent.stream().filter(packet -> QaPlayers.entityId(packet) == projectile.getId())
                .map(ClientboundEntityEventPacket::getEventId).toList();
    }

    private static <T> String name(Optional<Holder<T>> holder) {
        return holder.map(Holder::getRegisteredName).orElse("none");
    }

    private static Set<String> ours(Set<Identifier> ids) {
        Set<String> ours = new TreeSet<>();
        ids.stream().filter(id -> id.getNamespace().equals(Realmforge.MOD_ID)).forEach(id -> ours.add(id.toString()));
        return ours;
    }

    private static Set<String> ids(List<String> ids) {
        return new TreeSet<>(ids);
    }

    /** {@code color} (ARGB) as the content writes it: {@code #RRGGBB}. */
    private static String hex(int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
    }

    /** An emitter of a look as the tests compare it: its rate and spread as floats, its own colours as written. */
    private record Emitter(String particle, float rate, float spread, List<String> colors) {
    }

    private static Emitter emitter(Identifier particle, float rate, float spread, Optional<List<Integer>> colors) {
        return new Emitter(particle.toString(), rate, spread,
                colors.orElse(List.of()).stream().map(SpellVisualsGameTests::hex).toList());
    }

    private static List<Emitter> floats(List<EmitterExpectation> expected) {
        return expected.stream().map(emitter -> new Emitter(emitter.particle(), (float) emitter.rate(),
                (float) emitter.spread(), emitter.colors())).toList();
    }

    /** A stone wall across the 5 x 5 platform at {@code z}, 4 blocks high. */
    private static void wall(GameTestHelper helper, int z) {
        for (int x = 0; x < 5; x++) {
            for (int y = 1; y <= 4; y++) {
                helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
    }
}
