package io.github.matejkadlec.realmforge.qa.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.data.PlayerPermissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;

/**
 * RLM-206: FTB Teams' parties are off in the pack (the core mod's {@code compat/ftbteams/FtbTeamsParties}, architecture
 * 19.4), so quest progress, realm unlocks included, stays per player. The GameTest server loads FTB Teams as the pack
 * does (ADR 0042).
 */
public final class FtbTeamsGameTests {
    /** FTB Teams' refusal, which the core mod's resources word as ours. */
    private static final String REFUSAL = "ftbteams.party_api_only";

    private FtbTeamsGameTests() {
    }

    /** {@code /ftbteams party create} is refused for a player and an operator, and both stay in their own team. */
    @QaGameTest
    public static void noPlayerCanCreateAParty(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-party-player");
        try {
            assertOwnTeam(helper, player);
            assertRefused(helper, player.createCommandSourceStack(), "party create Raiders", REFUSAL);
            assertRefused(helper, player.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS),
                    "party create Raiders", REFUSAL);
            assertOwnTeam(helper, player);
        } finally {
            server.getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** The My Team screen offers no Create a Party: FTB Teams sends it the permissions this reads. */
    @QaGameTest
    public static void theMyTeamScreenOffersNoParty(GameTestHelper helper) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-party-screen");
        try {
            helper.assertTrue(!PlayerPermissions.forPlayer(player).createParty(),
                    "the My Team screen offers no Create a Party");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** Without a party nobody can invite anyone, so nobody joins one either. */
    @QaGameTest
    public static void nobodyCanInviteWithoutAParty(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer host = QaPlayers.loginOnPlatform(helper, "qa-party-host");
        ServerPlayer guest = QaPlayers.loginOnPlatform(helper, "qa-party-guest");
        try {
            assertRefused(helper, host.createCommandSourceStack(), "party invite qa-party-guest", null);
            assertOwnTeam(helper, host);
            assertOwnTeam(helper, guest);
        } finally {
            server.getPlayerList().remove(host);
            server.getPlayerList().remove(guest);
        }
        helper.succeed();
    }

    private static void assertOwnTeam(GameTestHelper helper, ServerPlayer player) {
        Team team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
        helper.assertTrue(team != null && team.isPlayerTeam(),
                player.getPlainTextName() + " is in their own team: " + (team == null ? "none" : team.getShortName()));
    }

    /** Runs {@code /ftbteams <command>} and checks that it fails, with the translation key {@code key} if given. */
    private static void assertRefused(GameTestHelper helper, CommandSourceStack source, String command, String key) {
        try {
            helper.getLevel().getServer().getCommands().getDispatcher().execute("ftbteams " + command, source);
        } catch (CommandSyntaxException refused) {
            if (key != null) {
                Component message = Component.translationArg(refused.getRawMessage());
                helper.assertTrue(message.getContents() instanceof TranslatableContents contents
                        && contents.getKey().equals(key), "/ftbteams " + command + " refused with " + key + ": "
                        + message.getString());
            }
            return;
        }
        throw helper.assertionException(Component.literal("/ftbteams " + command + " succeeded"));
    }
}
