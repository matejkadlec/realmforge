package io.github.matejkadlec.realmforge.boss;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.combat.InCombat;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import io.github.matejkadlec.realmforge.mob.MobLevels;
import io.github.matejkadlec.realmforge.mob.custom.CustomMob;
import io.github.matejkadlec.realmforge.mob.custom.CustomMobSpec;
import io.github.matejkadlec.realmforge.mob.custom.MeleeMob;
import io.github.matejkadlec.realmforge.spell.AreaEffect;
import io.github.matejkadlec.realmforge.spell.CombatState;
import io.github.matejkadlec.realmforge.spell.FarthestPlayerTargeting;
import io.github.matejkadlec.realmforge.spell.RandomPlayerTargeting;
import io.github.matejkadlec.realmforge.spell.SelfTargeting;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellAim;
import io.github.matejkadlec.realmforge.spell.SpellCaster;
import io.github.matejkadlec.realmforge.spell.SpellEffect;
import io.github.matejkadlec.realmforge.spell.SpellTarget;
import io.github.matejkadlec.realmforge.spell.SpellTargeting;
import io.github.matejkadlec.realmforge.spell.Spells;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The body of a boss (architecture 14.3), the custom mob archetype {@code boss}: a melee mob ({@link MeleeMob}) that
 * runs the fight of its entry in {@code realmforge:boss} ({@link Boss}, found by its entity type through
 * {@link Bosses}).
 *
 * <ul>
 *   <li><b>Phases:</b> once a fight has begun (it has had a target since its last reset), {@link BossPhaseMachine}
 *   moves it into the next phase when that phase's trigger holds, and the time in a phase counts; a boss nobody has
 *   pulled stays in its first phase, so an enrage timer starts with the fight. A new phase cancels the cast in
 *   progress, casts its {@code on_enter} spells at once (without their cooldowns), and for
 *   the definition's phase change time the boss stands, casts nothing and takes no hurt, which particles round it
 *   show; a refused hit sounds like a blocked one.</li>
 *   <li><b>Abilities:</b> while it has a target, it casts its phase's abilities through {@link SpellCaster}, the
 *   first one ready that the engine lets it cast, so every attack is a readable cast. It aims at its target, unless
 *   the spell picks its own ({@code random_player}, {@code farthest_player}) or acts on itself; an area round itself
 *   it casts only while its target stands inside it. Between casts it fights in melee; while a cast runs it stands
 *   and faces its target ({@link BossHoldGoal}), and a ring of particles marks where the cast's area will land, so a
 *   player can step out of it (research BM-2).</li>
 *   <li><b>Arena:</b> its home is the spot where it first stood on the ground. It attacks only what stands within the
 *   arena's radius of home ({@link #canAttack}, which vanilla's target goals and {@code Mob#getTarget} ask, so they
 *   let go of a target that leaves it), and takes no hurt from outside it. Pulled out of it, it resets: back home, at full
 *   health, in its first phase, its effects, casts and cooldowns gone. It resets the same way once its fight is
 *   over: it had a target and has none now, and it is out of combat (architecture 9.4).</li>
 *   <li><b>Boss bar:</b> its {@link BossBar} in the definition's colour, titled with its name and its phase's.</li>
 *   <li><b>Level:</b> its definition's, unless a dungeon run gives it the run's ({@link MobLevels.FixedLevel}).</li>
 *   <li><b>Persistence:</b> it never despawns, as vanilla's bosses do not, however far the players are, and it never
 *   goes through a portal, as the Wither does not: its arena and home belong to the dimension it stands in.</li>
 * </ul>
 */
public class BossMob extends MeleeMob implements MobLevels.FixedLevel {
    private static final String PHASE = "realmforge:boss_phase";
    private static final String PHASE_TICKS = "realmforge:boss_phase_ticks";
    private static final String PHASE_CHANGE = "realmforge:boss_phase_change";
    private static final String ENGAGED = "realmforge:boss_engaged";
    /** The proc origin of a phase's {@code on_enter} spells: their hits trigger no proc (architecture 12). */
    private static final Identifier PHASE_SPELLS = Realmforge.id("boss_phase");
    /** How often, in ticks, a boss that is not casting tries its abilities. */
    private static final int CAST_INTERVAL = 5;
    /** How often, in ticks, the particles of a phase change show. */
    private static final int PHASE_CHANGE_PARTICLE_INTERVAL = 4;
    /** How often, in ticks, the ring that marks where a cast's area lands shows. */
    private static final int LANDING_RING_INTERVAL = 4;
    /** The space between two particles of that ring, in blocks. */
    private static final double LANDING_RING_SPACING = 0.6;
    /** The ring's particles: dark grey dust (Matěj's playtest of Astone, 2026-10-29: grey, not orange or purple). */
    private static final DustParticleOptions LANDING_RING = new DustParticleOptions(0x4A4A4A, 1.5F);

    private final BossBar bossBar = new BossBar(this, BossEvent.BossBarColor.RED);
    /** When each ability may be cast again, in game time; its own cooldown, beside the spell's. */
    private final Map<ResourceKey<Spell>, Long> abilityReady = new HashMap<>();
    private @Nullable Holder.Reference<Boss> definition;
    private boolean resolved;
    private int phase;
    private int phaseTicks;
    private int phaseChangeLeft;
    /**
     * Whether it has had a target since its last reset: only then do its phases move on and its time in a phase count,
     * and only a fight that happened can end.
     */
    private boolean engaged;
    private int castDelay;

    public BossMob(EntityType<? extends BossMob> type, Level level, CustomMobSpec spec) {
        super(type, level, spec);
    }

    /** The archetype {@code boss}'s factory for {@code mob/custom/Archetypes}. */
    @SuppressWarnings("unchecked")
    static CustomMob create(EntityType<? extends CustomMob> type, Level level, CustomMobSpec spec) {
        return new BossMob((EntityType<? extends BossMob>) type, level, spec);
    }

    /** Its fight, once the server knows it; empty on clients and for a body no entry names. */
    public Optional<Holder.Reference<Boss>> definition() {
        if (!resolved && level() instanceof ServerLevel) {
            resolved = true;
            definition = Bosses.of(registryAccess(), getType()).orElse(null);
            if (definition != null) {
                bossBar.setColor(definition.value().barColor());
                phase = Math.min(phase, definition.value().phases().size() - 1);
                updateTitle();
            }
        }
        return Optional.ofNullable(definition);
    }

    @Override
    public OptionalInt fixedLevel() {
        return definition().map(boss -> OptionalInt.of(boss.value().level())).orElse(OptionalInt.empty());
    }

    /** The index of its current phase, 0 for the first. */
    public int phaseIndex() {
        return phase;
    }

    /** Its current phase; empty without a definition. */
    public Optional<BossPhase> phase() {
        return definition().map(boss -> boss.value().phases().get(phase));
    }

    /** The ticks of fight since it entered its current phase. */
    public int ticksInPhase() {
        return phaseTicks;
    }

    /** Whether it is changing phase: it stands, casts nothing and takes no hurt. */
    public boolean changingPhase() {
        return phaseChangeLeft > 0;
    }

    /** Its boss bar. */
    public BossBar bossBar() {
        return bossBar;
    }

    /** Its home, the arena's centre, once it has stood on the ground. */
    public Optional<BlockPos> home() {
        return post();
    }

    /** Whether {@code entity} stands in its arena; anything does before the boss has a home. */
    public boolean inArena(Entity entity) {
        Optional<BlockPos> home = home();
        if (home.isEmpty() || definition().isEmpty()) {
            return true;
        }
        double radius = definition().get().value().arena().radius();
        return entity.position().distanceToSqr(Vec3.atBottomCenterOf(home.get())) <= radius * radius;
    }

    @Override
    protected void addFightGoals() {
        goalSelector.addGoal(1, new BossHoldGoal(this));
        super.addFightGoals();
    }

    /**
     * It attacks only what stands in its arena. Vanilla's target goals and {@code Mob#getTarget} ask this, so a target
     * that leaves the arena is let go and forgotten, instead of being handed back by the goal that remembers it.
     */
    @Override
    public boolean canAttack(LivingEntity target) {
        return super.canAttack(target) && inArena(target);
    }

    /** It fights whoever hurts it and the nearest player it sees, while they stand in its arena. */
    @Override
    protected void addTargetGoals() {
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
                (target, level) -> inArena(target)));
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(target);
        if (target != null) {
            engaged = true;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level && !isDeadOrDying()) {
            followFight(level);
        }
        bossBar.update();
    }

    /**
     * The fight on the server, every tick: the arena and the end of a fight, the phases while a fight runs, the phase
     * change.
     */
    private void followFight(ServerLevel level) {
        Optional<Holder.Reference<Boss>> boss = definition();
        if (boss.isEmpty()) {
            return;
        }
        if (!inArena(this) || (engaged && getTarget() == null && !InCombat.isInCombat(this))) {
            reset();
            return;
        }
        if (engaged) {
            OptionalInt next = BossPhaseMachine.next(boss.get().value().phases(), phase,
                    new BossPhaseMachine.Progress(getHealth() / getMaxHealth(), phaseTicks));
            if (next.isPresent()) {
                enterPhase(next.getAsInt());
            } else {
                phaseTicks++;
            }
        }
        if (phaseChangeLeft > 0) {
            if (phaseChangeLeft % PHASE_CHANGE_PARTICLE_INTERVAL == 0) {
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY() + getBbHeight() / 2, getZ(), 12,
                        getBbWidth() / 2, getBbHeight() / 3, getBbWidth() / 2, 0.2);
            }
            phaseChangeLeft--;
        }
        if (tickCount % LANDING_RING_INTERVAL == 0) {
            markLanding(level);
        }
    }

    /** Where the area of a cast lands: the middle and the radius of the ring that marks it. */
    public record Landing(Vec3 middle, double radius) {
    }

    /**
     * Where the area of its cast in progress will land, when the spell's first effect is an area: round the entity the
     * cast acts on (itself, or its target where it stands now), or round the ground point the cast was aimed at when
     * it began. On the server only.
     */
    public Optional<Landing> landing() {
        Optional<CombatState.Cast> cast = Spells.currentCast(this);
        Optional<SpellTarget> target = Spells.currentCastTarget(this);
        if (cast.isEmpty() || target.isEmpty()) {
            return Optional.empty();
        }
        Vec3 middle = target.get().entity().map(Entity::position).orElse(target.get().position());
        return Spells.get(registryAccess(), cast.get().spell())
                .flatMap(spell -> landingArea(spell.value()))
                .map(area -> new Landing(middle, area.radius()));
    }

    /** While it casts, a ring of particles marks where the cast's area lands ({@link #landing}). */
    private void markLanding(ServerLevel level) {
        landing().ifPresent(landing -> {
            int count = Mth.ceil(2 * Math.PI * landing.radius() / LANDING_RING_SPACING);
            for (int i = 0; i < count; i++) {
                double angle = 2 * Math.PI * i / count;
                level.sendParticles(LANDING_RING, landing.middle().x + Math.cos(angle) * landing.radius(),
                        landing.middle().y + 0.1, landing.middle().z + Math.sin(angle) * landing.radius(), 1, 0, 0,
                        0, 0);
            }
        });
    }

    /** The area {@code spell} lands, its first effect, if that is one. */
    private static Optional<AreaEffect> landingArea(Spell spell) {
        if (spell.effects().isEmpty()) {
            return Optional.empty();
        }
        SpellEffect first = spell.effects().getFirst();
        return first instanceof AreaEffect area ? Optional.of(area) : Optional.empty();
    }

    /**
     * Moves it into phase {@code index} (0 for the first): the cast in progress ends, a later phase casts its
     * {@code on_enter} spells and starts the phase change, and the bar shows the phase. Tests and {@code /qa boss}
     * call it too.
     */
    public void enterPhase(int index) {
        Optional<Holder.Reference<Boss>> boss = definition();
        if (boss.isEmpty() || index < 0 || index >= boss.get().value().phases().size()) {
            throw new IllegalArgumentException("no phase " + index + " for " + getType());
        }
        phase = index;
        phaseTicks = 0;
        Spells.cancelCast(this);
        getNavigation().stop();
        BossPhase entered = boss.get().value().phases().get(index);
        phaseChangeLeft = index > 0 ? boss.get().value().phaseChangeTicks() : 0;
        for (ResourceKey<Spell> key : entered.onEnter()) {
            Spells.get(registryAccess(), key).ifPresent(spell -> SpellCaster.castProc(this, spell, 1,
                    aim(spell.value().targeting(), getTarget()), PHASE_SPELLS));
        }
        updateTitle();
    }

    /**
     * Puts it back as it was before the fight: home, full health, the first phase, without effects, a cast, cooldowns
     * or a target.
     */
    public void reset() {
        Spells.cancelCast(this);
        Spells.resetCooldowns(this);
        abilityReady.clear();
        setTarget(null);
        setLastHurtByMob(null);
        getNavigation().stop();
        removeAllEffects();
        home().ifPresent(home -> teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5));
        setDeltaMovement(Vec3.ZERO);
        setHealth(getMaxHealth());
        engaged = false;
        phase = 0;
        phaseTicks = 0;
        phaseChangeLeft = 0;
        updateTitle();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        // A target outside the arena is none: getTarget asks canAttack.
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || changingPhase() || Spells.currentCast(this).isPresent()) {
            return;
        }
        if (--castDelay > 0) {
            return;
        }
        castDelay = CAST_INTERVAL;
        castAbility(level, target);
    }

    /** Casts the first ability of its phase that is ready and that the engine lets it cast. */
    private void castAbility(ServerLevel level, LivingEntity target) {
        Optional<BossPhase> current = phase();
        if (current.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        for (BossAbility ability : current.get().abilities()) {
            if (abilityReady.getOrDefault(ability.spell(), 0L) > now) {
                continue;
            }
            Optional<Holder.Reference<Spell>> spell = Spells.get(registryAccess(), ability.spell());
            if (spell.isEmpty() || !worthCasting(spell.get().value(), target)) {
                continue;
            }
            if (SpellCaster.cast(this, ability.spell(), aim(spell.get().value().targeting(), target)).success()) {
                abilityReady.put(ability.spell(), now + ability.cooldownTicks());
                return;
            }
        }
    }

    /**
     * Whether {@code spell} is worth casting at {@code target} now: an area round the boss itself (a quake) only while
     * the target stands inside it, measured as the area measures; every other spell whenever the engine allows it.
     */
    private boolean worthCasting(Spell spell, LivingEntity target) {
        if (!(spell.targeting() instanceof SelfTargeting)) {
            return true;
        }
        return landingArea(spell).map(area -> {
            double dx = target.getX() - getX();
            double dz = target.getZ() - getZ();
            return dx * dx + dz * dz <= area.radius() * area.radius();
        }).orElse(true);
    }

    /** What a cast aims at: its target, unless the spell picks its own target or acts on the caster. */
    private static SpellAim aim(SpellTargeting targeting, @Nullable LivingEntity target) {
        boolean picksItself = targeting instanceof RandomPlayerTargeting || targeting instanceof FarthestPlayerTargeting
                || targeting instanceof SelfTargeting;
        return picksItself ? SpellAim.NONE : SpellAim.at(target);
    }

    /** No hurt while it changes phase, nor from outside its arena; a refused hit sounds blocked. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && definition().isPresent()
                && (changingPhase() || (source.getEntity() != null && !inArena(source.getEntity())))) {
            playSound(SoundEvents.SHIELD_BLOCK.value(), 1.0F, 0.8F + getRandom().nextFloat() * 0.4F);
            return false;
        }
        return super.hurtServer(level, source, damage);
    }

    private void updateTitle() {
        Optional<BossPhase> current = Optional.ofNullable(definition).map(boss -> boss.value().phases().get(phase));
        bossBar.setTitle(current.<Component>map(entered -> Component.translatable(GeneratedTexts.GUI_BOSS_BAR_TITLE,
                getDisplayName(), entered.displayName())).orElse(null));
    }

    /** It never goes through a portal, as the Wither does not: its arena and home stay in its own dimension. */
    @Override
    public boolean canUsePortal(boolean ignorePassenger) {
        return false;
    }

    /** A boss never despawns, however far the players are (RLM-482). */
    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossBar.startSeenBy(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.stopSeenBy(player);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt(PHASE, phase);
        output.putInt(PHASE_TICKS, phaseTicks);
        output.putInt(PHASE_CHANGE, phaseChangeLeft);
        output.putBoolean(ENGAGED, engaged);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        phase = Math.max(0, input.getIntOr(PHASE, 0));
        phaseTicks = Math.max(0, input.getIntOr(PHASE_TICKS, 0));
        phaseChangeLeft = Math.max(0, input.getIntOr(PHASE_CHANGE, 0));
        engaged = input.getBooleanOr(ENGAGED, false);
        if (definition != null) {
            phase = Math.min(phase, definition.value().phases().size() - 1);
            updateTitle();
        }
    }
}
