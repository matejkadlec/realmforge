package io.github.matejkadlec.realmforge.qa;

import com.mojang.logging.LogUtils;
import io.github.matejkadlec.realmforge.qa.castanim.QaCastAnimation;
import io.github.matejkadlec.realmforge.qa.combat.CombatLog;
import io.github.matejkadlec.realmforge.qa.combat.TrainingDummy;
import io.github.matejkadlec.realmforge.qa.command.QaCommands;
import io.github.matejkadlec.realmforge.qa.gametest.QaGameTests;
import io.github.matejkadlec.realmforge.qa.instance.InstanceBench;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/** Entry point of the dev-only Realmforge QA mod. Never released. */
@Mod(RealmforgeQa.MOD_ID)
public final class RealmforgeQa {
    public static final String MOD_ID = "realmforge_qa";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RealmforgeQa(IEventBus modEventBus) {
        QaCastAnimation.register(modEventBus);
        QaGameTests.register(modEventBus);
        TrainingDummy.register(modEventBus);
        CombatLog.register();
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, QaCommands::register);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class,
                event -> InstanceBench.onServerTick(event.getServer()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> InstanceBench.onServerStopped());
    }

    /** Returns {@code realmforge_qa:<path>}. */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
