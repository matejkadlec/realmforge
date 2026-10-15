package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.character.CharacterRegistries;
import io.github.matejkadlec.realmforge.character.KillXpMultiplier;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedKillXpMultiplierExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedKillXpMultiplierExpectations.KillXpMultiplierExpectation;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedKillXpMultiplierExpectations.Sample;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

/**
 * C2 (RLM-23): every entry of {@code content/kill_xp_multipliers/} reached the server's datapack registry
 * {@code realmforge:kill_xp_multiplier} as written, and gives the pipeline's multiplier at the sample sizes
 * ({@link GeneratedKillXpMultiplierExpectations}).
 */
public final class ContentKillXpMultiplierGameTests {
    private ContentKillXpMultiplierGameTests() {
    }

    @QaGameTest
    public static void contentKillXpMultipliersAreLoadedAsWritten(GameTestHelper helper) {
        helper.assertFalse(GeneratedKillXpMultiplierExpectations.MULTIPLIERS.isEmpty(),
                "no kill XP multiplier expectations were generated");
        Registry<KillXpMultiplier> multipliers =
                helper.getLevel().registryAccess().lookupOrThrow(CharacterRegistries.KILL_XP_MULTIPLIER);
        Set<String> expectedIds = new TreeSet<>();
        GeneratedKillXpMultiplierExpectations.MULTIPLIERS.forEach(expected -> expectedIds.add(expected.id()));
        Set<String> loadedIds = new TreeSet<>();
        multipliers.keySet().stream()
                .filter(id -> id.getNamespace().equals(Realmforge.MOD_ID))
                .forEach(id -> loadedIds.add(id.toString()));
        helper.assertValueEqual(loadedIds, expectedIds, "entries in realmforge:kill_xp_multiplier");

        for (KillXpMultiplierExpectation expected : GeneratedKillXpMultiplierExpectations.MULTIPLIERS) {
            KillXpMultiplier entry = multipliers.getValue(Identifier.parse(expected.id()));
            helper.assertTrue(entry != null, "kill XP multiplier is not loaded: " + expected.id());
            helper.assertValueEqual(entry.entity().getRegisteredName(), expected.entity(), expected.id() + " entity");
            helper.assertValueEqual(entry.multiplier(), expected.multiplier(), expected.id() + " multiplier");
            helper.assertValueEqual(entry.bySize().size(), expected.bySize().size(), expected.id() + " size entries");
            for (int i = 0; i < expected.bySize().size(); i++) {
                helper.assertValueEqual(entry.bySize().get(i).maxSize(), expected.bySize().get(i).maxSize(),
                        expected.id() + " max_size of size entry " + i);
                helper.assertValueEqual(entry.bySize().get(i).multiplier(), expected.bySize().get(i).multiplier(),
                        expected.id() + " multiplier of size entry " + i);
            }
            for (Sample sample : expected.samples()) {
                OptionalInt size = sample.size() < 0 ? OptionalInt.empty() : OptionalInt.of(sample.size());
                helper.assertValueEqual(entry.at(size), sample.multiplier(), expected.id() + " at size " + size);
            }
        }
        helper.succeed();
    }
}
