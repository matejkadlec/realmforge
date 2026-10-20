package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.core.HasAdvancement;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedRealmExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedRealmExpectations.RealmExpectation;
import io.github.matejkadlec.realmforge.world.DimensionRules;
import io.github.matejkadlec.realmforge.world.WorldRegistries;
import io.github.matejkadlec.realmforge.world.realm.Realm;
import io.github.matejkadlec.realmforge.world.realm.Realms;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * W2a (RLM-32), W2b (RLM-154): every entry of {@code content/realms/} reached the datapack registry
 * {@code realmforge:realm} as written, with its signet and portal colour; a realm's unlock advancement exists with
 * {@code minecraft:impossible} as its only criterion and a hidden toast, and its dimension's rules require that unlock.
 * Overworld's entry is no realm and has neither ({@link GeneratedRealmExpectations}).
 */
public final class ContentRealmGameTests {
    private ContentRealmGameTests() {
    }

    @QaGameTest
    public static void contentRealmsAreLoadedAsWritten(GameTestHelper helper) {
        helper.assertFalse(GeneratedRealmExpectations.REALMS.isEmpty(), "no realm expectations were generated");
        Registry<Realm> realms = helper.getLevel().registryAccess().lookupOrThrow(WorldRegistries.REALM);
        Set<String> expectedIds = new TreeSet<>();
        GeneratedRealmExpectations.REALMS.forEach(expected -> expectedIds.add(expected.id()));
        Set<String> loadedIds = new TreeSet<>();
        realms.keySet().stream()
                .filter(id -> id.getNamespace().equals(Realmforge.MOD_ID))
                .forEach(id -> loadedIds.add(id.toString()));
        helper.assertValueEqual(loadedIds, expectedIds, "realms in realmforge:realm");

        for (RealmExpectation expected : GeneratedRealmExpectations.REALMS) {
            Realm realm = realms.getValue(Identifier.parse(expected.id()));
            helper.assertTrue(realm != null, "realm is not loaded: " + expected.id());
            ResourceKey<Level> dimension =
                    ResourceKey.create(Registries.DIMENSION, Identifier.parse(expected.dimension()));
            helper.assertValueEqual(realm.dimension(), dimension, expected.id() + " dimension");
            helper.assertValueEqual(realm.name(), expected.nameKey(), expected.id() + " name");
            helper.assertValueEqual(realm.lockedHint(), expected.lockedHintKey(), expected.id() + " hint");
            helper.assertTrue(Language.getInstance().has(realm.name()), expected.id() + ": no en_us name");
            helper.assertValueEqual(realm.colour() & 0xFFFFFF, expected.colour(), expected.id() + " portal colour");
            helper.assertValueEqual(realm.signet().map(Identifier::toString).orElse(""), expected.signet(),
                    expected.id() + " signet");
            if (dimension.equals(Level.OVERWORLD)) {
                helper.assertTrue(expected.unlockAdvancement().isEmpty(), "Overworld needs no unlock");
                helper.assertTrue(Realms.overworld(helper.getLevel().registryAccess()).isPresent()
                        && !Realms.isRealm(helper.getLevel().registryAccess(), dimension), "Overworld is no realm");
                continue;
            }
            helper.assertTrue(Language.getInstance().has(realm.lockedHint()), expected.id() + ": no en_us hint");
            helper.assertTrue(realm.signet().flatMap(BuiltInRegistries.ITEM::getOptional)
                            .flatMap(signet -> Realms.bySignet(helper.getLevel().registryAccess(), signet))
                            .map(holder -> holder.key().identifier().toString().equals(expected.id()))
                            .orElse(false), expected.id() + " is its signet's realm");

            ResourceKey<Realm> key = ResourceKey.create(WorldRegistries.REALM, Identifier.parse(expected.id()));
            Identifier unlock = Realms.unlockAdvancement(key);
            helper.assertValueEqual(unlock.toString(), expected.unlockAdvancement(), expected.id() + " unlock id");
            AdvancementHolder advancement = helper.getLevel().getServer().getAdvancements().get(unlock);
            helper.assertTrue(advancement != null, "unlock advancement is not loaded: " + unlock);
            helper.assertValueEqual(advancement.value().criteria().size(), 1, unlock + " criteria");
            helper.assertTrue(advancement.value().criteria().values().iterator().next().trigger()
                            == net.minecraft.advancements.CriteriaTriggers.IMPOSSIBLE,
                    unlock + " is granted only by a quest or a command");
            helper.assertTrue(advancement.value().display().map(display -> display.isHidden()
                            && display.shouldShowToast() && !display.shouldAnnounceChat()).orElse(false),
                    unlock + " is hidden, shows a toast and adds no chat line");

            DimensionRules rules = DimensionRules.of(helper.getLevel().registryAccess(), dimension).orElse(null);
            helper.assertTrue(rules != null, expected.dimension() + " has dimension rules");
            helper.assertTrue(rules.access().contains(new HasAdvancement(unlock)),
                    expected.dimension() + " requires the realm's unlock");
        }
        helper.succeed();
    }
}
