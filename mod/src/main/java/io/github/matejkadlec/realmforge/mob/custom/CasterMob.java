package io.github.matejkadlec.realmforge.mob.custom;

import com.geckolib.animatable.manager.AnimatableManager;
import io.github.matejkadlec.realmforge.spell.CastEnd;
import io.github.matejkadlec.realmforge.spell.CastEndedEvent;
import io.github.matejkadlec.realmforge.spell.Spells;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The caster archetype (architecture 14.2, {@code archetype: caster}): it casts its spells at its target, standing
 * still and facing it, goes after a target it cannot reach, and steps aside between its casts
 * ({@link CasterFightGoal}). Clients see a cast in progress as every custom mob's ({@link #isCasting}); the renderer
 * draws the projectile a caster's spell forms.
 */
public class CasterMob extends CustomMob {
    /** The shortest reach of its spells, read from the spell registry once; negative until then. */
    private double reach = -1;
    /** How its last cast ended, until its fight goal reads it. Server only. */
    private @Nullable CastEnd castEnd;

    public CasterMob(EntityType<? extends CasterMob> type, Level level, CustomMobSpec spec) {
        super(type, level, spec);
    }

    @Override
    protected void addFightGoals() {
        goalSelector.addGoal(2, new CasterFightGoal(this));
    }

    /**
     * The shortest reach of its spells, in blocks: within it, every one of them reaches its target. 0 when none of them
     * exists.
     */
    public double reach() {
        if (reach < 0) {
            reach = spec().spells().stream()
                    .flatMap(spell -> Spells.get(registryAccess(), spell).stream())
                    .mapToDouble(spell -> spell.value().targeting().reach())
                    .min().orElse(0);
        }
        return reach;
    }

    /** How its last cast ended, once: the fight goal steps aside after a completed one. */
    Optional<CastEnd> takeCastEnd() {
        Optional<CastEnd> end = Optional.ofNullable(castEnd);
        castEnd = null;
        return end;
    }

    /** {@code CastEndedEvent}: a caster's cast ended (registered by {@code MobFeature}). */
    public static void onCastEnded(CastEndedEvent event) {
        if (event.getCaster() instanceof CasterMob mob) {
            mob.castEnd = event.getEnd();
        }
    }

    /** Its cast animation is every custom mob's ({@link CustomMob#isCasting}). */
    @Override
    protected void addActionControllers(AnimatableManager.ControllerRegistrar controllers) {
    }
}
