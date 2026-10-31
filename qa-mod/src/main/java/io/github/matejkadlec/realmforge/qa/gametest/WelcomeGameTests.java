package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.core.WelcomeMessage;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import io.github.matejkadlec.realmforge.qa.FtbLibraryGreeting;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

/**
 * RLM-520: a player gets "Welcome to Realmforge v0.0.1 (Alpha)" in chat once per login (the core mod's
 * {@code core/WelcomeMessage}), and the dev client's filter drops exactly the greeting FTB Library sends in a
 * development environment, which the GameTest server is.
 */
public final class WelcomeGameTests {
    private WelcomeGameTests() {
    }

    /**
     * One welcome line on login, with the mod's version (no "+mc" suffix) and phase from its metadata; which phase
     * {@code gradle.properties} names is {@code tools/tests/test_versions.py}'s check.
     */
    @QaGameTest
    public static void aLoginGetsTheWelcomeLineOnce(GameTestHelper helper) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-welcome");
        try {
            List<TranslatableContents> welcomes = welcomes(QaPlayers.sentChatLines(player));
            helper.assertTrue(welcomes.size() == 1, "one welcome line on login: " + welcomes);
            TranslatableContents welcome = welcomes.getFirst();
            IModInfo info = ModList.get().getModContainerById(Realmforge.MOD_ID).orElseThrow().getModInfo();
            String version = info.getVersion().toString();
            helper.assertTrue(version.contains("+mc"), "the jar's version carries the Minecraft suffix: " + version);
            Object phase = info.getModProperties().get("phase");
            helper.assertTrue(phase != null, "neoforge.mods.toml hands the mod its phase (mod_phase)");
            Component expected = WelcomeMessage.message(WelcomeMessage.version(version), WelcomeMessage.phase(phase));
            helper.assertTrue(expected.getContents().equals(welcome),
                    "the line shows the version without its suffix and the phase: " + welcome);
            helper.assertTrue(!welcome.getArgs()[0].toString().contains("+"), "no build metadata in the line");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** A respawn is no login: no second welcome line. */
    @QaGameTest
    public static void aRespawnGetsNoWelcomeLine(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-welcome-respawn");
        try {
            QaPlayers.sentChatLines(player);
            player = server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
            List<TranslatableContents> welcomes = welcomes(QaPlayers.sentChatLines(player));
            helper.assertTrue(welcomes.isEmpty(), "no welcome line on respawn: " + welcomes);
        } finally {
            server.getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * FTB Library greets on login in a development environment, and the dev client's filter matches that greeting,
     * not our welcome line. A FTB Library update that rewords it fails here.
     */
    @QaGameTest
    public static void theFilterMatchesFtbLibrarysGreeting(GameTestHelper helper) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-welcome-ftb");
        try {
            List<Component> lines = QaPlayers.sentChatLines(player);
            long greetings = lines.stream().filter(FtbLibraryGreeting::isGreeting).count();
            helper.assertTrue(greetings == 1, "FTB Library's greeting is sent once and matched: " + lines);
            helper.assertTrue(!FtbLibraryGreeting.isGreeting(WelcomeMessage.message()), "the filter keeps our line");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    private static List<TranslatableContents> welcomes(List<Component> lines) {
        return lines.stream()
                .map(Component::getContents)
                .filter(TranslatableContents.class::isInstance)
                .map(TranslatableContents.class::cast)
                .filter(contents -> contents.getKey().equals(GeneratedTexts.MESSAGE_WELCOME)
                        || contents.getKey().equals(GeneratedTexts.MESSAGE_WELCOME_PHASE))
                .toList();
    }
}
