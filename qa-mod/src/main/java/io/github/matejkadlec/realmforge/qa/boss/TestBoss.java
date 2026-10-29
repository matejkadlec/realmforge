package io.github.matejkadlec.realmforge.qa.boss;

import io.github.matejkadlec.realmforge.boss.BossBar;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * The QA test boss (RLM-173, architecture 20): a zombie half again as big, with {@value #MAX_HEALTH} health at level
 * 1, in {@code c:bosses}, that carries the core mod's {@link BossBar} without a fight of its own, so the bar can be
 * tried and tested on its own (the boss framework's test boss is {@code realmforge:test_titan}, W5a).
 * {@code /summon realmforge_qa:test_boss} in the dev client. It is always an adult on foot and never a
 * zombie leader, and it neither burns in daylight nor turns into a drowned. Like every hostile mob, it gets a level
 * when it joins the world (architecture 14.1). Like vanilla's bosses, it never despawns, however far the players are
 * (RLM-482): {@code /summon} leaves its persistence unset, so it requires it itself.
 */
public final class TestBoss extends Zombie {
    public static final double MAX_HEALTH = 200;

    private final BossBar bossBar = new BossBar(this, BossEvent.BossBarColor.RED);

    public TestBoss(EntityType<? extends TestBoss> type, Level level) {
        super(type, level);
    }

    static AttributeSupplier.Builder attributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.SCALE, 1.5)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1);
    }

    public BossBar bossBar() {
        return bossBar;
    }

    @Override
    public void tick() {
        super.tick();
        bossBar.update();
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

    /** A boss never despawns: {@code Mob#checkDespawn} keeps a mob that requires persistence (RLM-482). */
    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    /** Never a baby, so never a chicken jockey either. */
    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        return super.finalizeSpawn(level, difficulty, spawnReason, new Zombie.ZombieGroupData(false, false));
    }

    /** No zombie leader's health, no reinforcements: the boss keeps its own attributes. */
    @Override
    protected void handleAttributes(float difficultyModifier, EntitySpawnReason spawnReason) {
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }
}
