package io.github.matejkadlec.realmforge.compat.ftbteams;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Turns FTB Teams' parties off (RLM-206, architecture 19.4). FTB Quests keeps quest progress per FTB Teams team, so a
 * player who joined someone's party would get that team's realm unlocks and chapter advancements at their next login,
 * which breaks Matěj's rule for shared quests (RLM-156): progress stays per player until our own parties (RLM-68)
 * share quests by that rule.
 *
 * <p>FTB Teams' own switch for a pack that brings its own parties, {@code setPartyCreationFromAPIOnly(true)}, refuses
 * {@code /ftbteams party create} and the My Team screen's Create a Party for every player, operators included, and
 * leaves creating a party to code through its API, which no mod of the pack calls. Without a party, nobody can invite
 * anyone or join one. Its refusal, {@code ftbteams.party_api_only}, reads as ours from the core mod's resources
 * ({@code assets/ftbteams/lang/en_us.json}), which load after FTB Teams' ({@code neoforge.mods.toml}). Runs only while
 * FTB Teams is loaded; the core mod compiles against the pack's build. Tested by the QA mod's
 * {@code FtbTeamsGameTests}.
 */
public final class FtbTeamsParties {
    public static final String MOD_ID = "ftbteams";

    private FtbTeamsParties() {
    }

    /** Registers the listener. Called once from {@code CompatFeature} when FTB Teams is loaded. */
    public static void register(IEventBus modBus) {
        // FTB Teams hands its API out from its own constructor, so it exists by common setup, before any world loads.
        modBus.addListener(FMLCommonSetupEvent.class,
                event -> event.enqueueWork(() -> FTBTeamsAPI.api().setPartyCreationFromAPIOnly(true)));
    }
}
