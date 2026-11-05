package io.github.matejkadlec.realmforge.character;

import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellRegistries;
import io.github.matejkadlec.realmforge.spell.Spells;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerPlayer;

/**
 * Teaches a character their race's racial spell (C4, architecture 10.1), into the racial slot beside the bar (X), from
 * creation on. A login catches up, so a character created before their race had one gets it. Class spells come from the
 * specialization's spell tree ({@link SpellTrees}).
 */
public final class RacialSpells {
    private RacialSpells() {
    }

    /** Teaches the player their racial spell, if they have a race with one. */
    public static void teach(ServerPlayer player) {
        Registry<Spell> spells = player.registryAccess().lookupOrThrow(SpellRegistries.SPELL);
        Characters.data(player).race()
                .flatMap(key -> player.registryAccess().lookupOrThrow(CharacterRegistries.RACE).get(key))
                .flatMap(race -> race.value().racialSpell())
                .flatMap(spell -> spell.unwrapKey().flatMap(spells::get))
                .ifPresent(spell -> Spells.learnRacial(player, spell));
    }
}
