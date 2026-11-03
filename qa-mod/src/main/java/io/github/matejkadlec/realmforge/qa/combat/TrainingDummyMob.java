package io.github.matejkadlec.realmforge.qa.combat;

import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * The training dummy's entity, {@code realmforge_qa:training_dummy} ({@link TrainingDummy}, RLM-528): a monster of its
 * own, so spells and aim assist treat it as hostile and it gets a level like any hostile mob, but in no entity type
 * tag, so it has no creature type (no undead, arthropod or illager rules, no icon in Neat's card). It looks like a
 * pillager with its arms down, has {@value TrainingDummy#MAX_HEALTH} health, no armor, every stat of ours and no AI,
 * makes no sound, and stays in peaceful (its type is not {@code notInPeaceful}, unlike vanilla's monsters).
 */
public final class TrainingDummyMob extends Monster {
    public TrainingDummyMob(EntityType<? extends TrainingDummyMob> type, Level level) {
        super(type, level);
        setNoAi(true);
        setSilent(true);
        setPersistenceRequired();
    }

    /** Its attributes: a monster's, {@value TrainingDummy#MAX_HEALTH} health, no knockback, and every stat of ours. */
    public static AttributeSupplier.Builder attributes() {
        AttributeSupplier.Builder builder = Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, TrainingDummy.MAX_HEALTH)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1);
        for (Holder<Attribute> stat : GeneratedStats.ALL) {
            builder.add(stat);
        }
        return builder;
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return null;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSqr) {
        return false;
    }
}
