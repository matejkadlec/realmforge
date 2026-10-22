package io.github.matejkadlec.realmforge.compat.jade;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.client.compat.jade.MobLevelTooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Our Jade plugin (architecture 19.4). Jade finds it by its annotation, on the client and the server, and only while
 * Jade is loaded, so it needs no registration of ours; it adds nothing on the server. On the client, Jade's tooltip
 * shows a mob's level before its name ({@link MobLevelTooltip}, RLM-338).
 */
@WailaPlugin(Realmforge.MOD_ID)
public final class JadeCompat implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        MobLevelTooltip.register(registration);
    }
}
