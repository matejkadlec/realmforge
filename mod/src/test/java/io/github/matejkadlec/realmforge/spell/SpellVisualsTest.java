package io.github.matejkadlec.realmforge.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.matejkadlec.realmforge.combat.CritRule;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * The parts of the spell visuals kit as data (RLM-639, ADR 0073): a look's colours, particles and sound, a model, and
 * which projectile a spell's visuals form during its cast; and the point in front of a caster where it forms.
 */
class SpellVisualsTest {
    private static final double CLOSE = 1e-6;
    private static final Identifier SHATTER = Identifier.parse("minecraft:block.glass.break");
    private static final Identifier BOULDER = Identifier.parse("realmforge:spell.astone_boulder.impact");
    private static final SpellLook FROST = new SpellLook(List.of(0xFFB8E2F4, 0xFF6ABCE2),
            Optional.of(new SpellLook.Trail(Identifier.parse("realmforge:mote"), 2)),
            Optional.of(new SpellLook.Burst(Identifier.parse("realmforge:shard"), 16)), Optional.of(SHATTER));
    private static final SpellModel ORB = new SpellModel(SpellModel.Shape.ORB, 1, SpellModel.Orientation.TUMBLE, 0);

    private static ProjectileEffect projectile(boolean withModel) {
        return new ProjectileEffect(30, 0.5, Optional.empty(), List.of(new DamageEffect(1, 0, Coefficient.NONE)),
                Optional.empty(), withModel ? Optional.of(Holder.direct(ORB)) : Optional.empty(),
                Holder.direct(new SpellTrajectory(SpellTrajectory.Kind.STRAIGHT)), Optional.empty());
    }

    private static Spell spell(SpellCast cast, Optional<Visuals.Forming> forming, SpellEffect first) {
        return new Spell("n", "d", Optional.empty(), Scaling.SPELL_POWER,
                new DirectionTargeting(40), cast, Optional.empty(), 0, Spell.DEFAULT_GLOBAL_COOLDOWN_TICKS, 1,
                CritRule.DEFAULT, false, List.of(), false, List.of(first), Optional.empty(), SpellConditions.NONE, 0,
                Optional.empty(), Optional.of(new Visuals(Holder.direct(FROST), forming, Optional.empty())));
    }

    @Test
    void aLookReadsItsColoursParticlesAndSound() {
        SpellLook look = SpellLook.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"colors\": "
                + "[\"#B8E2F4\", \"#6ABCE2\"], \"trail\": {\"particle\": \"realmforge:mote\", \"per_block\": 2.0}, "
                + "\"burst\": {\"particle\": \"realmforge:shard\", \"count\": 16}, "
                + "\"impact_sound\": \"minecraft:block.glass.break\"}")).getOrThrow();
        assertEquals(FROST, look);
        assertEquals(look, SpellLook.CODEC.parse(JsonOps.INSTANCE,
                SpellLook.CODEC.encodeStart(JsonOps.INSTANCE, look).getOrThrow()).getOrThrow(), "it round-trips");
        RandomSource random = RandomSource.create(1);
        for (int n = 0; n < 32; n++) {
            assertTrue(FROST.colors().contains(FROST.color(random)), "a particle takes one of the look's colours");
        }
    }

    @Test
    void aLookKeepsToTheParticleBudget() {
        assertTrue(SpellLook.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"colors\": []}")).isError(),
                "a look without a colour");
        assertTrue(SpellLook.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"colors\": [\"#FFFFFF\"], "
                + "\"trail\": {\"particle\": \"realmforge:mote\", \"per_block\": 3.5}}")).isError(),
                "a trail of more than 3 a block");
        assertTrue(SpellLook.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"colors\": [\"#FFFFFF\"], "
                + "\"burst\": {\"particle\": \"realmforge:shard\", \"count\": 25}}")).isError(),
                "a burst of more than 24");
    }

    @Test
    void aModelReadsItsShapeAndOnlyAForwardOneSpins() {
        assertEquals(ORB, SpellModel.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"shape\": \"orb\"}"))
                .getOrThrow(), "the defaults: it fills the hitbox and tumbles");
        assertEquals(new SpellModel(SpellModel.Shape.ORB, 0.6F, SpellModel.Orientation.FORWARD, 360),
                SpellModel.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"shape\": \"orb\", \"scale\": 0.6, "
                        + "\"orientation\": \"forward\", \"spin_degrees_per_second\": 360.0}")).getOrThrow());
        assertTrue(SpellModel.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"shape\": \"orb\", "
                + "\"spin_degrees_per_second\": 90.0}")).isError(), "a tumbling model that spins about its flight");
        assertTrue(SpellModel.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"shape\": \"orb\", "
                + "\"scale\": 0.0}")).isError(), "a model of no size");
        assertTrue(SpellModel.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"shape\": \"cube\"}")).isError(),
                "an unknown shape");
    }

    @Test
    void aCastTimeFormsItsFirstProjectileWhenItHasAModel() {
        SpellCast castTime = SpellCast.castTime(30, false, false);
        ProjectileEffect bolt = projectile(true);
        assertEquals(Optional.of(bolt), SpellVisuals.formedProjectile(
                spell(castTime, Optional.of(Visuals.Forming.GROW), bolt)), "grow");
        assertEquals(Optional.of(bolt), SpellVisuals.formedProjectile(
                spell(castTime, Optional.of(Visuals.Forming.STORM), bolt)), "storm, its fallback");
        assertEquals(Optional.empty(), SpellVisuals.formedProjectile(
                spell(castTime, Optional.of(Visuals.Forming.HANDS), bolt)), "hands shows no model");
        assertEquals(Optional.empty(), SpellVisuals.formedProjectile(spell(castTime, Optional.empty(), bolt)),
                "a spell that names no forming");
        assertEquals(Optional.empty(), SpellVisuals.formedProjectile(
                spell(castTime, Optional.of(Visuals.Forming.GROW), projectile(false))), "a projectile with no body");
        assertEquals(Optional.empty(), SpellVisuals.formedProjectile(
                spell(SpellCast.channel(30, 10, false, false), Optional.of(Visuals.Forming.GROW), bolt)),
                "a channel forms nothing");
        assertEquals(Optional.empty(), SpellVisuals.formedProjectile(spell(castTime,
                Optional.of(Visuals.Forming.GROW), new DamageEffect(1, 0, Coefficient.NONE))), "no projectile first");
    }

    @Test
    void aSpellsOwnImpactSoundComesBeforeItsLooks() {
        assertEquals(Optional.of(SHATTER),
                new Visuals(Holder.direct(FROST), Optional.empty(), Optional.empty()).impactSoundOrLooks());
        assertEquals(Optional.of(BOULDER),
                new Visuals(Holder.direct(FROST), Optional.empty(), Optional.of(BOULDER)).impactSoundOrLooks());
    }

    @Test
    void theHewnChanterFormsItsStoneInItsRaisedHands() {
        // 0.7 wide, 3 tall, its eyes at vanilla's 85 % (2.55), facing south: its hands reach about 1.1 blocks ahead
        // and 3 blocks up while it casts.
        Vec3 offset = SpellVisuals.formOffset(0.7F, 3.0F, 2.55F, 0);
        assertEquals(0, offset.x, CLOSE);
        assertEquals(2.95, offset.y, CLOSE);
        assertEquals(1.1, offset.z, CLOSE);
    }

    @Test
    void theFormPointTurnsWithTheBody() {
        // Yaw 90 faces west (-x).
        Vec3 offset = SpellVisuals.formOffset(0.7F, 3.0F, 2.55F, 90);
        assertEquals(-1.1, offset.x, CLOSE);
        assertEquals(0, offset.z, CLOSE);
    }

    @Test
    void aShortCasterFormsNoHigherThanItsHeight() {
        // A 2-block caster's eyes are at 1.7: 0.4 above them would be past the room it stands in.
        assertEquals(2.0, SpellVisuals.formOffset(0.6F, 2.0F, 1.7F, 0).y, CLOSE);
    }
}
