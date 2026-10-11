package io.github.matejkadlec.realmforge.qa.client;

import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import io.github.matejkadlec.realmforge.qa.client.castanim.QaCastAnimationClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** Client entry point of the QA mod: everything that touches rendering or input. Never loaded on a server. */
@Mod(value = RealmforgeQa.MOD_ID, dist = Dist.CLIENT)
public final class RealmforgeQaClient {
    public RealmforgeQaClient(IEventBus modEventBus) {
        QaCastAnimationClient.register(modEventBus);
    }
}
