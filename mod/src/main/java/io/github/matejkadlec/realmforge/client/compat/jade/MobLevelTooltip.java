package io.github.matejkadlec.realmforge.client.compat.jade;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.client.compat.MobLevelLabel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.JadeIds;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade's tooltip names a mob with its level first (RLM-338, {@link MobLevelLabel}): it replaces the name line that
 * Jade's own provider adds ({@link JadeIds#CORE_OBJECT_NAME}, priority -10100), so it runs after it, at the default
 * priority. It is required, so Jade's settings list no switch for it, and a mob without a level keeps Jade's name.
 */
public final class MobLevelTooltip implements IEntityComponentProvider {
    private static final Identifier UID = Realmforge.id("mob_level");
    private static final MobLevelTooltip INSTANCE = new MobLevelTooltip();

    private MobLevelTooltip() {
    }

    public static void register(IWailaClientRegistration registration) {
        registration.registerEntityComponent(INSTANCE, Mob.class);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        MobLevelLabel.forMob(accessor.getEntity(), accessor.getPlayer())
                .ifPresent(line -> tooltip.replace(JadeIds.CORE_OBJECT_NAME, line));
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    @Override
    public boolean isRequired() {
        return true;
    }
}
